package moe.rukamori.archivetune.playback

import android.content.Intent
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.SilentHandler
import moe.rukamori.archivetune.extensions.mediaItems
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.together.TogetherPlaybackSync
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.getAsync
import moe.rukamori.archivetune.utils.reportException
import timber.log.Timber
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal fun MusicService.startTogetherHost(
    port: Int,
    displayName: String,
    settings: moe.rukamori.archivetune.together.TogetherRoomSettings,
) {
    ensureScopesActive()
    scope.launch(SilentHandler) {
        togetherSessionState.value = moe.rukamori.archivetune.together.TogetherSessionState.Idle
    }

    ioScope.launch(SilentHandler) {
        stopTogetherInternal()
        togetherIsOnlineSession = false

        val localIp = getLocalIpv4Address()
        val sessionId =
            java.util.UUID
                .randomUUID()
                .toString()
        val sessionKey =
            java.util.UUID
                .randomUUID()
                .toString()
        val joinInfo =
            moe.rukamori.archivetune.together.TogetherJoinInfo(
                host = localIp ?: "127.0.0.1",
                port = port,
                sessionId = sessionId,
                sessionKey = sessionKey,
            )
        val joinLink =
            moe.rukamori.archivetune.together.TogetherLink
                .encode(joinInfo)

        val server =
            moe.rukamori.archivetune.together.TogetherServer(
                scope = ioScope,
                sessionId = sessionId,
                sessionKey = sessionKey,
                hostDisplayName = displayName.trim().ifBlank { getString(R.string.app_name) },
                initialSettings = settings,
                hostParticipantId = togetherHostId,
            )

        server.onEvent = { event ->
            ioScope.launch(SilentHandler) {
                handleTogetherHostEvent(event) { server.currentSettings() }
            }
        }

        server.start(port)
        togetherServer = server

        scope.launch(SilentHandler) {
            togetherSessionState.value =
                moe.rukamori.archivetune.together.TogetherSessionState.Hosting(
                    sessionId = sessionId,
                    joinLink = joinLink,
                    localAddressHint = localIp,
                    port = port,
                    settings = settings,
                    roomState = null,
                )
        }

        togetherBroadcastJob =
            ioScope.launch(SilentHandler) {
                while (togetherServer === server) {
                    if (togetherAuthorityParticipantId == null || togetherAuthorityParticipantId == togetherHostId) {
                        val state = buildTogetherRoomState(sessionId = sessionId, hostId = togetherHostId)
                        server.broadcastRoomState(state)
                        scope.launch(SilentHandler) {
                            val hosting = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Hosting
                            if (hosting?.sessionId == sessionId) {
                                togetherSessionState.value =
                                    hosting.copy(
                                        settings = server.currentSettings(),
                                        roomState =
                                            state.copy(
                                                participants = server.currentParticipants(),
                                                settings = server.currentSettings(),
                                            ),
                                    )
                            }
                        }
                    }
                    kotlinx.coroutines.delay(TogetherPlaybackSync.BroadcastIntervalMs)
                }
            }
    }
}

internal fun MusicService.togetherOnlineErrorMessage(t: Throwable): String {
    if (t is moe.rukamori.archivetune.together.TogetherOnlineApiException) {
        val code = t.statusCode
        return when {
            code == 404 -> getString(R.string.together_session_not_found)
            code != null && code in 500..599 -> getString(R.string.together_server_error)
            else -> t.message ?: getString(R.string.network_unavailable)
        }
    }
    val root = generateSequence(t) { it.cause }.lastOrNull() ?: t
    return when (root) {
        is UnknownHostException -> getString(R.string.together_server_unreachable)
        is ConnectException -> getString(R.string.together_server_unreachable)
        is SocketTimeoutException -> getString(R.string.together_connection_timed_out)
        is javax.net.ssl.SSLHandshakeException -> getString(R.string.together_server_unreachable)
        else -> getString(R.string.network_unavailable)
    }
}

internal fun MusicService.startTogetherOnlineHost(
    displayName: String,
    settings: moe.rukamori.archivetune.together.TogetherRoomSettings,
) {
    ensureScopesActive()
    scope.launch(SilentHandler) {
        togetherSessionState.value = moe.rukamori.archivetune.together.TogetherSessionState.Idle
    }

    ioScope.launch(SilentHandler) {
        stopTogetherInternal()
        togetherIsOnlineSession = true

        val baseUrl =
            moe.rukamori.archivetune.together.TogetherOnlineEndpoint
                .baseUrlOrNull(dataStore)
        if (baseUrl == null) {
            scope.launch(SilentHandler) {
                togetherSessionState.value =
                    moe.rukamori.archivetune.together.TogetherSessionState.Error(
                        message = getString(R.string.together_online_not_configured),
                        recoverable = true,
                    )
            }
            return@launch
        }

        val togetherToken =
            moe.rukamori.archivetune.BuildConfig.TOGETHER_BEARER_TOKEN
                .trim()
                .takeIf { it.isNotBlank() }
        if (togetherToken == null) {
            scope.launch(SilentHandler) {
                togetherSessionState.value =
                    moe.rukamori.archivetune.together.TogetherSessionState.Error(
                        message = getString(R.string.together_token_missing),
                        recoverable = true,
                    )
            }
            return@launch
        }

        val api =
            moe.rukamori.archivetune.together
                .TogetherOnlineApi(baseUrl = baseUrl, bearerToken = togetherToken)
        val hostName = displayName.trim().ifBlank { getString(R.string.app_name) }

        val created =
            runCatching {
                api.createSession(
                    hostDisplayName = hostName,
                    settings = settings,
                )
            }.getOrElse { t ->
                scope.launch(SilentHandler) {
                    togetherSessionState.value =
                        moe.rukamori.archivetune.together.TogetherSessionState.Error(
                            message = togetherOnlineErrorMessage(t),
                            recoverable = true,
                        )
                }
                reportException(t)
                return@launch
            }

        val onlineHost =
            moe.rukamori.archivetune.together.TogetherOnlineHost(
                externalScope = ioScope,
                sessionId = created.sessionId,
                sessionKey = created.hostKey,
                hostId = togetherHostId,
                hostDisplayName = hostName,
                initialSettings = created.settings,
                clientId = getOrCreateTogetherClientId(),
                bearerToken = togetherToken,
            )

        onlineHost.onEvent = { event ->
            ioScope.launch(SilentHandler) {
                handleTogetherHostEvent(event) { onlineHost.currentSettings() }
            }
        }

        togetherOnlineHost = onlineHost

        scope.launch(SilentHandler) {
            togetherSessionState.value =
                moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline(
                    sessionId = created.sessionId,
                    code = created.code,
                    settings = created.settings,
                    roomState = null,
                )
        }

        val wsUrl =
            moe.rukamori.archivetune.together.TogetherOnlineEndpoint.onlineWebSocketUrlOrNull(
                rawWsUrl = created.wsUrl,
                baseUrl = baseUrl,
            )
        if (wsUrl == null) {
            scope.launch(SilentHandler) {
                togetherSessionState.value =
                    moe.rukamori.archivetune.together.TogetherSessionState.Error(
                        message = "Connection failed: Invalid server websocket URL",
                        recoverable = true,
                    )
            }
            ioScope.launch(SilentHandler) { stopTogetherInternal() }
            return@launch
        }

        togetherOnlineConnectJob?.cancel()
        togetherOnlineConnectJob =
            ioScope.launch(SilentHandler) {
                onlineHost.connect(wsUrl)
            }

        togetherBroadcastJob =
            ioScope.launch(SilentHandler) {
                while (togetherOnlineHost === onlineHost) {
                    val state =
                        if (togetherAuthorityParticipantId == null || togetherAuthorityParticipantId == togetherHostId) {
                            buildTogetherRoomState(
                                sessionId = created.sessionId,
                                hostId = togetherHostId,
                            )
                        } else {
                            null
                        }
                    if (state != null) {
                        onlineHost.broadcastRoomState(state)
                        scope.launch(SilentHandler) {
                            val hosting =
                                togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline
                            if (hosting?.sessionId == created.sessionId) {
                                val currentSettings = onlineHost.currentSettings()
                                togetherSessionState.value =
                                    hosting.copy(
                                        settings = currentSettings,
                                        roomState =
                                            state.copy(
                                                participants = onlineHost.currentParticipants(),
                                                settings = currentSettings,
                                            ),
                                    )
                            }
                        }
                    }
                    kotlinx.coroutines.delay(TogetherPlaybackSync.BroadcastIntervalMs)
                }
            }
    }
}

