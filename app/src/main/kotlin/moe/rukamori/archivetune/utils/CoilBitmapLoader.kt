/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.media3.common.util.BitmapLoader
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import moe.rukamori.archivetune.utils.reportException

class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope,
) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            try {
                if (data.isEmpty()) {
                    throw IllegalArgumentException("Empty image data")
                }

                BitmapFactory.decodeByteArray(data, 0, data.size)?.also { bitmap ->
                    return@future bitmap
                }

                throw IllegalStateException("Could not decode image data")
            } catch (e: Exception) {
                reportException(e)
                return@future createBitmap(64, 64)
            }
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            try {
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(uri)
                        .allowHardware(false)
                        .size(1080, 1080)
                        .memoryCacheKey("notification:$uri")
                        .build()

                val result = context.imageLoader.execute(request)

                when (result) {
                    is SuccessResult -> {
                        try {
                            return@future result.image.toBitmap().copy(Bitmap.Config.ARGB_8888, false)
                        } catch (e: Exception) {
                            reportException(e)
                        }
                    }

                    is ErrorResult -> {
                        reportException(result.throwable)
                    }
                }
            } catch (e: Exception) {
                reportException(e)
            }

            createBitmap(1, 1)
        }
}
