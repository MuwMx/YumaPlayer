/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.together
import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

const val TogetherChatMaxTextLength: Int = 1000
const val TogetherChatTranscriptLimit: Int = 200
const val TogetherChatHistoryLimit: Int = 60
const val TogetherChatMinIntervalMs: Long = 350L
const val TogetherChatTypingTtlMs: Long = 4500L
const val TogetherChatRateLimitedCode: String = "CHAT_RATE_LIMITED"
const val TogetherChatDisabledCode: String = "CHAT_DISABLED"
const val TogetherChatInvalidCode: String = "CHAT_MESSAGE_INVALID"

@Serializable
enum class ChatMessageKind {
    TEXT,
    TRACK,
    SYSTEM,
}

@Serializable
enum class ChatSystemEvent {
    PARTICIPANT_JOINED,
    PARTICIPANT_LEFT,
}

@Serializable
@SerialName("chat_message")
@Immutable
data class ChatMessage(
    val sessionId: String,
    val participantId: String,
    val senderName: String = "",
    val messageId: String = UUID.randomUUID().toString(),
    val text: String = "",
    val track: TogetherTrack? = null,
    val kind: ChatMessageKind = ChatMessageKind.TEXT,
    val systemEvent: ChatSystemEvent? = null,
    val sentAtEpochMs: Long = 0L,
) : TogetherMessage

@Serializable
@SerialName("chat_typing")
@Immutable
data class ChatTyping(
    val sessionId: String,
    val participantId: String,
    val displayName: String = "",
    val isTyping: Boolean = true,
) : TogetherMessage

@Serializable
@SerialName("chat_history")
@Immutable
data class ChatHistory(
    val sessionId: String,
    val messages: List<ChatMessage> = emptyList(),
) : TogetherMessage

@Immutable
sealed interface TogetherChatResult {
    data class Accepted(
        val message: ChatMessage,
    ) : TogetherChatResult

    data object Empty : TogetherChatResult

    data object TooFrequent : TogetherChatResult

    data object Invalid : TogetherChatResult
}

/**
 * Authoritative, bounded chat log shared by the LAN server and the online host bridge.
 *
 * The owner of the transcript (host or relay-connected bridge) is the single source of truth for
 * ordering, sender names and timestamps, so every participant renders the same conversation.
 */
