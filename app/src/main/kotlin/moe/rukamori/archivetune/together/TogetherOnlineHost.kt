/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.together

import androidx.compose.runtime.Immutable
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.utils.reportException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Immutable
sealed class TogetherOnlineHostState {
    data object Idle : TogetherOnlineHostState()

    data object Connecting : TogetherOnlineHostState()

    data class Connected(
        val wsUrl: String,
        val sessionId: String,
        val hostParticipantId: String,
    ) : TogetherOnlineHostState()
}

class TogetherOnlineHost(
    externalScope: CoroutineScope,
    val sessionId: String,
    private val sessionKey: String,
    private val hostId: String,
    private val hostDisplayName: String,
    initialSettings: TogetherRoomSettings,
    clientId: String = UUID.randomUUID().toString(),
    private val bearerToken: String? = null,
) {
    private val client =
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(30, TimeUnit.SECONDS)
                    writeTimeout(15, TimeUnit.SECONDS)
                    pingInterval(25, TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                }
            }
            install(WebSockets) {
                pingIntervalMillis = 25_000
            }
        }

    private val scope = CoroutineScope(externalScope.coroutineContext + SupervisorJob())
    private val mutex = Mutex()
    private var settings: TogetherRoomSettings = initialSettings

    private var session: WebSocketSession? = null
    private var loopJob: Job? = null
    private var hostParticipantId: String? = null
    private var authorityParticipantId: String? = null

    private val clientId = clientId.trim().ifBlank { UUID.randomUUID().toString() }.take(64)
    private val normalizedBearerToken: String? = bearerToken?.trim()?.takeIf { it.isNotBlank() }

    private data class Guest(
        val participantId: String,
        val clientId: String,
        val name: String,
        var pending: Boolean,
    )

    private val guests = ConcurrentHashMap<String, Guest>()

    @Volatile
    private var lastParticipants: List<TogetherParticipant> = emptyList()

    var onEvent: ((TogetherServerEvent) -> Unit)? = null
    private val chat = TogetherChatTranscript(sessionId = sessionId)

    fun chatHistory(): List<ChatMessage> = chat.history()

    suspend fun connect(wsUrl: String) {
        disconnect()
        hostParticipantId = null
        authorityParticipantId = null
        guests.clear()
        lastParticipants = emptyList()
        chat.clear()

        val trimmed = wsUrl.trim()
        val urls = listOfNotNull(trimmed, alternateWebSocketSchemeOrNull(trimmed)).distinct()

        val token = normalizedBearerToken
        if (token == null) {
            onEvent?.invoke(TogetherServerEvent.Error("Together token is missing"))
            return
        }

        var lastError: Throwable? = null
        for (candidate in urls) {
            try {
                client.webSocket(
                    urlString = candidate,
                    request = {
                        header("Authorization", "Bearer $token")
                    },
                ) {
                    session = this
                    val hello =
                        ClientHello(
                            protocolVersion = TogetherProtocolVersion,
                            sessionId = sessionId,
                            sessionKey = sessionKey,
                            clientId = clientId,
                            displayName = hostDisplayName.trim(),
                        )
                    send(TogetherJson.json.encodeToString(TogetherMessage.serializer(), hello))
                    runLoop(this, candidate)
                }
                return
            } catch (t: Throwable) {
                lastError = t
            }
        }

        onEvent?.invoke(TogetherServerEvent.Error(connectionFailureMessage(lastError), lastError))
    }

    private fun alternateWebSocketSchemeOrNull(url: String): String? {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("ws://") -> "wss://${trimmed.removePrefix("ws://")}"
            trimmed.startsWith("wss://") -> "ws://${trimmed.removePrefix("wss://")}"
            else -> null
        }
    }

    private fun connectionFailureMessage(t: Throwable?): String {
        val root = generateSequence(t) { it.cause }.lastOrNull()
        val raw = root?.message?.trim().orEmpty()
        val reason =
            when (root) {
                is java.net.UnknownHostException -> {
                    "Server not found"
                }

                is java.net.ConnectException -> {
                    "Connection refused"
                }

                is java.net.SocketTimeoutException -> {
                    "Connection timed out"
                }

                is javax.net.ssl.SSLHandshakeException -> {
                    "Secure connection failed"
                }

                is IllegalArgumentException -> {
                    if (raw.contains("ws", ignoreCase = true) &&
                        raw.contains("scheme", ignoreCase = true)
                    ) {
                        "Invalid server websocket URL"
                    } else {
                        null
                    }
                }

                else -> {
                    null
                }
            }

        val detail = reason ?: raw.takeIf { it.isNotBlank() }
        return if (detail == null) "Connection failed" else "Connection failed: $detail"
    }

    suspend fun disconnect() {
        loopJob?.cancel()
        loopJob?.cancelAndJoin()
        loopJob = null
        runCatching { session?.close(CloseReason(CloseReason.Codes.NORMAL, "Disconnect")) }
        session = null
        hostParticipantId = null
        authorityParticipantId = null
        guests.clear()
        lastParticipants = emptyList()
    }

    fun currentParticipants(): List<TogetherParticipant> = lastParticipants

    /**
     * Publishes a host message to the relay and to the local transcript. Guests that already saw the
     * frame when the relay fanned it out merge it by message id, so nothing is duplicated.
     */
    suspend fun postHostChat(
        text: String,
        track: TogetherTrack?,
    ): ChatMessage {
        val message =
            chat.acceptLocal(
                text = text,
                senderName = hostDisplayName,
                participantId = hostId,
                track = track,
            )
        sendFrame(message)
        onEvent?.invoke(TogetherServerEvent.ChatReceived(message))
        return message
    }

    suspend fun postHostTyping(isTyping: Boolean) {
        sendFrame(
            ChatTyping(
                sessionId = sessionId,
                participantId = hostId,
                displayName = hostDisplayName,
                isTyping = isTyping,
            ),
        )
    }

    private suspend fun sendFrame(message: TogetherMessage) {
        val current = session ?: return
        runCatching {
            current.send(TogetherJson.json.encodeToString(TogetherMessage.serializer(), message))
        }
    }

    private suspend fun handleGuestChat(candidate: ChatMessage) {
        val guest = guests[candidate.participantId] ?: return
        if (guest.pending) return
        if (!currentSettings().allowChat) return
        when (
            val result =
                chat.acceptIncoming(
                    candidate = candidate,
                    senderName = guest.name,
                    participantId = guest.participantId,
                )
        ) {
            is TogetherChatResult.Accepted -> {
                sendFrame(result.message)
                onEvent?.invoke(TogetherServerEvent.ChatReceived(result.message))
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun announceSystemChat(
        event: ChatSystemEvent,
        participantName: String,
    ) {
        if (!currentSettings().allowChat) return
        val message = chat.recordSystem(event = event, participantName = participantName)
        sendFrame(message)
        onEvent?.invoke(TogetherServerEvent.ChatReceived(message))
    }

    suspend fun currentSettings(): TogetherRoomSettings = mutex.withLock { settings }

    suspend fun updateSettings(newSettings: TogetherRoomSettings) {
        mutex.withLock {
            settings = newSettings
        }
    }

    suspend fun approveParticipant(
        participantId: String,
        approved: Boolean,
    ) {
        try {
            val guest = guests[participantId] ?: return
            if (!guest.pending) return

            if (!approved) {
                runCatching {
                    session?.send(
                        TogetherJson.json.encodeToString(
                            TogetherMessage.serializer(),
                            JoinDecision(sessionId = sessionId, participantId = participantId, approved = false),
                        ),
                    )
                }
                guest.pending = false
                return
            }

            guest.pending = false
            runCatching {
                session?.send(
                    TogetherJson.json.encodeToString(
                        TogetherMessage.serializer(),
                        JoinDecision(sessionId = sessionId, participantId = participantId, approved = true),
                    ),
                )
            }
            onEvent?.invoke(
                TogetherServerEvent.ParticipantJoined(
                    TogetherParticipant(
                        id = participantId,
                        name = guest.name,
                        isHost = false,
                        isPending = false,
                        isConnected = true,
                    ),
                ),
            )
            rebuildParticipantsSnapshot()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportException(e)
        }
    }

    suspend fun kickParticipant(
        participantId: String,
        reason: String?,
    ) {
        if (!guests.containsKey(participantId)) return
        runCatching {
            session?.send(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    KickParticipant(sessionId = sessionId, participantId = participantId, reason = reason),
                ),
            )
        }
    }

    suspend fun banParticipant(
        participantId: String,
        reason: String?,
    ) {
        if (!guests.containsKey(participantId)) return
        runCatching {
            session?.send(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    BanParticipant(sessionId = sessionId, participantId = participantId, reason = reason),
                ),
            )
        }
    }

    suspend fun transferHostOwnership(participantId: String) {
        val guest = guests[participantId] ?: return
        if (guest.pending) return
        runCatching {
            session?.send(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    HostTransfer(sessionId = sessionId, participantId = participantId),
                ),
            )
        }
    }

    suspend fun broadcastRoomState(state: TogetherRoomState) {
        val snapshotSettings = mutex.withLock { settings }
        val activeHostId = authorityParticipantId ?: hostId
        rebuildParticipantsSnapshot()
        val roomState =
            state.copy(
                hostId = activeHostId,
                settings = snapshotSettings,
                participants = lastParticipants,
            )

        runCatching {
            session?.send(
                TogetherJson.json.encodeToString(
                    TogetherMessage.serializer(),
                    RoomStateMessage(roomState),
                ),
            )
        }
    }

    private fun rebuildParticipantsSnapshot() {
        val activeHostId = authorityParticipantId ?: hostId
        val host =
            TogetherParticipant(
                id = hostId,
                name = hostDisplayName,
                isHost = activeHostId == hostId,
                isPending = false,
                isConnected = true,
            )

        val guestList =
            guests.values
                .sortedBy { it.name.lowercase() }
                .map {
                    TogetherParticipant(
                        id = it.participantId,
                        name = it.name,
                        isHost = it.participantId == activeHostId,
                        isPending = it.pending,
                        isConnected = true,
                    )
                }

        lastParticipants =
            buildList {
                add(host)
                addAll(guestList)
            }
    }

    private suspend fun runLoop(
        session: WebSocketSession,
        wsUrl: String,
    ) {
        loopJob =
            scope.launch {
                try {
                    while (true) {
                        val frame =
                            try {
                                session.incoming.receive()
                            } catch (_: ClosedReceiveChannelException) {
                                break
                            }

                        val text = (frame as? Frame.Text)?.readText() ?: continue
                        val message =
                            runCatching { TogetherJson.json.decodeFromString(TogetherMessage.serializer(), text) }
                                .getOrElse {
                                    onEvent?.invoke(TogetherServerEvent.Error("Failed to decode message", it))
                                    continue
                                }

                        when (message) {
                            is ServerWelcome -> {
                                if (message.sessionId == sessionId) {
                                    hostParticipantId = message.participantId
                                    mutex.withLock { settings = message.settings }
                                    authorityParticipantId = hostId
                                }
                            }

                            is JoinRequest -> {
                                if (message.sessionId == sessionId) {
                                    val participant = message.participant.copy(isHost = false, isConnected = true, isPending = true)
                                    guests[participant.id] =
                                        Guest(
                                            participantId = participant.id,
                                            clientId = "",
                                            name = participant.name,
                                            pending = true,
                                        )
                                    rebuildParticipantsSnapshot()
                                    onEvent?.invoke(TogetherServerEvent.JoinRequested(participant))
                                }
                            }

                            is ParticipantJoined -> {
                                if (message.sessionId == sessionId) {
                                    val participant = message.participant.copy(isHost = false, isConnected = true, isPending = false)
                                    guests[participant.id] =
                                        Guest(
                                            participantId = participant.id,
                                            clientId = "",
                                            name = participant.name,
                                            pending = false,
                                        )
                                   rebuildParticipantsSnapshot()
                                    announceSystemChat(ChatSystemEvent.PARTICIPANT_JOINED, participant.name)
                                    sendFrame(ChatHistory(sessionId = sessionId, messages = chat.history()))
                                    onEvent?.invoke(TogetherServerEvent.ParticipantJoined(participant))
                                }
                            }

                            is ParticipantLeft -> {
                                if (message.sessionId == sessionId) {
                                    val removed = guests.remove(message.participantId)
                                    if (authorityParticipantId == message.participantId) {
                                        authorityParticipantId = hostId
                                    }
                                    rebuildParticipantsSnapshot()
                                    if (removed != null) {
                                        announceSystemChat(ChatSystemEvent.PARTICIPANT_LEFT, removed.name)
                                    }
                                    onEvent?.invoke(TogetherServerEvent.ParticipantLeft(message.participantId, message.reason))
                                }
                            }

                            is RoomStateMessage -> {
                                if (message.state.sessionId == sessionId) {
                                    onEvent?.invoke(TogetherServerEvent.RoomStateReceived(message.state))
                                }
                            }

                            is HostTransferred -> {
                                if (message.sessionId == sessionId) {
                                    authorityParticipantId = message.participantId
                                    rebuildParticipantsSnapshot()
                                    onEvent?.invoke(TogetherServerEvent.HostTransferred(message.participantId))
                                }
                            }

                            is ControlRequest -> {
                                if (message.sessionId == sessionId) onEvent?.invoke(TogetherServerEvent.ControlRequested(message))
                            }

                            is AddTrackRequest -> {
                                if (message.sessionId == sessionId) onEvent?.invoke(TogetherServerEvent.AddTrackRequested(message))
                            }

                            is ChatMessage -> {
                                if (message.sessionId == sessionId) {
                                    handleGuestChat(message)
                                }
                            }

                            is ChatTyping -> {
                                if (message.sessionId == sessionId && currentSettings().allowChat) {
                                    val normalized =
                                        message.copy(
                                            displayName =
                                                guests[message.participantId]?.name
                                                    ?: message.displayName,
                                        )
                                    sendFrame(normalized)
                                    onEvent?.invoke(TogetherServerEvent.ChatTypingReceived(normalized))
                                }
                            }

                            is ChatHistory -> {
                                Unit
                            }

                            is ServerError -> {
                                onEvent?.invoke(TogetherServerEvent.Error(message.message, null))
                            }

                            else -> {
                                Unit
                            }
                        }
                    }
                } catch (t: Throwable) {
                    onEvent?.invoke(TogetherServerEvent.Error("Connection loop failed", t))
                } finally {
                    hostParticipantId = null
                    authorityParticipantId = null
                    guests.clear()
                    lastParticipants = emptyList()
                    chat.clear()
                    runCatching { session.close(CloseReason(CloseReason.Codes.NORMAL, "Disconnected")) }
                    onEvent?.invoke(TogetherServerEvent.Error("Disconnected", null))
                }
            }
        loopJob?.join()
    }
}
