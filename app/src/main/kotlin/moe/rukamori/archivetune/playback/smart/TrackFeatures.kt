package moe.rukamori.archivetune.playback.smart

import moe.rukamori.archivetune.audiodsp.TrackAnalysisResult
import org.json.JSONObject

object TrackFeatures {
    init {
        try {
            System.loadLibrary("yuma_analysis")
        } catch (_: UnsatisfiedLinkError) {
        }
    }

    @JvmStatic
    private external fun nativeAnalyze(samples: FloatArray, sampleRate: Double, duration: Double): String
    @JvmStatic
    private external fun nativeEnergyCliff(samples: FloatArray, sampleRate: Double, duration: Double): Double
    @JvmStatic
    private external fun nativeSampleRate(): Double
    @JvmStatic
    private external fun nativeResample(input: FloatArray, inRate: Double, outRate: Double): FloatArray

    fun sampleRate(): Double = nativeSampleRate()

    fun resample(input: FloatArray, inRate: Double, outRate: Double): FloatArray {
        return nativeResample(input, inRate, outRate)
    }

    fun analyze(samples: FloatArray, duration: Double): TrackAnalysisResult? = runCatching {
        analyzeInternal(samples, duration, nativeSampleRate())
    }.getOrNull()

    fun analyze(samples: FloatArray, duration: Double, sampleRate: Double): TrackAnalysisResult? = runCatching {
        analyzeInternal(samples, duration, sampleRate)
    }.getOrNull()

    fun energyCliff(samples: FloatArray, duration: Double): Double = runCatching {
        nativeEnergyCliff(samples, nativeSampleRate(), duration)
    }.getOrDefault(0.0)

    private fun analyzeInternal(samples: FloatArray, duration: Double, sampleRate: Double): TrackAnalysisResult {
        val jsonStr = nativeAnalyze(samples, sampleRate, duration)
        val json = JSONObject(jsonStr)
        return TrackAnalysisResult(
            bpm = json.optFiniteDouble("bpm", 0.0),
            mixInTime = json.optFiniteDouble("mixInTime", 0.0),
            mixOutTime = json.optFiniteDouble("mixOutTime", 0.0),
            contentEndTime = json.optFiniteDouble("contentEndTime", 0.0),
            firstBeat = json.optFiniteDouble("firstBeat", 0.0),
            rawJson = jsonStr
        )
    }

    private fun JSONObject.optFiniteDouble(name: String, fallback: Double): Double {
        if (isNull(name)) return fallback
        val value = optDouble(name, fallback)
        return if (value.isFinite()) value else fallback
    }
}
