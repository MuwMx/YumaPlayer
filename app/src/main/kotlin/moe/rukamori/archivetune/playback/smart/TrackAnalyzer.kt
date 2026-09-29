package moe.rukamori.archivetune.playback.smart

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.App
import moe.rukamori.archivetune.audiodsp.AnalysisStore
import moe.rukamori.archivetune.audiodsp.TrackAnalysisResult
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.TrackAnalysisEntity
import org.json.JSONObject
import timber.log.Timber

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface TrackAnalyzerEntryPoint {
    fun database(): MusicDatabase
}

object TrackAnalyzer : AnalysisStore {
    private const val KICK_OFF_COOLDOWN_MS = 10_000L
    private val analyzerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val memoryCache = ConcurrentHashMap<String, TrackAnalysisResult>()
    private val inFlight = ConcurrentHashMap<String, Deferred<TrackAnalysisResult?>>()
    private val lastKickTimestamps = ConcurrentHashMap<String, Long>()
    private val analysisSemaphore = Semaphore(2)
    private val _analysisEvents = MutableSharedFlow<Pair<String, TrackAnalysisResult>>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val analysisEvents: SharedFlow<Pair<String, TrackAnalysisResult>> = _analysisEvents.asSharedFlow()

    fun shouldThrottleKickOff(trackId: String): Boolean {
        if (trackId.isBlank()) return true
        val now = android.os.SystemClock.elapsedRealtime()
        val last = lastKickTimestamps[trackId]
        if (last != null && now - last < KICK_OFF_COOLDOWN_MS) {
            return true
        }
        lastKickTimestamps[trackId] = now
        return false
    }

    @Volatile
    var database: MusicDatabase? = null
        get() = field ?: resolveDatabase()

    private fun resolveDatabase(): MusicDatabase? {
        return runCatching {
            val app = App.instance
            EntryPointAccessors.fromApplication(
                app,
                TrackAnalyzerEntryPoint::class.java,
            ).database()
        }.getOrNull()?.also { database = it }
    }

    override fun getCached(trackId: String): TrackAnalysisResult? = memoryCache[trackId]

    fun hasCached(trackId: String): Boolean = memoryCache.containsKey(trackId)

    suspend fun getOrFetchCached(trackId: String): TrackAnalysisResult? {
        if (trackId.isBlank()) return null
        memoryCache[trackId]?.let { return it }

        val cachedDb = runCatching {
            withContext(Dispatchers.IO) {
                database?.trackAnalysisDao()?.get(trackId)
            }
        }.getOrNull()

        if (cachedDb != null) {
            val result = toResult(cachedDb)
            if (result.contentEndTime in 1.0..35.0 && result.mixOutTime in 1.0..35.0) {
                Timber.tag("TrackAnalyzer").w("Ignoring corrupted short analysis in Room for trackId=$trackId contentEnd=${result.contentEndTime}")
            } else {
                Timber.tag("TrackAnalyzer").d("Room hit trackId=$trackId bpm=${result.bpm} mixOut=${result.mixOutTime}")
                memoryCache[trackId] = result
                _analysisEvents.tryEmit(trackId to result)
                return result
            }
        }
        return null
    }

    fun putCached(trackId: String, result: TrackAnalysisResult) {
        memoryCache[trackId] = result
        _analysisEvents.tryEmit(trackId to result)
    }

    fun clearCache() {
        memoryCache.clear()
    }

    suspend fun analyze(
        trackId: String,
        filePath: String,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = AudioDecoder.DEFAULT_HEAD_DECODE_MS,
    ): TrackAnalysisResult? {
        if (filePath.startsWith("http://", ignoreCase = true) || filePath.startsWith("https://", ignoreCase = true)) {
            Timber.tag("TrackAnalyzer").w("Rejecting remote filePath=$filePath for trackId=$trackId")
            return null
        }
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return null
        val durationMs = if (durationSeconds != null && durationSeconds > 0.0) (durationSeconds * 1000.0).roundToLong() else null
        return analyzeWithSource(
            trackId = trackId,
            durationSeconds = durationSeconds,
            source = "filePath",
            decodeHeadSamples = { AudioDecoder.decode(file, startMs, endMs) },
            decodeTailSamples = if (durationMs != null && durationMs > AudioDecoder.TAIL_WINDOW_MS) {
                { AudioDecoder.decodeTail(file, durationMs) }
            } else null,
            decodeFullSamples = if (AudioDecoder.canDoFullPass(durationMs)) {
                { AudioDecoder.decodeFull(file, durationMs!!) }
            } else null,
        )
    }

