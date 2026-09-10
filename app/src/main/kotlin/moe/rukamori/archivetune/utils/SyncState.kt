/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.spotify.SpotifyLibraryRepository
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncState
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        val database: MusicDatabase,
        val spotifyRepository: SpotifyLibraryRepository,
    ) {
        val syncScope = CoroutineScope(Dispatchers.IO)
        val syncEnabled = MutableStateFlow(true)
        val syncGeneration = AtomicLong(0L)

        val syncMutex = Mutex()
        val ytmPlaylistSyncMutex = Mutex()
        val spotifyPlaylistSyncMutex = Mutex()
        val playlistSyncMutex = ytmPlaylistSyncMutex
        val dbWriteSemaphore = Semaphore(2)

        init {
            syncScope.launch {
                context.dataStore.data
                    .map { it[YtmSyncKey] ?: true }
                    .distinctUntilChanged()
                    .collect { enabled ->
                        syncEnabled.value = enabled
                        if (!enabled) {
                            syncGeneration.incrementAndGet()
                        }
                    }
            }
        }

        suspend fun isLoggedIn(): Boolean {
            val cookie =
                context.dataStore.data
                    .map { it[InnerTubeCookieKey] }
                    .first()
            return hasYouTubeLoginCookie(cookie)
        }

        suspend fun isYtmSyncEnabled(): Boolean {
            val enabled =
                context.dataStore.data
                    .map { it[YtmSyncKey] ?: true }
                    .first()
            syncEnabled.value = enabled
            if (!enabled) {
                syncGeneration.incrementAndGet()
            }
            return enabled
        }

        fun isSyncStillEnabled(gen: Long): Boolean = syncEnabled.value && syncGeneration.get() == gen
    }

internal fun likedSongTimestamp(
    baseTimestamp: LocalDateTime,
    index: Int,
): LocalDateTime = baseTimestamp.minusSeconds(index.toLong())
