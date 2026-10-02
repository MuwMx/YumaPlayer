/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection

@Composable
internal fun PlayerMenuArtistAndAlbumSection(
    splitArtists: List<PlayerMenuSplitArtist>,
    album: MediaMetadata.Album?,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    onDismiss: () -> Unit,
    onShowSelectArtistDialog: () -> Unit,
) {
    MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
        Column {
            if (splitArtists.isNotEmpty()) {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.view_artist)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.artist),
                            contentDescription = null,
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            if (splitArtists.size == 1 && splitArtists[0].originalArtist != null) {
                                onDismiss()
                                playerBottomSheetState.snapTo(playerBottomSheetState.collapsedBound)
                                navController.navigate("artist/${splitArtists[0].originalArtist!!.id}")
                            } else {
                                onShowSelectArtistDialog()
                            }
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            if (splitArtists.isNotEmpty() && album != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }

            if (album != null) {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.view_album)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.album),
                            contentDescription = null,
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            onDismiss()
                            playerBottomSheetState.snapTo(playerBottomSheetState.collapsedBound)
                            navController.navigate("album/${album.id}")
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
internal fun PlayerMenuOptionsSection(
    isQueueTrigger: Boolean?,
    onRemoveFromQueue: (() -> Unit)?,
    playerConnection: PlayerConnection,
    onShowDetailsDialog: () -> Unit,
    onShowEqualizerDialog: () -> Unit,
    onShowPitchTempoDialog: () -> Unit,
    onDismiss: () -> Unit,
) {
    MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
        Column {
            if (isQueueTrigger == true && onRemoveFromQueue != null) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.remove_from_queue),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.delete),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            onRemoveFromQueue()
                            onDismiss()
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }

            ListItem(
                headlineContent = { Text(text = stringResource(R.string.details)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_about),
                        contentDescription = null,
                    )
                },
                modifier =
                    Modifier.clickable {
                        onShowDetailsDialog()
                        onDismiss()
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            if (isQueueTrigger != true) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.equalizer)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.equalizer),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable(onClick = onShowEqualizerDialog),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.tempo_and_pitch)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.speed),
                            contentDescription = null,
                        )
                    },
                    supportingContent = {
                        val playbackParameters by playerConnection.playbackParameters.collectAsStateWithLifecycle()
                        Text(
                            text = "x${formatMultiplier(
                                playbackParameters.speed,
                            )} • x${formatMultiplier(playbackParameters.pitch)}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.clickable(onClick = onShowPitchTempoDialog),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
