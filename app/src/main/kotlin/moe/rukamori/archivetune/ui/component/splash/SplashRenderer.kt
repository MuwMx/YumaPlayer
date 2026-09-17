package moe.rukamori.archivetune.ui.component.splash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

const val LINK_BINS: Int = 16
const val LINK_LINE_WIDTH_DP: Float = 1.5f
const val SHOCKWAVE_STROKE_WIDTH_DP: Float = 2.0f

class SplashRenderer {
    val starPath = Path()
    private val defaultLoops = listOf(0 until SplashSlots.SLOT_COUNT)

    val okBins = Array(LINK_BINS) { i ->
        Color.White.copy(alpha = (i + 0.5f) / LINK_BINS.toFloat())
    }
    val failBins = Array(LINK_BINS) { i ->
        Fu.fail.color.copy(alpha = (i + 0.5f) / LINK_BINS.toFloat())
    }

    private var cachedDensity = -1f
    private val slotLookup = arrayOfNulls<SplashParticle>(128)
    private var whiteSprite: ImageBitmap? = null
    private var failSprite: ImageBitmap? = null

    private var cachedGlowBrush: Brush? = null
    private var cachedGlowRadius: Float = -1f
    private var cachedGlowColor: Color = Color.Unspecified
    private var cachedGlowStrength: Float = -1f

    private fun glowSprite(base: Color): ImageBitmap {
        val size = 128
        val bmp = ImageBitmap(size, size)
        val c = size / 2f
        val alphas = floatArrayOf(1f, 0.72f, 0.3f, 0.09f, 0.025f, 0f)
        val stops = floatArrayOf(0f, 0.1f, 0.24f, 0.5f, 0.78f, 1f)
        val colors = IntArray(alphas.size) { i -> base.copy(alpha = alphas[i]).toArgb() }
        val paint = android.graphics.Paint().apply {
            shader = android.graphics.RadialGradient(
                c, c, c, colors, stops, android.graphics.Shader.TileMode.CLAMP
            )
        }
        android.graphics.Canvas(bmp.asAndroidBitmap()).drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        return bmp
    }

    private fun spriteFor(isCross: Boolean): ImageBitmap {
        return if (isCross) {
            failSprite ?: glowSprite(Fu.fail.color).also { failSprite = it }
        } else {
            whiteSprite ?: glowSprite(Color.White).also { whiteSprite = it }
        }
    }
    var linkStrokeWidthPx: Float = 1.5f
        private set
    var igniteStrokeWidthPx: Float = 2.5f
        private set
    var shockwaveStroke: Stroke = Stroke(width = 2f)
        private set

    fun ensureDensity(density: Float) {
        if (cachedDensity != density) {
            cachedDensity = density
            linkStrokeWidthPx = LINK_LINE_WIDTH_DP * density
            igniteStrokeWidthPx = 2.5f * density
            shockwaveStroke = Stroke(width = SHOCKWAVE_STROKE_WIDTH_DP * density)
        }
    }

    fun DrawScope.render(engine: SplashEngine) {
        ensureDensity(density)
        drawFormationGlow(engine)

        val timeSec = System.currentTimeMillis() / 1000f
        val isErrorCross = engine.shape == SplashSlots.SHAPE_CROSS && engine.currentPhase == SplashPhase.Error
        val swingDeg = if (isErrorCross) sin(timeSec * SplashConfig.Effects.SWING_SPEED) * SplashConfig.Effects.SWING_ANGLE_DEG else 0f
        val breathScale = if (isErrorCross) 1f + sin(timeSec * SplashConfig.Effects.BREATH_SPEED) * SplashConfig.Effects.BREATH_SCALE else 1f
        val floatY = if (isErrorCross) sin(timeSec * SplashConfig.Effects.FLOAT_Y_SPEED) * SplashConfig.Effects.FLOAT_Y_DP.dp.toPx() else 0f

        translate(top = floatY) {
            rotate(degrees = swingDeg, pivot = center) {
                scale(scale = breathScale, pivot = center) {
                    drawFormationLinks(engine)
                    drawParticles(engine)
                    if (engine.phase == "ignite") {
                        drawIgnite(engine)
                    }
                }
            }
        }

        drawShockwave(
            shockwave = engine.shockwave,
            color = if (engine.shape == SplashSlots.SHAPE_CROSS) Fu.fail.color else Color.White
        )
        drawScreenFlash(engine)
    }

