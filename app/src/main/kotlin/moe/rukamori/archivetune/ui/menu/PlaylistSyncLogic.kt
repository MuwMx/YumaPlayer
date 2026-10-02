/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.isSyncEnabled
import moe.rukamori.archivetune.extensions.isUserLoggedIn
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.utils.SyncUtils
import timber.log.Timber
import java.time.LocalDateTime
import kotlin.math.roundToInt

@Immutable
internal data class PlaylistSyncProgressUi(
    val completedSongs: Int,
    val totalSongs: Int,
) {
    val percent: Int
        get() =
            if (totalSongs <= 0) {
                0
            } else {
                (completedSongs.coerceIn(0, totalSongs).toFloat() / totalSongs.toFloat() * 100f)
                    .roundToInt()
                    .coerceIn(0, 100)
            }
}

internal fun startPlaylistSyncToYouTube(
    coroutineScope: CoroutineScope,
    context: Context,
    playlist: Playlist,
    songs: List<Song>,
    database: MusicDatabase,
    syncUtils: SyncUtils,
    onProgressUpdate: (PlaylistSyncProgressUi?) -> Unit,
    onSuccess: () -> Unit,
): Job =
    coroutineScope.launch(Dispatchers.IO) {
        var lastProgressPercent = -1
        var lastProgressCompleted = -1

        fun updateProgress(
            completedSongs: Int,
            totalSongs: Int,
        ) {
            val nextProgressPercent =
                if (totalSongs <= 0) {
                    -1
                } else {
                    (completedSongs.coerceIn(0, totalSongs).toFloat() / totalSongs.toFloat() * 100f)
                        .roundToInt()
                        .coerceIn(0, 100)
                }
            val shouldUpdate =
                totalSongs <= 0 ||
                    completedSongs == totalSongs ||
                    nextProgressPercent != lastProgressPercent ||
                    completedSongs - lastProgressCompleted >= 25

            if (!shouldUpdate) return

            lastProgressPercent = nextProgressPercent
            lastProgressCompleted = completedSongs

            coroutineScope.launch(Dispatchers.Main) {
                onProgressUpdate(
                    PlaylistSyncProgressUi(
                        completedSongs = completedSongs,
                        totalSongs = totalSongs,
                    ),
                )
            }
        }

        try {
            if (!context.isSyncEnabled()) {
                withContext(Dispatchers.Main) {
                    Toast
                        .makeText(
                            context,
                            context.getString(
                                if (context.isUserLoggedIn()) {
                                    R.string.sync_disabled
                                } else {
                                    R.string.not_logged_in_youtube
                                },
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
                return@launch
            }

            val browseId = playlist.playlist.browseId ?: YouTube.createPlaylist(playlist.playlist.name).getOrThrow()
            if (playlist.playlist.browseId == null) {
                updateProgress(completedSongs = 0, totalSongs = songs.size)
                YouTube
                    .addSongsToPlaylist(
                        playlistId = browseId,
                        videoIds = songs.map(Song::id),
                        onProgress = ::updateProgress,
                    ).getOrThrow()
                database.query {
                    update(
                        playlist.playlist.copy(
                            browseId = browseId,
                            lastUpdateTime = LocalDateTime.now(),
                            remoteSongCount = songs.size,
                        ),
                    )
                }
            } else {
                updateProgress(completedSongs = 0, totalSongs = 0)
                syncUtils.syncPlaylistNow(
                    browseId = browseId,
                    playlistId = playlist.id,
                    propagateFailures = true,
                ) { completedSongs, totalSongs ->
                    updateProgress(
                        completedSongs = completedSongs,
                        totalSongs = totalSongs,
                    )
                }
            }

            withContext(Dispatchers.Main) {
                onProgressUpdate(null)
                Toast
                    .makeText(
                        context,
                        context.getString(R.string.playlist_synced),
                        Toast.LENGTH_SHORT,
                    ).show()
                onSuccess()
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                withContext(Dispatchers.Main) {
                    onProgressUpdate(null)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync playlist ${playlist.playlist.name}")
            withContext(Dispatchers.Main) {
                onProgressUpdate(null)
                Toast
                    .makeText(
                        context,
                        context.getString(R.string.playlist_sync_failed, e.syncErrorDetail(context)),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        } finally {
            withContext(NonCancellable) {
                withContext(Dispatchers.Main) {
                    onProgressUpdate(null)
                }
            }
        }
    }

internal fun Throwable.syncErrorDetail(context: Context): String =
    localizedMessage
        ?.takeIf(String::isNotBlank)
        ?: javaClass.simpleName.takeIf(String::isNotBlank)
        ?: context.getString(R.string.error_unknown)
