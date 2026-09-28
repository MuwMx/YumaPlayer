package moe.rukamori.archivetune.playback.smart

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteOrder
import timber.log.Timber

object AudioDecoder {
    private const val TIMEOUT_US = 10_000L
    private const val MAX_CONSECUTIVE_TIMEOUTS = 50
    const val TARGET_SAMPLE_RATE: Double = 11025.0
    const val DEFAULT_HEAD_DECODE_MS: Long = 30_000L
    const val MAX_HEAD_DECODE_MS: Long = 45_000L
    const val TAIL_WINDOW_MS: Long = 45_000L
    const val DECODE_WATCHDOG_MS: Long = 45_000L

    fun decode(
        filePath: String,
        startMs: Long = 0L,
        endMs: Long = DEFAULT_HEAD_DECODE_MS,
    ): FloatArray? {
        if (filePath.startsWith("http://", ignoreCase = true) || filePath.startsWith("https://", ignoreCase = true)) {
            return null
        }
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return null
        return decodeInternal(
            setDataSource = { it.setDataSource(file.absolutePath) },
            startMs = startMs,
            endMs = endMs,
        )
    }

    fun decode(
        file: File,
        startMs: Long = 0L,
        endMs: Long = DEFAULT_HEAD_DECODE_MS,
    ): FloatArray? {
        if (!file.exists() || !file.canRead()) return null
        return decodeInternal(
            setDataSource = { it.setDataSource(file.absolutePath) },
            startMs = startMs,
            endMs = endMs,
        )
    }