class TogetherChatTranscript(
    private val sessionId: String,
    private val maxEntries: Int = TogetherChatTranscriptLimit,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val lock = Any()
    private val entries = ArrayDeque<ChatMessage>()
    private val seenMessageIds = mutableSetOf<String>()
    private val lastAcceptedAtMs = HashMap<String, Long>()

    /**
     * Records a message authored by a guest.
     * [senderName] always comes from the session, never from the payload, so nobody can impersonate
     * another participant.
     */
    fun acceptIncoming(
        candidate: ChatMessage,
        senderName: String,
        participantId: String,
        minIntervalMs: Long = TogetherChatMinIntervalMs,
    ): TogetherChatResult {
        val normalized = normalize(candidate, senderName, participantId) ?: return TogetherChatResult.Invalid
        if (normalized.kind == ChatMessageKind.SYSTEM) return TogetherChatResult.Invalid
        if (normalized.text.isEmpty() && normalized.track == null) return TogetherChatResult.Empty
        synchronized(lock) {
            if (seenMessageIds.contains(normalized.messageId)) return TogetherChatResult.Empty
            val last = lastAcceptedAtMs[normalized.participantId] ?: 0L
            if (nowMs() - last < minIntervalMs) return TogetherChatResult.TooFrequent
            lastAcceptedAtMs[normalized.participantId] = nowMs()
            storeLocked(normalized)
            return TogetherChatResult.Accepted(normalized)
        }
    }

    /** Records a message authored by the local authority (host), bypassing the rate limiter. */
    fun acceptLocal(
        text: String,
        senderName: String,
        participantId: String,
        track: TogetherTrack? = null,
        messageId: String = UUID.randomUUID().toString(),
    ): ChatMessage {
        val kind = if (track != null) ChatMessageKind.TRACK else ChatMessageKind.TEXT
        val message =
            ChatMessage(
                sessionId = sessionId,
                participantId = participantId,
                senderName = senderName.trim().ifBlank { DEFAULT_SENDER_NAME },
                messageId = messageId.ifBlank { UUID.randomUUID().toString() },
                text = text.trim().take(TogetherChatMaxTextLength),
                track = track,
                kind = kind,
                sentAtEpochMs = nowMs(),
            )
        synchronized(lock) {
            storeLocked(message)
        }
        return message
    }

    fun recordSystem(
        event: ChatSystemEvent,
        participantName: String,
    ): ChatMessage {
        val message =
            ChatMessage(
                sessionId = sessionId,
                participantId = SYSTEM_PARTICIPANT_ID,
                senderName = "",
                messageId = UUID.randomUUID().toString(),
                text = participantName.trim(),
                kind = ChatMessageKind.SYSTEM,
                systemEvent = event,
                sentAtEpochMs = nowMs(),
            )
        synchronized(lock) {
            storeLocked(message)
        }
        return message
    }

    fun history(
        limit: Int = TogetherChatHistoryLimit,
    ): List<ChatMessage> =
        synchronized(lock) {
            entries.takeLast(limit.coerceAtLeast(0)).toList()
        }

    fun snapshot(): List<ChatMessage> =
        synchronized(lock) {
            entries.toList()
        }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            seenMessageIds.clear()
            lastAcceptedAtMs.clear()
        }
    }

    private fun normalize(
        candidate: ChatMessage,
        senderName: String,
        participantId: String,
    ): ChatMessage? {
        if (candidate.sessionId != sessionId) return null
        val text = candidate.text.trim().take(TogetherChatMaxTextLength)
        val kind =
            when {
                candidate.track != null -> ChatMessageKind.TRACK
                else -> candidate.kind
            }
        return candidate.copy(
            sessionId = sessionId,
            participantId = participantId,
            senderName = senderName.trim().ifBlank { DEFAULT_SENDER_NAME },
            messageId = candidate.messageId.ifBlank { UUID.randomUUID().toString() }.take(64),
            text = text,
            kind = kind,
            systemEvent = if (kind == ChatMessageKind.SYSTEM) candidate.systemEvent else null,
            sentAtEpochMs = if (candidate.sentAtEpochMs > 0L) candidate.sentAtEpochMs else nowMs(),
        )
    }

    private fun storeLocked(message: ChatMessage) {
        if (!seenMessageIds.add(message.messageId)) return
        entries.addLast(message)
        while (entries.size > maxEntries) {
            val removed = entries.removeFirst()
            seenMessageIds.remove(removed.messageId)
            if (removed.participantId != SYSTEM_PARTICIPANT_ID) {
                lastAcceptedAtMs.remove(removed.participantId)
            }
        }
    }

    private companion object {
        const val DEFAULT_SENDER_NAME = "Guest"
        const val SYSTEM_PARTICIPANT_ID = "system"
    }
}

/** Chat messages carry a playable track payload; map it once per layer instead of per screen. */
fun ChatMessage.sharedTrackOrNull(): TogetherTrack? = track.takeIf { kind == ChatMessageKind.TRACK }

/** Who is currently typing; entries expire on the client that receives them. */
@Immutable
data class TogetherTypingParticipant(
    val participantId: String,
    val displayName: String,
    val expiresAtEpochMs: Long,
)

/**
 * Merges incoming messages into the locally rendered transcript.
 *
 * The same message can legitimately arrive twice (optimistic local echo, relay fan-out, history
 * replay), so message id is the identity and timestamps keep the reading order stable.
 */
fun mergeTogetherChatMessages(
    current: List<ChatMessage>,
    incoming: List<ChatMessage>,
    limit: Int = TogetherChatTranscriptLimit,
): List<ChatMessage> {
    if (incoming.isEmpty()) return current
    val byId = LinkedHashMap<String, ChatMessage>(current.size + incoming.size)
    current.forEach { message -> byId[message.messageId] = message }
    incoming.forEach { message -> byId[message.messageId] = message }
    val merged = byId.values.sortedBy { it.sentAtEpochMs }
    return if (merged.size <= limit) merged else merged.takeLast(limit)
}
