/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.home

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.aicontentfilter.AiContentFilterPolicy
import moe.rukamori.archivetune.aicontentfilter.AiContentFilterSettings
import moe.rukamori.archivetune.aicontentfilter.AiContentFilterStatus
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import moe.rukamori.archivetune.aicontentfilter.ObserveAiContentFilterUseCase
import moe.rukamori.archivetune.auth.SwitchSavedYouTubeAccountUseCase
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.Button
import moe.rukamori.archivetune.innertube.models.MusicCardShelfRenderer
import moe.rukamori.archivetune.innertube.models.MusicCarouselShelfRenderer
import moe.rukamori.archivetune.innertube.models.NavigationEndpoint
import moe.rukamori.archivetune.innertube.models.Runs
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.Thumbnail
import moe.rukamori.archivetune.innertube.models.ThumbnailRenderer
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.home.HomeViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityShelfPipelineRegressionTest {

    private val context: Context = mockk(relaxed = true)
    private val database: MusicDatabase = mockk(relaxed = true)
    private val syncUtils: SyncUtils = mockk(relaxed = true)
    private val switchSavedYouTubeAccount: SwitchSavedYouTubeAccountUseCase = mockk(relaxed = true)
    private val observePreferences: ObserveHomePresentationPreferencesUseCase = mockk()
    private val observeAiContentFilter: ObserveAiContentFilterUseCase = mockk()
    private val loadAiPolicy: LoadAiContentFilterPolicyUseCase = mockk()
    private val filterAiContent = FilterAiContentUseCase()
    private val repository: HomeRepository = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var prepareCommunityUseCase: PrepareCommunityHomePageUseCase
    private lateinit var loadCommunityUseCase: LoadCommunityPlaylistPreviewsUseCase
    private lateinit var filterCommunityUseCase: FilterCommunityPreviewsUseCase

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
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
        every { observeAiContentFilter() } returns flowOf(
            AiContentFilterSettings(enabled = false, includeModerateConfidence = false) to
                AiContentFilterStatus(blocklistCount = 0, warnlistCount = 0, lastUpdatedEpochMillis = 0L),
        )
        coEvery { loadAiPolicy() } returns AiContentFilterPolicy.Disabled

        coEvery { database.getBlockedArtistIds() } returns emptyList()
        every { database.quickPicks(any()) } returns flowOf(emptyList())
        every { database.quickPicks() } returns flowOf(emptyList())
        every { database.recentSongs(any()) } returns flowOf(emptyList())
        every { database.recentSongs() } returns flowOf(emptyList())
        every { database.allSongs() } returns flowOf(emptyList())
        every { database.forgottenFavorites(any()) } returns flowOf(emptyList())
        every { database.forgottenFavorites() } returns flowOf(emptyList())
        every { database.mostPlayedSongs(any(), any(), any(), any()) } returns flowOf(emptyList())
        every { database.mostPlayedSongs(any(), any(), any()) } returns flowOf(emptyList())
        every { database.mostPlayedAlbums(any(), any(), any(), any()) } returns flowOf(emptyList())
        every { database.mostPlayedAlbums(any(), any(), any()) } returns flowOf(emptyList())
        every { database.mostPlayedArtists(any(), any(), any(), any()) } returns flowOf(emptyList())
        every { database.mostPlayedArtists(any(), any(), any()) } returns flowOf(emptyList())
        every { database.mostPlayedArtists(any()) } returns flowOf(emptyList())

        prepareCommunityUseCase = PrepareCommunityHomePageUseCase(context)
        loadCommunityUseCase = LoadCommunityPlaylistPreviewsUseCase(repository)
        filterCommunityUseCase = FilterCommunityPreviewsUseCase(context, database, loadAiPolicy, filterAiContent)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createRealisticMusicCard(
        playlistId: String,
        title: String,
    ): MusicCardShelfRenderer = MusicCardShelfRenderer(
        title = Runs(listOf(moe.rukamori.archivetune.innertube.models.Run(title, null))),
        subtitle = Runs(listOf(moe.rukamori.archivetune.innertube.models.Run("Creator • 45 songs", null))),
        thumbnail = ThumbnailRenderer(
            musicThumbnailRenderer = ThumbnailRenderer.MusicThumbnailRenderer(
                thumbnail = moe.rukamori.archivetune.innertube.models.Thumbnails(
                    listOf(Thumbnail("https://thumb/$playlistId", 400, 400)),
                ),
                thumbnailCrop = null,
                thumbnailScale = null,
            ),
            musicAnimatedThumbnailRenderer = null,
            croppedSquareThumbnailRenderer = null,
        ),
        header = null,
        contents = emptyList(),
        buttons = listOf(
            Button(
                buttonRenderer = Button.ButtonRenderer(
                    text = Runs(listOf(moe.rukamori.archivetune.innertube.models.Run("Play", null))),
                    icon = moe.rukamori.archivetune.innertube.models.Icon("PLAY_ARROW"),
                    command = NavigationEndpoint(watchEndpoint = WatchEndpoint(playlistId = playlistId)),
                    navigationEndpoint = null,
                ),
            ),
            Button(
                buttonRenderer = Button.ButtonRenderer(
                    text = Runs(listOf(moe.rukamori.archivetune.innertube.models.Run("Shuffle", null))),
                    icon = moe.rukamori.archivetune.innertube.models.Icon("MUSIC_SHUFFLE"),
                    command = NavigationEndpoint(watchEndpoint = WatchEndpoint(playlistId = playlistId, params = "shuffle")),
                    navigationEndpoint = null,
                ),
            ),
        ),
        onTap = NavigationEndpoint(
            browseEndpoint = BrowseEndpoint(browseId = "VL$playlistId"),
        ),
        subtitleBadges = null,
    )

    @Test
    fun realisticServerCommunityCarousel_isParsed_detected_andSelectedForSpecializedBranch() = runTest {
        // 1. Replay real InnerTube response: Carousel with MusicCardShelfRenderer contents
        val cardRenderer1 = createRealisticMusicCard("PL_comm_1", "Chill Community Vibes")
        val cardRenderer2 = createRealisticMusicCard("PL_comm_2", "Coding Community Mix")

        val carouselHeader = MusicCarouselShelfRenderer.Header(
            musicCarouselShelfBasicHeaderRenderer = MusicCarouselShelfRenderer.Header.MusicCarouselShelfBasicHeaderRenderer(
                strapline = null,
                title = Runs(listOf(moe.rukamori.archivetune.innertube.models.Run("From the community", null))),
                thumbnail = null,
                moreContentButton = null,
            ),
        )
        val carouselRenderer = MusicCarouselShelfRenderer(
            header = carouselHeader,
            contents = listOf(
                MusicCarouselShelfRenderer.Content(
                    musicTwoRowItemRenderer = null,
                    musicResponsiveListItemRenderer = null,
                    musicCardShelfRenderer = cardRenderer1,
                    musicNavigationButtonRenderer = null,
                ),
                MusicCarouselShelfRenderer.Content(
                    musicTwoRowItemRenderer = null,
                    musicResponsiveListItemRenderer = null,
                    musicCardShelfRenderer = cardRenderer2,
                    musicNavigationButtonRenderer = null,
                ),
            ),
            itemSize = "MUSIC_CAROUSEL_SHELF_ITEM_SIZE_TALL",
            numItemsPerColumn = 1,
        )

        val parsedSection = HomePage.Section.fromMusicCarouselShelfRenderer(carouselRenderer)
        assertNotNull("Carousel containing MusicCardShelfRenderer must be successfully parsed into Section", parsedSection)
        assertEquals("From the community", parsedSection!!.title)
        assertEquals(2, parsedSection.featuredCards.size)
        assertEquals("PL_comm_1", parsedSection.featuredCards[0].id)
        assertEquals("Chill Community Vibes", parsedSection.featuredCards[0].title)
        assertEquals("PL_comm_2", parsedSection.featuredCards[1].id)
        assertEquals("Coding Community Mix", parsedSection.featuredCards[1].title)

        // 2. Prepare HomePage containing the realistic Community Section + another generic section
        val genericSection = HomePage.Section(
            title = "Top Charts",
            label = null,
            thumbnail = null,
            endpoint = BrowseEndpoint(browseId = "FEmusic_charts"),
            items = listOf(SongItem(id = "chart_1", title = "Hit Song", artists = emptyList(), thumbnail = "https://thumb/1")),
        )
        val rawHomePage = HomePage(chips = null, sections = listOf(genericSection, parsedSection))

        // 3. Run through PrepareCommunityHomePageUseCase
        val prepared = prepareCommunityUseCase(rawHomePage, allowStructuralFallback = true)

        assertNotNull("Community section must be detected by marker / fallback", prepared.communitySection)
        assertEquals("From the community", prepared.communitySection!!.title)
        assertEquals(2, prepared.communitySection!!.featuredCards.size)

        // 4. Assert generic carousel loop skips the community shelf (it is cleanly extracted from homePage.sections)
        assertEquals(1, prepared.homePage.sections.size)
        assertEquals("Top Charts", prepared.homePage.sections[0].title)
        assertFalse(
            "Community shelf must NOT remain in generic homePage.sections to prevent duplicate or generic carousel render",
            prepared.homePage.sections.any { it.title == "From the community" || it.featuredCards.isNotEmpty() },
        )

        // 5. Verify ViewModel integration holds communitySection in state
        coEvery { YouTube.home(any(), any()) } returns Result.success(rawHomePage)
        coEvery { YouTube.home(any()) } returns Result.success(rawHomePage)
        coEvery { YouTube.home() } returns Result.success(rawHomePage)
        coEvery { repository.loadPlaylistPreview(any(), any()) } returns Result.success(
            listOf(SongItem(id = "preview_1", title = "Preview Track", artists = emptyList(), thumbnail = "https://thumb/prev")),
        )

        val viewModel = HomeViewModel(
            context = context,
            database = database,
            syncUtils = syncUtils,
            switchSavedYouTubeAccount = switchSavedYouTubeAccount,
            observeHomePresentationPreferences = observePreferences,
            loadAiContentFilterPolicy = loadAiPolicy,
            filterAiContent = filterAiContent,
            prepareCommunityHomePage = prepareCommunityUseCase,
            loadCommunityPlaylistPreviews = loadCommunityUseCase,
            filterCommunityPreviews = filterCommunityUseCase,
        )

        // Give coroutines on Dispatchers.IO time to complete
        var attempts = 0
        while (attempts < 50 && (viewModel.communitySection.value == null || viewModel.communitySection.value?.items?.isEmpty() == true)) {
            Thread.sleep(50)
            attempts++
        }

        val vmCommunity = viewModel.communitySection.value
        assertNotNull("ViewModel must expose populated communitySection", vmCommunity)
        assertEquals(2, vmCommunity!!.featuredCards.size)
        assertTrue("Specialized Community branch has featured cards ready for UI", vmCommunity.featuredCards.isNotEmpty())
    }
}
