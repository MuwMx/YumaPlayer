/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt
import moe.rukamori.archivetune.core.common.math.stackBlurPixels

object ImageBlurUtils {
    /**
     * Apply a stack blur to a bitmap. For large bitmaps (>720px on longest side),
     * the image is downscaled before blurring for performance.
     *
     * @param source The source bitmap (will not be recycled).
     * @param radius Blur radius (typically 1..25, clamped to 0.5..48).
     * @return A new blurred bitmap, or the source if radius is negligible.
     */
    fun blur(
        source: Bitmap,
        radius: Float,
    ): Bitmap {
        val safeRadius = radius.coerceIn(0f, 48f)
        if (safeRadius <= 0.5f) return source

        val maxDimension = max(source.width, source.height)
        if (maxDimension <= 720 || safeRadius < 8f) {
            return stackBlur(source, safeRadius.roundToInt().coerceAtLeast(1))
        }

        val scale = 720f / maxDimension.toFloat()
        val scaledWidth = (source.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (source.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        val blurred = stackBlur(scaled, (safeRadius * scale).roundToInt().coerceAtLeast(1))
        return Bitmap.createScaledBitmap(blurred, source.width, source.height, true)
    }

    /**
     * Stack blur implementation — a fast box-blur approximation that operates
     * on pixel arrays without any native / RenderScript dependencies.
     */
    private fun stackBlur(
        source: Bitmap,
        radius: Int,
    ): Bitmap {
        val bitmap = source.copy(Bitmap.Config.ARGB_8888, true) ?: source
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        stackBlurPixels(pixels, width, height, radius)

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
