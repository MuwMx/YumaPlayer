/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.serialization.Serializable
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.Locale

internal const val CanvasMaxCachedVideoDimensionPx = 4_096
internal const val CanvasCacheLogTag = "CanvasCache"

internal enum class CanvasVideoVariant(
    val cacheKey: String,
) {
    Regular(cacheKey = "regular"),
    Vertical(cacheKey = "vertical"),
}

@Serializable
internal data class CanvasCacheEntry(
    val mediaId: String,
    val artwork: CanvasArtwork,
    val regularFileName: String? = null,
    val verticalFileName: String? = null,
    val createdAtMs: Long,
    val lastAccessedAtMs: Long,
) {
    fun byteSize(directory: File): Long =
        listOfNotNull(regularFileName, verticalFileName)
            .sumOf { fileName ->
                directory
                    .resolve(fileName)
                    .takeIf { file -> file.isUsableFile() }
                    ?.length()
                    ?: 0L
            }

    fun toPlayableArtwork(
        directory: File,
        preferCachedOnly: Boolean,
    ): CanvasArtwork? {
        val regularUri =
            regularFileName
                ?.let(directory::resolve)
                ?.takeIf { file -> file.isUsableFile() && file.isPlayableCanvasVideo() }
                ?.let { file -> Uri.fromFile(file).toString() }
        val verticalUri =
            verticalFileName
                ?.let(directory::resolve)
                ?.takeIf { file -> file.isUsableFile() && file.isPlayableCanvasVideo() }
                ?.let { file -> Uri.fromFile(file).toString() }
        if (regularUri == null && verticalUri == null) return null
        return artwork.copy(
            animated = if (preferCachedOnly) regularUri else regularUri ?: artwork.animated.takeIfNotBlank(),
            videoUrl = if (preferCachedOnly) regularUri else regularUri ?: artwork.videoUrl.takeIfNotBlank(),
            animatedVertical =
                if (preferCachedOnly) {
                    verticalUri
                } else {
                    verticalUri ?: artwork.animatedVertical.takeIfNotBlank()
                },
            videoUrlVertical =
                if (preferCachedOnly) {
                    verticalUri
                } else {
                    verticalUri ?: artwork.videoUrlVertical.takeIfNotBlank()
                },
        )
    }
}

internal fun CanvasArtwork.downloadableRegularUrl(): String? =
    videoUrl.takeIfDownloadableVideo() ?: animated.takeIfDownloadableVideo()

internal fun CanvasArtwork.downloadableVerticalUrl(): String? =
    videoUrlVertical.takeIfDownloadableVideo() ?: animatedVertical.takeIfDownloadableVideo()

internal fun String?.takeIfDownloadableVideo(): String? =
    this
        ?.trim()
        ?.takeIf { value ->
            val normalized = value.lowercase(Locale.ROOT)
            value.isNotBlank() &&
                !normalized.contains(".m3u8") &&
                !normalized.contains("application/x-mpegurl") &&
                (normalized.startsWith("http://") || normalized.startsWith("https://"))
        }

internal fun String?.takeIfNotBlank(): String? = this?.takeIf { it.isNotBlank() }

internal fun canvasFileName(
    mediaId: String,
    variant: CanvasVideoVariant,
    url: String,
): String {
    val digest =
        MessageDigest
            .getInstance("SHA-256")
            .digest("$mediaId|${variant.cacheKey}|$url".toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    return "${variant.cacheKey}-$digest.mp4"
}

internal fun Int.toCanvasCacheLimitBytes(): Long =
    when {
        this < 0 -> {
            Long.MAX_VALUE
        }

        this == 0 -> {
            0L
        }

        else -> {
            toLong()
                .coerceAtMost(Long.MAX_VALUE / 1_024L / 1_024L)
                .coerceAtLeast(0L) * 1_024L * 1_024L
        }
    }

internal fun File.isUsableFile(): Boolean = isFile && length() > 0L

internal fun File.isPlayableCanvasVideo(): Boolean {
    val fileName = name
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(absolutePath)
        (0 until extractor.trackCount).any { index ->
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            mime.startsWith("video/") && format.isSupportedCanvasVideoFormat(fileName)
        }
    } catch (error: Throwable) {
        Timber.tag(CanvasCacheLogTag).w(error, "Failed to inspect cached canvas video: file=%s", fileName)
        false
    } finally {
        extractor.release()
    }
}

