/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewMenuItem
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import java.time.LocalDateTime

@Composable
internal fun YouTubeSongLibraryAndSpeedDialSection(
    song: SongItem,
    librarySong: Song?,
    isInSpeedDial: Boolean,
    database: MusicDatabase,
    coroutineScope: CoroutineScope,
    context: Context,
    onToggleSpeedDial: () -> Unit,
) {
    val sectionCount = 2
    MenuSurfaceSection {
        NewMenuItem(
            headlineContent = {
                Text(
                    text =
                        if (librarySong?.song?.inLibrary != null) {
                            stringResource(R.string.remove_from_library)
                        } else {
                            stringResource(R.string.add_to_library)
                        },
                )
            },
            leadingContent = {
                Icon(
                    painter =
                        painterResource(
                            if (librarySong?.song?.inLibrary != null) {
                                R.drawable.library_add_check
                            } else {
                                R.drawable.library_add
                            },
                        ),
                    contentDescription = null,
                )
            },
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                    val shouldAdd = librarySong?.song?.inLibrary == null
                    val remoteResult = YouTube.likeVideo(song.id, shouldAdd)
                    if (remoteResult.isFailure) {
                        withContext(Dispatchers.Main) {
                            Toast
                                .makeText(context, context.getString(R.string.error_unknown), Toast.LENGTH_SHORT)
                                .show()
                        }
                        return@launch
                    }

                    val now = LocalDateTime.now()
                    database.withTransaction {
                        val base = librarySong?.song ?: song.toMediaMetadata().toSongEntity()
                        if (librarySong == null) {
                            insert(song.toMediaMetadata())
                        }
                        update(
                            base.copy(
                                liked = shouldAdd,
                                likedDate = if (shouldAdd) now else null,
                                inLibrary = if (shouldAdd) now else null,
                            ),
                        )
                    }
                }
            },
            index = 0,
            count = sectionCount,
        )

        NewMenuItem(
            headlineContent = {
                Text(
                    text =
                        stringResource(
                            if (isInSpeedDial) {
                                R.string.remove_from_speed_dial
                            } else {
                                R.string.pin_to_speed_dial
                            },
                        ),
                )
            },
            leadingContent = {
                Icon(
                    painter = painterResource(if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark),
                    contentDescription = null,
                )
            },
            onClick = onToggleSpeedDial,
            index = 1,
            count = sectionCount,
        )
    }
}

@Composable
internal fun YouTubeSongDetailsSection(
    songId: String,
    bottomSheetPageState: BottomSheetPageState,
    onDismiss: () -> Unit,
) {
    MenuSurfaceSection {
        NewMenuItem(
            headlineContent = { Text(text = stringResource(R.string.details)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_about),
                    contentDescription = null,
                )
            },
            onClick = {
                onDismiss()
                bottomSheetPageState.show {
                    ShowMediaInfo(songId)
                }
            },
            index = 0,
            count = 1,
        )
    }
}
