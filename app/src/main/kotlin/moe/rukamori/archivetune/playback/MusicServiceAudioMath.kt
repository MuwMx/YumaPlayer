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