    suspend fun analyze(
        trackId: String,
        file: File,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = AudioDecoder.DEFAULT_HEAD_DECODE_MS,
    ): TrackAnalysisResult? {
        if (!file.exists() || !file.canRead()) return null
        val durationMs = if (durationSeconds != null && durationSeconds > 0.0) (durationSeconds * 1000.0).roundToLong() else null
        return analyzeWithSource(
            trackId = trackId,
            durationSeconds = durationSeconds,
            source = "file",
            decodeHeadSamples = { AudioDecoder.decode(file, startMs, endMs) },
            decodeTailSamples = if (durationMs != null && durationMs > AudioDecoder.TAIL_WINDOW_MS) {
                { AudioDecoder.decodeTail(file, durationMs) }
            } else null,
            decodeFullSamples = if (AudioDecoder.canDoFullPass(durationMs)) {
                { AudioDecoder.decodeFull(file, durationMs!!) }
            } else null,
        )
    }

    suspend fun analyze(
        trackId: String,
        context: Context,
        uri: Uri,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = AudioDecoder.DEFAULT_HEAD_DECODE_MS,
    ): TrackAnalysisResult? {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            Timber.tag("TrackAnalyzer").w("Rejecting remote uri=$uri for trackId=$trackId")
            return null
        }
        if (scheme != "content" && scheme != "file" && scheme != "android.resource" && scheme != null) {
            Timber.tag("TrackAnalyzer").w("Rejecting unsupported uri scheme=$scheme for trackId=$trackId")
            return null
        }
        val durationMs = if (durationSeconds != null && durationSeconds > 0.0) (durationSeconds * 1000.0).roundToLong() else null
        return analyzeWithSource(
            trackId = trackId,
            durationSeconds = durationSeconds,
            source = "uri:$uri",
            decodeHeadSamples = { AudioDecoder.decode(context, uri, startMs, endMs) },
            decodeTailSamples = if (durationMs != null && durationMs > AudioDecoder.TAIL_WINDOW_MS) {
                { AudioDecoder.decodeTail(context, uri, durationMs) }
            } else null,
            decodeFullSamples = if (AudioDecoder.canDoFullPass(durationMs)) {
                { AudioDecoder.decodeFull(context, uri, durationMs!!) }
            } else null,
        )
    }

    suspend fun analyze(
        trackId: String,
        cache: Cache,
        cacheKey: String,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = AudioDecoder.DEFAULT_HEAD_DECODE_MS,
    ): TrackAnalysisResult? {
        val length = runCatching {
            cache.getContentMetadata(cacheKey).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
        }.getOrDefault(-1L)
        if (length <= 0L || !cache.isCached(cacheKey, 0L, length)) {
            Timber.tag("TrackAnalyzer").d("Waiting on cache for $trackId")
            return null
        }
        val durationMs = if (durationSeconds != null && durationSeconds > 0.0) (durationSeconds * 1000.0).roundToLong() else null
        return analyzeWithSource(
            trackId = trackId,
            durationSeconds = durationSeconds,
            source = "cache:$cacheKey",
            decodeHeadSamples = { AudioDecoder.decode(cache, cacheKey, startMs, endMs) },
            decodeTailSamples = if (durationMs != null && durationMs > AudioDecoder.TAIL_WINDOW_MS) {
                { AudioDecoder.decodeTail(cache, cacheKey, durationMs) }
            } else null,
            decodeFullSamples = if (AudioDecoder.canDoFullPass(durationMs)) {
                { AudioDecoder.decodeFull(cache, cacheKey, durationMs!!) }
            } else null,
        )
    }

    suspend fun analyze(
        trackId: String,
        mediaDataSource: MediaDataSource,
        durationSeconds: Double? = null,
        startMs: Long = 0L,
        endMs: Long = AudioDecoder.DEFAULT_HEAD_DECODE_MS,
    ): TrackAnalysisResult? {
        val durationMs = if (durationSeconds != null && durationSeconds > 0.0) (durationSeconds * 1000.0).roundToLong() else null
        return analyzeWithSource(
            trackId = trackId,
            durationSeconds = durationSeconds,
            source = "dataSource",
            decodeHeadSamples = { AudioDecoder.decode(mediaDataSource, startMs, endMs) },
            decodeTailSamples = if (durationMs != null && durationMs > AudioDecoder.TAIL_WINDOW_MS) {
                { AudioDecoder.decodeTail(mediaDataSource, durationMs) }
            } else null,
        )
    }

    suspend fun analyze(
        trackId: String,
        durationSeconds: Double? = null,
        decodeSamples: () -> FloatArray?,
    ): TrackAnalysisResult? = analyzeWithSource(
        trackId = trackId,
        durationSeconds = durationSeconds,
        source = "unknown",
        decodeHeadSamples = decodeSamples,
        decodeTailSamples = null,
    )

    suspend fun analyze(
        trackId: String,
        durationSeconds: Double? = null,
        decodeHeadSamples: () -> FloatArray?,
        decodeTailSamples: (() -> FloatArray?)?,
    ): TrackAnalysisResult? = analyzeWithSource(
        trackId = trackId,
        durationSeconds = durationSeconds,
        source = "unknown",
        decodeHeadSamples = decodeHeadSamples,
        decodeTailSamples = decodeTailSamples,
    )

    suspend fun analyzeWithSource(
        trackId: String,
        durationSeconds: Double? = null,
        source: String = "unknown",
        decodeSamples: () -> FloatArray?,
    ): TrackAnalysisResult? = analyzeWithSource(
        trackId = trackId,
        durationSeconds = durationSeconds,
        source = source,
        decodeHeadSamples = decodeSamples,
        decodeTailSamples = null,
    )

    suspend fun analyzeWithSource(
        trackId: String,
        durationSeconds: Double? = null,
        source: String = "unknown",
        decodeHeadSamples: () -> FloatArray?,
        decodeTailSamples: (() -> FloatArray?)? = null,
        decodeFullSamples: (() -> FloatArray?)? = null,
    ): TrackAnalysisResult? = withContext(Dispatchers.Default) {
        memoryCache[trackId]?.let { return@withContext it }

        var activeDeferred: Deferred<TrackAnalysisResult?>? = null
        val isNew = synchronized(inFlight) {
            memoryCache[trackId]?.let { return@withContext it }
            activeDeferred = inFlight[trackId]
            if (activeDeferred != null) {
                false
            } else {
                val newDeferred = analyzerScope.async(Dispatchers.Default) {
                    try {
                        val startedMs = System.currentTimeMillis()
                        val cachedDb = runCatching {
                            withContext(Dispatchers.IO) {
                                database?.trackAnalysisDao()?.get(trackId)
                            }
                        }.getOrNull()

                        if (cachedDb != null) {
                            val result = toResult(cachedDb)
                            Timber.tag("TrackAnalyzer").d("Room hit trackId=$trackId bpm=${result.bpm} mixOut=${result.mixOutTime}")
                            memoryCache[trackId] = result
                            _analysisEvents.tryEmit(trackId to result)
                            return@async result
                        }

                        if (durationSeconds != null && durationSeconds.isFinite() && durationSeconds > 0.0) {
                            Timber.tag("TrackAnalyzer").d("Analysing $trackId, ${durationSeconds}s, PLAYBACK")
                        } else {
                            Timber.tag("TrackAnalyzer").d("Analysing $trackId, PLAYBACK")
                        }

                        analysisSemaphore.withPermit {
                            memoryCache[trackId]?.let { return@withPermit it }

                            val headSamples = runCatching { decodeHeadSamples() }.getOrNull()
                            if (headSamples == null || headSamples.isEmpty()) {
                                Timber.tag("TrackAnalyzer").w("Decode yielded no samples trackId=$trackId source=$source")
                                return@withPermit null
                            }

                            val targetSampleRate = runCatching { TrackFeatures.sampleRate() }
                                .getOrDefault(AudioDecoder.TARGET_SAMPLE_RATE)
                            val duration = if (durationSeconds != null && durationSeconds > 0.0) {
                                durationSeconds
                            } else {
                                0.0
                            }

                            val headResult = runCatching { TrackFeatures.analyze(headSamples, duration) }.getOrNull()
                            if (headResult == null) {
                                Timber.tag("TrackAnalyzer").w("Native analyze returned null/crashed trackId=$trackId samples=${headSamples.size}")
                                return@withPermit null
                            }

                            val durationMs = if (durationSeconds != null && durationSeconds > 0.0) {
                                (durationSeconds * 1000.0).roundToLong()
                            } else {
                                null
                            }

                            val finalResult = if (decodeTailSamples != null && durationMs != null && durationMs > AudioDecoder.TAIL_WINDOW_MS) {
                                val tailSamples = runCatching { decodeTailSamples() }.getOrNull()
                                if (tailSamples != null && tailSamples.isNotEmpty()) {
                                    val tailDuration = if (targetSampleRate > 0.0) {
                                        tailSamples.size / targetSampleRate
                                    } else {
                                        AudioDecoder.TAIL_WINDOW_MS / 1000.0
                                    }
                                    val tailResult = runCatching { TrackFeatures.analyze(tailSamples, tailDuration) }.getOrNull()
                                    if (tailResult != null) {
                                        mergeHeadAndTail(headResult, tailResult, durationMs, durationSeconds)
                                    } else {
                                        Timber.tag("TrackAnalyzer").w("Tail native analyze returned null, falling back to head result trackId=$trackId")
                                        headResult
                                    }
                                } else {
                                    Timber.tag("TrackAnalyzer").d("Tail decode yielded no samples, falling back to head result trackId=$trackId")
                                    headResult
                                }
                            } else {
                                headResult
                            }

                            val cliffedResult = applyFullSpanCliff(
                                base = finalResult,
                                durationSeconds = durationSeconds,
                                durationMs = durationMs,
                                decodeFullSamples = decodeFullSamples,
                                trackId = trackId,
                            )

                            val isFullDurationKnown = durationSeconds != null && durationSeconds > 45.0

                            val sanitizedResult = if (!isFullDurationKnown) {
                                cliffedResult.copy(
                                    mixOutTime = 0.0,
                                    contentEndTime = 0.0 ,
                                    rawJson = ""
                                )
                            } else {
                                cliffedResult
                            }

                            Timber.tag("TrackAnalyzer").d(
                                "Analysed $trackId in ${System.currentTimeMillis() - startedMs}ms " +
                                        "bpm=${sanitizedResult.bpm} mixOut=${sanitizedResult.mixOutTime}",
                            )

                            if (isFullDurationKnown) {
                                memoryCache[trackId] = sanitizedResult
                                analyzerScope.launch(Dispatchers.IO) {
                                    runCatching {
                                        database?.trackAnalysisDao()?.upsert(fromResult(trackId, sanitizedResult))
                                    }
                                }
                            } else {
                                Timber.tag("TrackAnalyzer").w("Not saving cues to Room or permanent cache for $trackId: duration is unknown or too short ($durationSeconds)")
                            }

                            _analysisEvents.tryEmit(trackId to sanitizedResult)
                            logCues(trackId, sanitizedResult)

                            sanitizedResult
                        }
                    } catch (e: Exception) {
                        Timber.tag("TrackAnalyzer").e(e, "Analysis crashed trackId=$trackId source=$source")
                        null
                    } finally {
                        synchronized(inFlight) {
                            inFlight.remove(trackId)
                        }
                    }
                }
                inFlight[trackId] = newDeferred
                activeDeferred = newDeferred
                true
            }
        }

        if (!isNew) {
            Timber.tag("TrackAnalyzer").d("Joining in-flight analysis trackId=$trackId source=$source")
        }

        activeDeferred?.await()
    }

    private fun applyFullSpanCliff(
        base: TrackAnalysisResult,
        durationSeconds: Double?,
        durationMs: Long?,
        decodeFullSamples: (() -> FloatArray?)?,
        trackId: String,
    ): TrackAnalysisResult {
        if (decodeFullSamples == null) return base
        if (!AudioDecoder.canDoFullPass(durationMs)) return base
        if (durationSeconds == null || durationSeconds <= 0.0) return base
        val contentEnd = if (base.contentEndTime > 0.0) base.contentEndTime else durationSeconds
        if (contentEnd <= 0.0) return base
        var fullSamples = runCatching { decodeFullSamples() }.getOrNull()
        if (fullSamples == null || fullSamples.isEmpty()) return base
        try {
            val cliff = runCatching { TrackFeatures.energyCliff(fullSamples, durationSeconds) }.getOrDefault(0.0)
            if (!cliff.isFinite() || cliff <= 0.0 || cliff >= contentEnd - 8.0) return base
            val mergedRawJson = runCatching {
                if (base.rawJson.isBlank()) return@runCatching base.rawJson
                val json = JSONObject(base.rawJson)
                json.put("mixOutTime", cliff)
                val candidates = json.optJSONArray("mixOutCandidates")
                val cliffObj = JSONObject().put("t", cliff).put("s", 0.95).put("y", "energy_cliff")
                if (candidates != null) {
                    var replaced = false
                    for (i in 0 until candidates.length()) {
                        val existing = candidates.optJSONObject(i) ?: continue
                        if (existing.optString("y") == "energy_cliff" &&
                            kotlin.math.abs(existing.optDouble("t", 0.0) - cliff) < 2.0
                        ) {
                            candidates.put(i, cliffObj)
                            replaced = true
                            break
                        }
                    }
                    if (!replaced) candidates.put(cliffObj)
                } else {
                    json.put("mixOutCandidates", org.json.JSONArray().put(cliffObj))
                }
                json.toString()
            }.getOrDefault(base.rawJson)
            return base.copy(mixOutTime = cliff, rawJson = mergedRawJson)
        } finally {
            fullSamples.fill(0f)
            fullSamples = null
        }
    }

    private fun logCues(trackId: String, result: TrackAnalysisResult) {
        runCatching {
            val safeId = trackId.map { if (it.code in 32..126) it else '?' }.joinToString("")
            val json = if (result.rawJson.isNotBlank()) JSONObject(result.rawJson) else null
            fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)
            fun candidates(key: String): String = runCatching {
                val arr = json?.optJSONArray(key) ?: return@runCatching ""
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val t = o.optDouble("t", Double.NaN)
                        val y = o.optString("y", "")
                        if (t.isFinite() && y.isNotBlank()) add("$y@${fmt(t)}")
                    }
                }.joinToString(", ")
            }.getOrDefault("")
            val outro = runCatching { json?.optDouble("outroStartTime", Double.NaN) }.getOrNull()
                ?.takeIf { it.isFinite() && it > 0.0 }
            val seen = LinkedHashSet<String>()
            val mixOutCands = buildList {
                runCatching {
                    val arr = json?.optJSONArray("mixOutCandidates")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val t = o.optDouble("t", Double.NaN)
                            val y = o.optString("y", "")
                            if (t.isFinite() && y.isNotBlank() && seen.add(y)) add("$y@${fmt(t)}")
                        }
                    }
                }
                if (outro != null && seen.add("outro_start")) add("outro_start@${fmt(outro)}")
                if (seen.add("content_end")) add("content_end@${fmt(result.contentEndTime)}")
            }.joinToString(", ")
            Timber.tag("TrackAnalyzer").d(
                "Cues $safeId: contentEnd=${fmt(result.contentEndTime)} " +
                    "mixIn=${fmt(result.mixInTime)} mixInCandidates=[${candidates("mixInCandidates")}] " +
                    "mixOut=${fmt(result.mixOutTime)} mixOutCandidates=[$mixOutCands]",
            )
        }
    }

    private fun mergeHeadAndTail(
        headResult: TrackAnalysisResult,
        tailResult: TrackAnalysisResult,
        durationMs: Long,
        durationSeconds: Double?,
    ): TrackAnalysisResult {
        val tailStartSec = (durationMs - AudioDecoder.TAIL_WINDOW_MS) / 1000.0
        val rawMixOut = tailResult.mixOutTime
        val mergedMixOut = if (rawMixOut > 0.0) rawMixOut + tailStartSec else headResult.mixOutTime
        val rawContentEnd = tailResult.contentEndTime
        val mergedContentEnd = if (rawContentEnd > 0.0) {
            rawContentEnd + tailStartSec
        } else {
            durationSeconds ?: headResult.contentEndTime
        }

        val mergedRawJson = runCatching {
            if (headResult.rawJson.isBlank()) return@runCatching ""
            val headJson = JSONObject(headResult.rawJson)
            val tailJson = if (tailResult.rawJson.isNotBlank()) JSONObject(tailResult.rawJson) else null
            if (tailJson != null) {
                if (mergedMixOut > 0.0) {
                    headJson.put("mixOutTime", mergedMixOut)
                }
                if (mergedContentEnd > 0.0) {
                    headJson.put("contentEndTime", mergedContentEnd)
                }
                val outroStart = tailJson.optDouble("outroStartTime", 0.0)
                if (outroStart > 0.0) {
                    headJson.put("outroStartTime", outroStart + tailStartSec)
                }
                val tailCandidates = tailJson.optJSONArray("mixOutCandidates")
                if (tailCandidates != null) {
                    val mergedCandidates = org.json.JSONArray()
                    for (i in 0 until tailCandidates.length()) {
                        val candidate = tailCandidates.optJSONObject(i) ?: continue
                        val candObj = JSONObject(candidate.toString())
                        val t = candObj.optDouble("t", 0.0)
                        if (t > 0.0) {
                            candObj.put("t", t + tailStartSec)
                        }
                        mergedCandidates.put(candObj)
                    }
                    headJson.put("mixOutCandidates", mergedCandidates)
                }
            }
            headJson.toString()
        }.getOrDefault(headResult.rawJson)

        return TrackAnalysisResult(
            bpm = headResult.bpm,
            mixInTime = headResult.mixInTime,
            mixOutTime = mergedMixOut,
            contentEndTime = mergedContentEnd,
            firstBeat = headResult.firstBeat,
            rawJson = mergedRawJson,
        )
    }

    fun analyzeAsync(
        trackId: String,
        durationSeconds: Double? = null,
        decodeSamples: () -> FloatArray?,
    ): Deferred<TrackAnalysisResult?> = analyzerScope.async(Dispatchers.Default) {
        analyze(trackId, durationSeconds, decodeSamples)
    }

    fun analyzeAsync(
        trackId: String,
        durationSeconds: Double? = null,
        decodeHeadSamples: () -> FloatArray?,
        decodeTailSamples: (() -> FloatArray?)?,
    ): Deferred<TrackAnalysisResult?> = analyzerScope.async(Dispatchers.Default) {
        analyze(trackId, durationSeconds, decodeHeadSamples, decodeTailSamples)
    }

    fun toResult(entity: TrackAnalysisEntity): TrackAnalysisResult = TrackAnalysisResult(
        bpm = entity.bpm,
        mixInTime = entity.mixInTime,
        mixOutTime = entity.mixOutTime,
        contentEndTime = entity.contentEndTime,
        firstBeat = entity.firstBeat,
        rawJson = "",
    )

    fun fromResult(
        trackId: String,
        result: TrackAnalysisResult,
        updatedAt: Long = System.currentTimeMillis(),
    ): TrackAnalysisEntity {
        val json = if (result.rawJson.isNotBlank()) runCatching { JSONObject(result.rawJson) }.getOrNull() else null
        val parsedBeatInterval = json?.optDouble("beatInterval", 0.0)?.takeIf { it.isFinite() && it > 0.0 }
            ?: if (result.bpm > 0.0) 60.0 / result.bpm else 0.0
        val parsedDynamicRangeDb = json?.optDouble("dynamicRangeDb", 0.0)?.takeIf { it.isFinite() } ?: 0.0
        val parsedLoudnessLufs = json?.optDouble("loudnessLufs", 0.0)?.takeIf { it.isFinite() } ?: 0.0

        return TrackAnalysisEntity(
            trackId = trackId,
            bpm = result.bpm,
            beatInterval = parsedBeatInterval,
            firstBeat = result.firstBeat,
            mixInTime = result.mixInTime,
            mixOutTime = result.mixOutTime,
            contentEndTime = result.contentEndTime,
            dynamicRangeDb = parsedDynamicRangeDb,
            loudnessLufs = parsedLoudnessLufs,
            updatedAt = updatedAt,
        )
    }
}

fun TrackAnalysisEntity.toResult(): TrackAnalysisResult = TrackAnalyzer.toResult(this)
