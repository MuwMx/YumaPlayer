package moe.rukamori.archivetune.playback.together

import moe.rukamori.archivetune.playback.MusicService

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.SilentHandler
import moe.rukamori.archivetune.playback.queue.buildTogetherTrack
import moe.rukamori.archivetune.together.AddTrackMode
import moe.rukamori.archivetune.together.ChatMessage
import moe.rukamori.archivetune.together.ChatTyping
import moe.rukamori.archivetune.together.ControlAction
import moe.rukamori.archivetune.together.TogetherChatDisabledCode
import moe.rukamori.archivetune.together.TogetherChatInvalidCode
import moe.rukamori.archivetune.together.TogetherChatRateLimitedCode
import moe.rukamori.archivetune.together.TogetherChatTypingTtlMs
import moe.rukamori.archivetune.together.TogetherLink
import moe.rukamori.archivetune.together.TogetherRole
import moe.rukamori.archivetune.together.TogetherSessionState
import moe.rukamori.archivetune.together.TogetherTrack
import moe.rukamori.archivetune.together.TogetherTypingParticipant
import moe.rukamori.archivetune.together.mergeTogetherChatMessages
import java.util.UUID

private const val TogetherTypingSendIntervalMs = 1200L
private const val TogetherTypingPruneIntervalMs = 700L

/** Session id of the states that can carry chat traffic. */
internal val MusicService.togetherChatSessionId: String?
    get() =
        when (val state = togetherSessionState.value) {
            is TogetherSessionState.Hosting -> state.sessionId
            is TogetherSessionState.HostingOnline -> state.sessionId
            is TogetherSessionState.Joined -> state.sessionId
            is TogetherSessionState.Joining -> TogetherLink.decode(state.joinLink)?.sessionId
            is TogetherSessionState.JoiningOnline -> state.code
            is TogetherSessionState.Error,
            TogetherSessionState.Idle,
            -> null
        }

internal fun MusicService.isTogetherHostRole(): Boolean =
    when (val state = togetherSessionState.value) {
        is TogetherSessionState.Hosting -> true
        is TogetherSessionState.HostingOnline -> true
        is TogetherSessionState.Joined -> state.role is TogetherRole.Host
        else -> false
    }

internal fun MusicService.appendTogetherChat(message: ChatMessage) {
    appendTogetherChatHistory(listOf(message))
}

/**
 * Merges incoming messages into the locally rendered transcript. Duplicates coming from the relay
 * fan-out, the server echo or a history replay collapse on message id.
 */
internal fun MusicService.appendTogetherChatHistory(messages: List<ChatMessage>) {
    if (messages.isEmpty()) return
    togetherChatMessages.update { current ->
        mergeTogetherChatMessages(current = current, incoming = messages)
    }
}

internal fun MusicService.handleTogetherChatTyping(typing: ChatTyping) {
    if (typing.participantId == togetherSelfParticipantId) return
    if (isTogetherHostRole() && typing.participantId == togetherHostId) return
    val now = System.currentTimeMillis()
    togetherTypingParticipants.update { current ->
        current
            .filterNot { it.participantId == typing.participantId }
            .plus(
                if (typing.isTyping) {
                    listOf(
                        TogetherTypingParticipant(
                            participantId = typing.participantId,
                            displayName =
                                typing.displayName.trim().ifBlank {
                                    getString(R.string.together_unknown_participant)
                                },
                            expiresAtEpochMs = now + TogetherChatTypingTtlMs,
                        ),
                    )
                } else {
                    emptyList()
                },
            ).filter { it.expiresAtEpochMs > now }
    }
    scheduleTogetherTypingPrune()
}

/** Clears the local transcript so a new room never shows the previous conversation. */
internal fun MusicService.resetTogetherChatState() {
    togetherChatTypingJob?.cancel()
    togetherChatTypingJob = null
    resetTogetherChat()
}

/**
 * Sends a chat message through whichever transport owns the current session: the embedded LAN
 * server, the online host bridge or the guest client.
 */
