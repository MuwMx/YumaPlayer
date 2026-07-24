package moe.rukamori.archivetune.home.effects

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

object HomeBackgroundEffects {

    @Immutable
    data class BlobConfig(
        val xPercent: Float,
        val yPercent: Float,
        val radiusPercent: Float,
        val alphaCenter: Float,
        val alphaEdge: Float
    )

    val CENTER_BLOB = BlobConfig(
        xPercent = 0.5f,
        yPercent = 0.35f,
        radiusPercent = 1.1f,
        alphaCenter = 0.40f,
        alphaEdge = 0.0f
    )

    @Volatile
    private var cachedNoiseBitmap: Bitmap? = null

    suspend fun generateNoiseBitmapAsync(
        width: Int = 64,
        height: Int = 64,
        density: Float = 0.03f
    ): Bitmap {
        cachedNoiseBitmap?.let { return it }

        return withContext(Dispatchers.Default) {
            synchronized(this@HomeBackgroundEffects) {
                cachedNoiseBitmap?.let { return@withContext it }

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val pixels = IntArray(width * height)
                val threshold = (density * 1000).toInt()

                for (i in pixels.indices) {
                    if (Random.nextInt(1000) < threshold) {
                        val alpha = Random.nextInt(1, 129)
                        pixels[i] = (alpha shl 24) or 0x00FFFFFF
                    } else {
                        pixels[i] = 0x00000000
                    }
                }

                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
                cachedNoiseBitmap = bitmap
                bitmap
            }
        }
    }

    fun clearNoiseCache() {
        cachedNoiseBitmap = null
    }
}