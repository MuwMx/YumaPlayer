/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.playback

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.audiodsp.FALL
import moe.rukamori.archivetune.audiodsp.RISE
import timber.log.Timber
import kotlin.math.abs

internal inline fun <T> MusicService.readAudioEffectValue(
    operation: String,
    block: () -> T,
): T? = runCatching(block).onFailure { error ->
    Timber.tag("MusicService").w(error, "Audio effect query failed: %s", operation)
}.getOrNull()

internal fun MusicService.updateEqCapabilitiesFromEffect(eq: Equalizer) {
    val bandCount = readAudioEffectValue("equalizer band count") { eq.numberOfBands.toInt().coerceAtLeast(0) } ?: 0
    val range = readAudioEffectValue("equalizer band range") { eq.bandLevelRange }
    val minMb = range?.getOrNull(0)?.toInt() ?: -1500
    val maxMb = range?.getOrNull(1)?.toInt() ?: 1500
    val center = (0 until bandCount).map { band ->
        (readAudioEffectValue("equalizer center frequency for band $band") { eq.getCenterFreq(band.toShort()) } ?: 0) / 1000
    }
    val presetCount = readAudioEffectValue("equalizer preset count") { eq.numberOfPresets.toInt().coerceAtLeast(0) } ?: 0
    val presets = (0 until presetCount).map { idx ->
        readAudioEffectValue("equalizer preset name for preset $idx") { eq.getPresetName(idx.toShort()).toString() } ?: "Preset ${idx + 1}"
    }
    eqCapabilities.value = EqCapabilities(
        bandCount = bandCount,
        minBandLevelMb = minMb,
        maxBandLevelMb = maxMb,
        centerFreqHz = center,
        systemPresets = presets,
    )
}

internal fun MusicService.releaseAudioEffectInstances() {
    audioEffectsSessionId = null
    try { equalizer?.release() } catch (_: Exception) {}
    try { bassBoost?.release() } catch (_: Exception) {}
    try { virtualizer?.release() } catch (_: Exception) {}
    try { loudnessEnhancer?.release() } catch (_: Exception) {}
    equalizer = null
    bassBoost = null
    virtualizer = null
    loudnessEnhancer = null
    eqCapabilities.value = null
}

internal fun MusicService.releaseAudioEffects() {
    audioEffectsInitializationJob?.cancel()
    audioEffectsInitializationJob = null
    releaseAudioEffectInstances()
}

internal fun MusicService.ensureAudioEffects(sessionId: Int) {
    if (sessionId <= 0) return
    if (audioEffectsSessionId == sessionId && equalizer != null) return
    audioEffectsInitializationJob?.cancel()
    audioEffectsInitializationJob = null
    if (initializeAudioEffects(sessionId)) return
    audioEffectsInitializationJob = scope.launch {
        repeat(MusicService.AUDIO_EFFECT_INITIALIZATION_MAX_ATTEMPTS - 1) {
            delay(MusicService.AUDIO_EFFECT_INITIALIZATION_RETRY_DELAY_MS)
            if (localPlayer.audioSessionId != sessionId || !shouldKeepAudioEffectSessionOpen()) return@launch
            if (initializeAudioEffects(sessionId)) return@launch
        }
    }
}

internal fun MusicService.initializeAudioEffects(sessionId: Int): Boolean {
    releaseAudioEffectInstances()
    audioEffectsSessionId = sessionId
    equalizer = createAudioEffect("Equalizer", sessionId) { Equalizer(0, sessionId) }
    bassBoost = createAudioEffect("BassBoost", sessionId) { BassBoost(0, sessionId) }
    virtualizer = createAudioEffect("Virtualizer", sessionId) { Virtualizer(0, sessionId) }
    loudnessEnhancer = createAudioEffect("LoudnessEnhancer", sessionId) { LoudnessEnhancer(sessionId) }
    equalizer?.let(::updateEqCapabilitiesFromEffect)
    applyEqSettingsToEffects(desiredEqSettings.value)
    return equalizer != null
}

