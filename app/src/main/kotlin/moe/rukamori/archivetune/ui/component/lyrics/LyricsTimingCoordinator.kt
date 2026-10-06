/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.media3.common.Player
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToLong

private const val LRC_LEAD_MS = 300L
private const val TTML_LEAD_MS = 0L
private const val LYRIC_VISUAL_TUNING_OFFSET_MS = 150L
private const val MANUAL_SCROLL_DEBOUNCE_MS = 50L
private const val SMOOTH_PLAYBACK_MAX_FORWARD_DRIFT_MS = 80L
private const val SMOOTH_PLAYBACK_MAX_BACKWARD_DRIFT_MS = 180L
private const val SMOOTH_PLAYBACK_DRIFT_CORRECTION = 0.55f

@Stable
internal class LyricsTimingState(
    val playbackSyncPosition: () -> Int,
    val lineFocusPosition: () -> Int,
    val nestedScrollConnection: NestedScrollConnection,
    val isManualScrolling: Boolean,
    val seekTo: (Long) -> Unit,
)

@Composable
internal fun rememberLyricsTimingState(
    player: Player,
    lyricsSessionKey: Pair<String, String?>,
    syncedLyrics: SyncedLyrics,
    isTtmlFormat: Boolean,
    animationsDisabled: Boolean,
    playbackSpeed: Float,
    isReadyToParse: Boolean,
    isLyricsVisible: Boolean,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    listState: LazyListState,
    isSelectionModeActive: Boolean,
    onSessionReset: () -> Unit = {},
): LyricsTimingState {
    val leadMs = if (isTtmlFormat) TTML_LEAD_MS else LRC_LEAD_MS

    val latestSliderPositionProvider = rememberUpdatedState(sliderPositionProvider)
    val latestLyricsSyncOffset = rememberUpdatedState(lyricsSyncOffset)
    val latestLeadMs = rememberUpdatedState(leadMs)
    val latestPlaybackSpeed = rememberUpdatedState(playbackSpeed)
    val latestIsLyricsVisible = rememberUpdatedState(isLyricsVisible)
    val latestOnSessionReset = rememberUpdatedState(onSessionReset)

    val playbackPositionMs =
        remember(player) {
            mutableLongStateOf(player.currentPosition.coerceAtLeast(0L))
        }
    var isManualScrolling by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }
    var frozenPositionMs by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(lyricsSessionKey) {
        playbackPositionMs.longValue = player.currentPosition.coerceAtLeast(0L)
        isManualScrolling = false
        lastManualScrollTime = 0L
        frozenPositionMs = -1L
        latestOnSessionReset.value()
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
            listState.scrollToItem(0, 0)
        }
    }

    LaunchedEffect(player, lyricsSessionKey, animationsDisabled, playbackSpeed, isReadyToParse, isLyricsVisible) {
        if (!isReadyToParse) {
            playbackPositionMs.longValue = player.currentPosition.coerceAtLeast(0L)
            return@LaunchedEffect
        }
        if (!isLyricsVisible) return@LaunchedEffect
        var wasSliderActive = false
        var anchorPlayerPositionMs = player.currentPosition.coerceAtLeast(0L)
        var anchorFrameNanos = 0L
        while (isActive) {
            if (!latestIsLyricsVisible.value) {
                anchorPlayerPositionMs = player.currentPosition.coerceAtLeast(0L)
                anchorFrameNanos = 0L
                delay(200L)
                continue
            }

            val sliderPosition = latestSliderPositionProvider.value()
            val isSliderActive = sliderPosition != null
            if (isSliderActive && !wasSliderActive) {
                isManualScrolling = false
            }
            wasSliderActive = isSliderActive

            val rawPosition = (sliderPosition ?: player.currentPosition).coerceAtLeast(0L)
            if (sliderPosition != null || !player.isPlaying || animationsDisabled) {
                anchorPlayerPositionMs = rawPosition
                anchorFrameNanos = 0L
                if (playbackPositionMs.longValue != rawPosition) {
                    playbackPositionMs.longValue = rawPosition
                }
                if (sliderPosition == null) {
                    delay(100L)
                } else {
                    withFrameNanos { }
                }
            } else {
                val frameNanos = withFrameNanos { frameTimeNanos -> frameTimeNanos }
                if (anchorFrameNanos == 0L) {
                    anchorFrameNanos = frameNanos
                    anchorPlayerPositionMs = rawPosition
                }

                val elapsedMs = ((frameNanos - anchorFrameNanos) / 1_000_000f) * latestPlaybackSpeed.value
                val projectedPosition = anchorPlayerPositionMs + elapsedMs.roundToLong()
                val driftMs = rawPosition - projectedPosition
                val nextPosition =
                    when {
                        driftMs > SMOOTH_PLAYBACK_MAX_FORWARD_DRIFT_MS ||
                            driftMs < -SMOOTH_PLAYBACK_MAX_BACKWARD_DRIFT_MS -> {
                            anchorPlayerPositionMs = rawPosition
                            anchorFrameNanos = frameNanos
                            rawPosition
                        }

                        driftMs != 0L -> {
                            projectedPosition + (driftMs * SMOOTH_PLAYBACK_DRIFT_CORRECTION).roundToLong()
                        }

                        else -> {
                            projectedPosition
                        }
                    }.coerceAtLeast(0L)

                if (playbackPositionMs.longValue != nextPosition) {
                    playbackPositionMs.longValue = nextPosition
                }
            }
        }
    }

    val playbackSyncPosition: () -> Int =
        remember(listState) {
            {
                val frozen = frozenPositionMs
                val baseMs =
                    if (isManualScrolling && listState.isScrollInProgress && frozen >= 0L) {
                        frozen
                    } else {
                        playbackPositionMs.longValue
                    }
                (
                    baseMs +
                        latestLyricsSyncOffset.value.toLong() +
                        latestLeadMs.value +
                        LYRIC_VISUAL_TUNING_OFFSET_MS
                ).coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt()
            }
        }

    val lineFocusPosition: () -> Int =
        remember(syncedLyrics) {
            {
                syncedLyrics.positionForStableLineFocus(playbackSyncPosition())
            }
        }

    val nestedScrollConnection =
        remember {
            var lastUserScrollEventMs = 0L
            object : NestedScrollConnection {
                private fun markManualScroll() {
                    val now = System.currentTimeMillis()
                    if (now - lastUserScrollEventMs >= MANUAL_SCROLL_DEBOUNCE_MS) {
                        isManualScrolling = true
                        lastManualScrollTime = now
                        lastUserScrollEventMs = now
                    }
                }

                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!isSelectionModeActive && source == NestedScrollSource.UserInput) {
                        markManualScroll()
                    }
                    return Offset.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    if (!isSelectionModeActive && isManualScrolling) {
                        lastManualScrollTime = System.currentTimeMillis()
                    }
                    return Velocity.Zero
                }
            }
        }

    LaunchedEffect(isManualScrolling, lastManualScrollTime) {
        if (isManualScrolling) {
            frozenPositionMs = playbackPositionMs.longValue
        } else {
            frozenPositionMs = -1L
        }
    }

    val seekTo: (Long) -> Unit =
        remember(player) {
            { targetPositionMs ->
                frozenPositionMs = -1L
                player.seekTo(targetPositionMs)
            }
        }

    return remember(playbackSyncPosition, lineFocusPosition, nestedScrollConnection, isManualScrolling, seekTo) {
        LyricsTimingState(
            playbackSyncPosition = playbackSyncPosition,
            lineFocusPosition = lineFocusPosition,
            nestedScrollConnection = nestedScrollConnection,
            isManualScrolling = isManualScrolling,
            seekTo = seekTo,
        )
    }
}
