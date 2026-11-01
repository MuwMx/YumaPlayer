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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.ui.main.LocalDatabase
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.menu.AddToPlaylistDialog
import moe.rukamori.archivetune.ui.menu.EqualizerDialog
import moe.rukamori.archivetune.ui.menu.TempoPitchDialog
import moe.rukamori.archivetune.ui.player.PlaybackErrorDialog
import moe.rukamori.archivetune.ui.player.PlaybackErrorKind
import moe.rukamori.archivetune.ui.player.lyrics_0.LyricsOptionsMenu
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.sett.FullPlayerOptionsMenu
import moe.rukamori.archivetune.ui.player.player_0.sett.PlayerMenuScreen
import moe.rukamori.archivetune.ui.player.queue_0.QueueOptionsMenu
import moe.rukamori.archivetune.ui.player.toPlaybackErrorInfo
import moe.rukamori.archivetune.ui.screens.login.buildLoginRoute
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
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<MediaMetadata?>(null) }
    val hasArtistId = mediaMetadata?.artists?.firstOrNull()?.id?.isNotBlank() == true
    val hasAlbumId = mediaMetadata?.album?.id?.isNotBlank() == true
    val activityResultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showPitchTempoDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showQueueAddToPlaylistDialog by remember { mutableStateOf(false) }

    val playbackError = state.playbackError
    if (playbackError != null) {
        val errorInfo = remember(playbackError) { playbackError.toPlaybackErrorInfo() }
        if (moe.rukamori.archivetune.BuildConfig.DEBUG &&
            (errorInfo.kind == PlaybackErrorKind.ConfirmationRequired || errorInfo.kind == PlaybackErrorKind.LoginRefreshRequired)
        ) {
            moe.rukamori.archivetune.playback.PlaybackTiming.log(
                "LOGIN_REQUIRED_DIALOG_COMPOSED",
                mediaId = state.trackUrl,
                extra = "kind=${errorInfo.kind}",
            )
        }
        PlaybackErrorDialog(
            error = playbackError,
            onDismiss = {
                playerConnection?.dismissPlaybackError()
            },
            onRetry = {
                playerConnection?.dismissPlaybackError()
                playerConnection?.player?.prepare()
                playerConnection?.player?.play()
            },
            onLogin = {
                playerConnection?.dismissPlaybackError()
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("navigate_to", buildLoginRoute(errorInfo.loginRecoveryUrl))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                runCatching { context.startActivity(intent) }
            },
        )
    }

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
        hasArtistId = hasArtistId,
        hasAlbumId = hasAlbumId,
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