internal fun MusicService.joinTogether(
    rawLink: String,
    displayName: String,
) {
    ensureScopesActive()
    val joinInfo =
        moe.rukamori.archivetune.together.TogetherLink
            .decode(rawLink)
    if (joinInfo == null) {
        scope.launch(SilentHandler) {
            togetherSessionState.value =
                moe.rukamori.archivetune.together.TogetherSessionState.Error(
                    message = getString(R.string.invalid_link),
                    recoverable = true,
                )
        }
        return
    }

    scope.launch(SilentHandler) {
        togetherSessionState.value =
            moe.rukamori.archivetune.together.TogetherSessionState
                .Joining(joinInfo.toDeepLink())
    }

    ioScope.launch(SilentHandler) {
        stopTogetherInternal()
        togetherIsOnlineSession = false
        val client =
            moe.rukamori.archivetune.together.TogetherClient(
                ioScope,
                clientId = getOrCreateTogetherClientId(),
            )
        togetherClient = client
        togetherClock =
            moe.rukamori.archivetune.together
                .TogetherClock()
        togetherSelfParticipantId = null
        togetherLastAppliedQueueHash = null

        togetherClientEventsJob?.cancel()
        togetherClientEventsJob =
            ioScope.launch(SilentHandler) {
                client.events.collect { event ->
                    when (event) {
                        is moe.rukamori.archivetune.together.TogetherClientEvent.Welcome -> {
                            togetherSelfParticipantId = event.welcome.participantId
                            scope.launch(SilentHandler) {
                                val state = togetherSessionState.value
                                if (state is moe.rukamori.archivetune.together.TogetherSessionState.Joining) {
                                    val selfName = displayName.trim().ifBlank { getString(R.string.together_role_guest) }
                                    val initial =
                                        moe.rukamori.archivetune.together.TogetherRoomState(
                                            sessionId = joinInfo.sessionId,
                                            hostId = togetherHostId,
                                            participants =
                                                listOf(
                                                    moe.rukamori.archivetune.together.TogetherParticipant(
                                                        id = event.welcome.participantId,
                                                        name = selfName,
                                                        isHost = false,
                                                        isPending = event.welcome.isPending,
                                                        isConnected = true,
                                                    ),
                                                ),
                                            settings = event.welcome.settings,
                                            queue = emptyList(),
                                            queueHash = "",
                                            currentIndex = 0,
                                            isPlaying = false,
                                            positionMs = 0L,
                                            repeatMode = 0,
                                            shuffleEnabled = false,
                                            sentAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                                        )
                                    togetherSessionState.value =
                                        moe.rukamori.archivetune.together.TogetherSessionState.Joined(
                                            role = moe.rukamori.archivetune.together.TogetherRole.Guest,
                                            sessionId = joinInfo.sessionId,
                                            selfParticipantId = event.welcome.participantId,
                                            roomState = initial,
                                        )
                                }
                            }
                            startTogetherHeartbeat(joinInfo.sessionId, client)
                        }

                        is moe.rukamori.archivetune.together.TogetherClientEvent.RoomState -> {
                            applyRemoteRoomState(event.state)
                        }

                        is moe.rukamori.archivetune.together.TogetherClientEvent.HostTransferred -> {
                            handleTogetherClientHostTransferred(event.transfer)
                        }

                        is moe.rukamori.archivetune.together.TogetherClientEvent.ControlRequested -> {
                            val joined =
                                togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
                            if (togetherAuthorityParticipantId == togetherSelfParticipantId &&
                                joined?.roomState?.settings?.allowGuestsToControlPlayback == true
                            ) {
                                applyHostControl(event.request.action)
                            }
                        }

                        is moe.rukamori.archivetune.together.TogetherClientEvent.AddTrackRequested -> {
                            val joined =
                                togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
                            if (togetherAuthorityParticipantId == togetherSelfParticipantId &&
                                joined?.roomState?.settings?.allowGuestsToAddTracks == true
                            ) {
                                applyHostAddTrack(event.request.track, event.request.mode)
                            }
                        }

                        is moe.rukamori.archivetune.together.TogetherClientEvent.JoinDecision -> {
                            if (!event.decision.approved) {
                                scope.launch(SilentHandler) {
                                    togetherSessionState.value =
                                        moe.rukamori.archivetune.together.TogetherSessionState.Error(
                                            message = getString(R.string.not_allowed),
                                            recoverable = true,
                                        )
                                }
                                ioScope.launch(SilentHandler) { stopTogetherInternal() }
                            }
                        }

                        is moe.rukamori.archivetune.together.TogetherClientEvent.ServerIssue -> {
                            Timber.tag("Together").w("server issue (lan) code=${event.code.orEmpty()} message=${event.message}")
                            when (event.code) {
                                "GUEST_CONTROL_DISABLED" -> {
                                    showTogetherNotice(event.message, key = "GUEST_CONTROL_DISABLED")
                                    val joined =
                                        togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
                                    if (joined?.role is moe.rukamori.archivetune.together.TogetherRole.Guest) {
                                        togetherPendingGuestControl = null
                                        togetherLastSentControlAction = null
                                        scope.launch(SilentHandler) { applyRemoteRoomState(joined.roomState, force = true) }
                                    }
                                }

                                "GUEST_ADD_DISABLED" -> {
                                    showTogetherNotice(event.message, key = "GUEST_ADD_DISABLED")
                                }

                                "HOST_OFFLINE" -> {
                                    showTogetherNotice(event.message, key = "HOST_OFFLINE")
                                }

                                else -> {
                                    scope.launch(SilentHandler) {
                                        togetherSessionState.value =
                                            moe.rukamori.archivetune.together.TogetherSessionState.Error(
                                                message = event.message,
                                                recoverable = true,
                                            )
                                    }
                                    ioScope.launch(SilentHandler) { stopTogetherInternal() }
                                }
                            }
                        }

                        is moe.rukamori.archivetune.together.TogetherClientEvent.HeartbeatPong -> {
                            val clock = togetherClock ?: return@collect
                            clock.onPong(
                                sentAtElapsedMs = event.pong.clientElapsedRealtimeMs,
                                receivedAtElapsedMs = event.receivedAtElapsedRealtimeMs,
                                serverElapsedMs = event.pong.serverElapsedRealtimeMs,
                            )
                        }

                        is moe.rukamori.archivetune.together.TogetherClientEvent.Error -> {
                            scope.launch(SilentHandler) {
                                togetherSessionState.value =
                                    moe.rukamori.archivetune.together.TogetherSessionState.Error(
                                        message = event.message,
                                        recoverable = true,
                                    )
                            }
                            ioScope.launch(SilentHandler) { stopTogetherInternal() }
                        }

                        moe.rukamori.archivetune.together.TogetherClientEvent.Disconnected -> {
                            val current = togetherSessionState.value
                            if (current is moe.rukamori.archivetune.together.TogetherSessionState.Idle) return@collect
                            scope.launch(SilentHandler) {
                                val currentState = togetherSessionState.value
                                togetherSessionState.value =
                                    moe.rukamori.archivetune.together.TogetherSessionState.Error(
                                        message =
                                            if (currentState is moe.rukamori.archivetune.together.TogetherSessionState.Joined &&
                                                currentState.role is moe.rukamori.archivetune.together.TogetherRole.Guest
                                            ) {
                                                getString(R.string.together_host_left_session)
                                            } else {
                                                getString(R.string.network_unavailable)
                                            },
                                        recoverable = true,
                                    )
                            }
                            ioScope.launch(SilentHandler) { stopTogetherInternal() }
                        }
                    }
                }
            }

        client.connect(joinInfo, displayName.trim().ifBlank { getString(R.string.together_role_guest) })
    }
}

