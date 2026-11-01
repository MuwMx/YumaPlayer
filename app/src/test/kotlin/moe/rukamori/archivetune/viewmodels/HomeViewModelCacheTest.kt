/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.aicontentfilter.AiContentFilterPolicy
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import moe.rukamori.archivetune.auth.SwitchSavedYouTubeAccountUseCase
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.home.HomeViewModel
import moe.rukamori.archivetune.home.FilterCommunityPreviewsUseCase
import moe.rukamori.archivetune.home.HomeAction
import moe.rukamori.archivetune.home.HomePresentationPreferences
import moe.rukamori.archivetune.home.HomeScreenState
import moe.rukamori.archivetune.home.LoadCommunityPlaylistPreviewsUseCase
import moe.rukamori.archivetune.home.ObserveHomePresentationPreferencesUseCase
import moe.rukamori.archivetune.home.PrepareCommunityHomePageUseCase
import moe.rukamori.archivetune.home.PreparedCommunityHomePage
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
import moe.rukamori.archivetune.home.account.HomeAccountCoordinator
import moe.rukamori.archivetune.home.feed.HomeFeedCache
import moe.rukamori.archivetune.home.feed.HomeFeedCoordinator
import moe.rukamori.archivetune.home.quickpicks.HomeQuickPicksManager
import moe.rukamori.archivetune.home.recommendations.HomeLocalRecommendationsUseCase
import moe.rukamori.archivetune.home.recommendations.HomeSimilarRecommendationsUseCase
import moe.rukamori.archivetune.home.state.HomeStateComposer
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.utils.SavedAccount
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class HomeViewModelCacheTest {

    private val context: Context = mockk(relaxed = true)
    private val database: MusicDatabase = mockk(relaxed = true)
    private val syncUtils: SyncUtils = mockk(relaxed = true)
    private val switchSavedYouTubeAccount: SwitchSavedYouTubeAccountUseCase = mockk(relaxed = true)
    private val observePreferences: ObserveHomePresentationPreferencesUseCase = mockk()
    private val loadAiPolicy: LoadAiContentFilterPolicyUseCase = mockk()
    private val filterAiContent = FilterAiContentUseCase()
    private val prepareCommunityUseCase: PrepareCommunityHomePageUseCase = mockk()
    private val loadCommunityUseCase: LoadCommunityPlaylistPreviewsUseCase = mockk(relaxed = true)
    private val filterCommunityUseCase: FilterCommunityPreviewsUseCase = mockk(relaxed = true)
    private val feedCache: HomeFeedCache = mockk(relaxed = true)

    private val localRecommendations: HomeLocalRecommendationsUseCase = mockk()
    private val quickPicksManager: HomeQuickPicksManager = mockk()
    private val similarRecommendationsUseCase: HomeSimilarRecommendationsUseCase = mockk()

    private val viewModelStore = ViewModelStore()

    @Before
    fun setUp() {
        mockkObject(YouTube)
        mockkStatic("moe.rukamori.archivetune.utils.DataStoreKt")

        every { context.dataStore.data } returns flowOf(emptyPreferences())
        every { any<DataStore<Preferences>>().get<Any>(any(), any()) } answers { thirdArg() }
        every { context.getString(R.string.filter_community_playlists) } returns "Плейлисты сообщества"
        every { context.getString(R.string.filter_playlists) } returns "Плейлисты"

        every { observePreferences() } returns flowOf(
            HomePresentationPreferences(
                showCategoryChips = true,
                quickPicksDisplayMode = QuickPicksDisplayMode.CARD,
                showTonalBackdrop = false,
            ),
        )
        coEvery { loadAiPolicy() } returns AiContentFilterPolicy.Disabled
        coEvery { switchSavedYouTubeAccount(any()) } returns Result.success(mockk(relaxed = true))

        every { prepareCommunityUseCase(any(), any()) } answers {
            PreparedCommunityHomePage(homePage = firstArg(), communitySection = null)
        }
        every { prepareCommunityUseCase(any()) } answers {
            PreparedCommunityHomePage(homePage = firstArg(), communitySection = null)
        }

        coEvery { localRecommendations.loadForgottenFavorites() } returns emptyList()
        coEvery { localRecommendations.loadKeepListening() } returns emptyList()
        coEvery { localRecommendations.loadSpeedDial() } returns emptyList()
        every { quickPicksManager.observe(any()) } returns MutableStateFlow(emptyList())
        coEvery { quickPicksManager.refresh() } returns emptyList()
        coEvery { similarRecommendationsUseCase() } returns emptyList()
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        unmockkAll()
    }

    private fun samplePage(title: String, songId: String): HomePage =
        HomePage(
            chips = null,
            sections = listOf(
                HomePage.Section(
                    title = title,
                    label = null,
                    thumbnail = null,
                    endpoint = null,
                    items = listOf(
                        SongItem(
                            id = songId,
                            title = "Track $songId",
                            artists = listOf(Artist("Artist", null)),
                            album = null,
                            duration = 100,
                            thumbnail = "https://thumb/$songId",
                            explicit = false,
                            endpoint = null,
                        ),
                    ),
                ),
            ),
            continuation = null,
        )

    private fun createViewModel(): HomeViewModel {
        val coordinator =
            HomeFeedCoordinator(
                context = context,
                database = database,
                loadAiContentFilterPolicy = loadAiPolicy,
                filterAiContent = filterAiContent,
                prepareCommunityHomePage = prepareCommunityUseCase,
                loadCommunityPlaylistPreviews = loadCommunityUseCase,
                filterCommunityPreviews = filterCommunityUseCase,
            )

        val viewModel =
            HomeViewModel(
                context = context,
                syncUtils = syncUtils,
                observeHomePresentationPreferences = observePreferences,
                quickPicksManager = quickPicksManager,
                localRecommendations = localRecommendations,
                similarRecommendationsUseCase = similarRecommendationsUseCase,
                accountCoordinator = HomeAccountCoordinator(context, syncUtils, switchSavedYouTubeAccount),
                feedCoordinator = coordinator,
                stateComposer = HomeStateComposer(),
                feedCache = feedCache,
            )
        viewModelStore.put("home-view-model-${System.nanoTime()}", viewModel)
        return viewModel
    }

    @Test
    fun startupCacheHydration_appliesCachedHome_beforeNetwork() = runBlocking(Dispatchers.Default) {
        val cachedPage = samplePage("Cached Section", "song_cached")
        val networkPage = samplePage("Network Section", "song_network")

        val networkProceed = CompletableDeferred<Unit>()
        val networkDone = CompletableDeferred<Unit>()

        coEvery { feedCache.loadHomePage() } returns cachedPage
        coEvery { YouTube.home() } coAnswers {
            networkProceed.await()
            networkDone.complete(Unit)
            Result.success(networkPage)
        }

        val viewModel = createViewModel()
        val collectorJob = launch { viewModel.screenState.collect {} }

        try {
            val cachedSuccess = withTimeout(5000) {
                viewModel.screenState.filterIsInstance<HomeScreenState.Success>().first {
                    it.uiState.homePage?.sections?.any { s -> s.title == "Cached Section" } == true
                }
            }
            assertNotNull(cachedSuccess)
            assertTrue(viewModel.allYtItems.value.any { it.id == "song_cached" })

            networkProceed.complete(Unit)
            networkDone.await()

            val freshSuccess = withTimeout(5000) {
                viewModel.screenState.filterIsInstance<HomeScreenState.Success>().first {
                    it.uiState.homePage?.sections?.any { s -> s.title == "Network Section" } == true
                }
            }
            assertNotNull(freshSuccess)
            assertTrue(viewModel.allYtItems.value.any { it.id == "song_network" })
            coVerify { feedCache.saveHomePage(networkPage) }
        } finally {
            collectorJob.cancel()
        }
    }

    @Test
    fun cacheBeforeNetworkOrdering_networkCannotStartUntilHydrationCompletes() = runBlocking(Dispatchers.Default) {
        val cachedPage = samplePage("Hydrated Page", "song_hydrated")
        val networkPage = samplePage("Network Page", "song_net")

        val cacheGate = CompletableDeferred<Unit>()
        val networkStarted = AtomicBoolean(false)
        val networkDone = CompletableDeferred<Unit>()

        coEvery { feedCache.loadHomePage() } coAnswers {
            cacheGate.await()
            cachedPage
        }
        coEvery { YouTube.home() } coAnswers {
            networkStarted.set(true)
            networkDone.complete(Unit)
            Result.success(networkPage)
        }

        val viewModel = createViewModel()
        val collectorJob = launch { viewModel.screenState.collect {} }

        try {
            assertFalse(networkStarted.get())

            cacheGate.complete(Unit)
            networkDone.await()

            withTimeout(5000) {
                viewModel.screenState.filterIsInstance<HomeScreenState.Success>().first {
                    it.uiState.homePage?.sections?.any { s -> s.title == "Network Page" } == true
                }
            }
            assertTrue(networkStarted.get())
        } finally {
            collectorJob.cancel()
        }
    }

    @Test
    fun accountSwitch_staleNetworkResponse_doesNotApply_or_persist() = runBlocking(Dispatchers.Default) {
        val staleNetworkPage = samplePage("Stale Account Feed", "stale_song")
        val freshAccountPage = samplePage("Fresh Account Feed", "fresh_song")

        val firstNetworkStarted = CompletableDeferred<Unit>()
        val firstNetworkGate = CompletableDeferred<Unit>()
        val callCount = AtomicInteger(0)

        coEvery { feedCache.loadHomePage() } returns null
        coEvery { YouTube.home() } coAnswers {
            val count = callCount.incrementAndGet()
            if (count == 1) {
                firstNetworkStarted.complete(Unit)
                firstNetworkGate.await()
                Result.success(staleNetworkPage)
            } else {
                Result.success(freshAccountPage)
            }
        }

        val viewModel = createViewModel()
        val collectorJob = launch { viewModel.screenState.collect {} }

        try {
            firstNetworkStarted.await()

            val targetAccount = SavedAccount(
                id = "acc_2",
                name = "Account 2",
                email = "acc2@example.com",
                channelHandle = "@acc2",
                innerTubeCookie = "cookie_2",
                visitorData = "",
                dataSyncId = "sync_2",
                ytmSync = true,
                selectedYtmPlaylists = "",
            )
            viewModel.switchToAccount(targetAccount, forceSyncOnSwitch = false)

            firstNetworkGate.complete(Unit)

            withTimeout(5000) {
                viewModel.screenState.filterIsInstance<HomeScreenState.Success>().first {
                    it.uiState.homePage?.sections?.any { s -> s.title == "Fresh Account Feed" } == true
                }
            }

            coVerify(exactly = 0) { feedCache.saveHomePage(staleNetworkPage) }
            coVerify(atLeast = 1) { feedCache.saveHomePage(freshAccountPage) }
            assertFalse(viewModel.allYtItems.value.any { it.id == "stale_song" })
            assertTrue(viewModel.allYtItems.value.any { it.id == "fresh_song" })
        } finally {
            collectorJob.cancel()
        }
    }

    @Test
    fun accountSwitch_racingWithInFlightSave_clearsCacheAndDoesNotRetainOldPage() = runBlocking(Dispatchers.Default) {
        val oldPage = samplePage("Old Account Page", "old_song")
        val saveEntered = CompletableDeferred<Unit>()
        val saveRelease = CompletableDeferred<Unit>()
        val clearInvoked = CompletableDeferred<Unit>()

        coEvery { feedCache.loadHomePage() } returns null
        coEvery { YouTube.home() } returns Result.success(oldPage)
        coEvery { feedCache.saveHomePage(any()) } coAnswers {
            saveEntered.complete(Unit)
            saveRelease.await()
        }
        coEvery { feedCache.clear() } coAnswers {
            clearInvoked.complete(Unit)
        }

        val viewModel = createViewModel()
        val collectorJob = launch { viewModel.screenState.collect {} }

        try {
            saveEntered.await()

            val newAccount = SavedAccount(
                id = "acc_new",
                name = "New Account",
                email = "new@example.com",
                channelHandle = "@new",
                innerTubeCookie = "cookie_new",
                visitorData = "",
                dataSyncId = "sync_new",
                ytmSync = true,
                selectedYtmPlaylists = "",
            )
            viewModel.switchToAccount(newAccount, forceSyncOnSwitch = false)

            saveRelease.complete(Unit)
            clearInvoked.await()

            withTimeout(5000) {
                while (viewModel.allYtItems.value.any { it.id == "old_song" }) {
                    kotlinx.coroutines.delay(10)
                }
            }
            coVerify(atLeast = 1) { feedCache.clear() }
            assertFalse(viewModel.allYtItems.value.any { it.id == "old_song" })
        } finally {
            collectorJob.cancel()
        }
    }

    @Test
    fun queuedRefresh_executesReload_whenLoadAlreadyRunning() = runBlocking(Dispatchers.Default) {
        val firstPage = samplePage("First Load", "song_first")
        val secondPage = samplePage("Second Load", "song_second")

        val firstStarted = CompletableDeferred<Unit>()
        val firstGate = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val secondDone = CompletableDeferred<Unit>()
        val callCount = AtomicInteger(0)

        coEvery { feedCache.loadHomePage() } returns null
        coEvery { YouTube.home() } coAnswers {
            val count = callCount.incrementAndGet()
            if (count == 1) {
                firstStarted.complete(Unit)
                firstGate.await()
                Result.success(firstPage)
            } else {
                secondStarted.complete(Unit)
                secondDone.complete(Unit)
                Result.success(secondPage)
            }
        }

        val viewModel = createViewModel()
        val collectorJob = launch { viewModel.screenState.collect {} }

        try {
            firstStarted.await()

            viewModel.onAction(HomeAction.Refresh)

            firstGate.complete(Unit)

            secondStarted.await()
            secondDone.await()
            assertEquals(2, callCount.get())

            withTimeout(5000) {
                viewModel.screenState.filterIsInstance<HomeScreenState.Success>().first {
                    it.uiState.homePage?.sections?.any { s -> s.title == "Second Load" } == true
                }
            }
            assertTrue(viewModel.allYtItems.value.any { it.id == "song_second" })
        } finally {
            collectorJob.cancel()
        }
    }

    @Test
    fun oldPreparationAndPublication_racingWithAccountSwitch_doesNotPublishOrRetainOldFeed() = runBlocking(Dispatchers.Default) {
        val oldPage = samplePage("Old Account Page", "old_racing_song")
        val prepEntered = CompletableDeferred<Unit>()
        val prepRelease = CompletableDeferred<Unit>()

        coEvery { feedCache.loadHomePage() } returns null
        coEvery { YouTube.home() } returns Result.success(oldPage)
        every { prepareCommunityUseCase(oldPage, any()) } answers {
            prepEntered.complete(Unit)
            runBlocking { prepRelease.await() }
            PreparedCommunityHomePage(homePage = oldPage, communitySection = null)
        }

        val viewModel = createViewModel()
        val collectorJob = launch { viewModel.screenState.collect {} }

        try {
            prepEntered.await()

            val newAccount = SavedAccount(
                id = "acc_switch_prep",
                name = "New Account",
                email = "new@example.com",
                channelHandle = "@new",
                innerTubeCookie = "cookie_new",
                visitorData = "",
                dataSyncId = "sync_new",
                ytmSync = true,
                selectedYtmPlaylists = "",
            )
            viewModel.switchToAccount(newAccount, forceSyncOnSwitch = false)

            prepRelease.complete(Unit)

            withTimeout(5000) {
                while (viewModel.allYtItems.value.any { it.id == "old_racing_song" }) {
                    kotlinx.coroutines.delay(10)
                }
            }
            coVerify(exactly = 0) { feedCache.saveHomePage(oldPage) }
            assertFalse(viewModel.allYtItems.value.any { it.id == "old_racing_song" })
        } finally {
            collectorJob.cancel()
        }
    }

    @Test
    fun newGenerationLoad_requestedBeforeCredentialsSwitchCompletes_waitsForReadyCredentials() = runBlocking(Dispatchers.Default) {
        val initialPage = samplePage("Init Page", "song_init")
        val newAccountPage = samplePage("New Account Page", "song_new_acc")

        val switchEntered = CompletableDeferred<Unit>()
        val switchRelease = CompletableDeferred<Unit>()
        val callCount = AtomicInteger(0)

        coEvery { feedCache.loadHomePage() } returns null
        coEvery { switchSavedYouTubeAccount(any()) } coAnswers {
            switchEntered.complete(Unit)
            switchRelease.await()
            Result.success(mockk(relaxed = true))
        }
        coEvery { YouTube.home() } coAnswers {
            val count = callCount.incrementAndGet()
            if (count == 1) {
                Result.success(initialPage)
            } else {
                Result.success(newAccountPage)
            }
        }

        val viewModel = createViewModel()
        val collectorJob = launch { viewModel.screenState.collect {} }

        try {
            withTimeout(5000) {
                viewModel.screenState.filterIsInstance<HomeScreenState.Success>().first {
                    it.uiState.homePage?.sections?.any { s -> s.title == "Init Page" } == true
                }
            }
            assertEquals(1, callCount.get())

            val newAccount = SavedAccount(
                id = "acc_in_flight",
                name = "In Flight Account",
                email = "inflight@example.com",
                channelHandle = "@inflight",
                innerTubeCookie = "cookie_inflight",
                visitorData = "",
                dataSyncId = "sync_inflight",
                ytmSync = true,
                selectedYtmPlaylists = "",
            )
            viewModel.switchToAccount(newAccount, forceSyncOnSwitch = false)
            switchEntered.await()

            viewModel.onAction(HomeAction.Refresh)

            kotlinx.coroutines.delay(100)
            assertEquals(1, callCount.get())

            switchRelease.complete(Unit)

            withTimeout(5000) {
                viewModel.screenState.filterIsInstance<HomeScreenState.Success>().first {
                    it.uiState.homePage?.sections?.any { s -> s.title == "New Account Page" } == true
                }
            }
            assertTrue(callCount.get() >= 2)
            assertTrue(viewModel.allYtItems.value.any { it.id == "song_new_acc" })
        } finally {
            collectorJob.cancel()
        }
    }
}