    fun DrawScope.drawFormationGlow(engine: SplashEngine) {
        if (engine.formStrength <= 0.005f || size.height <= 0f) return
        val baseColor = if (engine.shape == SplashSlots.SHAPE_CROSS) Fu.fail.color else Fu.ok.color
        val r = size.height * 0.9f
        if (r <= 0f) return
        val c = center
        val strength = (engine.formStrength * 20f).toInt() / 20f
        if (cachedGlowBrush == null || cachedGlowRadius != r || cachedGlowColor != baseColor || cachedGlowStrength != strength) {
            cachedGlowRadius = r
            cachedGlowColor = baseColor
            cachedGlowStrength = strength
            cachedGlowBrush = Brush.radialGradient(
                0.0f to baseColor.copy(alpha = (strength * 0.15f).coerceIn(0f, 1f)),
                0.35f to baseColor.copy(alpha = (strength * 0.06f).coerceIn(0f, 1f)),
                1.0f to Color.Transparent,
                center = c,
                radius = r
            )
        }
        cachedGlowBrush?.let { brush ->
            drawCircle(brush = brush, radius = r, center = c)
        }
    }

    fun DrawScope.drawFormationLinks(engine: SplashEngine) {
        if (engine.formStrength <= 0.01f || (engine.phase != "gather" && engine.phase != "ignite" && engine.phase != "error" && engine.phase != "transit")) return

        val boxSize = SplashSlots.boxSize(engine.shape, engine.width, engine.height, density)
        val maxDist = maxOf(boxSize * 0.65f, SplashConfig.Effects.LINK_DISTANCE_DP.dp.toPx())
        if (maxDist <= 0f) return

        val maxDistSq = maxDist * maxDist
        val invMaxDist = 1f / maxDist

        val isIgnite = engine.phase == "ignite"
        val strokeW = if (isIgnite) igniteStrokeWidthPx else linkStrokeWidthPx
        val bins = okBins

        for (j in slotLookup.indices) slotLookup[j] = null
        for (p in engine.particles) {
            if (p.isMember && p.slotIndex >= 0 && p.slotIndex < slotLookup.size) {
                slotLookup[p.slotIndex] = p
            }
        }

        val loops = engine.currentShapeData.loops.ifEmpty { defaultLoops }
        val slotCount = if (engine.currentShapeData.slots.isNotEmpty()) engine.currentShapeData.slots.size else SplashSlots.SLOT_COUNT
        for (range in loops) {
            val count = range.count()
            for (i in 0 until count) {
                val idx1 = range.first + i
                val idx2 = range.first + ((i + 1) % count)
                if (idx1 !in slotLookup.indices || idx2 !in slotLookup.indices) continue
                val p1 = slotLookup[idx1] ?: continue
                val p2 = slotLookup[idx2] ?: continue

                val dx1 = p1.x - p1.targetX
                val dy1 = p1.y - p1.targetY
                val dist1Sq = dx1 * dx1 + dy1 * dy1

                val dx2 = p2.x - p2.targetX
                val dy2 = p2.y - p2.targetY
                val dist2Sq = dx2 * dx2 + dy2 * dy2

                val conv1 = if (dist1Sq < maxDistSq) max(0f, 1f - kotlin.math.sqrt(dist1Sq) * invMaxDist) else 0f
                val conv2 = if (dist2Sq < maxDistSq) max(0f, 1f - kotlin.math.sqrt(dist2Sq) * invMaxDist) else 0f
                val rawAlpha = engine.formStrength * min(conv1, conv2) * 0.72f

                if (rawAlpha > 0.01f || isIgnite) {
                    val segNorm = (idx1 + 0.5f) / slotCount.toFloat()
                    var waveDist = abs(segNorm - engine.pulseWave)
                    if (waveDist > 0.5f) waveDist = 1.0f - waveDist
                    val waveBoost = max(0f, 1f - waveDist / 0.12f).pow(2)

                    val bin = ((rawAlpha + waveBoost * 0.4f).coerceIn(0f, 1f) * LINK_BINS).toInt().coerceIn(0, LINK_BINS - 1)
                    val lineCol = if (isIgnite) Color.White else bins[bin]
                    val lineAlpha = if (isIgnite) 1.0f else min(1f, rawAlpha + waveBoost * 0.9f)

                    drawLine(
                        color = lineCol.copy(alpha = lineAlpha),
                        start = Offset(p1.x, p1.y),
                        end = Offset(p2.x, p2.y),
                        strokeWidth = strokeW * (1f + (if (isIgnite) 0f else waveBoost * 1.6f))
                    )
                }
            }
        }
    }

