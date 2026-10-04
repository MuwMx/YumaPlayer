/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.playback

import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.utils.reportException
import timber.log.Timber

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
