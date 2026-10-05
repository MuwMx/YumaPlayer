/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:Suppress("DEPRECATION")

package moe.rukamori.archivetune.playback.host

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.constants.AudioQualityKey
import moe.rukamori.archivetune.constants.MemoryCacheToggleKey
import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.constants.PlayerStreamClientKey
import moe.rukamori.archivetune.constants.TogetherClientIdKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.EqSettings
import moe.rukamori.archivetune.together.ControlAction
import moe.rukamori.archivetune.together.TogetherClient
import moe.rukamori.archivetune.together.TogetherClock
import moe.rukamori.archivetune.together.TogetherOnlineHost
import moe.rukamori.archivetune.together.TogetherRole
import moe.rukamori.archivetune.together.TogetherServer
import moe.rukamori.archivetune.together.TogetherSessionState
import moe.rukamori.archivetune.utils.enumPreference
import moe.rukamori.archivetune.utils.getAsync
import moe.rukamori.archivetune.utils.preference
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceConfigCollector(
    private val context: Context,
    private val scope: CoroutineScope,
    private val database: MusicDatabase,
    private val dataStore: DataStore<Preferences>,
) {
    val audioQuality by enumPreference(
        context,
        AudioQualityKey,
        AudioQuality.AUTO,
    )
    val preferredStreamClient by enumPreference(
        context,
        PlayerStreamClientKey,
        PlayerStreamClient.ANDROID_VR,
    )
    val enableMemoryCache by preference(
        context,
        MemoryCacheToggleKey,
        true,
    )

    val currentMediaMetadata = MutableStateFlow<MediaMetadata?>(null)

    val currentSong: StateFlow<Song?> =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.song(mediaMetadata?.id)
            }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Lazily, null)

    val currentFormat: Flow<FormatEntity?> =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.format(mediaMetadata?.id)
            }.flowOn(Dispatchers.IO)

    val desiredEqSettings =
        MutableStateFlow(
            EqSettings(
                enabled = false,
                bandLevelsMb = emptyList(),
                outputGainEnabled = false,
                outputGainMb = 0,
                bassBoostEnabled = false,
                bassBoostStrength = 0,
                virtualizerEnabled = false,
                virtualizerStrength = 0,
            ),
        )

    val togetherSessionState =
        MutableStateFlow<TogetherSessionState>(
            TogetherSessionState.Idle,
        )
    var togetherServer: TogetherServer? = null
    var togetherOnlineHost: TogetherOnlineHost? = null
    var togetherClient: TogetherClient? = null
    var togetherBroadcastJob: Job? = null
    var togetherOnlineConnectJob: Job? = null
    var togetherClientEventsJob: Job? = null
    var togetherHeartbeatJob: Job? = null
    var togetherClock: TogetherClock? = null
    var togetherSelfParticipantId: String? = null
    var togetherAuthorityParticipantId: String? = null
    var togetherLastAppliedQueueHash: String? = null
    var togetherIsOnlineSession: Boolean = false

    @Volatile
    var togetherApplyingRemote: Boolean = false

    @Volatile
    var togetherSuppressEchoUntilElapsedMs: Long = 0L

    @Volatile
    var togetherLastAppliedRoomStateSentAtElapsedMs: Long = 0L

    @Volatile
    var togetherLastRemoteAppliedPlayWhenReady: Boolean? = null

    @Volatile
    var togetherLastRemoteAppliedIndex: Int = -1

    @Volatile
    var togetherLastSentControlAtElapsedMs: Long = 0L

    @Volatile
    var togetherLastSentControlAction: ControlAction? = null

    val togetherHostId: String = "host"

    fun isTogetherApplyingRemote(): Boolean = togetherApplyingRemote

    fun isTogetherHostSessionActive(): Boolean {
        val state = togetherSessionState.value
        return state is TogetherSessionState.Hosting ||
            state is TogetherSessionState.HostingOnline ||
            (
                state is TogetherSessionState.Joined &&
                    state.role is TogetherRole.Host
            )
    }

    fun isTogetherSessionIdle(): Boolean =
        togetherSessionState.value is TogetherSessionState.Idle

    fun resetTogetherSessionState() {
        togetherSessionState.value = TogetherSessionState.Idle
    }

    suspend fun getOrCreateTogetherClientId(): String {
        val existing = dataStore.getAsync(TogetherClientIdKey)?.trim().orEmpty()
        if (existing.isNotBlank()) return existing
        val generated = UUID.randomUUID().toString()
        dataStore.edit { prefs -> prefs[TogetherClientIdKey] = generated }
        return generated
    }
}