    fun DrawScope.drawParticles(engine: SplashEngine) {
        val particles = engine.particles
        val isCross = engine.shape == SplashSlots.SHAPE_CROSS
        val isDust = engine.phase == "dust"
        val globalOp = if (isDust) engine.globalOpacity else 1f
        val memberColor = if (isCross) Fu.fail.color else Color.White
        val memberCore = if (isCross) Fu.fail.coreColor else Color.White
        val slotCount = if (engine.currentShapeData.slots.isNotEmpty()) engine.currentShapeData.slots.size else SplashSlots.SLOT_COUNT
        val maxHalo = SplashConfig.Effects.MAX_HALO_DP.dp.toPx()
        val sprite = spriteFor(isCross)

        for (i in particles.indices) {
            val p = particles[i]
            val glow = engine.formStrength.coerceIn(0f, 1f)
            val color = if (p.isMember) {
                if (p.isRare) memberCore else memberColor
            } else {
                if (isCross && glow > 0.4f) Fu.fail.color else Color.White
            }
            val memberAlpha = 0.95f * (0.45f + 0.55f * glow)
            val floaterAlpha = 0.4f * p.depth * p.lum * (1f - 0.55f * glow)
            val baseAlpha = if (p.isMember) memberAlpha else floaterAlpha
            val alpha = (baseAlpha * globalOp * engine.particleAlpha).coerceIn(0f, 1f)

            if (alpha > 0.005f && p.radius > 0f) {
                var waveBoost = 0f
                if (p.isMember && p.slotIndex >= 0 && glow > 0.5f) {
                    val slotNorm = p.slotIndex / slotCount.toFloat()
                    var waveDist = abs(slotNorm - engine.pulseWave)
                    if (waveDist > 0.5f) waveDist = 1.0f - waveDist
                    waveBoost = max(0f, 1f - waveDist / 0.14f).pow(2)
                }

                val isIgnite = engine.phase == "ignite"
                val igniteFlare = if (isIgnite && p.isMember) 1.4f else 1f
                val center = Offset(p.x, p.y)
                val currentRadius = p.radius * (1f + waveBoost * 0.75f) * igniteFlare

                val vOuter = (currentRadius * (9.0f + p.depth * 4.5f)).coerceAtMost(maxHalo)
                drawImage(
                    image = sprite,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(128, 128),
                    dstOffset = IntOffset(
                        (center.x - vOuter / 2f).roundToInt(),
                        (center.y - vOuter / 2f).roundToInt()
                    ),
                    dstSize = IntSize(vOuter.roundToInt(), vOuter.roundToInt()),
                    alpha = min(1f, alpha * 0.55f)
                )

                val vMid = (currentRadius * 4.5f).coerceAtMost(maxHalo)
                drawImage(
                    image = sprite,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(128, 128),
                    dstOffset = IntOffset(
                        (center.x - vMid / 2f).roundToInt(),
                        (center.y - vMid / 2f).roundToInt()
                    ),
                    dstSize = IntSize(vMid.roundToInt(), vMid.roundToInt()),
                    alpha = min(1f, alpha * 1.0f)
                )

                val coreCol = if (isIgnite && p.isMember) Color.White else (if (waveBoost > 0.12f) memberCore else color)
                drawCircle(
                    color = coreCol.copy(alpha = min(1f, alpha * 1.0f)),
                    radius = currentRadius * 0.7f,
                    center = center
                )
            }
        }
    }

