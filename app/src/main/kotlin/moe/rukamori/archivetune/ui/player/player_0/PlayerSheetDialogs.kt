package moe.rukamori.archivetune.ui.player.player_0

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.ui.menu.AddToPlaylistDialog
import moe.rukamori.archivetune.ui.menu.EqualizerDialog
import moe.rukamori.archivetune.ui.menu.TempoPitchDialog
import moe.rukamori.archivetune.ui.player.lyrics_0.LyricsOptionsMenu
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.sett.FullPlayerOptionsMenu
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.player.queue_0.QueueOptionsMenu
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.QueueUiState
import moe.rukamori.archivetune.ui.state.UpdateState

@Composable
internal fun PlayerSheetDialogs(
    state: PlayerUiState,
    queueState: QueueUiState,
    updateState: UpdateState,
    onAction: (PlayerAction) -> Unit,
    onBackgroundStyleChanged: (Boolean) -> Unit,
    onImmersiveChanged: (Boolean) -> Unit,
    isLyricsMenuVisible: Boolean,
    onDismissLyricsMenu: () -> Unit,
    isQueueMenuVisible: Boolean,
    onDismissQueueMenu: () -> Unit,
    showSettingsMenu: Boolean,
    menuInitialScreen: PlayerMenuScreen,
    onDismissSettingsMenu: () -> Unit,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val activityResultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showPitchTempoDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showQueueAddToPlaylistDialog by remember { mutableStateOf(false) }

    LyricsOptionsMenu(
        isVisible = isLyricsMenuVisible,
        onDismiss = onDismissLyricsMenu,
        onAction = onAction,
        state = state
    )

    QueueOptionsMenu(
        isVisible = isQueueMenuVisible,
        onDismiss = onDismissQueueMenu,
        onAction = onAction,
        onSaveAsPlaylist = {
            onDismissQueueMenu()
            showQueueAddToPlaylistDialog = true
        },
        state = state
    )

    FullPlayerOptionsMenu(
        expanded = showSettingsMenu,
        initialScreen = menuInitialScreen,
        onDismissRequest = onDismissSettingsMenu,
        state = state,
        updateState = updateState,
        onBackgroundStyleChanged = onBackgroundStyleChanged,
        onImmersiveChanged = onImmersiveChanged,
        onOpenEqualizer = {
            onDismissSettingsMenu()
            showEqualizerDialog = true
        },
        onOpenPlaybackSpeed = {
            onDismissSettingsMenu()
            showPitchTempoDialog = true
        },
        onOpenAddToPlaylist = {
            onDismissSettingsMenu()
            showAddToPlaylistDialog = true
        },
        onAction = onAction
    )

    if (showEqualizerDialog) {
        EqualizerDialog(
            onDismiss = { showEqualizerDialog = false },
            openSystemEqualizer = {
                try {
                    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                        playerConnection?.localPlayer?.audioSessionId?.let { extra ->
                            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, extra)
                        }
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        activityResultLauncher.launch(intent)
                    } else {
                        Toast.makeText(context, context.getString(R.string.system_equalizer_not_found), Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    Toast.makeText(context, context.getString(R.string.system_equalizer_not_found), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showPitchTempoDialog) {
        TempoPitchDialog(onDismiss = { showPitchTempoDialog = false })
    }

    if (showAddToPlaylistDialog && state.trackUrl.isNotBlank()) {
        AddToPlaylistDialog(
            isVisible = showAddToPlaylistDialog,
            onGetSong = { listOf(state.trackUrl) },
            onDismiss = { showAddToPlaylistDialog = false }
        )
    }

    if (showQueueAddToPlaylistDialog && queueState.queueWindows.isNotEmpty()) {
        val database = LocalDatabase.current
        AddToPlaylistDialog(
            isVisible = showQueueAddToPlaylistDialog,
            onGetSong = {
                val songIds = database.withTransaction {
                    queueState.queueWindows.mapNotNull { window ->
                        window.mediaItem.metadata?.also { insert(it) }?.id
                    }
                }
                songIds
            },
            onDismiss = { showQueueAddToPlaylistDialog = false },
            onAddComplete = { _, playlistNames ->
                val message = when {
                    playlistNames.size == 1 -> context.getString(R.string.added_to_playlist, playlistNames.first())
                    else -> context.getString(R.string.added_to_n_playlists, playlistNames.size)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        )
    }
}