internal inline fun <T> MusicService.createAudioEffect(
    name: String,
    sessionId: Int,
    factory: () -> T,
): T? = runCatching(factory).onFailure { error ->
    Timber.tag(MusicService.TAG).w(error, "%s initialization failed for audio session %d", name, sessionId)
}.getOrNull()

internal fun MusicService.applyEqSettingsToEffects(settings: EqSettings) {
    val eq = equalizer ?: return
    val caps = eqCapabilities.value
    val bandCount = caps?.bandCount ?: readAudioEffectValue("equalizer band count") { eq.numberOfBands.toInt() } ?: 0
    val minMb = caps?.minBandLevelMb ?: readAudioEffectValue("equalizer minimum band level") { eq.bandLevelRange.getOrNull(0)?.toInt() } ?: -1500
    val maxMb = caps?.maxBandLevelMb ?: readAudioEffectValue("equalizer maximum band level") { eq.bandLevelRange.getOrNull(1)?.toInt() } ?: 1500
    val levels = resampleLevelsByIndex(settings.bandLevelsMb, bandCount)
    runCatching { eq.enabled = settings.enabled }
    for (band in 0 until bandCount) {
        val levelMb = levels.getOrNull(band)?.coerceIn(minMb, maxMb) ?: 0
        runCatching { eq.setBandLevel(band.toShort(), levelMb.toShort()) }
    }
    bassBoost?.let { bb ->
        runCatching { bb.enabled = settings.bassBoostEnabled }
        runCatching { bb.setStrength(settings.bassBoostStrength.toShort()) }
    }
    virtualizer?.let { v ->
        runCatching { v.enabled = settings.virtualizerEnabled }
        runCatching { v.setStrength(settings.virtualizerStrength.toShort()) }
    }
    loudnessEnhancer?.let { le ->
        val gainMb = if (settings.outputGainEnabled) settings.outputGainMb.coerceIn(-1500, 1500) else 0
        runCatching { le.setTargetGain(gainMb) }
        runCatching { le.enabled = settings.outputGainEnabled }
    }
}

internal fun MusicService.shouldKeepAudioEffectSessionOpen(): Boolean {
    val playbackState = localPlayer.playbackState
    return playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY
}

internal fun MusicService.reconcileAudioEffectSession() {
    if (!shouldKeepAudioEffectSessionOpen()) {
        closeAudioEffectSession()
        return
    }
    val sessionId = localPlayer.audioSessionId
    if (sessionId > 0) rebindAudioEffectSession(sessionId)
}

internal fun MusicService.openAudioEffectSession() {
    if (isAudioEffectSessionOpened) return
    val sessionId = localPlayer.audioSessionId
    if (sessionId <= 0) return
    isAudioEffectSessionOpened = true
    openedAudioSessionId = sessionId
    ensureAudioEffects(sessionId)
    sendOpenAudioEffectSessionBroadcast(sessionId)
}

internal fun MusicService.closeAudioEffectSession() {
    if (!isAudioEffectSessionOpened) return
    isAudioEffectSessionOpened = false
    val sessionId = openedAudioSessionId ?: localPlayer.audioSessionId
    openedAudioSessionId = null
    releaseAudioEffects()
    if (sessionId <= 0) return
    sendCloseAudioEffectSessionBroadcast(sessionId)
}

internal fun MusicService.rebindAudioEffectSession(newSessionId: Int) {
    if (newSessionId <= 0 || !shouldKeepAudioEffectSessionOpen()) return
    val oldSessionId = openedAudioSessionId
    if (!isAudioEffectSessionOpened) {
        openAudioEffectSession()
        return
    }
    if (oldSessionId == newSessionId) {
        ensureAudioEffects(newSessionId)
        return
    }
    if (oldSessionId != null && oldSessionId > 0) sendCloseAudioEffectSessionBroadcast(oldSessionId)
    openedAudioSessionId = newSessionId
    ensureAudioEffects(newSessionId)
    sendOpenAudioEffectSessionBroadcast(newSessionId)
}

internal fun MusicService.sendOpenAudioEffectSessionBroadcast(sessionId: Int) {
    sendBroadcast(
        android.content.Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        },
    )
}

internal fun MusicService.sendCloseAudioEffectSessionBroadcast(sessionId: Int) {
    sendBroadcast(
        android.content.Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
        },
    )
}

