/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.home.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.home.HomePresentationPreferences
import moe.rukamori.archivetune.home.HomeScreenState
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.models.SimilarRecommendation
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

class HomeStateComposer
    @Inject
    constructor() {
        private fun combineLocalContent(
            quickPicks: Flow<List<Song>?>,
            speedDialItems: Flow<List<LocalItem>>,
            forgottenFavorites: Flow<List<Song>?>,
            keepListening: Flow<List<LocalItem>?>,
        ): Flow<HomeLocalContent> =
            combine(
                quickPicks,
                speedDialItems,
                forgottenFavorites,
                keepListening,
            ) { quickPicks, speedDialItems, forgottenFavorites, keepListening ->
                HomeLocalContent(
                    quickPicks = quickPicks.orEmpty(),
                    speedDialItems = speedDialItems,
                    forgottenFavorites = forgottenFavorites.orEmpty(),
                    keepListening = keepListening.orEmpty(),
                )
            }

        private fun combineRemoteContent(
            homePage: Flow<HomePage?>,
            similarRecommendations: Flow<List<SimilarRecommendation>?>,
            accountPlaylists: Flow<List<PlaylistItem>?>,
            accountName: Flow<String>,
            accountImageUrl: Flow<String?>,
            communitySection: Flow<HomePage.Section?>,
        ): Flow<HomeRemoteContent> =
            combine(
                homePage,
                similarRecommendations,
                accountPlaylists,
                combine(accountName, accountImageUrl) { name, img -> name to img },
                communitySection,
            ) { homePage, similarRecommendations, accountPlaylists, (accountName, accountImageUrl), communitySection ->
                HomeRemoteContent(
                    homePage = homePage,
                    similarRecommendations = similarRecommendations.orEmpty(),
                    accountPlaylists = accountPlaylists.orEmpty(),
                    accountName = accountName,
                    accountImageUrl = accountImageUrl,
                    communitySection = communitySection,
                )
            }

        @OptIn(FlowPreview::class)
        fun compose(
            scope: CoroutineScope,
            localContent: Flow<HomeLocalContent>,
            remoteContent: Flow<HomeRemoteContent>,
            selectedChip: StateFlow<HomePage.Chip?>,
            presentationPreferences: Flow<HomePresentationPreferences>,
            isLoading: StateFlow<Boolean>,
            isInitialLoadComplete: StateFlow<Boolean>,
            loadError: StateFlow<Int?>,
            isRefreshing: StateFlow<Boolean>,
            isLoadingMore: StateFlow<Boolean>,
        ): StateFlow<HomeScreenState> {
            val homeContent =
                combine(
                    localContent,
                    remoteContent,
                    selectedChip,
                ) { local, remote, chip ->
                    HomeContent(
                        local = local,
                        remote = remote,
                        selectedChip = chip,
                    )
                }

            return combine(
                homeContent,
                presentationPreferences,
                isLoading,
                isInitialLoadComplete,
                loadError,
            ) { content, preferences, loading, initialComplete, error ->
                HomeStateInputs(
                    content = content,
                    preferences = preferences,
                    isLoading = loading,
                    isInitialLoadComplete = initialComplete,
                    loadError = error,
                )
            }.combine(
                combine(isRefreshing, isLoadingMore) { refreshing, loadingMore ->
                    refreshing to loadingMore
                },
            ) { inputs, loadingState ->
                inputs.toScreenState(
                    isRefreshing = loadingState.first,
                    isLoadingMore = loadingState.second,
                )
            }.debounce(100.milliseconds)
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HomeScreenState.Loading,
            )
        }

        @OptIn(FlowPreview::class)
        fun compose(
            scope: CoroutineScope,
            quickPicks: Flow<List<Song>?>,
            speedDialItems: Flow<List<LocalItem>>,
            forgottenFavorites: Flow<List<Song>?>,
            keepListening: Flow<List<LocalItem>?>,
            homePage: Flow<HomePage?>,
            similarRecommendations: Flow<List<SimilarRecommendation>?>,
            accountPlaylists: Flow<List<PlaylistItem>?>,
            accountName: Flow<String>,
            accountImageUrl: Flow<String?>,
            communitySection: Flow<HomePage.Section?>,
            selectedChip: StateFlow<HomePage.Chip?>,
            presentationPreferences: Flow<HomePresentationPreferences>,
            isLoading: StateFlow<Boolean>,
            isInitialLoadComplete: StateFlow<Boolean>,
            loadError: StateFlow<Int?>,
            isRefreshing: StateFlow<Boolean>,
            isLoadingMore: StateFlow<Boolean>,
        ): StateFlow<HomeScreenState> =
            compose(
                scope = scope,
                localContent = combineLocalContent(quickPicks, speedDialItems, forgottenFavorites, keepListening),
                remoteContent = combineRemoteContent(homePage, similarRecommendations, accountPlaylists, accountName, accountImageUrl, communitySection),
                selectedChip = selectedChip,
                presentationPreferences = presentationPreferences,
                isLoading = isLoading,
                isInitialLoadComplete = isInitialLoadComplete,
                loadError = loadError,
                isRefreshing = isRefreshing,
                isLoadingMore = isLoadingMore,
            )
    }
