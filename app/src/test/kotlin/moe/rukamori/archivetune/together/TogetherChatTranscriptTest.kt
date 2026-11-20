package moe.rukamori.archivetune.together
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherChatTranscriptTest {
    private var now = 1_000L

    private fun transcript(maxEntries: Int = TogetherChatTranscriptLimit) =
        TogetherChatTranscript(sessionId = "room-1", maxEntries = maxEntries, nowMs = { now })

    private fun message(
        text: String = "hello",
        messageId: String = "m-$text",
        track: TogetherTrack? = null,
        kind: ChatMessageKind = if (track != null) ChatMessageKind.TRACK else ChatMessageKind.TEXT,
        sessionId: String = "room-1",
    ) = ChatMessage(
        sessionId = sessionId,
        participantId = "ignored-by-host",
        senderName = "Impostor",
        messageId = messageId,
        text = text,
        track = track,
        kind = kind,
        sentAtEpochMs = 0L,
    )

    @Test
    fun incomingMessageIsReauthorisedByTheHost() {
        val result = transcript().acceptIncoming(message(text = "  hey  "), senderName = "Ana", participantId = "p-1")
        val accepted = result as TogetherChatResult.Accepted
        assertEquals("hey", accepted.message.text)
        assertEquals("Ana", accepted.message.senderName)
        assertEquals("p-1", accepted.message.participantId)
        assertEquals(ChatMessageKind.TEXT, accepted.message.kind)
        assertEquals(1_000L, accepted.message.sentAtEpochMs)
    }

    @Test
    fun blankSenderNameFallsBackToGuest() {
        val result = transcript().acceptIncoming(message(), senderName = "   ", participantId = "p-1")
        assertEquals("Guest", (result as TogetherChatResult.Accepted).message.senderName)
    }

    @Test
    fun otherSessionIsRejected() {
        val result = transcript().acceptIncoming(message(sessionId = "room-2"), senderName = "Ana", participantId = "p-1")
        assertEquals(TogetherChatResult.Invalid, result)
    }

    @Test
    fun guestsCannotForgeSystemMessages() {
        val candidate = message(kind = ChatMessageKind.SYSTEM).copy(systemEvent = ChatSystemEvent.PARTICIPANT_LEFT)
        val result = transcript().acceptIncoming(candidate, senderName = "Ana", participantId = "p-1")
        assertEquals(TogetherChatResult.Invalid, result)
    }

    @Test
    fun emptyMessageWithoutTrackIsRejected() {
        val result = transcript().acceptIncoming(message(text = "   "), senderName = "Ana", participantId = "p-1")
        assertEquals(TogetherChatResult.Empty, result)
    }

    @Test
    fun repeatedMessageIdIsIgnored() {
        val chat = transcript()
        assertTrue(chat.acceptIncoming(message(text = "once"), senderName = "Ana", participantId = "p-1") is TogetherChatResult.Accepted)
        now += TogetherChatMinIntervalMs
        assertEquals(TogetherChatResult.Empty, chat.acceptIncoming(message(text = "once"), senderName = "Ana", participantId = "p-1"))
        assertEquals(1, chat.snapshot().size)
    }

    @Test
    fun floodingIsRateLimitedPerParticipant() {
        val chat = transcript()
        assertTrue(chat.acceptIncoming(message(text = "a", messageId = "1"), senderName = "Ana", participantId = "p-1") is TogetherChatResult.Accepted)
        assertEquals(
            TogetherChatResult.TooFrequent,
            chat.acceptIncoming(message(text = "b", messageId = "2"), senderName = "Ana", participantId = "p-1"),
        )
        assertTrue(
            chat.acceptIncoming(message(text = "c", messageId = "3"), senderName = "Bia", participantId = "p-2")
                is TogetherChatResult.Accepted,
        )
        now += TogetherChatMinIntervalMs
        assertTrue(
            chat.acceptIncoming(message(text = "d", messageId = "4"), senderName = "Ana", participantId = "p-1")
                is TogetherChatResult.Accepted,
        )
    }

    @Test
    fun sharedTrackBecomesTrackMessage() {
        val track = TogetherTrack(id = "t1", title = "Song", artists = listOf("Artist"), durationSec = 210)
        val accepted = transcript().acceptLocal(text = "", senderName = "Host", participantId = "host", track = track)
        assertEquals(ChatMessageKind.TRACK, accepted.kind)
        assertEquals(track, accepted.sharedTrackOrNull())
    }

    @Test
    fun transcriptIsBoundedAndHistoryReturnsTheTail() {
        val chat = transcript(maxEntries = 3)
        repeat(5) { index ->
            chat.acceptLocal(text = "msg-$index", senderName = "Host", participantId = "host", messageId = "id-$index")
        }
        assertEquals(listOf("msg-2", "msg-3", "msg-4"), chat.snapshot().map { it.text })
        assertEquals(listOf("msg-3", "msg-4"), chat.history(limit = 2).map { it.text })
    }

    @Test
    fun systemEventsKeepTheParticipantNameAsArgument() {
        val chat = transcript()
        val joined = chat.recordSystem(ChatSystemEvent.PARTICIPANT_JOINED, "Ana")
        assertEquals(ChatMessageKind.SYSTEM, joined.kind)
        assertEquals(ChatSystemEvent.PARTICIPANT_JOINED, joined.systemEvent)
        assertEquals("Ana", joined.text)
        assertNull(joined.sharedTrackOrNull())
    }

    @Test
    fun mergeKeepsOrderAndDropsDuplicates() {
        val chat = transcript()
        val first = chat.acceptLocal(text = "one", senderName = "Host", participantId = "host", messageId = "id-1")
        now += 1_000L
        val second = chat.acceptLocal(text = "two", senderName = "Host", participantId = "host", messageId = "id-2")
        val merged = mergeTogetherChatMessages(current = listOf(first), incoming = listOf(second, first))
        assertEquals(listOf("one", "two"), merged.map { it.text })
    }
}
