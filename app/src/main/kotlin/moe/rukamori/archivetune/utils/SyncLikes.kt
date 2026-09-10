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
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.constants.SpotifySyncLikesKey
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.spotify.SpotifySync
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncLikes
    @Inject
    constructor(
        private val state: SyncState,
    ) {
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
                        YouTube.likeVideo(s.id, s.likedYtm)
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
                                YouTube.likeVideo(song.id, song.likedYtm)
                            }
                        }.awaitAll()
                    }
                }
            }
        }
    }
