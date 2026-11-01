/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.home.feed

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.home.FilterCommunityPreviewsUseCase
import moe.rukamori.archivetune.home.LoadCommunityPlaylistPreviewsUseCase
import moe.rukamori.archivetune.home.PrepareCommunityHomePageUseCase
import moe.rukamori.archivetune.home.PreparedCommunityHomePage
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

class HomeFeedCoordinator
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        private val loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
        private val filterAiContent: FilterAiContentUseCase,
        private val prepareCommunityHomePage: PrepareCommunityHomePageUseCase,
        private val loadCommunityPlaylistPreviews: LoadCommunityPlaylistPreviewsUseCase,
        private val filterCommunityPreviews: FilterCommunityPreviewsUseCase,
    ) {
        private val _homePage = MutableStateFlow<HomePage?>(null)
        val homePage: StateFlow<HomePage?> = _homePage.asStateFlow()

        private val _communitySection = MutableStateFlow<HomePage.Section?>(null)
        val communitySection: StateFlow<HomePage.Section?> = _communitySection.asStateFlow()

        private val _selectedChip = MutableStateFlow<HomePage.Chip?>(null)
        val selectedChip: StateFlow<HomePage.Chip?> = _selectedChip.asStateFlow()

        private val _isLoadingMore = MutableStateFlow(false)
        val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

        private val previousHomePage = MutableStateFlow<HomePage?>(null)
        private val previousCommunitySection = MutableStateFlow<HomePage.Section?>(null)
        private val communityPreviewGeneration = AtomicLong(0L)
        private val feedDataGeneration = AtomicLong(0L)
        private var communityPreviewJob: Job? = null
        private var chipLoadJob: Job? = null
        private var paginationJob: Job? = null

        private var boundScope: CoroutineScope? = null
        private var onItemsUpdatedCallback: (() -> Unit)? = null

        fun bind(
            scope: CoroutineScope,
            onItemsUpdated: () -> Unit = {},
        ) {
            this.boundScope = scope
            this.onItemsUpdatedCallback = onItemsUpdated
            scope.coroutineContext[Job]?.invokeOnCompletion {
                if (boundScope == scope) {
                    boundScope = null
                    onItemsUpdatedCallback = null
                    communityPreviewJob = null
                    chipLoadJob = null
                    paginationJob = null
                }
            }
        }

        fun prepareInitialHome(page: HomePage): PreparedCommunityHomePage =
            prepareCommunityHomePage(page, allowStructuralFallback = true)

        fun publishPreparedInitialHome(prepared: PreparedCommunityHomePage) {
            feedDataGeneration.incrementAndGet()
            _homePage.value = prepared.homePage
            updateCommunitySection(prepared.communitySection)
        }

        fun applyInitialHome(page: HomePage) {
            val prepared = prepareInitialHome(page)
            publishPreparedInitialHome(prepared)
        }

        fun onInitialHomeFailure() {
            updateCommunitySection(null)
        }

        fun clear() {
            feedDataGeneration.incrementAndGet()
            communityPreviewGeneration.incrementAndGet()
            communityPreviewJob?.cancel()
            communityPreviewJob = null
            chipLoadJob?.cancel()
            chipLoadJob = null
            paginationJob?.cancel()
            paginationJob = null
            _homePage.value = null
            _communitySection.value = null
            _selectedChip.value = null
            previousHomePage.value = null
            previousCommunitySection.value = null
            _isLoadingMore.value = false
            onItemsUpdatedCallback?.invoke()
        }

        internal fun updateCommunitySection(section: HomePage.Section?) {
            val generation = communityPreviewGeneration.incrementAndGet()
            communityPreviewJob?.cancel()
            communityPreviewJob = null
            _communitySection.value = section
            if (section == null || section.featuredCards.all { card -> card.itemIds.isNotEmpty() }) return

            val scope = boundScope ?: return
            communityPreviewJob =
                scope.launch(Dispatchers.IO) {
                    try {
                        val enrichedSection =
                            filterCommunityPreviews(
                                loadCommunityPlaylistPreviews(section),
                            )
                        if (communityPreviewGeneration.get() == generation) {
                            _communitySection.value = enrichedSection
                            onItemsUpdatedCallback?.invoke()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
        }

        fun toggleChip(chip: HomePage.Chip?) {
            Timber.tag("HomeDebug").d(
                "toggleChip clicked: title=%s, hasParams=%b",
                chip?.title,
                !chip?.endpoint?.params.isNullOrBlank(),
            )
            chipLoadJob?.cancel()
            if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
                Timber.tag("HomeDebug").d("toggleChip deselected, restoring previous homePage")
                _homePage.value = previousHomePage.value
                updateCommunitySection(previousCommunitySection.value)
                previousHomePage.value = null
                previousCommunitySection.value = null
                _selectedChip.value = null
                onItemsUpdatedCallback?.invoke()
                return
            }

            if (selectedChip.value == null) {
                previousHomePage.value = homePage.value
                previousCommunitySection.value = communitySection.value
            }

            val scope = boundScope ?: return
            val chipGeneration = feedDataGeneration.incrementAndGet()
            chipLoadJob =
                scope.launch(Dispatchers.IO) {
                    loadHome(chipParams = chip.endpoint?.params)
                        .onSuccess { nextSections ->
                            if (chipGeneration != feedDataGeneration.get()) return@onSuccess
                            Timber.tag("HomeDebug").d(
                                "toggleChip SUCCESS: loaded %d sections: %s",
                                nextSections.sections.size,
                                nextSections.sections.map { it.title },
                            )
                            if (nextSections.sections.isEmpty()) {
                                Timber.tag("HomeDebug").w("toggleChip returned empty sections, ignoring")
                                if (selectedChip.value == null) {
                                    previousHomePage.value = null
                                    previousCommunitySection.value = null
                                }
                                return@onSuccess
                            }
                            _selectedChip.value = chip
                            val filteredPage = nextSections.copy(chips = homePage.value?.chips)
                            val prepared = prepareCommunityHomePage(filteredPage, allowStructuralFallback = false)
                            _homePage.value = prepared.homePage
                            updateCommunitySection(prepared.communitySection)
                            onItemsUpdatedCallback?.invoke()
                        }.onFailure {
                            if (chipGeneration != feedDataGeneration.get()) return@onFailure
                            Timber.tag("HomeDebug").e(it, "toggleChip FAILED")
                            reportException(it)
                            if (selectedChip.value == null) {
                                previousHomePage.value = null
                                previousCommunitySection.value = null
                            }
                        }
                }
        }

        fun loadMoreItems(continuation: String?) {
            if (continuation == null || _isLoadingMore.value) return

            val scope = boundScope ?: return
            val paginationGeneration = feedDataGeneration.get()
            paginationJob?.cancel()
            paginationJob =
                scope.launch(Dispatchers.IO) {
                    _isLoadingMore.value = true
                    try {
                        val nextSections = loadMore(continuation).getOrNull() ?: return@launch
                        if (paginationGeneration != feedDataGeneration.get()) return@launch
                        val prepared =
                            prepareCommunityHomePage(
                                homePage = nextSections,
                                allowStructuralFallback = _communitySection.value == null,
                            )
                        if (paginationGeneration != feedDataGeneration.get()) return@launch
                        if (_communitySection.value == null && prepared.communitySection != null) {
                            updateCommunitySection(prepared.communitySection)
                        }
                        _homePage.value =
                            nextSections.copy(
                                chips = homePage.value?.chips,
                                sections = homePage.value?.sections.orEmpty() + prepared.homePage.sections,
                            )
                        onItemsUpdatedCallback?.invoke()
                    } finally {
                        if (paginationGeneration == feedDataGeneration.get()) {
                            _isLoadingMore.value = false
                        }
                    }
                }
        }

        suspend fun loadHome(chipParams: String? = null): Result<HomePage> =
            withContext(Dispatchers.IO) {
                val result =
                    if (chipParams != null) {
                        YouTube.home(params = chipParams)
                    } else {
                        YouTube.home()
                    }
                result.map { page ->
                    val filtered = filterHomePageSections(page, filterChips = true)
                    Timber.tag("HomeDebug").d(
                        "loadHome(params=%s): sections=%s, chips=%s",
                        chipParams,
                        filtered.sections.map { it.title },
                        filtered.chips?.map { "${it.title}(hasParams=${!it.endpoint?.params.isNullOrBlank()})" },
                    )
                    filtered
                }.onFailure {
                    Timber.tag("HomeDebug").e(it, "loadHome(params=%s) FAILED", chipParams)
                }
            }

        private suspend fun loadMore(continuation: String): Result<HomePage> =
            withContext(Dispatchers.IO) {
                YouTube.home(continuation = continuation).map { page ->
                    filterHomePageSections(page, filterChips = false)
                }
            }

        private fun filterHomeChips(chips: List<HomePage.Chip>?): List<HomePage.Chip>? =
            chips?.filterNot {
                it.title.contains("podcasts", ignoreCase = true)
            }

        private suspend fun filterHomePageSections(
            page: HomePage,
            filterChips: Boolean,
        ): HomePage {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideo = context.dataStore.get(HideVideoKey, false)
            val blockedArtistIds = database.getBlockedArtistIds().toSet()
            val aiContentFilterPolicy = loadAiContentFilterPolicy()

            return page.copy(
                chips = if (filterChips) filterHomeChips(page.chips) else page.chips,
                sections =
                    page.sections.map { section ->
                        section.copy(
                            items =
                                filterAiContent(
                                    section.items
                                        .filterExplicit(hideExplicit)
                                        .filterVideo(hideVideo)
                                        .filterBlockedArtists(blockedArtistIds),
                                    aiContentFilterPolicy,
                                ),
                        )
                    },
            )
        }
    }
