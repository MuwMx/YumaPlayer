package moe.rukamori.archivetune.playback

import android.database.ContentObserver
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Handler
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.EqualizerBandLevelsMbKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostEnabledKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostStrengthKey
import moe.rukamori.archivetune.constants.EqualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainMbKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerStrengthKey
import moe.rukamori.archivetune.utils.reportException
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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

internal fun MusicService.setupAudioFocusRequest() {
    audioFocusRequest = AudioManager.AUDIOFOCUS_GAIN.let { _ ->
        AudioManager.AUDIOFOCUS_GAIN
        android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            ).setOnAudioFocusChangeListener { focusChange -> handleAudioFocusChange(focusChange) }
            .setAcceptsDelayedFocusGain(true).build()
    }
}

internal fun MusicService.shouldKeepPlaybackAudible(): Boolean {
    if (!isPlayerInitialized()) return false
    if (player.currentMediaItem == null || !player.playWhenReady) return false
    return player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
}

internal fun MusicService.restoreAudioFocusVolume() {
    audioFocusVolumeFactor.value = 1f
    hasAudioFocus = true
    lastAudioFocusState = AudioManager.AUDIOFOCUS_GAIN
}

internal fun MusicService.pauseForAudioFocusLoss(resumeWhenFocusReturns: Boolean) {
    audioFocusVolumeFactor.value = 1f
    wasPlayingBeforeAudioFocusLoss = resumeWhenFocusReturns && player.playWhenReady
    if (player.playWhenReady) player.pause()
}

internal fun MusicService.ensureAudioFocusForActivePlayback(): Boolean {
    if (!player.playWhenReady) return true
    if (requestAudioFocus()) return true
    pauseForAudioFocusLoss(resumeWhenFocusReturns = true)
    return false
}

internal fun MusicService.handleAudioFocusChange(focusChange: Int) {
    when (focusChange) {
        AudioManager.AUDIOFOCUS_GAIN -> {
            hasAudioFocus = true
            audioFocusVolumeFactor.value = 1f
            if (wasPlayingBeforeAudioFocusLoss) { player.play(); wasPlayingBeforeAudioFocusLoss = false }
            lastAudioFocusState = focusChange
        }
        AudioManager.AUDIOFOCUS_LOSS -> {
            hasAudioFocus = false
            pauseForAudioFocusLoss(resumeWhenFocusReturns = false)
            abandonAudioFocus()
            lastAudioFocusState = focusChange
        }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
            hasAudioFocus = false
            pauseForAudioFocusLoss(resumeWhenFocusReturns = true)
            lastAudioFocusState = focusChange
        }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
            hasAudioFocus = false
            pauseForAudioFocusLoss(resumeWhenFocusReturns = true)
            lastAudioFocusState = focusChange
        }
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {
            hasAudioFocus = true
            audioFocusVolumeFactor.value = 1f
            if (wasPlayingBeforeAudioFocusLoss) { player.play(); wasPlayingBeforeAudioFocusLoss = false }
            lastAudioFocusState = focusChange
        }
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
            hasAudioFocus = true
            audioFocusVolumeFactor.value = 1f
            lastAudioFocusState = focusChange
        }
    }
}

internal fun MusicService.requestAudioFocus(): Boolean {
    if (hasAudioFocus) {
        if (audioFocusVolumeFactor.value != 1f || lastAudioFocusState == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) restoreAudioFocusVolume()
        return true
    }
    audioFocusRequest?.let { request ->
        val result = audioManager.requestAudioFocus(request)
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (hasAudioFocus) restoreAudioFocusVolume()
        return hasAudioFocus
    }
    return false
}

internal fun MusicService.abandonAudioFocus() {
    if (hasAudioFocus) {
        audioFocusRequest?.let { request ->
            audioManager.abandonAudioFocusRequest(request)
            hasAudioFocus = false
        }
    }
}

internal fun MusicService.isDeviceMutedNow(): Boolean {
    val streamVolume = runCatching { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrElse { error ->
        reportException(error); return player.isDeviceMuted || player.deviceVolume <= 0
    }
    val isStreamMuted = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
        runCatching { audioManager.isStreamMute(AudioManager.STREAM_MUSIC) }.getOrElse { error -> reportException(error); false }
    return isStreamMuted || streamVolume <= 0
}

internal fun MusicService.registerMuteRecoveryObserver() {
    if (muteRecoveryObserver != null) return
    val observer = object : ContentObserver(Handler(mainLooper)) {
        override fun onChange(selfChange: Boolean) {
            if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0) handleDeviceMuteStateChanged()
        }
    }
    contentResolver.registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, observer)
    muteRecoveryObserver = observer
}

internal fun MusicService.unregisterMuteRecoveryObserver() {
    muteRecoveryObserver?.let { contentResolver.unregisterContentObserver(it) }
    muteRecoveryObserver = null
}

