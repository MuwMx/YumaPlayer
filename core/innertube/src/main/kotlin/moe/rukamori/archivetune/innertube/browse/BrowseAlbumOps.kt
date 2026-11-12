/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.browse

import io.ktor.client.call.body
import moe.rukamori.archivetune.innertube.InnerTube
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.MusicResponsiveListItemRenderer
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.response.BrowseResponse
import moe.rukamori.archivetune.innertube.pages.AlbumPage
import moe.rukamori.archivetune.innertube.pages.NewReleaseAlbumPage

internal object BrowseAlbumOps {
    private inline val innerTube: InnerTube get() = YouTube.innerTube

    suspend fun album(
        browseId: String,
        withSongs: Boolean = true,
    ): Result<AlbumPage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()
            val playlistId =
                AlbumPage.getPlaylistId(response)
                    ?: throw IllegalStateException("Missing album playlist id for $browseId")
            val albumTitle =
                AlbumPage.getTitle(response)
                    ?: throw IllegalStateException("Missing album title for $browseId")
            val albumArtists = AlbumPage.getArtists(response).takeIf { it.isNotEmpty() }
            val albumYear = AlbumPage.getYear(response)
            val albumThumbnail =
                AlbumPage.getThumbnail(response)
                    ?: throw IllegalStateException("Missing album thumbnail url for $browseId")
            val albumItem =
                AlbumItem(
                    browseId = browseId,
                    playlistId = playlistId,
                    title = albumTitle,
                    artists = albumArtists,
                    year = albumYear,
                    thumbnail = albumThumbnail,
                    explicit = false,
                )
            val inlineSongs = if (withSongs) AlbumPage.getSongs(response, albumItem) else emptyList()
            val songs =
                if (withSongs) {
                    val fetchedSongs =
                        runCatching {
                            albumSongs(playlistId, albumItem).getOrThrow()
                        }.getOrElse { error ->
                            if (inlineSongs.isNotEmpty()) {
                                inlineSongs
                            } else {
                                throw error
                            }
                        }

                    if (fetchedSongs.isEmpty() && inlineSongs.isNotEmpty()) {
                        inlineSongs
                    } else {
                        fetchedSongs
                    }
                } else {
                    emptyList()
                }

            AlbumPage(
                album = albumItem,
                songs = songs,
                otherVersions =
                    response.contents
                        ?.twoColumnBrowseResultsRenderer
                        ?.secondaryContents
                        ?.sectionListRenderer
                        ?.contents
                        ?.mapNotNull { it.musicCarouselShelfRenderer }
                        ?.flatMap { it.contents }
                        ?.mapNotNull { it.musicTwoRowItemRenderer }
                        ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                        ?.distinctBy { it.id }
                        .orEmpty(),
            )
        }

    suspend fun albumSongs(
        playlistId: String,
        album: AlbumItem? = null,
    ): Result<List<SongItem>> =
        runCatching {
            val cleanBrowseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
            var response = innerTube.browse(WEB_REMIX, cleanBrowseId).body<BrowseResponse>()
            val songs = linkedMapOf<String, SongItem>()

            fun appendSongs(
                candidates: List<MusicResponsiveListItemRenderer>,
                parsedSongs: List<SongItem>,
                source: String,
            ): Boolean {
                if (candidates.isNotEmpty() && parsedSongs.isEmpty()) {
                    throw IllegalStateException("Unable to parse album songs from $source for playlist $playlistId")
                }

                val previousSize = songs.size
                parsedSongs.forEach { songs.putIfAbsent(it.id, it) }
                return songs.size > previousSize
            }

            appendSongs(
                candidates = AlbumPage.getSongRenderers(response),
                parsedSongs = AlbumPage.getSongs(response, album),
                source = "initial response",
            )

            var continuation = AlbumPage.getSongContinuation(response)
            val seenContinuations = mutableSetOf<String>()
            var requestCount = 0
            val maxRequests = 50

            var consecutiveEmptyResponses = 0
            while (continuation != null && requestCount < maxRequests) {
                if (continuation in seenContinuations) {
                    break
                }
                seenContinuations.add(continuation)
                requestCount++

                response =
                    innerTube
                        .browse(
                            client = WEB_REMIX,
                            continuation = continuation,
                        ).body<BrowseResponse>()

                val newSongCandidates = AlbumPage.getContinuationSongRenderers(response)
                val newSongs = AlbumPage.getContinuationSongs(response, album)
                val hasNewSongs =
                    if (newSongCandidates.isNotEmpty() || newSongs.isNotEmpty()) {
                        appendSongs(
                            candidates = newSongCandidates,
                            parsedSongs = newSongs,
                            source = "continuation response",
                        )
                    } else {
                        false
                    }

                if (!hasNewSongs) {
                    consecutiveEmptyResponses++
                    if (consecutiveEmptyResponses >= 2) break
                } else {
                    consecutiveEmptyResponses = 0
                }

                continuation = AlbumPage.getNextSongContinuation(response)
            }
            songs.values.toList()
        }
}
