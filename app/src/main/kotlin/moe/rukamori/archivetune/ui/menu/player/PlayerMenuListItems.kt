/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu.player

import android.widget.Toast
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.spotify.SpotifyPageTargets
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.menu.tempo.formatMultiplier

@Composable
internal fun PlayerMenuArtistAndAlbumSection(
    splitArtists: List<PlayerMenuSplitArtist>,
    album: MediaMetadata.Album?,
    songId: String,
    database: MusicDatabase,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    onDismiss: () -> Unit,
    onShowSelectArtistDialog: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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
                            val singleArtist = splitArtists.singleOrNull()?.originalArtist
                            if (singleArtist != null) {
                                val artistId = singleArtist.id
                                if (SpotifyPageTargets.isYouTubeArtistId(artistId)) {
                                    onDismiss()
                                    playerBottomSheetState.snapTo(playerBottomSheetState.collapsedBound)
                                    navController.navigate("artist/$artistId")
                                } else {
                                    coroutineScope.launch {
                                        val target =
                                            SpotifyPageTargets.artistId(
                                                songId = songId,
                                                artistId = artistId.orEmpty(),
                                                artistName = singleArtist.name,
                                                database = database,
                                            )
                                        if (target == null) {
                                            Toast.makeText(context, R.string.page_not_found_on_youtube, Toast.LENGTH_SHORT).show()
                                        } else {
                                            onDismiss()
                                            playerBottomSheetState.snapTo(playerBottomSheetState.collapsedBound)
                                            navController.navigate("artist/$target")
                                        }
                                    }
                                }
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
                            if (SpotifyPageTargets.isYouTubeAlbumId(album.id)) {
                                onDismiss()
                                playerBottomSheetState.snapTo(playerBottomSheetState.collapsedBound)
                                navController.navigate("album/${album.id}")
                            } else {
                                coroutineScope.launch {
                                    val target =
                                        SpotifyPageTargets.albumId(
                                            songId = songId,
                                            albumId = album.id,
                                            albumTitle = album.title,
                                            artistName = splitArtists.firstOrNull()?.name,
                                            database = database,
                                        )
                                    if (target == null) {
                                        Toast.makeText(context, R.string.page_not_found_on_youtube, Toast.LENGTH_SHORT).show()
                                    } else {
                                        onDismiss()
                                        playerBottomSheetState.snapTo(playerBottomSheetState.collapsedBound)
                                        navController.navigate("album/$target")
                                    }
                                }
                            }
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