internal fun MusicService.handleDeviceMuteStateChanged(playbackRequestedWhileMuted: Boolean = false) {
    if (!pauseOnDeviceMuteEnabled || isTogetherGuestSession()) {
        wasAutoPausedByDeviceMute = false
        unregisterMuteRecoveryObserver()
        return
    }
    if (isDeviceMutedNow()) {
        if (playbackRequestedWhileMuted && restoreDeviceMusicVolumeForPlayback()) {
            wasAutoPausedByDeviceMute = false
            unregisterMuteRecoveryObserver()
            return
        }
        val canPauseNow = player.currentMediaItem != null && player.playWhenReady && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
        if (canPauseNow) {
            player.pause()
            wasAutoPausedByDeviceMute = true
            registerMuteRecoveryObserver()
            if (playbackRequestedWhileMuted) showDeviceMutePlaybackNotice()
        }
        return
    }
    unregisterMuteRecoveryObserver()
    if (!wasAutoPausedByDeviceMute) return
    wasAutoPausedByDeviceMute = false
    val canResumeNow = player.currentMediaItem != null && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
    if (canResumeNow) player.play()
}

internal fun MusicService.restoreDeviceMusicVolumeForPlayback(): Boolean {
    val recoveryPercent = deviceMutePlaybackRecoveryVolumePercent.coerceIn(0, 100)
    if (recoveryPercent <= 0) return false
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (maxVolume <= 0) return false
    val targetVolume = kotlin.math.ceil(maxVolume * (recoveryPercent / 100.0)).toInt().coerceIn(1, maxVolume)
    return runCatching {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
    }.getOrElse { reportException(it); false }
}

internal fun MusicService.showDeviceMutePlaybackNotice() {
    val now = android.os.SystemClock.elapsedRealtime()
    if (now - lastDeviceMutePlaybackNoticeAtElapsedMs < MusicService.DEVICE_MUTE_PLAYBACK_NOTICE_INTERVAL_MS) return
    lastDeviceMutePlaybackNoticeAtElapsedMs = now
    scope.launch(moe.rukamori.archivetune.extensions.SilentHandler) {
        android.widget.Toast.makeText(this@showDeviceMutePlaybackNotice, moe.rukamori.archivetune.R.string.device_volume_zero_playback_paused, android.widget.Toast.LENGTH_SHORT).show()
    }
}

internal fun MusicService.isTogetherGuestSession(): Boolean {
    val joined = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
    return joined?.role is moe.rukamori.archivetune.together.TogetherRole.Guest
}

internal fun MusicService.calculateEffectivePlayerVolume(playerVolume: Float, normalizeFactor: Float, audioFocusVolumeFactor: Float): Float {
    val safePlayerVolume = playerVolume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
    val safeNormalizeFactor = normalizeFactor.takeIf { it.isFinite() }?.coerceIn(MusicService.MIN_AUDIO_NORMALIZATION_FACTOR, MusicService.MAX_AUDIO_NORMALIZATION_FACTOR) ?: 1f
    val safeAudioFocusVolumeFactor = audioFocusVolumeFactor.takeIf { it.isFinite() }?.coerceIn(MusicService.MIN_AUDIO_FOCUS_VOLUME_FACTOR, 1f) ?: 1f
    return (safePlayerVolume * safeNormalizeFactor * safeAudioFocusVolumeFactor).coerceIn(0f, maxSafeGainFactor)
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
            applyEffectiveVolume(interpolatedVolume)
            if (progress >= 1f) break
            delay(MusicService.EFFECTIVE_VOLUME_RAMP_FRAME_MS)
        }
        applyEffectiveVolume(targetVolume)
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
    applyEffectiveVolume(finalVolume)
}

internal fun MusicService.applyEffectiveVolume(finalVolume: Float = currentEffectivePlayerVolume()) {
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

internal fun MusicService.ensureAudiblePlaybackVolume(reason: String) {
    if (!isPlayerInitialized()) return
    if (isCrossfading || crossfadeHandoffInProgress) return
    if (!shouldKeepPlaybackAudible()) return
    if (playerVolume.value <= 0f) return
    val expectedVolume = currentEffectivePlayerVolume()
    if (expectedVolume <= MusicService.MIN_AUDIBLE_EFFECTIVE_VOLUME) return
    if (player.volume > MusicService.STUCK_MUTED_VOLUME_EPSILON) return
    Timber.tag(MusicService.TAG).w("Restoring muted primary player volume during active playback: reason=%s expected=%s actual=%s", reason, expectedVolume, player.volume)
    applyEffectiveVolumeImmediately(expectedVolume)
}

internal fun MusicService.updateAudiblePlaybackRecovery() {
    if (!isPlayerInitialized() || !shouldKeepPlaybackAudible()) {
        audiblePlaybackRecoveryJob?.cancel()
        audiblePlaybackRecoveryJob = null
        return
    }
    if (audiblePlaybackRecoveryJob?.isActive == true) return
    audiblePlaybackRecoveryJob = scope.launch {
        while (isActive && shouldKeepPlaybackAudible()) {
            ensureAudiblePlaybackVolume("watchdog")
            delay(MusicService.AUDIBLE_PLAYBACK_VOLUME_CHECK_MS)
        }
        audiblePlaybackRecoveryJob = null
    }
}

internal fun MusicService.applyCrossfadeVolumes(progress: Float, outgoingBaseVolume: Float, incomingBaseVolume: Float, outgoingPlayer: ExoPlayer, incomingPlayer: ExoPlayer) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val radians = clampedProgress.toDouble() * (PI / 2.0)
    outgoingPlayer.volume = (outgoingBaseVolume * cos(radians).toFloat()).coerceIn(0f, maxSafeGainFactor)
    incomingPlayer.volume = (incomingBaseVolume * sin(radians).toFloat()).coerceIn(0f, maxSafeGainFactor)
}

