/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.home

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import moe.rukamori.archivetune.auth.SwitchSavedYouTubeAccountUseCase
import moe.rukamori.archivetune.constants.SpeedDialSongIdsKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.home.FilterCommunityPreviewsUseCase
import moe.rukamori.archivetune.home.HomeAction
import moe.rukamori.archivetune.home.HomeScreenState
import moe.rukamori.archivetune.home.LoadCommunityPlaylistPreviewsUseCase
import moe.rukamori.archivetune.home.ObserveHomePresentationPreferencesUseCase
import moe.rukamori.archivetune.home.PrepareCommunityHomePageUseCase
import moe.rukamori.archivetune.home.account.HomeAccountCoordinator
import moe.rukamori.archivetune.home.feed.HomeFeedCache
import moe.rukamori.archivetune.home.feed.HomeFeedCoordinator
import moe.rukamori.archivetune.home.quickpicks.HomeQuickPicksManager
import moe.rukamori.archivetune.home.recommendations.HomeLocalRecommendationsUseCase
import moe.rukamori.archivetune.home.recommendations.HomeSimilarRecommendationsUseCase
import moe.rukamori.archivetune.home.state.AccountChannelUiModel
import moe.rukamori.archivetune.home.state.AccountChannelsState
import moe.rukamori.archivetune.home.state.HomeStateComposer
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.models.SimilarRecommendation
import moe.rukamori.archivetune.utils.SavedAccount
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.reportException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class HomeViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val syncUtils: SyncUtils,
    observeHomePresentationPreferences: ObserveHomePresentationPreferencesUseCase,
    private val quickPicksManager: HomeQuickPicksManager,
    private val localRecommendations: HomeLocalRecommendationsUseCase,
    private val similarRecommendationsUseCase: HomeSimilarRecommendationsUseCase,
    private val accountCoordinator: HomeAccountCoordinator,
    private val feedCoordinator: HomeFeedCoordinator,
    private val stateComposer: HomeStateComposer,
    private val feedCache: HomeFeedCache,
) : ViewModel() {
    constructor(
        context: Context,
        database: MusicDatabase,
        syncUtils: SyncUtils,
        switchSavedYouTubeAccount: SwitchSavedYouTubeAccountUseCase,
        observeHomePresentationPreferences: ObserveHomePresentationPreferencesUseCase,
        loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
        filterAiContent: FilterAiContentUseCase,
        prepareCommunityHomePage: PrepareCommunityHomePageUseCase,
        loadCommunityPlaylistPreviews: LoadCommunityPlaylistPreviewsUseCase,
        filterCommunityPreviews: FilterCommunityPreviewsUseCase,
        feedCache: HomeFeedCache = HomeFeedCache(context),
    ) : this(
        context = context,
        syncUtils = syncUtils,
        observeHomePresentationPreferences = observeHomePresentationPreferences,
        quickPicksManager = HomeQuickPicksManager(context, database),
        localRecommendations = HomeLocalRecommendationsUseCase(context, database),
        similarRecommendationsUseCase = HomeSimilarRecommendationsUseCase(context, database, loadAiContentFilterPolicy, filterAiContent),
        accountCoordinator = HomeAccountCoordinator(context, syncUtils, switchSavedYouTubeAccount),
        feedCoordinator = HomeFeedCoordinator(
            context = context,
            database = database,
            loadAiContentFilterPolicy = loadAiContentFilterPolicy,
            filterAiContent = filterAiContent,
            prepareCommunityHomePage = prepareCommunityHomePage,
            loadCommunityPlaylistPreviews = loadCommunityPlaylistPreviews,
            filterCommunityPreviews = filterCommunityPreviews,
        ),
        stateComposer = HomeStateComposer(),
        feedCache = feedCache,
    )

    private val isRefreshing = MutableStateFlow(false)
    private val isLoading = MutableStateFlow(false)
    private val isInitialLoadComplete = MutableStateFlow(false)
    private val loadError = MutableStateFlow<Int?>(null)
    private val loadMutex = Mutex()
    private val accountMutex = Mutex()
    private val stateLock = Any()
    private val activeAccountGeneration = AtomicLong(0L)
    private val readyAccountGeneration = AtomicLong(0L)
    @Volatile
    private var reloadRequested = false
    private var cacheHydrationJob: Job? = null

    private val quickPicks = quickPicksManager.observe(viewModelScope)
    private val speedDialItems = MutableStateFlow<List<LocalItem>>(emptyList())
    private val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    private val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    private val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)

    val communitySection: StateFlow<HomePage.Section?> = feedCoordinator.communitySection

    private val _allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allLocalItems: StateFlow<List<LocalItem>> = _allLocalItems.asStateFlow()
    private val _allYtItems = MutableStateFlow<List<YTItem>>(emptyList())
    val allYtItems: StateFlow<List<YTItem>> = _allYtItems.asStateFlow()

    val accountName: StateFlow<String> = accountCoordinator.accountName
    val accountImageUrl: StateFlow<String?> = accountCoordinator.accountImageUrl
    val accountChannelsState: StateFlow<AccountChannelsState> = accountCoordinator.accountChannelsState
    val accountAccentColor: StateFlow<Color?> = accountCoordinator.accountAccentColor

    private val presentationPreferences = observeHomePresentationPreferences()

    val screenState: StateFlow<HomeScreenState> =
        stateComposer.compose(
            scope = viewModelScope,
            quickPicks = quickPicks,
            speedDialItems = speedDialItems,
            forgottenFavorites = forgottenFavorites,
            keepListening = keepListening,
            homePage = feedCoordinator.homePage,
            similarRecommendations = similarRecommendations,
            accountPlaylists = accountCoordinator.accountPlaylists,
            accountName = accountCoordinator.accountName,
            accountImageUrl = accountCoordinator.accountImageUrl,
            communitySection = feedCoordinator.communitySection,
            selectedChip = feedCoordinator.selectedChip,
            presentationPreferences = presentationPreferences,
            isLoading = isLoading,
            isInitialLoadComplete = isInitialLoadComplete,
            loadError = loadError,
            isRefreshing = isRefreshing,
            isLoadingMore = feedCoordinator.isLoadingMore,
        )

    private fun updateAllLocalItems() {
        _allLocalItems.value =
            (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
                .filter { it is Song || it is Album }
    }

    private fun updateAllYtItems() {
        val allItems: List<YTItem> =
            similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                feedCoordinator.homePage.value
                    ?.sections
                    ?.flatMap { it.items }
                    .orEmpty() +
                feedCoordinator.communitySection.value?.items.orEmpty()
        _allYtItems.value = allItems
    }

    private suspend fun load() {
        delay(150.milliseconds)
        try {
            cacheHydrationJob?.join()
        } catch (e: CancellationException) {
            throw e
        }

        while (true) {
            reloadRequested = true
            if (!loadMutex.tryLock()) return

            try {
                do {
                    reloadRequested = false
                    executeLoad()
                } while (reloadRequested)
            } finally {
                loadMutex.unlock()
            }

            if (!reloadRequested) break
        }
    }

    private suspend fun executeLoad() {
        isLoading.value = true
        loadError.value = null
        val currentAccountGen = activeAccountGeneration.get()

        try {
            supervisorScope {
                launch {
                    val favorites = localRecommendations.loadForgottenFavorites()
                    synchronized(stateLock) {
                        if (currentAccountGen == activeAccountGeneration.get()) {
                            forgottenFavorites.value = favorites
                        }
                    }
                }
                launch {
                    val keep = localRecommendations.loadKeepListening()
                    synchronized(stateLock) {
                        if (currentAccountGen == activeAccountGeneration.get()) {
                            keepListening.value = keep
                        }
                    }
                }
            }

            synchronized(stateLock) {
                if (currentAccountGen == activeAccountGeneration.get()) {
                    updateAllLocalItems()
                }
            }

            val networkResult =
                accountMutex.withLock {
                    if (currentAccountGen != activeAccountGeneration.get() ||
                        currentAccountGen != readyAccountGeneration.get()
                    ) {
                        return@executeLoad
                    }
                    feedCoordinator.loadHome()
                }

            networkResult
                .onSuccess { page ->
                    val prepared = feedCoordinator.prepareInitialHome(page)
                    synchronized(stateLock) {
                        if (currentAccountGen == activeAccountGeneration.get()) {
                            feedCoordinator.publishPreparedInitialHome(prepared)
                            updateAllYtItems()
                        }
                    }
                    accountMutex.withLock {
                        if (currentAccountGen == activeAccountGeneration.get()) {
                            feedCache.saveHomePage(page)
                        }
                    }
                }.onFailure {
                    synchronized(stateLock) {
                        if (currentAccountGen == activeAccountGeneration.get()) {
                            reportException(it)
                            loadError.value = R.string.error_unknown
                            feedCoordinator.onInitialHomeFailure()
                        }
                    }
                }

            viewModelScope.launch(Dispatchers.IO) {
                val similar = similarRecommendationsUseCase()
                synchronized(stateLock) {
                    if (currentAccountGen == activeAccountGeneration.get()) {
                        similarRecommendations.value = similar
                        updateAllYtItems()
                    }
                }
            }

            synchronized(stateLock) {
                if (currentAccountGen == activeAccountGeneration.get()) {
                    updateAllYtItems()
                    isInitialLoadComplete.value = true
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportException(e)
            synchronized(stateLock) {
                if (currentAccountGen == activeAccountGeneration.get()) {
                    loadError.value = R.string.error_unknown
                }
            }
        } finally {
            synchronized(stateLock) {
                if (currentAccountGen == activeAccountGeneration.get()) {
                    isInitialLoadComplete.value = true
                }
            }
            isLoading.value = false
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Refresh -> refresh()
            is HomeAction.SelectChip -> feedCoordinator.toggleChip(action.chip)
            is HomeAction.LoadMore -> feedCoordinator.loadMoreItems(action.continuation)
        }
    }

    private fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing.value = true
            try {
                supervisorScope {
                    launch { load() }
                    launch {
                        quickPicksManager.refresh()
                        updateAllLocalItems()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun switchToAccount(
        account: SavedAccount,
        forceSyncOnSwitch: Boolean,
    ) {
        val switchGen = activeAccountGeneration.incrementAndGet()
        cacheHydrationJob?.cancel()
        synchronized(stateLock) {
            feedCoordinator.clear()
            similarRecommendations.value = null
            updateAllYtItems()
        }
        viewModelScope.launch(Dispatchers.IO) {
            accountMutex.withLock {
                feedCache.clear()
                accountCoordinator.switchAccount(account, forceSyncOnSwitch)
                readyAccountGeneration.set(switchGen)
            }
            if (switchGen == activeAccountGeneration.get()) {
                load()
            }
        }
    }

    fun switchToAccountChannel(
        channel: AccountChannelUiModel,
        forceSyncOnSwitch: Boolean,
    ) {
        val switchGen = activeAccountGeneration.incrementAndGet()
        cacheHydrationJob?.cancel()
        synchronized(stateLock) {
            feedCoordinator.clear()
            similarRecommendations.value = null
            updateAllYtItems()
        }
        viewModelScope.launch(Dispatchers.IO) {
            accountMutex.withLock {
                feedCache.clear()
                accountCoordinator.switchChannel(channel, forceSyncOnSwitch)
                readyAccountGeneration.set(switchGen)
            }
            if (switchGen == activeAccountGeneration.get()) {
                load()
            }
        }
    }

    init {
        feedCoordinator.bind(viewModelScope) {
            updateAllYtItems()
        }

        val initAccountGen = activeAccountGeneration.get()
        cacheHydrationJob =
            viewModelScope.launch(Dispatchers.IO) {
                val cached = feedCache.loadHomePage()
                if (cached != null) {
                    val prepared = feedCoordinator.prepareInitialHome(cached)
                    synchronized(stateLock) {
                        if (initAccountGen == activeAccountGeneration.get()) {
                            feedCoordinator.publishPreparedInitialHome(prepared)
                            updateAllYtItems()
                        }
                    }
                }
            }

        viewModelScope.launch(Dispatchers.IO) {
            quickPicks.collect {
                updateAllLocalItems()
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            load()
        }

        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[SpeedDialSongIdsKey].orEmpty() }
                .distinctUntilChanged()
                .collect {
                    speedDialItems.value = localRecommendations.loadSpeedDial()
                    updateAllLocalItems()
                }
        }

        viewModelScope.launch(Dispatchers.IO) {
            delay(3000.milliseconds)
            syncUtils.cleanupDuplicatePlaylists()
        }

        accountCoordinator.observeCookies(viewModelScope)
        accountCoordinator.observeAccentColor(viewModelScope)
    }
}
