/*
 * YumaPlayer (2026) | Ported from Mochy-Music (GPL-3.0) — ui/player/SongCarousel.kt
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.media3.common.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.state.QueueUiState
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor

@Immutable
data class CarouselSong(
    val uid: Any,
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUri: String?,
    val step: Int,
)

val LocalSongCarousel = staticCompositionLocalOf<SongCarousel?> { null }

private const val SettleOmega = 18f
private const val MaxFrameSec = 1f / 40f
private const val RestDistance = 0.0005f
private const val RestSpeed = 0.01f
private const val SkipDistance = 0.004f
private const val SkipSpeed = 0.06f
private const val SkipConfirmMs = 1_200L
private const val SwipeCommit = 0.5f
private const val EdgeGive = 0.3f

@Stable
class SongCarousel internal constructor(private val scope: CoroutineScope) {
    var position by mutableFloatStateOf(0f)
        private set

    var step by mutableIntStateOf(0)
        private set

    var slots by mutableStateOf<List<CarouselSong>>(emptyList())
        private set

    var dragging by mutableStateOf(false)
        private set

    val away: Float get() = abs(position - step)

    val hasPrevious: Boolean get() = slots.any { it.step < step }
    val hasNext: Boolean get() = slots.any { it.step > step }

    fun offsetOf(slot: CarouselSong): Float = slot.step - position

    val playingOffset: Float get() = step - position

    fun crossfadeAlpha(slot: CarouselSong): Float {
        val lower = floor(position).toInt()
        val fraction = position - lower
        return when (slot.step) {
            lower -> 1f
            lower + 1 -> if (slots.any { it.step == lower }) fraction else 1f
            else -> 0f
        }
    }

    private var playing: CarouselSong? = null
    private var previousUid: Any? = null
    private var nextUid: Any? = null
    private var queueIndex = -1
    private var outgoing: CarouselSong? = null

    private var lastWindows: List<Timeline.Window>? = null
    private var lastCurrentIndex: Int = -1
    private var lastTrackUrl: String? = null

    internal var swipeSkip: ((forward: Boolean) -> Unit)? = null

    internal fun onQueue(
        queue: QueueUiState,
        trackUrl: String?,
        title: String?,
        artist: String?,
        coverUrl: String?,
    ) {
        val items = queue.queueWindows
        val index = queue.currentWindowIndex

        if (items === lastWindows && index == lastCurrentIndex && trackUrl == lastTrackUrl && playing != null) {
            return
        }
        lastWindows = items
        lastCurrentIndex = index
        lastTrackUrl = trackUrl

        val now = if (index in items.indices) {
            songOf(items[index], step, trackUrl, title, artist, coverUrl, current = true)
        } else {
            if (trackUrl.isNullOrEmpty()) return
            CarouselSong(
                uid = trackUrl,
                mediaId = trackUrl,
                title = title.orEmpty(),
                artist = artist.orEmpty(),
                album = "",
                artworkUri = coverUrl,
                step = step,
            )
        }

        val last = playing
        if (last != null && last.uid != now.uid) {
            pendingSkip = null
            expectedStep = null
            confirmJob?.cancel()
            val delta = when (now.uid) {
                nextUid -> 1
                previousUid -> -1
                else -> if (index in 0 until queueIndex) -1 else 1
            }
            outgoing = if (now.uid != nextUid && now.uid != previousUid) last else null
            step += delta
        }

        val current = now.copy(step = step)
        playing = current
        queueIndex = index
        previousUid = items.getOrNull(index - 1)?.uid
        nextUid = items.getOrNull(index + 1)?.uid

        val gone = outgoing
        val row = buildList {
            items.getOrNull(index - 1)?.let { add(songOf(it, step - 1, trackUrl, title, artist, coverUrl, current = false)) }
            add(current)
            items.getOrNull(index + 1)?.let { add(songOf(it, step + 1, trackUrl, title, artist, coverUrl, current = false)) }
            if (gone != null && none { it.uid == gone.uid }) {
                removeAll { it.step == gone.step }
                add(gone)
            }
        }.sortedBy { it.step }

        if (row != slots) slots = row
    }

    private fun songOf(
        window: Timeline.Window,
        step: Int,
        trackUrl: String?,
        title: String?,
        artist: String?,
        coverUrl: String?,
        current: Boolean,
    ): CarouselSong {
        val mediaItem = window.mediaItem
        val metadata = mediaItem.mediaMetadata
        val isTrack = mediaItem.mediaId == trackUrl
        val uri = metadata.artworkUri?.toString()
            ?: mediaItem.localConfiguration?.uri?.toString()
            ?: coverUrl.takeIf { current && isTrack }

        return CarouselSong(
            uid = window.uid,
            mediaId = mediaItem.mediaId,
            title = metadata.title?.toString()?.ifEmpty { if (isTrack) title.orEmpty() else "" } ?: if (isTrack) title.orEmpty() else "",
            artist = metadata.artist?.toString()?.ifEmpty { if (isTrack) artist.orEmpty() else "" } ?: if (isTrack) artist.orEmpty() else "",
            album = metadata.albumTitle?.toString().orEmpty(),
            artworkUri = uri,
            step = step,
        )
    }

    private var job: Job? = null
    private var velocity = 0f
    private var headingTo = Float.NaN

    private var pendingSkip: (() -> Unit)? = null
    private var pendingTarget = 0
    private var expectedStep: Int? = null
    private var confirmJob: Job? = null

    private fun restingStep(): Int = expectedStep ?: step

    internal fun settle() {
        if (dragging) return
        glideTo(restingStep())
    }

    private fun firePendingSkip() {
        val skip = pendingSkip ?: return
        pendingSkip = null
        expect(pendingTarget, SkipConfirmMs)
        skip()
    }

    private fun expect(target: Int, confirmMs: Long) {
        expectedStep = target
        confirmJob?.cancel()
        confirmJob = scope.launch {
            delay(confirmMs)
            expectedStep = null
            settle()
        }
    }

    private fun glideTo(target: Int) {
        val to = target.toFloat()
        if (headingTo == to && job?.isActive == true) return
        job?.cancel()
        headingTo = to
        job = scope.launch {
            var lastNanos = 0L
            var resting = false
            while (!resting) {
                resting = withFrameNanos { now ->
                    val dt = if (lastNanos == 0L) 1f / 120f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, MaxFrameSec)
                    lastNanos = now
                    val y = position - to
                    val e = exp(-SettleOmega * dt)
                    val tmp = (velocity + SettleOmega * y) * dt
                    val next = (y + tmp) * e
                    velocity = (velocity - SettleOmega * tmp) * e
                    position = to + next
                    abs(next) < RestDistance && abs(velocity) < RestSpeed
                }
                if (pendingSkip != null && abs(position - to) < SkipDistance && abs(velocity) < SkipSpeed) {
                    firePendingSkip()
                }
            }
            position = to
            velocity = 0f
            headingTo = Float.NaN
            firePendingSkip()
            if (restingStep() != target) {
                glideTo(restingStep())
                return@launch
            }
            if (outgoing != null) {
                outgoing = null
                lastWindows = null
            }
        }
    }

    private var dragFrom = 0f

    fun hold() {
        if (dragging) return
        job?.cancel()
        headingTo = Float.NaN
        velocity = 0f
        firePendingSkip()
    }

    fun startDrag() {
        hold()
        dragFrom = position
        dragging = true
    }

    fun dragTo(travelledPx: Float, spacingPx: Float, hasPrevious: Boolean, hasNext: Boolean) {
        if (!dragging || spacingPx <= 0f) return
        val base = restingStep()
        val raw = dragFrom - travelledPx / spacingPx
        val low = minOf(base - (if (hasPrevious) 1f else 0f), dragFrom)
        val high = maxOf(base + (if (hasNext) 1f else 0f), dragFrom)
        position = when {
            raw < low -> low - (low - raw) * EdgeGive
            raw > high -> high + (raw - high) * EdgeGive
            else -> raw
        }
    }

    fun release(
        velocityPx: Float,
        spacingPx: Float,
        flingPx: Float,
        hasPrevious: Boolean,
        hasNext: Boolean,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
    ): Boolean {
        if (!dragging) return false
        dragging = false
        val spacing = spacingPx.coerceAtLeast(1f)
        val base = restingStep()
        val moved = position - base
        val speed = -velocityPx / spacing
        val fling = flingPx / spacing
        val forward = (moved > SwipeCommit || speed > fling) && speed > -fling && hasNext
        val back = !forward && (moved < -SwipeCommit || speed < -fling) && speed < fling && hasPrevious
        val target = base + when {
            forward -> 1
            back -> -1
            else -> 0
        }
        val skip: (() -> Unit)? = when {
            forward -> onNext
            back -> onPrevious
            else -> null
        }
        pendingTarget = target
        pendingSkip = skip
        velocity = speed
        glideTo(target)
        return skip != null
    }

    fun cancelDrag() {
        if (!dragging) return
        dragging = false
        velocity = 0f
        glideTo(restingStep())
    }
}

@Composable
fun rememberSongCarousel(
    queueState: QueueUiState,
    playerUiState: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
): SongCarousel {
    val scope = rememberCoroutineScope()
    val carousel = remember(scope) { SongCarousel(scope) }

    SideEffect {
        carousel.swipeSkip = { forward ->
            onAction(if (forward) PlayerAction.Next else PlayerAction.Previous)
        }
    }

    carousel.onQueue(
        queue = queueState,
        trackUrl = playerUiState.trackUrl,
        title = playerUiState.title,
        artist = playerUiState.artist,
        coverUrl = playerUiState.coverUrl,
    )

    LaunchedEffect(carousel, carousel.step) {
        carousel.settle()
    }

    return carousel
}

fun Modifier.carouselDragGesture(
    carousel: SongCarousel?,
    enabled: Boolean,
    spacingPxProvider: () -> Float,
    flingThresholdPx: Float,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCommit: () -> Unit = {},
): Modifier {
    if (!enabled || carousel == null) return this
    return this.pointerInput(carousel, enabled) {
        val velocityTracker = VelocityTracker()
        var accumulatedDragX = 0f
        detectHorizontalDragGestures(
            onDragStart = {
                velocityTracker.resetTracking()
                accumulatedDragX = 0f
                carousel.startDrag()
            },
            onDragEnd = {
                val velocityX = velocityTracker.calculateVelocity().x
                val committed = carousel.release(
                    velocityPx = velocityX,
                    spacingPx = spacingPxProvider().coerceAtLeast(1f),
                    flingPx = flingThresholdPx,
                    hasPrevious = carousel.hasPrevious,
                    hasNext = carousel.hasNext,
                    onNext = onNext,
                    onPrevious = onPrevious,
                )
                if (committed) onCommit()
            },
            onDragCancel = {
                carousel.cancelDrag()
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                accumulatedDragX += dragAmount
                carousel.dragTo(
                    travelledPx = accumulatedDragX,
                    spacingPx = spacingPxProvider().coerceAtLeast(1f),
                    hasPrevious = carousel.hasPrevious,
                    hasNext = carousel.hasNext,
                )
            }
        )
    }
}
