/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.home.feed

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.aicontentfilter.AiContentFilterPolicy
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.home.FilterCommunityPreviewsUseCase
import moe.rukamori.archivetune.home.LoadCommunityPlaylistPreviewsUseCase
import moe.rukamori.archivetune.home.PrepareCommunityHomePageUseCase
import moe.rukamori.archivetune.home.PreparedCommunityHomePage
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class HomeFeedCoordinatorTest {

    private val context: Context = mockk(relaxed = true)
    private val database: MusicDatabase = mockk(relaxed = true)
    private val loadAiPolicy: LoadAiContentFilterPolicyUseCase = mockk()
    private val filterAiContent: FilterAiContentUseCase = FilterAiContentUseCase()
    private val prepareCommunityUseCase: PrepareCommunityHomePageUseCase = mockk(relaxed = true)
    private val loadCommunityUseCase: LoadCommunityPlaylistPreviewsUseCase = mockk(relaxed = true)
    private val filterCommunityUseCase: FilterCommunityPreviewsUseCase = mockk(relaxed = true)

    private lateinit var coordinator: HomeFeedCoordinator

    @Before
    fun setUp() {
        mockkObject(YouTube)
        mockkStatic("moe.rukamori.archivetune.utils.DataStoreKt")

        every { context.dataStore.data } returns flowOf(emptyPreferences())
        every { any<DataStore<Preferences>>().get<Any>(any(), any()) } answers { thirdArg() }
        coEvery { loadAiPolicy() } returns AiContentFilterPolicy.Disabled

        coordinator = HomeFeedCoordinator(
            context = context,
            database = database,
            loadAiContentFilterPolicy = loadAiPolicy,
            filterAiContent = filterAiContent,
            prepareCommunityHomePage = prepareCommunityUseCase,
            loadCommunityPlaylistPreviews = loadCommunityUseCase,
            filterCommunityPreviews = filterCommunityUseCase,
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun initialHome_appliesHomePage_withoutTriggeringCallback() = runBlocking {
        val callbackInvoked = AtomicBoolean(false)
        val scope = CoroutineScope(Dispatchers.Default + Job())

        try {
            coordinator.bind(scope) {
                callbackInvoked.set(true)
            }

            val testSection = HomePage.Section(
                title = "Test Section",
                label = null,
                thumbnail = null,
                endpoint = null,
                items = emptyList(),
            )
            val testPage = HomePage(chips = null, sections = listOf(testSection))
            every { prepareCommunityUseCase(testPage, allowStructuralFallback = true) } returns
                PreparedCommunityHomePage(homePage = testPage, communitySection = null)

            coordinator.applyInitialHome(testPage)

            assertEquals(testPage, coordinator.homePage.value)
            assertNull(coordinator.communitySection.value)
            assertFalse(callbackInvoked.get())
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    @Test
    fun toggleChip_selectThenDeselect_restoresPreviousHomePageAndCommunity() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())

        try {
            val initialCommunity = HomePage.Section(
                title = "Initial Community",
                label = null,
                thumbnail = null,
                endpoint = null,
                items = emptyList(),
            )
            val initialPage = HomePage(
                chips = null,
                sections = listOf(
                    HomePage.Section(
                        title = "Initial Section",
                        label = null,
                        thumbnail = null,
                        endpoint = null,
                        items = emptyList(),
                    ),
                ),
            )

            every { prepareCommunityUseCase(initialPage, allowStructuralFallback = true) } returns
                PreparedCommunityHomePage(homePage = initialPage, communitySection = initialCommunity)

            coordinator.applyInitialHome(initialPage)
            assertEquals(initialPage, coordinator.homePage.value)
            assertEquals(initialCommunity, coordinator.communitySection.value)

            val chip = HomePage.Chip(
                title = "Relax",
                endpoint = BrowseEndpoint(browseId = "FE_relax", params = "relax_params"),
                deselectEndPoint = null,
            )
            val chipSection = HomePage.Section(
                title = "Relaxing Music",
                label = null,
                thumbnail = null,
                endpoint = null,
                items = emptyList(),
            )
            val chipPage = HomePage(chips = null, sections = listOf(chipSection))

            coEvery { YouTube.home(params = "relax_params") } returns Result.success(chipPage)
            every { prepareCommunityUseCase(any(), allowStructuralFallback = false) } returns
                PreparedCommunityHomePage(homePage = chipPage, communitySection = null)

            val chipSelectedDeferred = CompletableDeferred<Unit>()
            var deselectionCallbackInvoked = false

            coordinator.bind(scope) {
                if (coordinator.selectedChip.value == chip) {
                    chipSelectedDeferred.complete(Unit)
                } else if (coordinator.selectedChip.value == null) {
                    deselectionCallbackInvoked = true
                }
            }

            // 1. Select chip
            coordinator.toggleChip(chip)
            chipSelectedDeferred.await()

            assertEquals(chip, coordinator.selectedChip.value)
            assertEquals(chipPage, coordinator.homePage.value)
            assertNull(coordinator.communitySection.value)

            // 2. Deselect chip (synchronous restore)
            coordinator.toggleChip(chip)

            assertNull(coordinator.selectedChip.value)
            assertEquals(initialPage, coordinator.homePage.value)
            assertEquals(initialCommunity, coordinator.communitySection.value)
            assertTrue(deselectionCallbackInvoked)
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    @Test
    fun updateCommunitySection_stalePreviewRejectedOnRapidUpdate() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())

        try {
            val card1 = HomePage.Section.FeaturedCard(
                id = "c1",
                title = "Card 1",
                subtitle = null,
                thumbnail = null,
                endpoint = null,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
                itemIds = emptyList(),
            )
            val card2 = HomePage.Section.FeaturedCard(
                id = "c2",
                title = "Card 2",
                subtitle = null,
                thumbnail = null,
                endpoint = null,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
                itemIds = emptyList(),
            )
            val section1 = HomePage.Section(
                title = "Community 1",
                label = null,
                thumbnail = null,
                endpoint = null,
                items = emptyList(),
                featuredCards = listOf(card1),
            )
            val section2 = HomePage.Section(
                title = "Community 2",
                label = null,
                thumbnail = null,
                endpoint = null,
                items = emptyList(),
                featuredCards = listOf(card2),
            )

            val section1LoadStarted = CompletableDeferred<Unit>()
            val section1Proceed = CompletableDeferred<Unit>()
            val section2EnrichedDeferred = CompletableDeferred<Unit>()

            coEvery { loadCommunityUseCase(section1) } coAnswers {
                section1LoadStarted.complete(Unit)
                section1Proceed.await()
                section1
            }
            coEvery { filterCommunityUseCase(section1) } returns
                section1.copy(title = "Stale Enriched Section 1")

            coEvery { loadCommunityUseCase(section2) } returns section2
            coEvery { filterCommunityUseCase(section2) } returns
                section2.copy(title = "Fresh Enriched Section 2")

            coordinator.bind(scope) {
                if (coordinator.communitySection.value?.title == "Fresh Enriched Section 2") {
                    section2EnrichedDeferred.complete(Unit)
                }
            }

            // Launch generation 1
            coordinator.updateCommunitySection(section1)
            section1LoadStarted.await()

            // Launch generation 2 (rapid switch cancels and supersedes gen 1)
            coordinator.updateCommunitySection(section2)

            // Let generation 1 proceed in background
            section1Proceed.complete(Unit)

            // Wait for generation 2 enrichment to complete
            section2EnrichedDeferred.await()

            assertEquals("Fresh Enriched Section 2", coordinator.communitySection.value?.title)
            assertEquals("c2", coordinator.communitySection.value?.featuredCards?.firstOrNull()?.id)
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    @Test
    fun delayedPagination_acrossClear_doesNotAppendToClearedFeed() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())

        try {
            coordinator.bind(scope)

            val initialSection = HomePage.Section(
                title = "Initial Section",
                label = null,
                thumbnail = null,
                endpoint = null,
                items = emptyList(),
            )
            val initialPage = HomePage(chips = null, sections = listOf(initialSection), continuation = "cont_1")
            every { prepareCommunityUseCase(initialPage, allowStructuralFallback = true) } returns
                PreparedCommunityHomePage(homePage = initialPage, communitySection = null)

            coordinator.applyInitialHome(initialPage)
            assertEquals(initialPage, coordinator.homePage.value)

            val paginationStarted = CompletableDeferred<Unit>()
            val paginationRelease = CompletableDeferred<Unit>()
            val pagedSection = HomePage.Section(
                title = "Paged Section",
                label = null,
                thumbnail = null,
                endpoint = null,
                items = emptyList(),
            )
            val pagedPage = HomePage(chips = null, sections = listOf(pagedSection))

            coEvery { YouTube.home(continuation = "cont_1") } coAnswers {
                paginationStarted.complete(Unit)
                paginationRelease.await()
                Result.success(pagedPage)
            }
            every { prepareCommunityUseCase(pagedPage, any()) } returns
                PreparedCommunityHomePage(homePage = pagedPage, communitySection = null)

            coordinator.loadMoreItems("cont_1")
            paginationStarted.await()

            coordinator.clear()
            assertNull(coordinator.homePage.value)

            paginationRelease.complete(Unit)

            kotlinx.coroutines.delay(50)
            assertNull(coordinator.homePage.value)
            assertFalse(coordinator.isLoadingMore.value)
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }
}