internal fun MusicService.onAudioOutputDeviceChanged() {
    if (!isPlayerInitialized()) return
    val outputSignature = currentAudioOutputDeviceSignature()
    if (outputSignature == lastAudioOutputDeviceSignature) return
    lastAudioOutputDeviceSignature = outputSignature
    cancelCrossfade(resetVolume = true, resetPauseAtEnd = true)
    player.setAudioAttributes(playbackAudioAttributes(), false)
    audioRouteRecoveryJob?.cancel()
    audioRouteRecoveryJob = scope.launch {
        delay(MusicService.AUDIO_ROUTE_CHANGE_DEBOUNCE_MS)
        recoverAudioRouteAfterDeviceChange()
    }
}

internal suspend fun MusicService.recoverAudioRouteAfterDeviceChange() {
    if (!isPlayerInitialized()) return
    rebindAudioEffectsAfterRouteChange()
    if (!shouldRebuildPlaybackForAudioRouteChange()) return
    val now = android.os.SystemClock.elapsedRealtime()
    if (now - lastAudioRouteRecoveryRealtimeMs < MusicService.AUDIO_ROUTE_RECOVERY_MIN_INTERVAL_MS) return
    lastAudioRouteRecoveryRealtimeMs = now
    val mediaItemIndex = player.currentMediaItemIndex.takeIf { it != androidx.media3.common.C.INDEX_UNSET } ?: return
    val playbackPosition = player.currentPosition.coerceAtLeast(0L)
    val shouldResumePlayback = player.playWhenReady
    Timber.tag("MusicService").i("Recovering audio route after output change at index=$mediaItemIndex position=$playbackPosition resume=$shouldResumePlayback")
    if (shouldResumePlayback && !requestAudioFocus()) {
        wasPlayingBeforeAudioFocusLoss = true
        player.playWhenReady = false
        return
    }
    player.playWhenReady = false
    player.prepare()
    player.seekTo(mediaItemIndex, playbackPosition)
    delay(MusicService.AUDIO_ROUTE_RECOVERY_RESUME_DELAY_MS)
    if (shouldResumePlayback && player.currentMediaItem != null && player.playbackState != Player.STATE_ENDED && requestAudioFocus()) {
        player.playWhenReady = true
    }
}

internal suspend fun MusicService.rebindAudioEffectsAfterRouteChange() {
    if (!isAudioEffectSessionOpened) return
    closeAudioEffectSession()
    if (!player.playWhenReady) return
    delay(MusicService.AUDIO_EFFECT_ROUTE_REBIND_DELAY_MS)
    openAudioEffectSession()
}

internal fun MusicService.shouldRebuildPlaybackForAudioRouteChange(): Boolean {
    if (player.currentMediaItem == null) return false
    if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) return false
    return player.playWhenReady || player.playbackState == Player.STATE_BUFFERING
}

internal fun MusicService.currentAudioOutputDeviceSignature(): String = runCatching {
    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).asSequence().filter { it.isSink }.sortedWith(
        compareBy<android.media.AudioDeviceInfo>({ it.type }, { it.id }, { it.productName?.toString().orEmpty() })
    ).joinToString(separator = "|") { device -> "${device.type}:${device.id}:${device.productName?.toString().orEmpty()}" }
}.getOrDefault("")

internal fun MusicService.playbackAudioAttributes(): androidx.media3.common.AudioAttributes = androidx.media3.common.AudioAttributes.Builder()
    .setUsage(androidx.media3.common.C.USAGE_MEDIA).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
    .setAllowedCapturePolicy(androidx.media3.common.C.ALLOW_CAPTURE_BY_ALL).build()

internal fun MusicService.bluetoothAutoStartEnabled(): Boolean = autoStartOnBluetoothEnabled

internal fun MusicService.handleBluetoothAutoStart() {
    if (isTogetherGuestSession()) return
    if (player.currentMediaItem != null && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
        if (!player.playWhenReady) player.play()
        return
    }
    if (player.mediaItemCount > 0) { player.prepare(); player.play() }
}

internal fun MusicService.isPlayerInitialized(): Boolean = try { player; true } catch (_: UninitializedPropertyAccessException) { false }
