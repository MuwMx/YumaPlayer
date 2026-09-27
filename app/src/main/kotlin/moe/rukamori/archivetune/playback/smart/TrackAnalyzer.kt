package moe.rukamori.archivetune.playback.smart

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import androidx.media3.datasource.cache.Cache
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

object TrackAnalyzer {
    private val analyzerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val memoryCache = ConcurrentHashMap<String, TrackAnalysisResult>()
    private val inFlight = ConcurrentHashMap<String, Deferred<TrackAnalysisResult?>>()

    fun getCached(trackId: String): TrackAnalysisResult? = memoryCache[trackId]

    fun hasCached(trackId: String): Boolean = memoryCache.containsKey(trackId)

    fun putCached(trackId: String, result: TrackAnalysisResult) {
        memoryCache[trackId] = result
    }

    fun clearCache() {
        memoryCache.clear()
    }

    suspend fun analyze(
        trackId: String,
        filePath: String,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = Long.MAX_VALUE,
    ): TrackAnalysisResult? = analyze(trackId, durationSeconds) {
        AudioDecoder.decode(filePath, startMs, endMs)
    }

    suspend fun analyze(
        trackId: String,
        file: File,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = Long.MAX_VALUE,
    ): TrackAnalysisResult? = analyze(trackId, durationSeconds) {
        AudioDecoder.decode(file, startMs, endMs)
    }

    suspend fun analyze(
        trackId: String,
        context: Context,
        uri: Uri,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = Long.MAX_VALUE,
    ): TrackAnalysisResult? = analyze(trackId, durationSeconds) {
        AudioDecoder.decode(context, uri, startMs, endMs)
    }

    suspend fun analyze(
        trackId: String,
        cache: Cache,
        cacheKey: String,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = Long.MAX_VALUE,
    ): TrackAnalysisResult? = analyze(trackId, durationSeconds) {
        AudioDecoder.decode(cache, cacheKey, startMs, endMs)
    }

    suspend fun analyze(
        trackId: String,
        mediaDataSource: MediaDataSource,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = Long.MAX_VALUE,
    ): TrackAnalysisResult? = analyze(trackId, durationSeconds) {
        AudioDecoder.decode(mediaDataSource, startMs, endMs)
    }

    suspend fun analyze(
        trackId: String,
        durationSeconds: Double? = null,
        decodeSamples: () -> FloatArray?,
    ): TrackAnalysisResult? = withContext(Dispatchers.Default) {
        memoryCache[trackId]?.let { return@withContext it }

        val activeDeferred = inFlight[trackId]
        if (activeDeferred != null) {
            return@withContext activeDeferred.await()
        }

        val deferred = analyzerScope.async(Dispatchers.Default) {
            try {
                val samples = decodeSamples() ?: return@async null
                if (samples.isEmpty()) return@async null

                val targetSampleRate = runCatching { TrackFeatures.sampleRate() }
                    .getOrDefault(AudioDecoder.TARGET_SAMPLE_RATE)
                val duration = if (durationSeconds != null && durationSeconds > 0.0) {
                    durationSeconds
                } else if (targetSampleRate > 0.0) {
                    samples.size / targetSampleRate
                } else {
                    0.0
                }

                val result = TrackFeatures.analyze(samples, duration) ?: return@async null
                memoryCache[trackId] = result
                result
            } finally {
                inFlight.remove(trackId)
            }
        }

        inFlight[trackId] = deferred
        deferred.await()
    }

    fun analyzeAsync(
        trackId: String,
        durationSeconds: Double? = null,
        decodeSamples: () -> FloatArray?,
    ): Deferred<TrackAnalysisResult?> = analyzerScope.async(Dispatchers.Default) {
        analyze(trackId, durationSeconds, decodeSamples)
    }
}
