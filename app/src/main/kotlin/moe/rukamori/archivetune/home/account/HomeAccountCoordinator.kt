/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.home.account

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.auth.SwitchSavedYouTubeAccountUseCase
import moe.rukamori.archivetune.constants.AccountChannelHandleKey
import moe.rukamori.archivetune.constants.AccountEmailKey
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.DataSyncIdKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.home.state.AccountChannelCollection
import moe.rukamori.archivetune.home.state.AccountChannelUiModel
import moe.rukamori.archivetune.home.state.AccountChannelsState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AccountChannel
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.withHighResAvatar
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.utils.SavedAccount
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.utils.toPlaybackAuthState
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeAccountCoordinator
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val syncUtils: SyncUtils,
        private val switchSavedYouTubeAccount: SwitchSavedYouTubeAccountUseCase,
    ) {
        private val _accountName = MutableStateFlow("")
        val accountName: StateFlow<String> = _accountName.asStateFlow()

        private val _accountImageUrl = MutableStateFlow<String?>(null)
        val accountImageUrl: StateFlow<String?> = _accountImageUrl.asStateFlow()

        private val _accountChannelsState = MutableStateFlow<AccountChannelsState>(AccountChannelsState.Empty)
        val accountChannelsState: StateFlow<AccountChannelsState> = _accountChannelsState.asStateFlow()

        private val _accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
        val accountPlaylists: StateFlow<List<PlaylistItem>?> = _accountPlaylists.asStateFlow()

        private val accentColorObserver = HomeAccountAccentColorObserver(context, accountImageUrl)
        val accountAccentColor: StateFlow<Color?> = accentColorObserver.accountAccentColor

        fun clearAccountData() {
            _accountName.value = ""
            _accountImageUrl.value = null
            _accountPlaylists.value = null
            _accountChannelsState.value = AccountChannelsState.Empty
            accentColorObserver.clear()
        }

        private fun prepareYouTubeAccount(cookie: String): Boolean =
            try {
                YouTube.cookie = cookie
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to set YouTube cookie")
                false
            }

        suspend fun refreshIdentity() =
            withContext(Dispatchers.IO) {
                _accountName.value = ""
                _accountImageUrl.value = null
                _accountChannelsState.value = AccountChannelsState.Loading

                try {
                    YouTube
                        .accountInfo()
                        .onSuccess { info ->
                            _accountName.value = info.name
                            _accountImageUrl.value = info.thumbnailUrl?.withHighResAvatar()
                        }.onFailure { error ->
                            Timber.w(error, "Failed to fetch account info")
                        }

                    YouTube
                        .accountChannels()
                        .onSuccess { channels ->
                            _accountChannelsState.value =
                                channels
                                    .map { it.toUiModel() }
                                    .takeIf { it.size > 1 }
                                    ?.let { AccountChannelsState.Success(AccountChannelCollection(it)) }
                                    ?: AccountChannelsState.Empty
                        }.onFailure { error ->
                            Timber.w(error, "Failed to fetch account channels")
                            reportException(error)
                            _accountChannelsState.value = AccountChannelsState.Error(error.message.orEmpty())
                        }
                } catch (e: CancellationException) {
                    _accountChannelsState.value = AccountChannelsState.Empty
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Exception fetching account info")
                    reportException(e)
                    _accountChannelsState.value = AccountChannelsState.Error(e.message.orEmpty())
                }
            }

        private fun AccountChannel.toUiModel(): AccountChannelUiModel =
            AccountChannelUiModel(
                name = name,
                byline = byline.orEmpty(),
                channelHandle = channelHandle.orEmpty(),
                thumbnailUrl = thumbnailUrl,
                dataSyncId = dataSyncId,
                isSelected = isSelected,
            )

        suspend fun refreshPlaylists() =
            withContext(Dispatchers.IO) {
                try {
                    YouTube
                        .library("FEmusic_liked_playlists")
                        .completed()
                        .onSuccess {
                            val lists =
                                it.items.filterIsInstance<PlaylistItem>().filterNot { playlist ->
                                    playlist.id == "SE"
                                }
                            _accountPlaylists.value = lists
                        }.onFailure { error ->
                            if (error is CancellationException) throw error
                            Timber.w(error, "Failed to fetch account playlists")
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Exception fetching account playlists")
                }
            }

        suspend fun switchAccount(
            account: SavedAccount,
            forceSyncOnSwitch: Boolean,
        ) = withContext(Dispatchers.IO) {
            try {
                val authState = switchSavedYouTubeAccount(account).getOrThrow()

                if (forceSyncOnSwitch && account.ytmSync && authState.hasLoginCookie) {
                    syncUtils.performFullSync(authoritative = true)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error switching account")
                reportException(e)
            }
        }

        suspend fun switchChannel(
            channel: AccountChannelUiModel,
            forceSyncOnSwitch: Boolean,
        ) = withContext(Dispatchers.IO) {
            if (channel.dataSyncId.isBlank()) return@withContext

            try {
                _accountChannelsState.value = AccountChannelsState.Loading

                context.dataStore.edit { preferences ->
                    preferences[DataSyncIdKey] = channel.dataSyncId
                    preferences[AccountNameKey] = channel.name
                    preferences[AccountChannelHandleKey] = channel.channelHandle
                    if (channel.byline.contains("@")) {
                        preferences[AccountEmailKey] = channel.byline
                    }
                }

                val authState =
                    context.dataStore.data
                        .first()
                        .toPlaybackAuthState()
                YouTube.authState = authState

                supervisorScope {
                    launch { refreshIdentity() }
                    launch { refreshPlaylists() }
                }

                if (forceSyncOnSwitch && context.dataStore.get(YtmSyncKey, true) && authState.hasLoginCookie) {
                    syncUtils.performFullSync(authoritative = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error switching account channel")
                reportException(e)
                _accountChannelsState.value = AccountChannelsState.Error(e.message.orEmpty())
            }
        }

        private val observerLock = Any()
        private var activeObserverJob: Job? = null
        private var activeScope: CoroutineScope? = null
        private val activeSubscribers = mutableMapOf<Any, SubscriberCallbacks>()
        private var wasPreviouslyLoggedIn = false
        private var isInitialEmission = true

        private class SubscriberCallbacks(
            val scope: CoroutineScope,
            val onAuthSuccess: suspend () -> Unit,
            val onAuthCleared: () -> Unit,
        )

        fun observeCookies(
            scope: CoroutineScope,
            onAuthSuccess: suspend () -> Unit = {},
            onAuthCleared: () -> Unit = {},
        ) {
            val callerJob = scope.coroutineContext[Job]
            if (callerJob != null && !callerJob.isActive) {
                return
            }

            val callerKey: Any = callerJob ?: scope
            synchronized(observerLock) {
                if (callerJob != null) {
                    if (!activeSubscribers.containsKey(callerJob)) {
                        callerJob.invokeOnCompletion {
                            synchronized(observerLock) {
                                activeSubscribers.remove(callerJob)
                                if (activeScope == scope) {
                                    activeObserverJob?.cancel()
                                    activeObserverJob = null
                                    activeScope = null
                                    val next = activeSubscribers.entries.firstOrNull { it.value.scope.isActive }?.value
                                    if (next != null) {
                                        startObserverLocked(next.scope)
                                    }
                                }
                            }
                        }
                    }
                }
                activeSubscribers[callerKey] = SubscriberCallbacks(scope, onAuthSuccess, onAuthCleared)

                if (activeObserverJob?.isActive != true) {
                    startObserverLocked(scope)
                }
            }
        }

        private fun startObserverLocked(scope: CoroutineScope) {
            activeScope = scope
            activeObserverJob =
                scope.launch(Dispatchers.IO) {
                    context.dataStore.data
                        .map { it[InnerTubeCookieKey] }
                        .distinctUntilChanged()
                        .collect { cookie ->
                            try {
                                val isLoggedIn = hasYouTubeLoginCookie(cookie)
                                val loginTransition = !isInitialEmission && isLoggedIn && !wasPreviouslyLoggedIn
                                wasPreviouslyLoggedIn = isLoggedIn
                                isInitialEmission = false

                                if (isLoggedIn && !cookie.isNullOrEmpty()) {
                                    if (!prepareYouTubeAccount(cookie)) {
                                        clearAccountData()
                                        notifyAuthCleared()
                                        return@collect
                                    }

                                    supervisorScope {
                                        delay(100)
                                        launch { refreshIdentity() }
                                        launch { refreshPlaylists() }
                                    }

                                    if (loginTransition) {
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                if (context.dataStore.get(YtmSyncKey, true)) {
                                                    syncUtils.performFullSync()
                                                }
                                            } catch (e: Exception) {
                                                Timber.e(e, "Error during login-triggered sync")
                                                reportException(e)
                                            }
                                        }
                                    }
                                    notifyAuthSuccess()
                                } else {
                                    clearAccountData()
                                    notifyAuthCleared()
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.e(e, "Error processing cookie change")
                                clearAccountData()
                                notifyAuthCleared()
                            }
                        }
                }
        }

        private suspend fun notifyAuthSuccess() {
            val active = synchronized(observerLock) {
                activeSubscribers.values.filter { it.scope.isActive }
            }
            for (cb in active) {
                try {
                    cb.onAuthSuccess()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error in onAuthSuccess callback")
                }
            }
        }

        private fun notifyAuthCleared() {
            val active = synchronized(observerLock) {
                activeSubscribers.values.filter { it.scope.isActive }
            }
            for (cb in active) {
                try {
                    cb.onAuthCleared()
                } catch (e: Exception) {
                    Timber.e(e, "Error in onAuthCleared callback")
                }
            }
        }

        fun observeAccentColor(scope: CoroutineScope) {
            accentColorObserver.observe(scope)
        }
    }