internal fun MusicService.currentEffectivePlayerVolume(): Float = calculateEffectivePlayerVolume(playerVolume.value, normalizeFactor.value, audioFocusVolumeFactor.value)

internal fun MusicService.currentEffectivePlayerVolumeForMediaId(mediaId: String): Float {
    val targetNormalizeFactor = if (audioNormalizationEnabled) audioNormalizationFactorCache[mediaId] ?: 1f else 1f
    return calculateEffectivePlayerVolume(playerVolume.value, targetNormalizeFactor, audioFocusVolumeFactor.value)
}

internal fun MusicService.updateEffectiveVolume(finalVolume: Float) {
    if (!isPlayerInitialized() || !shouldRampEffectiveVolume(finalVolume)) {
        applyEffectiveVolumeImmediately(finalVolume)
        return
    }
    val startVolume = player.volume.takeIf { it.isFinite() }?.coerceIn(0f, maxSafeGainFactor) ?: finalVolume
    val targetVolume = finalVolume.coerceIn(0f, maxSafeGainFactor)
    if (abs(targetVolume - startVolume) <= MusicService.EFFECTIVE_VOLUME_RAMP_MIN_DELTA) {
        applyEffectiveVolumeImmediately(targetVolume)
        return
    }
    effectiveVolumeRampJob?.cancel()
    effectiveVolumeRampJob = scope.launch {
        val durationMs = if (targetVolume > startVolume) MusicService.EFFECTIVE_VOLUME_RAMP_UP_MS else MusicService.EFFECTIVE_VOLUME_RAMP_DOWN_MS
        val startedAtMs = android.os.SystemClock.elapsedRealtime()
        while (isActive) {
            val elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAtMs
            val progress = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            val easedProgress = progress * progress * (3f - (2f * progress))
            val interpolatedVolume = startVolume + ((targetVolume - startVolume) * easedProgress)
            renderEffectiveVolume(interpolatedVolume)
            if (progress >= 1f) break
            delay(MusicService.EFFECTIVE_VOLUME_RAMP_FRAME_MS)
        }
        renderEffectiveVolume(targetVolume)
        effectiveVolumeRampJob = null
    }
}

internal fun MusicService.shouldRampEffectiveVolume(finalVolume: Float): Boolean {
    if (isCrossfading || crossfadeHandoffInProgress) return false
    if (!shouldKeepPlaybackAudible()) return false
    if (!finalVolume.isFinite()) return false
    if (player.volume <= MusicService.STUCK_MUTED_VOLUME_EPSILON) return false
    return true
}

internal fun MusicService.applyEffectiveVolumeImmediately(finalVolume: Float = currentEffectivePlayerVolume()) {
    effectiveVolumeRampJob?.cancel()
    effectiveVolumeRampJob = null
    renderEffectiveVolume(finalVolume)
}

internal fun MusicService.renderEffectiveVolume(finalVolume: Float = currentEffectivePlayerVolume()) {
    crossfadeBaseVolume = finalVolume
    val incomingPlayer = secondaryCrossfadePlayer
    if (isCrossfading && incomingPlayer != null) {
        val incomingBaseVolume = secondaryCrossfadeTarget?.let { currentEffectivePlayerVolumeForMediaId(it.mediaId) } ?: finalVolume
        crossfadeIncomingBaseVolume = incomingBaseVolume
        applyCrossfadeVolumes(crossfadeProgress, finalVolume, incomingBaseVolume, localPlayer, incomingPlayer)
        return
    }
    if (isPlayerInitialized()) player.volume = finalVolume
    incomingPlayer?.volume = 0f
}

internal fun MusicService.applyCrossfadeVolumes(progress: Float, outgoingBaseVolume: Float, incomingBaseVolume: Float, outgoingPlayer: ExoPlayer, incomingPlayer: ExoPlayer) {
    outgoingPlayer.volume = (outgoingBaseVolume * FALL(progress)).coerceIn(0f, maxSafeGainFactor)
    incomingPlayer.volume = (incomingBaseVolume * RISE(progress)).coerceIn(0f, maxSafeGainFactor)
}