internal fun MusicService.joinTogetherOnline(
    code: String,
    displayName: String,
) {
    ensureScopesActive()
    val trimmedCode = code.trim()
    if (trimmedCode.isBlank()) {
        scope.launch(SilentHandler) {
            togetherSessionState.value =
                moe.rukamori.archivetune.together.TogetherSessionState.Error(
                    message = getString(R.string.invalid_code),
                    recoverable = true,
                )
        }
        return
    }

    scope.launch(SilentHandler) {
        togetherSessionState.value =
            moe.rukamori.archivetune.together.TogetherSessionState
                .JoiningOnline(trimmedCode)
    }

    ioScope.launch(SilentHandler) {
        stopTogetherInternal()
        togetherIsOnlineSession = true

        val baseUrl =
            moe.rukamori.archivetune.together.TogetherOnlineEndpoint
                .baseUrlOrNull(dataStore)
        if (baseUrl == null) {
            scope.launch(SilentHandler) {
                togetherSessionState.value =
                    moe.rukamori.archivetune.together.TogetherSessionState.Error(
                        message = getString(R.string.together_online_not_configured),
                        recoverable = true,
                    )
            }
            return@launch
        }

        val togetherToken =
            moe.rukamori.archivetune.BuildConfig.TOGETHER_BEARER_TOKEN
                .trim()
                .takeIf { it.isNotBlank() }
        if (togetherToken == null) {
            scope.launch(SilentHandler) {
                togetherSessionState.value =
                    moe.rukamori.archivetune.together.TogetherSessionState.Error(
                        message = getString(R.string.together_token_missing),
                        recoverable = true,
                    )
            }
            return@launch
        }

        val api =
            moe.rukamori.archivetune.together
                .TogetherOnlineApi(baseUrl = baseUrl, bearerToken = togetherToken)
        val resolved =
            runCatching { api.resolveCode(trimmedCode) }
                .getOrElse { t ->
                    scope.launch(SilentHandler) {
                        togetherSessionState.value =
                            moe.rukamori.archivetune.together.TogetherSessionState.Error(
                                message = togetherOnlineErrorMessage(t),
                                recoverable = true,
                            )
                    }
                    reportException(t)
                    return@launch
                }

        val client =
            moe.rukamori.archivetune.together.TogetherClient(
                ioScope,
                clientId = getOrCreateTogetherClientId(),
                bearerToken = togetherToken,
            )
        togetherClient = client
        togetherClock =
            moe.rukamori.archivetune.together
                .TogetherClock()
        togetherSelfParticipantId = null
        togetherLastAppliedQueueHash = null

        togetherClientEventsJob?.cancel()
        togetherClientEventsJob =
            ioScope.launch(SilentHandler) {
                client.events.collect { event ->
                    when (event) {
                        is moe.rukamori.archivetune.together.TogetherClientEvent.Welcome -> {
                            togetherSelfParticipantId = event.welcome.participantId
                            scope.launch(SilentHandler) {
                                val state = togetherSessionState.value
                                if (state is moe.rukamori.archivetune.together.TogetherSessionState.JoiningOnline) {
                                    val selfName = displayName.trim().ifBlank { getString(R.string.together_role_guest) }
                                    val initial =
                                        moe.rukamori.archivetune.together.TogetherRoomState(
                                            sessionId = resolved.sessionId,
                                            hostId = togetherHostId,
                                            participants =
                                                listOf(
                                                    moe.rukamori.archivetune.together.TogetherParticipant(
                                                        id = event.welcome.participantId,
                                                        name = selfName,
                                                        isHost = false,
                                                        isPending = event.welcome.isPending,
                                                        isConnected = true,
                                                    ),
                                                ),
                                            settings = event.welcome.settings,
                                            queue = emptyList(),
                                            queueHash = "",
                                            currentIndex = 0,
                                            isPlaying = false,
                                            positionMs = 0L,
                                            repeatMode = 0,
                                            shuffleEnabled = false,
                                            sentAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
                                        )
                                    togetherSessionState.value =
                                        moe.rukamori.archivetune.together.TogetherSessionState.Joined(
                                            role = moe.rukamori.archivetune.together.TogetherRole.Guest,
                                            sessionId = resolved.sessionId,
                                            selfParticipantId = event.welcome.participantId,
                                            roomState = initial,
                                        )
                                }
                            }
                            startTogetherHeartbeat(resolved.sessionId, client)
                        }

                        is moe.rukamori.archivetune.together.TogetherClientEvent.RoomState -> {
                            applyRemoteRoomState(event.state)
                        }

                        is moe.rukamori.archivetune.together.TogetherClientEvent.HostTransferred -> {
                            handleTogetherClientHostTransferred(event.transfer)
                        }

                        is moe.rukamori.archivetune.together.TogetherClientEvent.ControlRequested -> {
                            val joined =
                                togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
                            if (togetherAuthorityParticipantId == togetherSelfParticipantId &&
                                joined?.roomState?.settings?.allowGuestsToControlPlayback == true
                            ) {
                                applyHostControl(event.request.action)
                            }
                        }

                        is moe.rukamori.archivetune.together.TogetherClientEvent.AddTrackRequested -> {
                            val joined =
                                togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
                            if (togetherAuthorityParticipantId == togetherSelfParticipantId &&
                                joined?.roomState?.settings?.allowGuestsToAddTracks == true
                            ) {
                                applyHostAddTrack(event.request.track, event.request.mode)
                            }
                        }

                        is moe.rukamori.archivetune.together.TogetherClientEvent.JoinDecision -> {
                            if (!event.decision.approved) {
                                scope.launch(SilentHandler) {
                                    togetherSessionState.value =
                                        moe.rukamori.archivetune.together.TogetherSessionState.Error(
                                            message = getString(R.string.not_allowed),
                                            recoverable = true,
                                        )
                                }
                                ioScope.launch(SilentHandler) { stopTogetherInternal() }
                            }
                        }

                        is moe.rukamori.archivetune.together.TogetherClientEvent.ServerIssue -> {
                            Timber.tag("Together").w("server issue (online) code=${event.code.orEmpty()} message=${event.message}")
                            when (event.code) {
                                "GUEST_CONTROL_DISABLED" -> {
                                    showTogetherNotice(event.message, key = "GUEST_CONTROL_DISABLED")
                                    val joined =
                                        togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
                                    if (joined?.role is moe.rukamori.archivetune.together.TogetherRole.Guest) {
                                        togetherPendingGuestControl = null
                                        togetherLastSentControlAction = null
                                        scope.launch(SilentHandler) { applyRemoteRoomState(joined.roomState, force = true) }
                                    }
                                }

                                "GUEST_ADD_DISABLED" -> {
                                    showTogetherNotice(event.message, key = "GUEST_ADD_DISABLED")
                                }

                                "HOST_OFFLINE" -> {
                                    showTogetherNotice(event.message, key = "HOST_OFFLINE")
                                }

                                else -> {
                                    scope.launch(SilentHandler) {
                                        togetherSessionState.value =
                                            moe.rukamori.archivetune.together.TogetherSessionState.Error(
                                                message = event.message,
                                                recoverable = true,
                                            )
                                    }
                                    ioScope.launch(SilentHandler) { stopTogetherInternal() }
                                }
                            }
                        }

                        is moe.rukamori.archivetune.together.TogetherClientEvent.HeartbeatPong -> {
                            val clock = togetherClock ?: return@collect
                            clock.onPong(
                                sentAtElapsedMs = event.pong.clientElapsedRealtimeMs,
                                receivedAtElapsedMs = event.receivedAtElapsedRealtimeMs,
                                serverElapsedMs = event.pong.serverElapsedRealtimeMs,
                            )
                        }

                        is moe.rukamori.archivetune.together.TogetherClientEvent.Error -> {
                            scope.launch(SilentHandler) {
                                togetherSessionState.value =
                                    moe.rukamori.archivetune.together.TogetherSessionState.Error(
                                        message = event.message,
                                        recoverable = true,
                                    )
                            }
                            ioScope.launch(SilentHandler) { stopTogetherInternal() }
                        }

                        moe.rukamori.archivetune.together.TogetherClientEvent.Disconnected -> {
                            val current = togetherSessionState.value
                            if (current is moe.rukamori.archivetune.together.TogetherSessionState.Idle) return@collect
                            scope.launch(SilentHandler) {
                                val currentState = togetherSessionState.value
                                togetherSessionState.value =
                                    moe.rukamori.archivetune.together.TogetherSessionState.Error(
                                        message =
                                            if (currentState is moe.rukamori.archivetune.together.TogetherSessionState.Joined &&
                                                currentState.role is moe.rukamori.archivetune.together.TogetherRole.Guest
                                            ) {
                                                getString(R.string.together_host_left_session)
                                            } else {
                                                getString(R.string.network_unavailable)
                                            },
                                        recoverable = true,
                                    )
                            }
                            ioScope.launch(SilentHandler) { stopTogetherInternal() }
                        }
                    }
                }
            }

        val wsUrl =
            moe.rukamori.archivetune.together.TogetherOnlineEndpoint.onlineWebSocketUrlOrNull(
                rawWsUrl = resolved.wsUrl,
                baseUrl = baseUrl,
            )
        if (wsUrl == null) {
            scope.launch(SilentHandler) {
                togetherSessionState.value =
                    moe.rukamori.archivetune.together.TogetherSessionState.Error(
                        message = "Connection failed: Invalid server websocket URL",
                        recoverable = true,
                    )
            }
            ioScope.launch(SilentHandler) { stopTogetherInternal() }
            return@launch
        }

        client.connect(
            wsUrl = wsUrl,
            sessionId = resolved.sessionId,
            sessionKey = resolved.guestKey,
            displayName = displayName.trim().ifBlank { getString(R.string.together_role_guest) },
        )
    }
}

