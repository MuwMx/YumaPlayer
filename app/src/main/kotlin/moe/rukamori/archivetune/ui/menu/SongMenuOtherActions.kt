/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewMenuItem
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.toggleSpeedDialPin

@Composable
internal fun SongMenuLibraryAndSpeedDialSection(
    song: Song,
    isLocalSong: Boolean,
    isInSpeedDial: Boolean,
    songPin: SpeedDialPin,
    speedDialPins: List<SpeedDialPin>,
    database: MusicDatabase,
    onSpeedDialSongIdsChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sectionCount = if (!isLocalSong) 2 else 1
    MenuSurfaceSection {
        if (!isLocalSong) {
            NewMenuItem(
                headlineContent = {
                    Text(
                        text =
                            stringResource(
                                if (song.song.inLibrary == null) {
                                    R.string.add_to_library
                                } else {
                                    R.string.remove_from_library
                                },
                            ),
                    )
                },
                leadingContent = {
                    Icon(
                        painter =
                            painterResource(
                                if (song.song.inLibrary == null) {
                                    R.drawable.library_add
                                } else {
                                    R.drawable.library_add_check
                                },
                            ),
                        contentDescription = null,
                    )
                },
                onClick = {
                    onDismiss()
                    database.query {
                        update(song.song.toggleLibrary())
                    }
                },
                index = 0,
                count = sectionCount,
            )
        }

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
            onClick = {
                val updatedPins = toggleSpeedDialPin(speedDialPins, songPin)
                onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
                onDismiss()
            },
            index = if (!isLocalSong) 1 else 0,
            count = sectionCount,
        )
    }
}

@Composable
internal fun SongMenuArtistAndAlbumSection(
    splitArtists: List<SongMenuSplitArtist>,
    albumId: String?,
    navController: NavController,
    onDismiss: () -> Unit,
    onShowSelectArtistDialog: () -> Unit,
) {
    val navItemCount = 1 + (if (albumId != null) 1 else 0)
    MenuSurfaceSection {
        NewMenuItem(
            headlineContent = { Text(text = stringResource(R.string.view_artist)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.artist),
                    contentDescription = null,
                )
            },
            onClick = {
                if (splitArtists.size == 1 && splitArtists[0].originalArtist != null) {
                    navController.navigate("artist/${splitArtists[0].originalArtist!!.id}")
                    onDismiss()
                } else {
                    onShowSelectArtistDialog()
                }
            },
            index = 0,
            count = navItemCount,
        )

        if (albumId != null) {
            NewMenuItem(
                headlineContent = { Text(text = stringResource(R.string.view_album)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.album),
                        contentDescription = null,
                    )
                },
                onClick = {
                    onDismiss()
                    navController.navigate("album/$albumId")
                },
                index = 1,
                count = navItemCount,
            )
        }
    }
}

@Composable
internal fun SongMenuRefetchAndDetailsSection(
    song: Song,
    isLocalSong: Boolean,
    rotationAnimation: Float,
    database: MusicDatabase,
    coroutineScope: CoroutineScope,
    bottomSheetPageState: BottomSheetPageState,
    onRotateRefetch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val infoItemCount = (if (!isLocalSong) 1 else 0) + 1
    MenuSurfaceSection {
        if (!isLocalSong) {
            NewMenuItem(
                headlineContent = { Text(text = stringResource(R.string.refetch)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.sync),
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer(rotationZ = rotationAnimation),
                    )
                },
                onClick = {
                    onRotateRefetch()
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube.queue(listOf(song.id)).onSuccess {
                            val newSong = it.firstOrNull()
                            if (newSong != null) {
                                database.transaction {
                                    update(song, newSong.toMediaMetadata())
                                }
                            }
                        }
                    }
                },
                index = 0,
                count = infoItemCount,
            )
        }

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
                    ShowMediaInfo(song.id)
                }
            },
            index = if (!isLocalSong) 1 else 0,
            count = infoItemCount,
        )
    }
}