internal fun MusicService.sendTogetherChat(
    text: String,
    track: TogetherTrack? = null,
) {
    val trimmed = text.trim()
    if (trimmed.isBlank() && track == null) return
    val sessionId = togetherChatSessionId ?: return
    val server = togetherServer
    val onlineHost = togetherOnlineHost
    val client = togetherClient
    when {
        server != null ->
            ioScope.launch(SilentHandler) {
                server.postHostChat(text = trimmed, track = track)
            }

        onlineHost != null ->
            ioScope.launch(SilentHandler) {
                onlineHost.postHostChat(text = trimmed, track = track)
            }

        client != null ->
            ioScope.launch(SilentHandler) {
                client.sendChat(
                    sessionId = sessionId,
                    text = trimmed,
                    track = track,
                    messageId = UUID.randomUUID().toString(),
                )
            }

        else -> showTogetherNotice(getString(R.string.not_allowed), key = "CHAT_NO_SESSION")
    }
}

internal fun MusicService.sendTogetherChatTyping(
    displayName: String,
    isTyping: Boolean,
) {
    val now = SystemClock.elapsedRealtime()
    if (isTyping && now - togetherLastTypingSentAtElapsedMs < TogetherTypingSendIntervalMs) return
    togetherLastTypingSentAtElapsedMs = now
    val sessionId = togetherChatSessionId ?: return
    val server = togetherServer
    val onlineHost = togetherOnlineHost
    val client = togetherClient
    when {
        server != null -> ioScope.launch(SilentHandler) { server.postHostTyping(isTyping) }

        onlineHost != null -> ioScope.launch(SilentHandler) { onlineHost.postHostTyping(isTyping) }

        client != null ->
            ioScope.launch(SilentHandler) {
                client.sendChatTyping(
                    sessionId = sessionId,
                    displayName = displayName.trim().ifBlank { getString(R.string.together_role_guest) },
                    isTyping = isTyping,
                )
            }

        else -> Unit
    }
}

/**
 * Chat rejections arrive with a stable code, so the receiver picks the localised wording instead of
 * echoing the sender-side text.
 */
internal fun MusicService.showTogetherChatIssue(
    code: String,
    fallback: String,
) {
    val message =
        when (code) {
            TogetherChatRateLimitedCode -> getString(R.string.together_chat_rate_limited)
            TogetherChatDisabledCode -> getString(R.string.together_chat_disabled)
            TogetherChatInvalidCode -> getString(R.string.together_chat_invalid)
            else -> fallback
        }
    showTogetherNotice(message, key = code)
}

/** Shares the song currently playing on this device into the room chat. */
internal fun MusicService.shareCurrentTrackToChat(): Boolean {
    val track = buildTogetherTrack(player.currentMediaItem) ?: return false
    sendTogetherChat(text = "", track = track)
    return true
}

/** Plays a track shared in chat, honouring the room permissions of the current role. */
internal fun MusicService.playTogetherSharedTrack(
    track: TogetherTrack,
    mode: AddTrackMode,
) {
    val state = togetherSessionState.value
    val isGuest = state is TogetherSessionState.Joined && state.role is TogetherRole.Guest
    if (isGuest) {
        requestTogetherAddTrack(track, mode)
        if (mode == AddTrackMode.PLAY_NEXT) {
            requestTogetherControl(ControlAction.SeekToTrack(track.id, 0L))
        }
        return
    }
    ioScope.launch(SilentHandler) {
        applyHostAddTrack(track, mode)
        if (mode == AddTrackMode.PLAY_NEXT) {
            applyHostControl(ControlAction.SeekToTrack(track.id, 0L))
        }
    }
}

private fun MusicService.scheduleTogetherTypingPrune() {
    if (togetherTypingParticipants.value.isEmpty()) return
    if (togetherChatTypingJob?.isActive == true) return
    togetherChatTypingJob =
        ioScope.launch(SilentHandler) {
            while (togetherTypingParticipants.value.isNotEmpty()) {
                delay(TogetherTypingPruneIntervalMs)
                val now = System.currentTimeMillis()
                togetherTypingParticipants.update { current ->
                    current.filter { it.expiresAtEpochMs > now }
                }
            }
        }
}