internal fun MusicService.leaveTogether() {
    ensureScopesActive()
    scope.launch(SilentHandler) {
        togetherSessionState.value = moe.rukamori.archivetune.together.TogetherSessionState.Idle
    }
    ioScope.launch(SilentHandler) { stopTogetherInternal() }
}

internal fun MusicService.updateTogetherSettings(settings: moe.rukamori.archivetune.together.TogetherRoomSettings) {
    val server = togetherServer
    val onlineHost = togetherOnlineHost
    if (server == null && onlineHost == null) return
    ioScope.launch(SilentHandler) {
        server?.updateSettings(settings)
        onlineHost?.updateSettings(settings)
    }
}

internal fun MusicService.approveTogetherParticipant(
    participantId: String,
    approved: Boolean,
) {
    val server = togetherServer
    val onlineHost = togetherOnlineHost
    if (server == null && onlineHost == null) return
    ioScope.launch(SilentHandler) {
        server?.approveParticipant(participantId, approved)
        onlineHost?.approveParticipant(participantId, approved)
    }
}

internal fun MusicService.kickTogetherParticipant(
    participantId: String,
    reason: String? = null,
) {
    val onlineHost = togetherOnlineHost ?: return
    ioScope.launch(SilentHandler) {
        onlineHost.kickParticipant(participantId, reason)
    }
}

