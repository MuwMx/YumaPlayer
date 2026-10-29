/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.SpotifyMatchEntity
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.spotify.mapping.SpotifyEntityMapper
import moe.rukamori.archivetune.spotify.mapping.SpotifyQueryBuilder
import moe.rukamori.archivetune.spotify.matching.SpotifySearchScorer
import moe.rukamori.archivetune.spotify.matching.TrackMatcher
import moe.rukamori.archivetune.spotify.matching.TrackQuery
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

object SpotifyPlaybackResolver {
    private const val CACHE_MAX_SIZE = 512
    private const val SONG_LOOKUP_CHUNK = 500
    private const val YIELD_MS = 100L
    private const val BACKGROUND_YIELD_MS = 250L

    // Sustained background searching is what gets an IP rate-limited.
    private const val BACKGROUND_SPACING_MS = 2_500L

    private val matcher = TrackMatcher()
    private val scorer = SpotifySearchScorer(matcher)

    // Searches for the song being played never wait; searches for songs that are
    // only being prepared yield to them and to each other, so preparing a queue
    // cannot slow down the current song or get the IP rate-limited.
    private val searchSemaphore = Semaphore(4)
    private val upcomingSemaphore = Semaphore(2)
    private val backgroundSemaphore = Semaphore(1)
    private val playingSearches = AtomicInteger(0)
    private val upcomingSearches = AtomicInteger(0)