private fun MediaFormat.isSupportedCanvasVideoFormat(fileName: String): Boolean {
    val mime = getString(MediaFormat.KEY_MIME)?.takeIf { value -> value.startsWith("video/") }
    if (mime == null) {
        Timber.tag(CanvasCacheLogTag).d("Validation rejected: file=%s reason=missing_or_non_video_mime", fileName)
        return false
    }
    val width = optionalInteger(MediaFormat.KEY_WIDTH)
    val height = optionalInteger(MediaFormat.KEY_HEIGHT)
    if (width == null || height == null) {
        Timber.tag(CanvasCacheLogTag).d(
            "Validation rejected: file=%s reason=missing_dimensions mime=%s width=%s height=%s",
            fileName,
            mime,
            width,
            height,
        )
        return false
    }
    val frameRate = optionalFrameRate()
    val profile = optionalInteger(MediaFormat.KEY_PROFILE)?.takeIf { value -> value > 0 }

    Timber.tag(CanvasCacheLogTag).d(
        "Extracted video metadata: file=%s mime=%s width=%d height=%d fps=%s profile=%s",
        fileName,
        mime,
        width,
        height,
        frameRate,
        profile,
    )

    if (width > CanvasMaxCachedVideoDimensionPx ||
        height > CanvasMaxCachedVideoDimensionPx
    ) {
        Timber.tag(CanvasCacheLogTag).d(
            "Validation rejected: file=%s reason=dimension_limit_exceeded width=%d height=%d maxDim=%d",
            fileName,
            width,
            height,
            CanvasMaxCachedVideoDimensionPx,
        )
        return false
    }

    return runCatching {
        MediaCodecList(MediaCodecList.ALL_CODECS)
            .codecInfos
            .any { codecInfo ->
                if (codecInfo.isEncoder) return@any false
                val supportedType =
                    codecInfo.supportedTypes.firstOrNull { type -> type.equals(mime, ignoreCase = true) }
                        ?: return@any false
                val capabilities = codecInfo.getCapabilitiesForType(supportedType)
                val profileSupported =
                    profile == null ||
                        capabilities.profileLevels.isEmpty() ||
                        capabilities.profileLevels.any { profileLevel -> profileLevel.profile == profile }
                Timber.tag(CanvasCacheLogTag).d(
                    "Candidate decoder: file=%s decoder=%s mime=%s profile=%s profileMatch=%b",
                    fileName,
                    codecInfo.name,
                    supportedType,
                    profile,
                    profileSupported,
                )
                if (!profileSupported) return@any false

                val videoCapabilities = capabilities.videoCapabilities ?: return@any true
                videoCapabilities.supportsCanvasSize(
                    fileName = fileName,
                    decoderName = codecInfo.name,
                    orientation = "direct",
                    width = width,
                    height = height,
                    frameRate = frameRate,
                ) ||
                    videoCapabilities.supportsCanvasSize(
                        fileName = fileName,
                        decoderName = codecInfo.name,
                        orientation = "flipped",
                        width = height,
                        height = width,
                        frameRate = frameRate,
                    )
            }
    }.onFailure { error ->
        Timber.tag(CanvasCacheLogTag).w(error, "Codec validation failed: file=%s", fileName)
    }.getOrDefault(false)
}

private fun MediaCodecInfo.VideoCapabilities.supportsCanvasSize(
    fileName: String,
    decoderName: String,
    orientation: String,
    width: Int,
    height: Int,
    frameRate: Double?,
): Boolean =
    runCatching {
        val sizeSupported = runCatching { isSizeSupported(width, height) }.getOrDefault(false)
        val rateSupported = if (frameRate != null && frameRate > 0.0) {
            runCatching { areSizeAndRateSupported(width, height, frameRate) }.getOrDefault(false)
        } else {
            false
        }

        if (rateSupported || sizeSupported) {
            return@runCatching true
        }

        val maxDim = maxOf(supportedWidths.upper, supportedHeights.upper)
        val totalPixels = width.toLong() * height.toLong()
        val max4kPixels = 3840L * 2160L

        val fitsInHardwareLimits = width <= maxDim && height <= maxDim && totalPixels <= max4kPixels

        Timber.tag(CanvasCacheLogTag).d(
            "Decoder capability fallback: file=%s decoder=%s width=%d height=%d maxDim=%d fits=%b",
            fileName,
            decoderName,
            width,
            height,
            maxDim,
            fitsInHardwareLimits,
        )

        fitsInHardwareLimits
    }.onFailure { error ->
        Timber.tag(CanvasCacheLogTag).w(
            error,
            "Decoder size check failed: file=%s decoder=%s orientation=%s width=%d height=%d",
            fileName,
            decoderName,
            orientation,
            width,
            height,
        )
    }.getOrDefault(false)

private fun MediaFormat.optionalInteger(key: String): Int? =
    if (containsKey(key)) {
        runCatching { getInteger(key) }
            .onFailure { error -> Timber.tag(CanvasCacheLogTag).w(error, "Failed to read integer key: %s", key) }
            .getOrNull()
    } else {
        null
    }

private fun MediaFormat.optionalFrameRate(): Double? =
    if (containsKey(MediaFormat.KEY_FRAME_RATE)) {
        runCatching { getInteger(MediaFormat.KEY_FRAME_RATE).toDouble() }
            .getOrElse {
                runCatching { getFloat(MediaFormat.KEY_FRAME_RATE).toDouble() }
                    .onFailure { error -> Timber.tag(CanvasCacheLogTag).w(error, "Failed to read frame rate") }
                    .getOrNull()
            }
    } else {
        null
    }