internal fun MusicService.banTogetherParticipant(
    participantId: String,
    reason: String? = null,
) {
    val onlineHost = togetherOnlineHost ?: return
    ioScope.launch(SilentHandler) {
        onlineHost.banParticipant(participantId, reason)
    }
}

internal fun MusicService.transferTogetherHostOwnership(participantId: String) {
    val targetId = participantId.trim()
    if (targetId.isBlank() || targetId == togetherHostId || targetId == togetherSelfParticipantId) return
    val server = togetherServer
    val onlineHost = togetherOnlineHost
    val client = togetherClient
    val joined = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
    ioScope.launch(SilentHandler) {
        when {
            server != null -> {
                server.transferHostOwnership(targetId)
            }

            onlineHost != null -> {
                onlineHost.transferHostOwnership(targetId)
            }

            joined?.role is moe.rukamori.archivetune.together.TogetherRole.Host && client != null -> {
                client.transferHostOwnership(joined.sessionId, targetId)
            }
        }
    }
}

internal fun MusicService.requestTogetherControl(action: moe.rukamori.archivetune.together.ControlAction) {
    val client =
        togetherClient ?: run {
            showTogetherNotice(getString(R.string.network_unavailable), key = "TOGETHER_CLIENT_MISSING")
            return
        }
    val state = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined ?: return
    if (state.role !is moe.rukamori.archivetune.together.TogetherRole.Guest) return
    if (!state.roomState.settings.allowGuestsToControlPlayback) {
        Timber.tag("Together").i("control blocked locally (disabled) action=${action::class.java.simpleName}")
        showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_CONTROL_DISABLED_LOCAL")
        return
    }
    val now = android.os.SystemClock.elapsedRealtime()
    val lastAction = togetherLastSentControlAction
    val lastAt = togetherLastSentControlAtElapsedMs
    if (lastAction == action && now - lastAt < 350L) return
    togetherLastSentControlAction = action
    togetherLastSentControlAtElapsedMs = now

    val timeout = if (togetherIsOnlineSession) 5000L else 2000L
    togetherPendingGuestControl =
        when (action) {
            moe.rukamori.archivetune.together.ControlAction.Play -> {
                MusicService.TogetherPendingGuestControl(desiredIsPlaying = true, requestedAtElapsedMs = now, expiresAtElapsedMs = now + timeout)
            }

            moe.rukamori.archivetune.together.ControlAction.Pause -> {
                MusicService.TogetherPendingGuestControl(desiredIsPlaying = false, requestedAtElapsedMs = now, expiresAtElapsedMs = now + timeout)
            }

            is moe.rukamori.archivetune.together.ControlAction.SeekToIndex -> {
                MusicService.TogetherPendingGuestControl(
                    desiredIndex = action.index.coerceAtLeast(0),
                    requestedAtElapsedMs = now,
                    expiresAtElapsedMs =
                        now + timeout,
                )
            }

            is moe.rukamori.archivetune.together.ControlAction.SeekToTrack -> {
                MusicService.TogetherPendingGuestControl(
                    desiredTrackId = action.trackId.trim().ifBlank { null },
                    requestedAtElapsedMs = now,
                    expiresAtElapsedMs = now + timeout,
                )
            }

            else -> {
                togetherPendingGuestControl
            }
        }
    client.requestControl(state.sessionId, action)
}

internal fun MusicService.requestTogetherAddTrack(
    track: moe.rukamori.archivetune.together.TogetherTrack,
    mode: moe.rukamori.archivetune.together.AddTrackMode,
) {
    val client = togetherClient ?: return
    val state = togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined ?: return
    if (state.role !is moe.rukamori.archivetune.together.TogetherRole.Guest) return
    if (!state.roomState.settings.allowGuestsToAddTracks) {
        Timber.tag("Together").i("add blocked locally (disabled) mode=$mode trackId=${track.id}")
        showTogetherNotice(getString(R.string.not_allowed), key = "GUEST_ADD_DISABLED_LOCAL")
        return
    }
    client.requestAddTrack(state.sessionId, track, mode)
}