    private val mutex = Mutex()
    private val cache =
        object : LinkedHashMap<String, MediaMetadata>(CACHE_MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaMetadata>?): Boolean = size > CACHE_MAX_SIZE
        }

    @Volatile
    private var databaseRef: MusicDatabase? = null

    /** Gives the resolver the database to read stored matches from before the first lookup. */
    fun attachDatabase(database: MusicDatabase) {
        databaseRef = database
    }

    fun database(): MusicDatabase? = databaseRef

    /**
     * Looks up the stored YouTube matches for the Spotify placeholders in [items] in one
     * database pass, keyed by placeholder id. It never searches YouTube, so a full
     * playlist can be queued instantly; placeholders without a usable match are left out.
     */
    suspend fun resolveStoredPlaceholders(items: List<MediaMetadata>): Map<String, MediaMetadata> =
        withContext(Dispatchers.IO) {
            val db = databaseRef ?: return@withContext emptyMap()
            val bySpotifyId =
                items
                    .filter { SpotifyPlaceholders.isPlaceholder(it.id) }
                    .associateBy { it.id.removePrefix(SPOTIFY_PLACEHOLDER_PREFIX) }
            if (bySpotifyId.isEmpty()) return@withContext emptyMap()

            val matches = db.getSpotifyMatches(bySpotifyId.keys.toList())
            val songs =
                matches
                    .map { it.youtubeId }
                    .distinct()
                    .chunked(SONG_LOOKUP_CHUNK)
                    .flatMap { ids -> db.getSongsByIds(ids) }
                    .associateBy { it.id }

            buildMap {
                matches.forEach { match ->
                    val source = bySpotifyId[match.spotifyId] ?: return@forEach
                    val song = songs[match.youtubeId]
                    if (song != null && !match.isManualOverride && !hasSharedArtist(source, song.toMediaMetadata())) {
                        return@forEach
                    }
                    val matched = song?.toMediaMetadata()
                    put(
                        source.id,
                        (matched ?: source.copy(id = match.youtubeId)).copy(
                            title = song?.song?.title ?: source.title,
                            thumbnailUrl = source.thumbnailUrl ?: song?.song?.thumbnailUrl,
                            duration = if (source.duration > 0) source.duration else song?.song?.duration ?: -1,
                            album = source.album ?: matched?.album,
                            spotifyTrackId = match.spotifyId,
                        ),
                    )
                }
            }
        }

    private fun hasSharedArtist(
        source: MediaMetadata,
        candidate: MediaMetadata,
    ): Boolean =
        scorer.hasArtistEvidence(
            trackArtists = source.artists.map { it.name },
            candidateTitle = candidate.title,
            candidateArtists = candidate.artists.map { it.name },
        )

    suspend fun resolveToMediaItem(
        track: SpotifyTrack,
        database: MusicDatabase? = null,
    ): MediaItem? = resolveToMetadata(track, database)?.toMediaItem()

    /**
     * Finds the YouTube song for [track], using a stored match when there is one.
     *
     * @param priority who is waiting for the match; see [MatchPriority].
     * @param throwOnSearchFailure throw an [IOException] instead of returning null
     * when nothing matched and YouTube searches failed (rate limit, no network),
     * so callers can tell "try again" from "not on YouTube".
     */
    suspend fun resolveToMetadata(
        track: SpotifyTrack,
        database: MusicDatabase? = null,
        priority: MatchPriority = MatchPriority.PLAYING,
        throwOnSearchFailure: Boolean = false,
    ): MediaMetadata? =
        withContext(Dispatchers.IO) {
            val db = database ?: databaseRef
            if (database != null && databaseRef == null) {
                databaseRef = database
            }

            val rawSpotifyId = track.id.removePrefix("spotify:track:").removePrefix("spotify:")

            mutex.withLock {
                (cache[rawSpotifyId] ?: cache[track.id])?.let { cached ->
                    Timber.tag("SpotifyMatching").d("CACHE HIT: '${track.name}' -> YT ${cached.id}")
                    return@withContext cached
                }
            }

            if (db != null) {
                val match =
                    if (rawSpotifyId.isNotBlank()) {
                        db.getSpotifyMatch(rawSpotifyId) ?: db.getSpotifyMatch(track.id)
                    } else {
                        db.getSpotifyMatch(track.id)
                    }
                val dbSong = match?.let { db.getSongByIdBlocking(it.youtubeId) }
                val implausible =
                    match != null &&
                        dbSong != null &&
                        !match.isManualOverride &&
                        !scorer.hasArtistEvidence(
                            trackArtists = track.artists.map { it.name },
                            candidateTitle = dbSong.song.title,
                            candidateArtists = dbSong.artists.map { it.name },
                        )
                if (implausible) {
                    Timber.tag("SpotifyMatching").w(
                        "Ignoring stored match '${track.name}' -> '${dbSong?.song?.title}': artist mismatch",
                    )
                }
                if (match != null && !implausible) {
                    val metadata =
                        if (dbSong != null) {
                            dbSong.toMediaMetadata().copy(
                                thumbnailUrl = SpotifyEntityMapper.getTrackThumbnail(track) ?: dbSong.song.thumbnailUrl,
                                duration = if (track.durationMs > 0) track.durationMs / 1000 else dbSong.song.duration,
                                album =
                                    track.album?.let { MediaMetadata.Album(id = it.id, title = it.name) }
                                        ?: dbSong.album?.let { MediaMetadata.Album(id = it.id, title = it.title) },
                                explicit = track.explicit || dbSong.song.explicit,
                                spotifyTrackId = track.id.takeIf(String::isNotBlank),
                                isrc = track.externalIds?.isrc?.takeIf { it.isNotBlank() } ?: match.isrc ?: dbSong.song.isrc,
                            )
                        } else {
                            MediaMetadata(
                                id = match.youtubeId,
                                title = track.name,
                                artists = track.artists.map { MediaMetadata.Artist(id = it.id, name = it.name) },
                                duration = if (track.durationMs > 0) track.durationMs / 1000 else -1,
                                thumbnailUrl = SpotifyEntityMapper.getTrackThumbnail(track),
                                album = track.album?.let { MediaMetadata.Album(id = it.id, title = it.name) },
                                explicit = track.explicit,
                                spotifyTrackId = track.id.takeIf(String::isNotBlank),
                                isrc = track.externalIds?.isrc?.takeIf { it.isNotBlank() } ?: match.isrc,
                            )
                        }

                    mutex.withLock {
                        cache[track.id] = metadata
                        if (rawSpotifyId.isNotBlank()) {
                            cache[rawSpotifyId] = metadata
                        }
                    }
                    Timber.tag("SpotifyMatching").d("DB MATCH HIT: '${track.name}' -> YT ${metadata.id}")
                    return@withContext metadata
                }
            }

            val artistsList = track.artists.map { collapseSpacedLetters(it.name) }.filter { it.isNotBlank() }
            val queryArtist = artistsList.joinToString(", ")
            Timber.tag("SpotifyMatching").d(
                "INPUT: title='${track.name}', artists=[$queryArtist], dur=${track.durationMs}ms, " +
                "isrc=${track.externalIds?.isrc}, explicit=${track.explicit}",
            )

            val query =
                SpotifyQueryBuilder.buildSearchQuery(
                    track.copy(artists = track.artists.map { it.copy(name = collapseSpacedLetters(it.name)) }),
                )
            Timber.tag("SpotifyMatching").d("QUERY: '$query'")

            val trackQuery =
                TrackQuery(
                    artist = queryArtist,
                    title = track.name,
                    album = track.album?.name,
                    isrc = track.externalIds?.isrc,
                    durationMs = track.durationMs.toLong(),
                    explicit = track.explicit,
                )

            // At most three requests, most precise first: official songs by
            // artist + title, then the general search (uploads and videos, which
            // carry the artist in the title), then songs by title alone.
            val rawTitle = track.name.trim()
            val artistAndTitle = "${artistsList.joinToString(" ")} $rawTitle".trim()
            val attempts =
                buildList {
                    add(query to SearchMode.SONGS)
                    if (rawTitle.isNotBlank()) {
                        add(artistAndTitle to SearchMode.SUMMARY)
                        add(rawTitle to SearchMode.SONGS)
                    }
                }.distinct()

            val failedSearches = AtomicInteger()

            fun decide(candidates: List<SongItem>): SpotifySearchScorer.CandidateDecision<SongItem>? {
                if (candidates.isEmpty()) return null
                val strict =
                    scorer.pickGeneric(
                        track = trackQuery,
                        candidates = candidates.filter { (it.duration ?: 0) > 0 },
                        titleOf = { it.title },
                        artistsOf = { it.artists.map { a -> a.name } },
                        durationMsOf = { (it.duration ?: 0) * 1000L },
                    )
                if (strict.accepted != null) return strict

                // Uploads often come back without a duration, so this fallback
                // also considers them; it still needs the Spotify artist on the YouTube side.
                return scorer.pickByTokenOverlap(
                    track = trackQuery,
                    candidates = candidates,
                    titleOf = { it.title },
                    artistsOf = { it.artists.map { a -> a.name } },
                    durationMsOf = { (it.duration ?: 0) * 1000L },
                ) ?: strict
            }

            suspend fun search(
                searchText: String,
                mode: SearchMode,
            ): SpotifySearchScorer.CandidateDecision<SongItem>? {
                val results =
                    when (mode) {
                        SearchMode.SONGS ->
                            YouTube
                                .search(query = searchText, filter = YouTube.SearchFilter.FILTER_SONG)
                                .map { it.items }
                        SearchMode.SUMMARY ->
                            YouTube
                                .searchSummary(searchText)
                                .map { page -> page.summaries.flatMap { it.items } }
                    }
                results.onFailure { failedSearches.incrementAndGet() }
                return decide(results.getOrNull().orEmpty().filterIsInstance<SongItem>().distinctBy { it.id })
            }

            suspend fun searchOneByOne() =
                attempts.firstNotNullOfOrNull { (searchText, mode) ->
                    search(searchText, mode)?.takeIf { it.accepted != null }
                }

            // For the song being played, send every search at once and take the
            // first acceptable one in order of preference; a miss then costs one
            // round trip instead of three.
            suspend fun searchAllAtOnce() =
                coroutineScope {
                    val searches = attempts.map { (searchText, mode) -> async { search(searchText, mode) } }
                    try {
                        searches.firstNotNullOfOrNull { it.await()?.takeIf { decision -> decision.accepted != null } }
                    } finally {
                        searches.forEach { it.cancel() }
                    }
                }

            val decision =
                when (priority) {
                    MatchPriority.PLAYING -> {
                        playingSearches.incrementAndGet()
                        try {
                            searchSemaphore.withPermit { searchAllAtOnce() }
                        } finally {
                            playingSearches.decrementAndGet()
                        }
                    }
                    MatchPriority.UPCOMING ->
                        upcomingSemaphore.withPermit {
                            while (playingSearches.get() > 0) delay(YIELD_MS)
                            upcomingSearches.incrementAndGet()
                            try {
                                searchOneByOne()
                            } finally {
                                upcomingSearches.decrementAndGet()
                            }
                        }
                    MatchPriority.BACKGROUND ->
                        backgroundSemaphore.withPermit {
                            while (playingSearches.get() > 0 || upcomingSearches.get() > 0) delay(BACKGROUND_YIELD_MS)
                            searchOneByOne().also { delay(BACKGROUND_SPACING_MS) }
                        }
                }

            val (best, score) =
                decision?.accepted?.let { it to decision.score } ?: run {
                    if (throwOnSearchFailure && failedSearches.get() > 0) {
                        throw IOException("YouTube search failed for '${track.name}' (${failedSearches.get()} requests)")
                    }
                    Timber.tag("SpotifyMatching").w("REJECTED '${track.name}': no candidate was accepted")
                    return@withContext null
                }
            Timber.tag("SpotifyMatching").i(
                "ACCEPTED '${track.name}' -> YT id=${best.id}, title='${best.title}', score=$score",
            )

            val bestMetadata = best.toMediaMetadata()
            val metadata =
                bestMetadata.copy(
                    thumbnailUrl = SpotifyEntityMapper.getTrackThumbnail(track) ?: best.thumbnail,
                    duration = if (track.durationMs > 0) track.durationMs / 1000 else best.duration ?: -1,
                    explicit = track.explicit || best.explicit,
                    album =
                        track.album?.let { MediaMetadata.Album(id = it.id, title = it.name) }
                            ?: bestMetadata.album,
                    spotifyTrackId = track.id.takeIf(String::isNotBlank),
                    isrc = track.externalIds?.isrc?.takeIf(String::isNotBlank) ?: bestMetadata.isrc,
                )

            Timber.tag("SpotifyMatching").d(
                "PLAYER OUTPUT: id=${metadata.id}, title='${metadata.title}', artists='${metadata.artists.joinToString { it.name }}', " +
                "dur=${metadata.duration}s, isrc=${metadata.isrc}, explicit=${metadata.explicit}",
            )

            mutex.withLock {
                cache[track.id] = metadata
                if (rawSpotifyId.isNotBlank()) {
                    cache[rawSpotifyId] = metadata
                }
            }

            val matchKey = if (rawSpotifyId.isNotBlank()) rawSpotifyId else track.id
            val existingMatch = db?.getSpotifyMatch(matchKey)
            if (existingMatch == null || !existingMatch.isManualOverride) {
                db?.insert(
                    SpotifyMatchEntity(
                        spotifyId = matchKey,
                        youtubeId = metadata.id,
                        title = track.name,
                        artist = track.artists.joinToString(" ") { it.name },
                        matchScore = score,
                        isrc = metadata.isrc,
                    ),
                )
            }

            metadata
        }
}

/** Who is waiting for a match; it decides how soon the search may run. */
enum class MatchPriority {
    /** The song is playing or about to be: its searches are sent at once and do not wait for the others. */
    PLAYING,

    /** A song being prepared, or a bulk copy: at most two searches at a time, and none while a playing song is searching. */
    UPCOMING,

    /** A song further away: one search at a time, spaced out, and only while nothing else is searching. */
    BACKGROUND,
}

private enum class SearchMode { SONGS, SUMMARY }

private val SPACED_LETTERS = Regex("(?<![\\p{L}\\p{N}])(?:[\\p{L}\\p{N}] ){2,}[\\p{L}\\p{N}](?![\\p{L}\\p{N}])")

/** "G a t e w a y" -> "Gateway": letter-spaced names match nothing on YouTube. */
internal fun collapseSpacedLetters(name: String): String =
    SPACED_LETTERS.replace(name) { match -> match.value.replace(" ", "") }.trim()