    fun DrawScope.drawFourPointStar(
        cx: Float,
        cy: Float,
        radius: Float,
        alpha: Float,
        color: Color = Color.White
    ) {
        if (alpha <= 0.005f || radius <= 0f) return

        val center = Offset(cx, cy)
        val haloRadius = radius * 1.6f
        val vStarHalo = (haloRadius * 2f).roundToInt()
        val isFail = color == Fu.fail.coreColor || color == Fu.fail.color
        val sprite = spriteFor(isFail)
        drawImage(
            image = sprite,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(128, 128),
            dstOffset = IntOffset((center.x - vStarHalo / 2f).roundToInt(), (center.y - vStarHalo / 2f).roundToInt()),
            dstSize = IntSize(vStarHalo, vStarHalo),
            alpha = (alpha * 0.45f).coerceIn(0f, 1f)
        )

        starPath.reset()
        val inner = radius * 0.08f
        starPath.moveTo(cx, cy - radius)
        starPath.quadraticTo(cx + inner, cy - inner, cx + radius, cy)
        starPath.quadraticTo(cx + inner, cy + inner, cx, cy + radius)
        starPath.quadraticTo(cx - inner, cy + inner, cx - radius, cy)
        starPath.quadraticTo(cx - inner, cy - inner, cx, cy - radius)
        starPath.close()

        drawPath(
            path = starPath,
            color = color.copy(alpha = alpha.coerceIn(0f, 1f))
        )
    }

    fun DrawScope.drawIgnite(engine: SplashEngine) {
        val coreColor = if (engine.shape == SplashSlots.SHAPE_CROSS) Fu.fail.coreColor else Color.White

        val elapsed = engine.phaseElapsedMs
        val limit = if (engine.isShort) SplashConfig.Timings.IGNITE_SHORT_MS else SplashConfig.Timings.IGNITE_FULL_MS
        val stagger = if (engine.isShort) SplashConfig.Effects.STAR_STAGGER_SHORT_MS else SplashConfig.Effects.STAR_STAGGER_MS
        val window = limit * 0.85f
        val starBase = SplashConfig.Effects.STAR_SIZE_DP.dp.toPx()
        val tips = engine.currentShapeData.tips
        for (d in tips.indices) {
            val p = ((elapsed - d * stagger) / window).coerceIn(0f, 1f)
            if (p <= 0f || p >= 1f) continue
            val flare = sin(p * Math.PI.toFloat())
            val tip = tips[d]
            drawFourPointStar(tip.x, tip.y, radius = starBase * flare, alpha = flare, color = coreColor)
        }
    }

    fun DrawScope.drawShockwave(
        shockwave: SplashShockwave?,
        color: Color = Color.White
    ) {
        val sw = shockwave ?: return
        if (sw.radius <= 0f || sw.maxRadius <= 0f) return
        val progress = (sw.radius / sw.maxRadius).coerceIn(0f, 1f)
        val effectiveAlpha = (sw.alpha * (1f - progress)).coerceIn(0f, 1f)
        if (effectiveAlpha <= 0.003f) return

        drawCircle(
            color = color.copy(alpha = effectiveAlpha),
            radius = sw.radius,
            center = Offset(sw.x, sw.y),
            style = shockwaveStroke
        )
    }

    fun DrawScope.drawScreenFlash(engine: SplashEngine) {
        if (engine.currentPhase != SplashPhase.Burst || engine.phaseElapsedMs > 120f) return
        val progress = (engine.phaseElapsedMs / 120f).coerceIn(0f, 1f)
        val alpha = 0.25f * (1f - progress)
        if (alpha <= 0.005f) return
        val radius = 200.dp.toPx()
        val brush = Brush.radialGradient(
            0.0f to Color.White.copy(alpha = alpha),
            1.0f to Color.Transparent,
            center = center,
            radius = radius,
        )
        drawCircle(
            brush = brush,
            radius = radius,
            center = center,
        )
    }

    fun DrawScope.drawScreenFlash(screenFlash: Float, color: Color = Color.White) {
        if (screenFlash > 0.005f) {
            drawRect(color = color.copy(alpha = screenFlash.coerceIn(0f, 1f)))
        }
    }
}