internal suspend fun MusicService.handleTogetherHostEvent(
    event: moe.rukamori.archivetune.together.TogetherServerEvent,
    currentSettings: suspend () -> moe.rukamori.archivetune.together.TogetherRoomSettings,
) {
    when (event) {
        is moe.rukamori.archivetune.together.TogetherServerEvent.ControlRequested -> {
            val settings = currentSettings()
            if (!settings.allowGuestsToControlPlayback) return
            applyHostControl(event.request.action)
        }

        is moe.rukamori.archivetune.together.TogetherServerEvent.AddTrackRequested -> {
            val settings = currentSettings()
            if (!settings.allowGuestsToAddTracks) return
            applyHostAddTrack(event.request.track, event.request.mode)
        }

        is moe.rukamori.archivetune.together.TogetherServerEvent.ParticipantJoined -> {
            val participant = event.participant
            if (!participant.isHost && !participant.isPending) {
                togetherParticipantNames[participant.id] = participant.name
                showTogetherParticipantNotification(participant.name, joined = true)
            }
        }

        is moe.rukamori.archivetune.together.TogetherServerEvent.ParticipantLeft -> {
            val participantName =
                togetherParticipantNames.remove(event.participantId)
                    ?: return
            showTogetherParticipantNotification(participantName, joined = false)
        }

        is moe.rukamori.archivetune.together.TogetherServerEvent.HostTransferred -> {
            handleTogetherHostTransferred(event.participantId)
        }

        is moe.rukamori.archivetune.together.TogetherServerEvent.RoomStateReceived -> {
            if (event.state.hostId != togetherHostId) {
                togetherSelfParticipantId = togetherHostId
                applyRemoteRoomState(event.state, force = true)
            }
        }

        is moe.rukamori.archivetune.together.TogetherServerEvent.Error -> {
            val current = togetherSessionState.value
            if (current is moe.rukamori.archivetune.together.TogetherSessionState.Idle) return
            togetherSessionState.value =
                moe.rukamori.archivetune.together.TogetherSessionState.Error(
                    message = event.message,
                    recoverable = true,
                )
            ioScope.launch(SilentHandler) { stopTogetherInternal() }
        }

        else -> {
            Unit
        }
    }
}

internal suspend fun MusicService.applyHostControl(action: moe.rukamori.archivetune.together.ControlAction) {
    withContext(Dispatchers.Main) {
        when (action) {
            moe.rukamori.archivetune.together.ControlAction.Play -> {
                if (!player.playWhenReady) {
                    player.prepare()
                    player.playWhenReady = true
                }
            }

            moe.rukamori.archivetune.together.ControlAction.Pause -> {
                if (player.playWhenReady) {
                    player.playWhenReady = false
                }
            }

            is moe.rukamori.archivetune.together.ControlAction.SeekTo -> {
                player.seekTo(action.positionMs.coerceAtLeast(0L))
                player.prepare()
            }

            moe.rukamori.archivetune.together.ControlAction.SkipNext -> {
                if (player.hasNextMediaItem()) {
                    player.seekToNext()
                    player.prepare()
                    player.playWhenReady = true
                }
            }

            moe.rukamori.archivetune.together.ControlAction.SkipPrevious -> {
                if (player.hasPreviousMediaItem()) {
                    player.seekToPrevious()
                    player.prepare()
                    player.playWhenReady = true
                }
            }

            is moe.rukamori.archivetune.together.ControlAction.SeekToTrack -> {
                val trackId = action.trackId.trim()
                if (trackId.isNotBlank()) {
                    val idx =
                        player.mediaItems.indexOfFirst {
                            val metaId = it.metadata?.id
                            it.mediaId == trackId || metaId == trackId
                        }
                    if (idx >= 0 && idx < player.mediaItemCount) {
                        player.seekTo(idx, action.positionMs.coerceAtLeast(0L))
                        player.prepare()
                    }
                }
            }

            is moe.rukamori.archivetune.together.ControlAction.SeekToIndex -> {
                val idx = action.index.coerceAtLeast(0)
                if (idx < player.mediaItemCount) {
                    player.seekTo(idx, action.positionMs.coerceAtLeast(0L))
                    player.prepare()
                }
            }

            is moe.rukamori.archivetune.together.ControlAction.SetRepeatMode -> {
                if (player.repeatMode != action.repeatMode) {
                    player.repeatMode = action.repeatMode
                }
            }

            is moe.rukamori.archivetune.together.ControlAction.SetShuffleEnabled -> {
                if (player.shuffleModeEnabled != action.shuffleEnabled) {
                    player.shuffleModeEnabled = action.shuffleEnabled
                }
            }
        }
    }
}

internal suspend fun MusicService.applyHostAddTrack(
    track: moe.rukamori.archivetune.together.TogetherTrack,
    mode: moe.rukamori.archivetune.together.AddTrackMode,
) {
    val mediaItem = track.toMediaMetadata().toMediaItem()
    withContext(Dispatchers.Main) {
        when (mode) {
            moe.rukamori.archivetune.together.AddTrackMode.PLAY_NEXT -> playNext(listOf(mediaItem))
            moe.rukamori.archivetune.together.AddTrackMode.ADD_TO_QUEUE -> addToQueue(listOf(mediaItem))
        }
    }
}

internal suspend fun MusicService.buildTogetherRoomState(
    sessionId: String,
    hostId: String,
): moe.rukamori.archivetune.together.TogetherRoomState =
    withContext(Dispatchers.Main) {
        val tracks =
            player.mediaItems.mapNotNull { it.metadata }.map { meta ->
                moe.rukamori.archivetune.together.TogetherTrack(
                    id = meta.id,
                    title = meta.title,
                    artists = meta.artists.map { it.name },
                    durationSec = meta.duration,
                    thumbnailUrl = meta.thumbnailUrl,
                )
            }

        val queueHash =
            moe.rukamori.archivetune.utils
                .md5(tracks.joinToString(separator = "|") { it.id })

        moe.rukamori.archivetune.together.TogetherRoomState(
            sessionId = sessionId,
            hostId = hostId,
            settings =
                moe.rukamori.archivetune.together
                    .TogetherRoomSettings(),
            participants = emptyList(),
            queue = tracks,
            queueHash = queueHash,
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            isPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            repeatMode = player.repeatMode,
            shuffleEnabled = player.shuffleModeEnabled,
            sentAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
        )
    }

