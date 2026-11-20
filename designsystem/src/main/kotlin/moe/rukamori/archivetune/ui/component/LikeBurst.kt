/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Ported from Mochy-Music (GPL-3.0) — ui/player/LikeBurst.kt
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.designsystem.R
import moe.rukamori.archivetune.ui.haptics.YumaHaptics
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** Which like button a heart flies home to: the one on the page that's showing. */
enum class LikeSlot { PLAYER, LYRICS, QUEUE }

private val LikeRed = Color(0xFFFF2D55)
private val LikePink = Color(0xFFFF8FAB)

/** The heart at the centre, at rest. Drawn larger and scaled down, so the thump stays crisp. */
private val HeartWidth = 104.dp
private const val HeartAspect = 76f / 88f
private const val HeartDrawOversize = 1.4f

/** The glyph inside a like button, which the heart leaves from and shrinks back into. */
private val ButtonGlyphWidth = 18.dp

private val ParticleWidth = 14.dp
private const val ParticleCount = 8

private const val TravelMs = 460
private const val FloatMs = 640
private const val HomeMs = 540

/** Shoots a touch past the centre and settles back: the flight in. */
private val TravelEasing = CubicBezierEasing(0.3f, 1.22f, 0.5f, 1f)
private val PopEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** Hangs back for a beat, then dives into the button. */
private val HomeEasing = CubicBezierEasing(0.5f, -0.18f, 0.7f, 1f)

/** Double taps in quick succession each get a heart, up to this many in the air at once. */
private const val MaxFlights = 4

/**
 * Drives the like animation: a heart leaves the like button (or the spot double-tapped on the
 * cover), arcs up to the middle of the screen, thumps red with a burst, floats back to white, and
 * dives home into the like button, which bounces as it lands.
 */
@Stable
class LikeBurstState {
    internal class Flight(val id: Long, val origin: Offset, val fromButton: Boolean, val seed: Int)

    internal val flights = mutableStateListOf<Flight>()
    private var nextId = 0L

    /** Each like button's bounds in root coordinates. Written on layout, read while drawing. */
    internal val buttonBounds = HashMap<LikeSlot, Rect>()

    /** The page showing, so a heart heads for the button the user can see. */
    var slotProvider: () -> LikeSlot = { LikeSlot.PLAYER }

    /** The like button's scale: 1 at rest, kicked when a heart lands. */
    internal val landing = Animatable(1f)

    /** Counts likes (not unlikes), for a caller that wants to react to a fresh like. */
    var likeCount by mutableIntStateOf(0)
        private set

    internal fun homeTarget(): Offset? = buttonBounds[slotProvider()]?.center

    /** A like button tapped. Liking sends a heart up out of the button; unliking is quiet. */
    fun likeFromButton(slot: LikeSlot, liked: Boolean, toggleLike: () -> Unit) {
        if (!liked) {
            buttonBounds[slot]?.let { launch(it.center, fromButton = true) }
            likeCount++
        }
        toggleLike()
    }

    /**
     * A double tap on the cover. Always a like, never an unlike - the heart still flies on a song
     * that's already liked, it just doesn't toggle anything.
     */
    fun likeFromDoubleTap(rootPosition: Offset, liked: Boolean, toggleLike: () -> Unit) {
        launch(rootPosition, fromButton = false)
        if (!liked) {
            likeCount++
            toggleLike()
        }
    }

    fun clear() = flights.clear()

    private fun launch(origin: Offset, fromButton: Boolean) {
        if (flights.size >= MaxFlights) flights.removeAt(0)
        flights += Flight(nextId++, origin, fromButton, Random.nextInt())
    }
}

/**
 * Marks a like button as the home of its [slot]: hearts launch from and land on it, and it
 * bounces when one lands. Put it on the button's modifier.
 */
fun Modifier.likeButtonTarget(state: LikeBurstState?, slot: LikeSlot): Modifier =
    if (state == null) {
        this
    } else {
        this
            .onGloballyPositioned { state.buttonBounds[slot] = it.boundsInRoot() }
            .graphicsLayer {
                if (state.slotProvider() == slot) {
                    val s = state.landing.value
                    scaleX = s
                    scaleY = s
                }
            }
    }

