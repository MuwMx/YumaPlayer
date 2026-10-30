/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.artist.ArtistItemsPrefetcher
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPageLayout
import moe.rukamori.archivetune.models.ItemsPage
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ArtistItemsViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        private val database: MusicDatabase,
        private val prefetcher: ArtistItemsPrefetcher,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val browseId = savedStateHandle.get<String>("browseId")!!
        private val params =
            savedStateHandle
                .get<String>("params")
                ?.takeUnless { it.isBlank() || it == "null" }

        val title = MutableStateFlow("")
        val itemsPage = MutableStateFlow<ItemsPage?>(null)
        val itemsLayout = MutableStateFlow(ArtistItemsPageLayout.LIST)

        /** True when the list could not be loaded, so the screen offers a retry instead of loading forever. */
        val loadFailed = MutableStateFlow(false)

        init {
            retry()
        }

        fun retry() {
            Timber.tag("ArtistItemsTrace").d(
                "ViewModelLoadStart: browseId=$browseId, paramsPresent=${params != null}, paramsLength=${params?.length ?: 0}"
            )
            loadFailed.value = false
            viewModelScope.launch {
                val endpoint = BrowseEndpoint(browseId = browseId, params = params)
                // The artist page usually started this list already; otherwise (or if that failed) load it here.
                // YouTube sometimes turns a request away for a moment; one quiet second try saves
                // the user from a failed page.
                (prefetcher.prefetched(endpoint)?.let { Result.success(it) } ?: YouTube.artistItems(endpoint))
                    .recoverCatching { error ->
                        if (error is CancellationException) throw error
                        delay(RETRY_DELAY_MS)
                        YouTube.artistItems(endpoint).getOrThrow()
                    }.onSuccess { artistItemsPage ->
                        title.value = artistItemsPage.title
                        itemsLayout.value = artistItemsPage.layout
                        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                        val blockedArtistIds = database.getBlockedArtistIds().toSet()

                        val rawCount = artistItemsPage.items.size
                        val distinctItems = artistItemsPage.items.distinctBy { it.id }
                        val distinctCount = distinctItems.size
                        val explicitFilteredItems = distinctItems.filterExplicit(hideExplicit)
                        val explicitFilteredCount = explicitFilteredItems.size
                        val blockedFilteredItems = explicitFilteredItems.filterBlockedArtists(blockedArtistIds)
                        val blockedFilteredCount = blockedFilteredItems.size
                        val publishedCount = blockedFilteredItems.size

                        Timber.tag("ArtistItemsTrace").d(
                            "ViewModelLoadSuccess: browseId=$browseId, title=\"${artistItemsPage.title}\", " +
                                "layout=${artistItemsPage.layout}, rawCount=$rawCount, distinctCount=$distinctCount, " +
                                "explicitFilteredCount=$explicitFilteredCount, blockedFilteredCount=$blockedFilteredCount, " +
                                "publishedCount=$publishedCount, continuationPresent=${artistItemsPage.continuation != null}"
                        )

                        itemsPage.value =
                            ItemsPage(
                                items = blockedFilteredItems,
                                continuation = artistItemsPage.continuation,
                            )
                    }.onFailure {
                        Timber.tag("ArtistItemsTrace").e(
                            it,
                            "ViewModelLoadError: browseId=$browseId, message=${it.message}"
                        )
                        reportException(it)
                        loadFailed.value = true
                    }
            }
        }

        fun loadMore() {
            viewModelScope.launch {
                val oldItemsPage = itemsPage.value ?: return@launch
                val continuation = oldItemsPage.continuation ?: return@launch
                Timber.tag("ArtistItemsTrace").d(
                    "ViewModelLoadMoreStart: browseId=$browseId, continuationLen=${continuation.length}"
                )
                YouTube
                    .artistItemsContinuation(continuation)
                    .onSuccess { artistItemsContinuationPage ->
                        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                        val blockedArtistIds = database.getBlockedArtistIds().toSet()

                        val prevPublishedCount = oldItemsPage.items.size
                        val rawContinuationCount = artistItemsContinuationPage.items.size
                        val combined = oldItemsPage.items + artistItemsContinuationPage.items
                        val distinctItems = combined.distinctBy { it.id }
                        val explicitFilteredItems = distinctItems.filterExplicit(hideExplicit)
                        val blockedFilteredItems = explicitFilteredItems.filterBlockedArtists(blockedArtistIds)
                        val publishedCount = blockedFilteredItems.size

                        Timber.tag("ArtistItemsTrace").d(
                            "ViewModelLoadMoreSuccess: browseId=$browseId, prevCount=$prevPublishedCount, " +
                                "rawContinuationCount=$rawContinuationCount, publishedCount=$publishedCount, " +
                                "continuationPresent=${artistItemsContinuationPage.continuation != null}"
                        )

                        itemsPage.update {
                            ItemsPage(
                                items = blockedFilteredItems,
                                continuation = artistItemsContinuationPage.continuation,
                            )
                        }
                    }.onFailure {
                        Timber.tag("ArtistItemsTrace").e(
                            it,
                            "ViewModelLoadMoreError: browseId=$browseId, message=${it.message}"
                        )
                        reportException(it)
                    }
            }
        }

        private companion object {
            const val RETRY_DELAY_MS = 1_000L
        }
    }