internal fun MusicService.markTogetherHostParticipant(
    state: moe.rukamori.archivetune.together.TogetherRoomState,
    hostId: String,
): moe.rukamori.archivetune.together.TogetherRoomState =
    state.copy(
        hostId = hostId,
        participants =
            state.participants.map { participant ->
                participant.copy(isHost = participant.id == hostId)
            },
    )

internal fun MusicService.handleTogetherHostTransferred(participantId: String) {
    togetherAuthorityParticipantId = participantId
    if (participantId != togetherHostId) {
        togetherSelfParticipantId = togetherHostId
    }
    scope.launch(SilentHandler) {
        when (val current = togetherSessionState.value) {
            is moe.rukamori.archivetune.together.TogetherSessionState.Hosting -> {
                val roomState = current.roomState?.let { markTogetherHostParticipant(it, participantId) }
                togetherSessionState.value =
                    moe.rukamori.archivetune.together.TogetherSessionState.Joined(
                        role =
                            if (participantId == togetherHostId) {
                                moe.rukamori.archivetune.together.TogetherRole.Host
                            } else {
                                moe.rukamori.archivetune.together.TogetherRole.Guest
                            },
                        sessionId = current.sessionId,
                        selfParticipantId = togetherHostId,
                        roomState =
                            roomState
                                ?: moe.rukamori.archivetune.together.TogetherRoomState(
                                    sessionId = current.sessionId,
                                    hostId = participantId,
                                ),
                    )
            }

            is moe.rukamori.archivetune.together.TogetherSessionState.HostingOnline -> {
                val roomState = current.roomState?.let { markTogetherHostParticipant(it, participantId) }
                togetherSessionState.value =
                    moe.rukamori.archivetune.together.TogetherSessionState.Joined(
                        role =
                            if (participantId == togetherHostId) {
                                moe.rukamori.archivetune.together.TogetherRole.Host
                            } else {
                                moe.rukamori.archivetune.together.TogetherRole.Guest
                            },
                        sessionId = current.sessionId,
                        selfParticipantId = togetherHostId,
                        roomState =
                            roomState
                                ?: moe.rukamori.archivetune.together.TogetherRoomState(
                                    sessionId = current.sessionId,
                                    hostId = participantId,
                                ),
                    )
            }

            is moe.rukamori.archivetune.together.TogetherSessionState.Joined -> {
                togetherSessionState.value =
                    current.copy(
                        role =
                            if (current.selfParticipantId == participantId) {
                                moe.rukamori.archivetune.together.TogetherRole.Host
                            } else {
                                moe.rukamori.archivetune.together.TogetherRole.Guest
                            },
                        roomState = markTogetherHostParticipant(current.roomState, participantId),
                    )
            }

            else -> {
                Unit
            }
        }
    }
}

internal fun MusicService.handleTogetherClientHostTransferred(transfer: moe.rukamori.archivetune.together.HostTransferred) {
    val participantId = transfer.participantId
    handleTogetherHostTransferred(participantId)
    val client = togetherClient ?: return
    if (participantId != togetherSelfParticipantId) return
    startTogetherAuthorityBroadcast(transfer.sessionId, participantId, client)
}

internal fun MusicService.startTogetherAuthorityBroadcast(
    sessionId: String,
    participantId: String,
    client: moe.rukamori.archivetune.together.TogetherClient,
) {
    togetherBroadcastJob?.cancel()
    togetherBroadcastJob =
        ioScope.launch(SilentHandler) {
            while (togetherClient === client && togetherAuthorityParticipantId == participantId) {
                val state = buildTogetherRoomState(sessionId = sessionId, hostId = participantId)
                client.sendRoomState(state)
                kotlinx.coroutines.delay(TogetherPlaybackSync.BroadcastIntervalMs)
            }
        }
}