/** Draws the hearts in flight. Full screen, above the player, and never takes a touch. */
@Composable
fun LikeBurstOverlay(state: LikeBurstState, modifier: Modifier = Modifier) {
    // The bounce outlives the heart that started it, so it runs here, not in the flight - and
    // this is remembered ahead of the early return, so it isn't cancelled with the last heart.
    val scope = rememberCoroutineScope()
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    if (state.flights.isEmpty()) return
    val heart = painterResource(R.drawable.ic_untitled_heart)
    // A second painter for the burst: a vector painter caches one size, and the particles are
    // drawn at a different one from the main heart.
    val particle = painterResource(R.drawable.ic_untitled_heart)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOffset = it.positionInRoot() },
    ) {
        for (flight in state.flights) {
            key(flight.id) {
                HeartFlight(
                    state = state,
                    flight = flight,
                    heart = heart,
                    particle = particle,
                    rootOffset = { rootOffset },
                    bounceScope = scope,
                    onDone = { state.flights.remove(flight) },
                )
            }
        }
    }
}

private class Particle(
    val dx: Float,
    val dy: Float,
    val reach: Dp,
    val scale: Float,
    val spin: Float,
    val color: Color,
)

@Composable
private fun HeartFlight(
    state: LikeBurstState,
    flight: LikeBurstState.Flight,
    heart: Painter,
    particle: Painter,
    rootOffset: () -> Offset,
    bounceScope: CoroutineScope,
    onDone: () -> Unit,
) {
    val haptics = rememberYumaHaptics()
    val glyphScale = ButtonGlyphWidth / HeartWidth

    val travel = remember { Animatable(0f) }
    val scale = remember { Animatable(if (flight.fromButton) glyphScale else 0f) }
    val redness = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val float = remember { Animatable(0f) }
    val home = remember { Animatable(0f) }

    val random = remember { Random(flight.seed) }
    // Curves one way or the other, so repeated hearts don't trace the same path.
    val side = remember { if (random.nextBoolean()) 1f else -1f }
    val particles = remember {
        val palette = listOf(LikeRed, LikePink, Color.White)
        List(ParticleCount) { i ->
            val angle = i * 2f * PI.toFloat() / ParticleCount + (random.nextFloat() - 0.5f) * 0.5f
            Particle(
                dx = cos(angle),
                dy = sin(angle),
                reach = (44 + random.nextInt(36)).dp,
                scale = 0.6f + random.nextFloat() * 0.5f,
                spin = (random.nextFloat() - 0.5f) * 70f,
                color = palette[i % palette.size],
            )
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope {
            if (!flight.fromButton) haptics.gestureThresholdActivate()

            // In: pops to full size as it arcs up to the middle.
            val pop = launch { scale.animateTo(1f, tween(TravelMs, easing = PopEasing)) }
            travel.animateTo(1f, tween(TravelMs, easing = TravelEasing))
            pop.join()

            // The thump: red, a beat bigger, a ring and a spray of little hearts.
            haptics.confirm()
            launch { redness.animateTo(1f, tween(110)) }
            launch { burst.animateTo(1f, tween(620, easing = LinearOutSlowInEasing)) }
            scale.animateTo(1.32f, tween(110, easing = LinearOutSlowInEasing))
            launch { scale.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 360f)) }

            // Float: bobs and wobbles while the red drains back to white.
            launch {
                delay(160)
                redness.animateTo(0f, tween(520))
            }
            float.animateTo(1f, tween(FloatMs, easing = LinearEasing))

            // Home, into whichever like button is showing.
            home.animateTo(1f, tween(HomeMs, easing = HomeEasing))
            haptics.segmentTick()
            bounceScope.launch {
                state.landing.snapTo(1f)
                state.landing.animateTo(1.3f, tween(90, easing = LinearOutSlowInEasing))
                state.landing.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 520f))
            }
        }
        onDone()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val offset = rootOffset()
        val origin = flight.origin - offset
        val center = Offset(size.width / 2f, size.height * 0.44f)
        val heartW = HeartWidth.toPx()

        // In: an arc up and over from the origin.
        val travelControl = Offset(
            (origin.x + center.x) / 2f + side * 48.dp.toPx(),
            min(origin.y, center.y) - 110.dp.toPx(),
        )
        val t = travel.value
        val inPos = quad(origin, travelControl, center, t)

        // Float: a slow rise with a bob and a sway.
        val f = float.value
        fun bobAt(f: Float) = Offset(
            sin(f * 2f * PI.toFloat() * 1.25f) * 7.dp.toPx() * side,
            -sin(f * 2f * PI.toFloat()) * 9.dp.toPx() - f * 16.dp.toPx(),
        )
        val floatPos = center + bobAt(f)
        val floatEnd = center + bobAt(1f)

        // Home: rises off the float a little, then dives down into the button.
        val target = state.homeTarget()?.minus(offset) ?: origin
        val h = home.value
        val homeControl = Offset(lerp(floatEnd.x, target.x, 0.25f), floatEnd.y - 90.dp.toPx())
        // != rather than >: the home curve dips below 0 first, a small wind-up away from the button.
        val pos = when {
            h != 0f -> quad(floatEnd, homeControl, target, h)
            f > 0f -> floatPos
            else -> inPos
        }

        val wobble = sin(f * 2f * PI.toFloat() * 1.5f) * 8f
        val rotation = when {
            h != 0f -> lerp(wobble, 0f, h) + sin(h * PI.toFloat()) * 16f * sign(target.x - floatEnd.x)
            f > 0f -> wobble
            else -> lerp(-22f * side, 0f, t.coerceIn(0f, 1f))
        }

        val homeShrink = lerp(1f, glyphScale, h * h)
        val s = scale.value * homeShrink
        // Jelly on the thump: wider than tall while it's over full size.
        val squash = (scale.value - 1f).coerceAtLeast(0f)
        val sx = s * (1f + squash * 0.28f)
        val sy = s * (1f - squash * 0.14f)
        val alpha = 1f - ((h - 0.84f) / 0.16f).coerceIn(0f, 1f)
        val red = redness.value
        val color = lerp(Color.White, LikeRed, red)

        drawBurst(center, heartW, particles, particle, burst.value)

        if (alpha > 0f && s > 0f) {
            // A soft shadow so a white heart still reads over a bright cover, and a red glow at the thump.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = 0.22f * alpha), Color.Transparent),
                    center = pos + Offset(0f, 8.dp.toPx() * s),
                    radius = heartW * 0.75f * s,
                ),
                radius = heartW * 0.75f * s,
                center = pos + Offset(0f, 8.dp.toPx() * s),
            )
            if (red > 0.01f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(LikeRed.copy(alpha = 0.45f * red * alpha), Color.Transparent),
                        center = pos,
                        radius = heartW * 0.95f * s,
                    ),
                    radius = heartW * 0.95f * s,
                    center = pos,
                )
            }

            val drawW = heartW * HeartDrawOversize
            val drawH = drawW * HeartAspect
            withTransform({
                translate(pos.x, pos.y)
                rotate(rotation, pivot = Offset.Zero)
                scale(sx / HeartDrawOversize, sy / HeartDrawOversize, pivot = Offset.Zero)
            }) {
                translate(-drawW / 2f, -drawH / 2f) {
                    with(heart) { draw(Size(drawW, drawH), alpha = alpha, colorFilter = ColorFilter.tint(color)) }
                }
            }
        }
    }
}

