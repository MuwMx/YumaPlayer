/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.home.account

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.utils.ColorExtractor
import timber.log.Timber

internal class HomeAccountAccentColorObserver(
    private val context: Context,
    private val accountImageUrl: StateFlow<String?>,
) {
    private val _accountAccentColor = MutableStateFlow<Color?>(null)
    val accountAccentColor: StateFlow<Color?> = _accountAccentColor.asStateFlow()

    private val accentColorLock = Any()
    private var activeAccentColorJob: Job? = null
    private var activeAccentColorOwner: Any? = null

    fun clear() {
        synchronized(accentColorLock) {
            _accountAccentColor.value = null
        }
    }

    fun observe(scope: CoroutineScope) {
        val callerJob = scope.coroutineContext[Job]
        if (callerJob != null && !callerJob.isActive) {
            return
        }

        synchronized(accentColorLock) {
            activeAccentColorJob?.cancel()
            _accountAccentColor.value = null

            val ownerKey: Any = callerJob ?: scope
            activeAccentColorOwner = ownerKey

            val launchedJob =
                scope.launch(Dispatchers.IO) {
                    val imageLoader by lazy { ImageLoader(context) }
                    accountImageUrl.collectLatest { url ->
                        if (url.isNullOrBlank()) {
                            _accountAccentColor.value = null
                            return@collectLatest
                        }

                        try {
                            val request =
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .size(96, 96)
                                    .allowHardware(false)
                                    .build()

                            val result = imageLoader.execute(request)
                            val bitmap = result.image?.toBitmap()

                            if (bitmap != null) {
                                val pixels = IntArray(bitmap.width * bitmap.height)
                                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                                val hex = ColorExtractor.extractVibrantHex(pixels)
                                val extractedColor =
                                    hex?.let {
                                        try {
                                            Color(it.toColorInt())
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }
                                _accountAccentColor.value = extractedColor
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.tag("HomeAccountCoordinator").w(e, "Failed to extract avatar color")
                        }
                    }
                }
            activeAccentColorJob = launchedJob

            callerJob?.invokeOnCompletion {
                synchronized(accentColorLock) {
                    if (activeAccentColorOwner === ownerKey && activeAccentColorJob === launchedJob) {
                        activeAccentColorJob?.cancel()
                        activeAccentColorJob = null
                        activeAccentColorOwner = null
                        _accountAccentColor.value = null
                    }
                }
            }
        }
    }
}
