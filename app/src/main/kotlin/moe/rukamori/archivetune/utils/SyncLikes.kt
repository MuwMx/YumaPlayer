/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.constants.SpotifySyncLikesKey
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.spotify.SpotifySync
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncLikes
@Inject
constructor(
    private val state: SyncState,
) {
    private val ytmSongMutexes = ConcurrentHashMap<String, Mutex>()
    private val latestYtmLikeVersions = ConcurrentHashMap<String, AtomicInteger>()
    private val latestYtmLikeStates = ConcurrentHashMap<String, Boolean>()
        fun likeSong(
            s: SongEntity,
            source: LikeSource,
            explicitSpotifyId: String? = null,
        ) {
            if (s.isLocal) return
            state.syncScope.launch {
                when (source) {
                    LikeSource.SPOTIFY -> {
                        val isSpotifyLikesSyncEnabled =
                            state.context.dataStore.data
                                .map { it[SpotifySyncLikesKey] ?: false }
                                .first()
                        if (isSpotifyLikesSyncEnabled) {
                            SpotifySync.syncLikeForSong(
                                state.context,
                                state.database,
                                s,
                                s.likedSpotify,
                                explicitSpotifyId,
                            )
                        }
                    }
                    LikeSource.YTM -> {
                        if (!state.isLoggedIn() || !state.isYtmSyncEnabled()) {
                            Timber.w("Skipping likeSong - user not logged in or YTM sync disabled")
                            return@launch
                        }
                        val gen = state.syncGeneration.get()
                        if (!state.isSyncStillEnabled(gen)) return@launch

                        state.markYtmLikeMutated(s.id)
                        latestYtmLikeStates[s.id] = s.likedYtm
                        val version = latestYtmLikeVersions
                            .computeIfAbsent(s.id) { AtomicInteger(0) }
                            .incrementAndGet()
                        val mutex = ytmSongMutexes.computeIfAbsent(s.id) { Mutex() }

                        mutex.withLock {
                            if (!state.isSyncStillEnabled(gen)) return@withLock
                            val currentVersion = latestYtmLikeVersions[s.id]?.get() ?: version
                            if (version != currentVersion) return@withLock
                            val targetLiked = latestYtmLikeStates[s.id] ?: s.likedYtm
                            state.markYtmLikeMutated(s.id)
                            YouTube.likeVideo(s.id, targetLiked)
                                .onFailure { e ->
                                    Timber.w(e, "Failed to sync YTM like for %s (liked=%s)", s.id, targetLiked)
                                }
                        }
                    }
                }
            }
        }

        fun likeSongs(songs: List<SongEntity>, source: LikeSource? = null) {
            val nonLocal = songs.filterNot { it.isLocal }.distinctBy { it.id }
            if (nonLocal.isEmpty()) return
            state.syncScope.launch {
                val (spotifyTargets, ytmTargets) = if (source != null) {
                    when (source) {
                        LikeSource.SPOTIFY -> nonLocal to emptyList<SongEntity>()
                        LikeSource.YTM -> emptyList<SongEntity>() to nonLocal
                    }
                } else {
                    nonLocal.partition { s ->
                        LikeSourceResolver.isSpotifyId(s.id, s.isLocal)
                    }
                }

                if (spotifyTargets.isNotEmpty()) {
                    val isSpotifyLikesSyncEnabled =
                        state.context.dataStore.data
                            .map { it[SpotifySyncLikesKey] ?: false }
                            .first()
                    if (isSpotifyLikesSyncEnabled) {
                        SpotifySync.syncLikeForSongs(state.context, state.database, spotifyTargets)
                    }
                }

                if (ytmTargets.isEmpty()) return@launch

                if (!state.isLoggedIn() || !state.isYtmSyncEnabled()) {
                    Timber.w("Skipping likeSongs - user not logged in or YTM sync disabled")
                    return@launch
                }
                val gen = state.syncGeneration.get()
                ytmTargets.chunked(8).forEach { batch ->
                    if (!state.isSyncStillEnabled(gen)) return@launch
                    coroutineScope {
                        batch.map { song ->
                            async {
                                if (!state.isSyncStillEnabled(gen)) return@async
                                state.markYtmLikeMutated(song.id)
                                YouTube.likeVideo(song.id, song.likedYtm)
                            }
                        }.awaitAll()
                    }
                }
            }
        }
    }