/** The ring and the spray of little hearts thrown off at the thump. */
private fun DrawScope.drawBurst(
    center: Offset,
    heartW: Float,
    particles: List<Particle>,
    particle: Painter,
    progress: Float,
) {
    if (progress <= 0f || progress >= 1f) return
    val out = 1f - (1f - progress) * (1f - progress) * (1f - progress) // ease-out cubic
    val fade = 1f - progress

    drawCircle(
        color = lerp(LikeRed, Color.White, progress * 0.6f).copy(alpha = 0.7f * fade),
        radius = heartW * 0.5f * lerp(0.7f, 1.9f, out),
        center = center,
        style = Stroke(width = lerp(5.dp.toPx(), 0.5.dp.toPx(), progress)),
    )

    val w = ParticleWidth.toPx()
    val h = w * HeartAspect
    val start = heartW * 0.32f
    for (p in particles) {
        val distance = start + p.reach.toPx() * out
        val at = center + Offset(p.dx * distance, p.dy * distance)
        val s = p.scale * (1f - progress * 0.5f)
        withTransform({
            translate(at.x, at.y)
            rotate(p.spin * progress, pivot = Offset.Zero)
            scale(s, s, pivot = Offset.Zero)
        }) {
            translate(-w / 2f, -h / 2f) {
                with(particle) { draw(Size(w, h), alpha = fade, colorFilter = ColorFilter.tint(p.color)) }
            }
        }
    }
}

private fun quad(a: Offset, control: Offset, b: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        u * u * a.x + 2f * u * t * control.x + t * t * b.x,
        u * u * a.y + 2f * u * t * control.y + t * t * b.y,
    )
}
