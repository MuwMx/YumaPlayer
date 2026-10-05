package moe.rukamori.archivetune.playback

import androidx.datastore.preferences.core.Preferences
import moe.rukamori.archivetune.constants.EqualizerBandLevelsMbKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostEnabledKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostStrengthKey
import moe.rukamori.archivetune.constants.EqualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainMbKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerStrengthKey
import moe.rukamori.archivetune.db.entities.FormatEntity
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

internal fun MusicService.readEqSettingsFromPrefs(prefs: Preferences): EqSettings {
    val levels = decodeBandLevelsMb(prefs[EqualizerBandLevelsMbKey])
    return EqSettings(
        enabled = prefs[EqualizerEnabledKey] ?: false,
        bandLevelsMb = levels,
        outputGainEnabled = prefs[EqualizerOutputGainEnabledKey] ?: false,
        outputGainMb = prefs[EqualizerOutputGainMbKey] ?: 0,
        bassBoostEnabled = prefs[EqualizerBassBoostEnabledKey] ?: false,
        bassBoostStrength = (prefs[EqualizerBassBoostStrengthKey] ?: 0).coerceIn(0, 1000),
        virtualizerEnabled = prefs[EqualizerVirtualizerEnabledKey] ?: false,
        virtualizerStrength = (prefs[EqualizerVirtualizerStrengthKey] ?: 0).coerceIn(0, 1000),
    )
}

internal fun MusicService.resampleLevelsByIndex(levelsMb: List<Int>, targetCount: Int): List<Int> {
    if (targetCount <= 0) return emptyList()
    if (levelsMb.isEmpty()) return List(targetCount) { 0 }
    if (levelsMb.size == targetCount) return levelsMb
    if (targetCount == 1) return listOf(levelsMb.sum() / levelsMb.size)
    val lastIndex = levelsMb.lastIndex.toFloat().coerceAtLeast(1f)
    return List(targetCount) { i ->
        val pos = i.toFloat() * lastIndex / (targetCount - 1).toFloat()
        val lo = kotlin.math.floor(pos).toInt().coerceIn(0, levelsMb.lastIndex)
        val hi = kotlin.math.ceil(pos).toInt().coerceIn(0, levelsMb.lastIndex)
        val t = (pos - lo.toFloat()).coerceIn(0f, 1f)
        val a = levelsMb[lo]
        val b = levelsMb[hi]
        (a + ((b - a) * t)).toInt()
    }
}

internal fun MusicService.calculateEffectivePlayerVolume(playerVolume: Float, normalizeFactor: Float, audioFocusVolumeFactor: Float): Float {
    val safePlayerVolume = playerVolume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
    val safeNormalizeFactor = normalizeFactor.takeIf { it.isFinite() }?.coerceIn(MusicService.MIN_AUDIO_NORMALIZATION_FACTOR, MusicService.MAX_AUDIO_NORMALIZATION_FACTOR) ?: 1f
    val safeAudioFocusVolumeFactor = audioFocusVolumeFactor.takeIf { it.isFinite() }?.coerceIn(MusicService.MIN_AUDIO_FOCUS_VOLUME_FACTOR, 1f) ?: 1f
    return (safePlayerVolume * safeNormalizeFactor * safeAudioFocusVolumeFactor).coerceIn(0f, maxSafeGainFactor)
}

internal val audioNormalizationFactorCache = ConcurrentHashMap<String, Float>()

internal fun calculateAudioNormalizationFactor(
    format: FormatEntity?,
    normalizeAudio: Boolean,
): Float {
    Timber.tag("AudioNormalization").d("Audio normalization enabled: $normalizeAudio")
    Timber.tag("AudioNormalization").d("Format loudnessDb: ${format?.loudnessDb}, perceptualLoudnessDb: ${format?.perceptualLoudnessDb}")

    if (!normalizeAudio) {
        Timber.tag("AudioNormalization").d("Normalization disabled - using factor 1.0")
        return 1f
    }

    val loudnessDb = format?.normalizationLoudnessDb()
    if (loudnessDb == null || !loudnessDb.isFinite()) {
        Timber.tag("AudioNormalization").w("Normalization enabled but no valid loudness data available - no normalization applied")
        return 1f
    }

    val rawFactor = 10f.pow(-loudnessDb / 20)
    val factor =
        if (rawFactor.isFinite()) {
            rawFactor.coerceIn(MusicService.MIN_AUDIO_NORMALIZATION_FACTOR, MusicService.MAX_AUDIO_NORMALIZATION_FACTOR)
        } else {
            1f
        }

    if (factor != rawFactor) {
        Timber.tag("AudioNormalization").d("Normalization factor clamped from $rawFactor to $factor")
    }
    Timber.tag("AudioNormalization").i("Applying normalization factor: $factor")
    return factor
}

internal fun resolveAudioNormalizationFactor(
    mediaId: String?,
    format: FormatEntity?,
    normalizeAudio: Boolean,
): Float {
    val currentMediaId = mediaId?.takeIf { it.isNotBlank() } ?: return 1f
    if (!normalizeAudio) {
        return 1f
    }

    if (format?.id == currentMediaId) {
        val factor = calculateAudioNormalizationFactor(format, normalizeAudio = true)
        audioNormalizationFactorCache[currentMediaId] = factor
        return factor
    }

    return audioNormalizationFactorCache[currentMediaId] ?: 1f
}

private fun FormatEntity.normalizationLoudnessDb(): Float? =
    sequenceOf(perceptualLoudnessDb, loudnessDb)
        .mapNotNull { it?.toFloat() }
        .firstOrNull { it.isFinite() }