    fun decode(
        context: Context,
        uri: Uri,
        startMs: Long = 0L,
        endMs: Long = DEFAULT_HEAD_DECODE_MS,
    ): FloatArray? {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") return null
        if (scheme != "file" && scheme != "content" && scheme != "android.resource" && scheme != null) {
            return null
        }
        if (scheme == "file" || scheme == null) {
            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    return decode(file, startMs, endMs)
                }
            }
        }
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                decode(pfd.fileDescriptor, 0L, Long.MAX_VALUE, startMs, endMs)
            }
        }.getOrNull()
    }

    fun decode(
        fd: FileDescriptor,
        offset: Long = 0L,
        length: Long = Long.MAX_VALUE,
        startMs: Long = 0L,
        endMs: Long = DEFAULT_HEAD_DECODE_MS,
    ): FloatArray? = decodeInternal(
        setDataSource = { it.setDataSource(fd, offset, length) },
        startMs = startMs,
        endMs = endMs,
    )

    fun decode(
        mediaDataSource: MediaDataSource,
        startMs: Long = 0L,
        endMs: Long = DEFAULT_HEAD_DECODE_MS,
    ): FloatArray? = decodeInternal(
        setDataSource = { it.setDataSource(mediaDataSource) },
        startMs = startMs,
        endMs = endMs,
    )

    fun decode(
        cache: Cache,
        cacheKey: String,
        startMs: Long = 0L,
        endMs: Long = DEFAULT_HEAD_DECODE_MS,
    ): FloatArray? {
        val length = runCatching {
            cache.getContentMetadata(cacheKey).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
        }.getOrDefault(-1L)
        if (length <= 0L || !cache.isCached(cacheKey, 0L, length)) {
            return null
        }
        val spans = runCatching { cache.getCachedSpans(cacheKey) }.getOrNull().orEmpty()
        if (spans.isEmpty()) return null
        if (spans.size == 1) {
            val singleFile = spans.first().file
            if (singleFile != null && singleFile.exists() && singleFile.canRead()) {
                return decode(singleFile, startMs, endMs)
            }
        }
        return decode(CacheMediaDataSource(cache, cacheKey), startMs, endMs)
    }

    fun decodeTail(
        filePath: String,
        durationMs: Long,
    ): FloatArray? {
        if (durationMs <= TAIL_WINDOW_MS) return null
        return decode(filePath, startMs = durationMs - TAIL_WINDOW_MS, endMs = durationMs)
    }

    fun decodeTail(
        file: File,
        durationMs: Long,
    ): FloatArray? {
        if (durationMs <= TAIL_WINDOW_MS) return null
        return decode(file, startMs = durationMs - TAIL_WINDOW_MS, endMs = durationMs)
    }

    fun decodeTail(
        context: Context,
        uri: Uri,
        durationMs: Long,
    ): FloatArray? {
        if (durationMs <= TAIL_WINDOW_MS) return null
        return decode(context, uri, startMs = durationMs - TAIL_WINDOW_MS, endMs = durationMs)
    }

    fun decodeTail(
        fd: FileDescriptor,
        offset: Long = 0L,
        length: Long = Long.MAX_VALUE,
        durationMs: Long,
    ): FloatArray? {
        if (durationMs <= TAIL_WINDOW_MS) return null
        return decode(fd, offset, length, startMs = durationMs - TAIL_WINDOW_MS, endMs = durationMs)
    }

    fun decodeTail(
        mediaDataSource: MediaDataSource,
        durationMs: Long,
    ): FloatArray? {
        if (durationMs <= TAIL_WINDOW_MS) return null
        return decode(mediaDataSource, startMs = durationMs - TAIL_WINDOW_MS, endMs = durationMs)
    }

    fun decodeTail(
        cache: Cache,
        cacheKey: String,
        durationMs: Long,
    ): FloatArray? {
        if (durationMs <= TAIL_WINDOW_MS) return null
        return decode(cache, cacheKey, startMs = durationMs - TAIL_WINDOW_MS, endMs = durationMs)
    }

    private fun decodeInternal(
        setDataSource: (MediaExtractor) -> Unit,
        startMs: Long,
        endMs: Long,
    ): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        return try {
            setDataSource(extractor)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return null
            }

            extractor.selectTrack(audioTrackIndex)

            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val safeStartMs = startMs.coerceAtLeast(0L)
            val requestedEndMs = if (endMs <= safeStartMs || endMs == Long.MAX_VALUE) {
                safeStartMs + DEFAULT_HEAD_DECODE_MS
            } else {
                endMs
            }
            val boundedEndMs = requestedEndMs.coerceAtMost(safeStartMs + MAX_HEAD_DECODE_MS)
            val startUs = safeStartMs * 1000L
            val endUs = boundedEndMs * 1000L
            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            var sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }
            if (sampleRate <= 0) sampleRate = 44100

            var channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }
            if (channelCount <= 0) channelCount = 1

            var pcmEncoding = if (audioFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                audioFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            var consecutiveTimeouts = 0
            val collector = FloatChunkList()
            val decodeStartMs = android.os.SystemClock.elapsedRealtime()
            var wallClockAborted = false

            while (!sawOutputEOS) {
                if (android.os.SystemClock.elapsedRealtime() - decodeStartMs > DECODE_WATCHDOG_MS) {
                    Timber.tag("AudioDecoder").w("Decode wall-clock guard tripped after ${DECODE_WATCHDOG_MS}ms; stopping decode loop")
                    wallClockAborted = true
                    break
                }
                if (!sawInputEOS) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer == null) {
                            sawInputEOS = true
                        } else {
                            inputBuffer.clear()
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                sawInputEOS = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                val isPastEnd = sampleTime > endUs
                                val flags = if (isPastEnd) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, flags)
                                val advanced = runCatching { extractor.advance() }.getOrDefault(false)
                                if (isPastEnd || !advanced) {
                                    sawInputEOS = true
                                }
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputIndex >= 0 -> {
                        consecutiveTimeouts = 0
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true
                        }
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                            val stopped = extractMonoSamples(
                                buffer = outputBuffer,
                                bufferSize = bufferInfo.size,
                                presentationTimeUs = bufferInfo.presentationTimeUs,
                                channelCount = channelCount,
                                sampleRate = sampleRate,
                                pcmEncoding = pcmEncoding,
                                startUs = startUs,
                                endUs = endUs,
                                collector = collector,
                            )
                            if (stopped) {
                                sawOutputEOS = true
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                    }

                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        consecutiveTimeouts = 0
                        val newFormat = codec.outputFormat
                        if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            if (sampleRate <= 0) sampleRate = 44100
                        }
                        if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            if (channelCount <= 0) channelCount = 1
                        }
                        if (newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }

                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        consecutiveTimeouts++
                        if (sawInputEOS && consecutiveTimeouts > MAX_CONSECUTIVE_TIMEOUTS) {
                            break
                        }
                    }
                }
            }

            val monoSamples = collector.toFloatArray()
            if (wallClockAborted) {
                Timber.tag("AudioDecoder").w("Decode stopped by watchdog, keeping ${monoSamples.size} decoded samples")
            }
            if (monoSamples.isEmpty()) return null

            val targetRate = runCatching { TrackFeatures.sampleRate() }.getOrDefault(TARGET_SAMPLE_RATE)
            if (sampleRate.toDouble() == targetRate) {
                monoSamples
            } else {
                val resampled = runCatching {
                    TrackFeatures.resample(monoSamples, sampleRate.toDouble(), targetRate)
                }.getOrNull()
                resampled ?: monoSamples
            }
        } catch (e: Exception) {
            Timber.tag("AudioDecoder").e(e, "Decode failed")
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun extractMonoSamples(
        buffer: java.nio.ByteBuffer,
        bufferSize: Int,
        presentationTimeUs: Long,
        channelCount: Int,
        sampleRate: Int,
        pcmEncoding: Int,
        startUs: Long,
        endUs: Long,
        collector: FloatChunkList,
    ): Boolean {
        val validChannels = if (channelCount > 0) channelCount else 1
        val bytesPerSample = when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_8BIT -> 1
            else -> 2
        }
        val bytesPerFrame = validChannels * bytesPerSample
        val frameCount = bufferSize / bytesPerFrame
        if (frameCount <= 0) return false

        val frameDurationUs = 1_000_000.0 / sampleRate
        if (presentationTimeUs >= 0L) {
            val bufferEndUs = presentationTimeUs + (frameCount * frameDurationUs).toLong()
            if (bufferEndUs < startUs) {
                return false
            }
            if (presentationTimeUs > endUs) {
                return true
            }
        }

        for (i in 0 until frameCount) {
            if (presentationTimeUs >= 0L) {
                val frameTimeUs = presentationTimeUs + (i * frameDurationUs).toLong()
                if (frameTimeUs < startUs) {
                    buffer.position(buffer.position() + bytesPerFrame)
                    continue
                }
                if (frameTimeUs > endUs) {
                    return true
                }
            }

            var sum = 0f
            when (pcmEncoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> {
                    for (ch in 0 until validChannels) {
                        sum += buffer.float
                    }
                }
                AudioFormat.ENCODING_PCM_8BIT -> {
                    for (ch in 0 until validChannels) {
                        val b = buffer.get().toInt() and 0xFF
                        sum += (b - 128) / 128.0f
                    }
                }
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                    for (ch in 0 until validChannels) {
                        val b0 = buffer.get().toInt() and 0xFF
                        val b1 = buffer.get().toInt() and 0xFF
                        val b2 = buffer.get().toInt()
                        val sample = (b2 shl 16) or (b1 shl 8) or b0
                        sum += sample / 8388608.0f
                    }
                }
                AudioFormat.ENCODING_PCM_32BIT -> {
                    for (ch in 0 until validChannels) {
                        sum += (buffer.int.toDouble() / 2147483648.0).toFloat()
                    }
                }
                else -> {
                    for (ch in 0 until validChannels) {
                        sum += buffer.short / 32768.0f
                    }
                }
            }
            val mono = (sum / validChannels).coerceIn(-1.0f, 1.0f)
            collector.add(mono)
        }
        return false
    }
}