internal suspend fun MusicService.applyRemoteRoomState(
    state: moe.rukamori.archivetune.together.TogetherRoomState,
    force: Boolean = false,
) {
    val pid = togetherSelfParticipantId ?: return
    val now = android.os.SystemClock.elapsedRealtime()

    val pending = togetherPendingGuestControl
    if (force) {
        togetherPendingGuestControl = null
    } else if (pending != null) {
        val currentTrackId = state.queue.getOrNull(state.currentIndex.coerceAtLeast(0))?.id
        val mismatch =
            (pending.desiredIsPlaying != null && state.isPlaying != pending.desiredIsPlaying) ||
                (pending.desiredIndex != null && state.currentIndex != pending.desiredIndex) ||
                (pending.desiredTrackId != null && currentTrackId != pending.desiredTrackId)
        if (now >= pending.expiresAtElapsedMs) {
            if ((pending.desiredIndex != null || pending.desiredTrackId != null) &&
                now - pending.requestedAtElapsedMs >= 1200L &&
                mismatch
            ) {
                showTogetherNotice(getString(R.string.together_song_change_failed), key = "GUEST_SEEK_TIMEOUT")
            }
            togetherPendingGuestControl = null
        } else {
            if (mismatch) return
            togetherPendingGuestControl = null
        }
    }

    val sentAt = state.sentAtElapsedRealtimeMs
    if (TogetherPlaybackSync.isStaleRoomState(
            sentAtElapsedRealtimeMs = sentAt,
            lastAppliedSentAtElapsedRealtimeMs = togetherLastAppliedRoomStateSentAtElapsedMs,
            force = force,
        )
    ) {
        return
    }

    val targetPos =
        TogetherPlaybackSync.targetPositionMs(
            state = state,
            isOnlineSession = togetherIsOnlineSession,
            clockSnapshot = if (togetherIsOnlineSession) null else togetherClock?.snapshot(),
            nowElapsedRealtimeMs = now,
        )

    withContext(Dispatchers.Main) {
        togetherApplyingRemote = true
        togetherSuppressEchoUntilElapsedMs =
            TogetherPlaybackSync.echoSuppressionUntil(
                android.os.SystemClock.elapsedRealtime(),
            )
        try {
            val desiredItems = state.queue.map { it.toMediaMetadata().toMediaItem() }
            val desiredIds = state.queue.map { it.id }
            val desiredHash = state.queueHash
            val localIds = player.mediaItems.mapNotNull { it.metadata?.id ?: it.mediaId }.filter { it.isNotBlank() }
            val localHash =
                if (localIds.isEmpty()) {
                    ""
                } else {
                    moe.rukamori.archivetune.utils
                        .md5(localIds.joinToString(separator = "|"))
                }
            val needsRebuild =
                TogetherPlaybackSync.needsQueueRebuild(
                    desiredHash = desiredHash,
                    desiredIds = desiredIds,
                    localHash = localHash,
                    localIds = localIds,
                )

            if (desiredItems.isNotEmpty() && needsRebuild) {
                togetherLastAppliedQueueHash = desiredHash.ifBlank { localHash }
                val startIndex = state.currentIndex.coerceIn(0, desiredItems.lastIndex)
                suppressAutoPlayback = false
                currentQueue =
                    moe.rukamori.archivetune.playback.queues.ListQueue(
                        title = getString(R.string.music_player),
                        items = desiredItems,
                        startIndex = startIndex,
                        position = targetPos,
                    )
                queueTitle = null
                player.setMediaItems(desiredItems, startIndex, targetPos)
                player.prepare()
                player.repeatMode = state.repeatMode
                player.shuffleModeEnabled = state.shuffleEnabled
                player.playWhenReady = state.isPlaying
                togetherLastRemoteAppliedIndex = startIndex
            } else {
                val index =
                    if (player.mediaItemCount > 0) {
                        state.currentIndex.coerceIn(0, player.mediaItemCount - 1)
                    } else {
                        0
                    }
                val indexChanged = player.mediaItemCount > 0 && index != player.currentMediaItemIndex

                if (indexChanged) {
                    if (player.repeatMode != state.repeatMode) player.repeatMode = state.repeatMode
                    if (player.shuffleModeEnabled != state.shuffleEnabled) player.shuffleModeEnabled = state.shuffleEnabled
                    player.seekTo(index, targetPos)
                    player.prepare()
                    player.playWhenReady = state.isPlaying
                } else {
                    val playbackStateChanged = player.playWhenReady != state.isPlaying
                    if (player.repeatMode != state.repeatMode) player.repeatMode = state.repeatMode
                    if (player.shuffleModeEnabled != state.shuffleEnabled) player.shuffleModeEnabled = state.shuffleEnabled
                    if (playbackStateChanged) player.playWhenReady = state.isPlaying
                    val shouldSeekForDrift =
                        TogetherPlaybackSync.shouldSeekForDrift(
                            currentPositionMs = player.currentPosition,
                            targetPositionMs = targetPos,
                            isPlaying = state.isPlaying,
                            isOnlineSession = togetherIsOnlineSession,
                        )
                    if (shouldSeekForDrift || (playbackStateChanged && !state.isPlaying)) {
                        player.seekTo(targetPos)
                        player.prepare()
                    }
                }
                togetherLastRemoteAppliedIndex = index
            }
            togetherLastRemoteAppliedPlayWhenReady = state.isPlaying
            togetherLastAppliedRoomStateSentAtElapsedMs = sentAt

            togetherSessionState.value =
                moe.rukamori.archivetune.together.TogetherSessionState.Joined(
                    role = moe.rukamori.archivetune.together.TogetherRole.Guest,
                    sessionId = state.sessionId,
                    selfParticipantId = pid,
                    roomState = state,
                )
        } finally {
            togetherApplyingRemote = false
        }
    }
}

internal fun MusicService.startTogetherHeartbeat(
    sessionId: String,
    client: moe.rukamori.archivetune.together.TogetherClient,
) {
    togetherHeartbeatJob?.cancel()
    togetherHeartbeatJob =
        ioScope.launch(SilentHandler) {
            var pingId = 0L
            while (togetherClient === client) {
                val now = android.os.SystemClock.elapsedRealtime()
                client.sendHeartbeat(sessionId = sessionId, pingId = pingId++, clientElapsedRealtimeMs = now)
                kotlinx.coroutines.delay(2000)
            }
        }
}

internal suspend fun MusicService.stopTogetherInternal() {
    togetherBroadcastJob?.cancel()
    togetherBroadcastJob = null

    togetherOnlineConnectJob?.cancel()
    togetherOnlineConnectJob = null

    togetherClientEventsJob?.cancel()
    togetherClientEventsJob = null

    togetherHeartbeatJob?.cancel()
    togetherHeartbeatJob = null

    togetherClock = null
    togetherSelfParticipantId = null
    togetherAuthorityParticipantId = null
    togetherParticipantNames.clear()
    togetherLastAppliedQueueHash = null
    togetherIsOnlineSession = false
    togetherApplyingRemote = false
    togetherSuppressEchoUntilElapsedMs = 0L
    togetherLastAppliedRoomStateSentAtElapsedMs = 0L
    togetherLastRemoteAppliedPlayWhenReady = null
    togetherLastRemoteAppliedIndex = -1
    togetherLastSentControlAtElapsedMs = 0L
    togetherLastSentControlAction = null
    togetherPendingGuestControl = null

    try {
        togetherClient?.disconnect()
    } catch (_: Exception) {
    }
    togetherClient = null

    try {
        togetherOnlineHost?.disconnect()
    } catch (_: Exception) {
    }
    togetherOnlineHost = null

    try {
        togetherServer?.stop()
    } catch (_: Exception) {
    }
    togetherServer = null
}

internal fun moe.rukamori.archivetune.together.TogetherTrack.toMediaMetadata(): moe.rukamori.archivetune.models.MediaMetadata =
    moe.rukamori.archivetune.models.MediaMetadata(
        id = id,
        title = title,
        artists =
            artists.map { name ->
                moe.rukamori.archivetune.models.MediaMetadata
                    .Artist(id = null, name = name)
            },
        duration = durationSec,
        thumbnailUrl = thumbnailUrl,
        album = null,
        setVideoId = null,
        explicit = false,
        liked = false,
        likedDate = null,
        inLibrary = null,
    )

internal fun MusicService.getLocalIpv4Address(): String? =
    runCatching {
        java.net.NetworkInterface
            .getNetworkInterfaces()
            .toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<java.net.Inet4Address>()
            .map { it.hostAddress }
            .firstOrNull { it?.isNotBlank() == true && it != "127.0.0.1" }
    }.getOrNull()
