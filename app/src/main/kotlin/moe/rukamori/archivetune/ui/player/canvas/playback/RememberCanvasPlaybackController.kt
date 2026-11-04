/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:Suppress("UnstableApi")

package moe.rukamori.archivetune.ui.player.canvas.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import moe.rukamori.archivetune.ui.player.canvas.network.createCanvasMediaSourceFactory
import moe.rukamori.archivetune.ui.player.canvas.network.createOkHttpClient
import moe.rukamori.archivetune.ui.player.canvas.network.resolveCanvasCache

private const val CanvasMaxVideoWidth = 4_096
private const val CanvasMaxVideoHeight = 4_096

@Composable
internal fun rememberCanvasPlaybackController(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    onReadyChange: (Boolean) -> Unit = {},
): CanvasPlaybackController {
    val currentOnReadyChange by rememberUpdatedState(onReadyChange)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val isStarted = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    val primary = primaryUrl?.takeIf { it.isNotBlank() }
    val fallback = fallbackUrl?.takeIf { it.isNotBlank() }
    val initial = primary ?: fallback

    if (initial == null) {
        SideEffect {
            currentOnReadyChange(false)
        }
        return remember {
            CanvasPlaybackController(player = null)
        }
    }

    val canvasCache =
        remember(context) {
            resolveCanvasCache(context)
        }
    val okHttpClient =
        remember {
            createOkHttpClient()
        }
    val mediaSourceFactory =
        remember(context, okHttpClient, canvasCache) {
            createCanvasMediaSourceFactory(
                context = context,
                okHttpClient = okHttpClient,
                canvasCache = canvasCache,
            )
        }
    val exoPlayer =
        remember(mediaSourceFactory) {
            val renderersFactory =
                DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true)
            ExoPlayer
                .Builder(context, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    trackSelectionParameters =
                        trackSelectionParameters
                            .buildUpon()
                            .setForceHighestSupportedBitrate(true)
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                            .setMaxVideoSize(CanvasMaxVideoWidth, CanvasMaxVideoHeight)
                            .build()
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = isPlaying
                }
        }

    val controller =
        remember(exoPlayer, initial) {
            CanvasPlaybackController(
                player = exoPlayer,
                initialUrl = initial,
            )
        }

    controller.onReadyChange = currentOnReadyChange

    LaunchedEffect(controller.isVideoReady) {
        currentOnReadyChange(controller.isVideoReady)
    }

    DisposableEffect(Unit) {
        onDispose {
            currentOnReadyChange(false)
        }
    }

    LaunchedEffect(primary, fallback) {
        controller.updateTargets(primary, fallback)
    }

    LaunchedEffect(isPlaying, isStarted, controller, controller.hasFailed) {
        controller.syncPlayback(
            isPlaying = isPlaying,
            isStarted = isStarted,
            lifecycleStateName = lifecycleState.name,
        )
    }

    LaunchedEffect(controller.currentUrl, isPlaying, primary, fallback, controller) {
        controller.monitorPlaybackStall(
            primary = primary,
            fallback = fallback,
            isPlaying = isPlaying,
            isStartedProvider = { lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) },
        )
    }

    DisposableEffect(controller, primary, fallback) {
        val detach =
            controller.attachListener(
                primary = primary,
                fallback = fallback,
                isPlayingProvider = { isPlaying },
                isStartedProvider = { lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) },
            )
        onDispose {
            detach()
        }
    }

    LaunchedEffect(controller.currentUrl, controller) {
        controller.prepareMedia(
            primary = primary,
            fallback = fallback,
            isPlaying = isPlaying,
            isStarted = isStarted,
        )
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            controller.release()
        }
    }

    return controller
}
