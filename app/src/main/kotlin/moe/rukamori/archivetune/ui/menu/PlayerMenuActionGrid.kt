/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.shareLocalAudio
import moe.rukamori.archivetune.utils.toggleSpeedDialPin

@Composable
internal fun PlayerMenuActionGrid(
    mediaMetadata: MediaMetadata,
    librarySong: Song?,
    isLocalMedia: Boolean,
    isInSpeedDial: Boolean,
    isQueueTrigger: Boolean?,
    songPin: SpeedDialPin,
    speedDialPins: List<SpeedDialPin>,
    castPlayerMenuAction: NewAction?,
    playerConnection: PlayerConnection,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    context: Context,
    onSpeedDialSongIdsChange: (String) -> Unit,
    onShowChoosePlaylistDialog: () -> Unit,
    onDismiss: () -> Unit,
) {
    MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
        NewActionGrid(
            actions =
                buildList {
                    castPlayerMenuAction?.let(::add)
                    if (!isLocalMedia) {
                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.radio),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.start_radio),
                                onClick = {
                                    playerConnection.startRadioSeamlessly()
                                    onDismiss()
                                },
                            ),
                        )
                    }
                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.playlist_add),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            text = stringResource(R.string.add_to_playlist),
                            onClick = onShowChoosePlaylistDialog,
                        ),
                    )
                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter =
                                        painterResource(
                                            if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark,
                                        ),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            text =
                                stringResource(
                                    if (isInSpeedDial) {
                                        R.string.remove_from_speed_dial
                                    } else {
                                        R.string.pin_to_speed_dial
                                    },
                                ),
                            onClick = {
                                val updatedPins = toggleSpeedDialPin(speedDialPins, songPin)
                                onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
                                onDismiss()
                            },
                        ),
                    )
                    add(
                        if (isLocalMedia) {
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_share),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.share),
                                onClick = {
                                    shareLocalAudio(context, mediaMetadata.id, librarySong?.format?.mimeType)
                                    onDismiss()
                                },
                            )
                        } else {
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.link),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.copy_link),
                                onClick = {
                                    val clipboard =
                                        context.getSystemService(
                                            Context.CLIPBOARD_SERVICE,
                                        ) as android.content.ClipboardManager
                                    val clip =
                                        android.content.ClipData.newPlainText(
                                            context.getString(R.string.copy_link),
                                            "https://music.youtube.com/watch?v=${mediaMetadata.id}",
                                        )
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                            )
                        },
                    )
                    if (!isLocalMedia) {
                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.fire),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.music_together),
                                onClick = {
                                    onDismiss()
                                    playerBottomSheetState.snapTo(playerBottomSheetState.collapsedBound)
                                    navController.navigate("settings/music_together")
                                },
                            ),
                        )
                    }
                    if (isQueueTrigger != true) {
                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_sleep_timer),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.aod_mode),
                                onClick = {
                                    playerConnection.aodModeEnabled.value = true
                                    onDismiss()
                                },
                            ),
                        )
                    }
                },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}
