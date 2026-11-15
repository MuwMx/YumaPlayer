/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.downloads.DownloadSectionUiModel
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.DownloadLibraryEvent
import moe.rukamori.archivetune.viewmodels.DownloadLibraryScreenState
import moe.rukamori.archivetune.viewmodels.DownloadLibraryTab
import moe.rukamori.archivetune.viewmodels.DownloadLibraryViewModel

@Composable
fun DownloadLibraryScreen(
    navController: NavController,
    viewModel: DownloadLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, playerConnection) {
        viewModel.events.collect { event ->
            when (event) {
                is DownloadLibraryEvent.Message -> {
                    snackbarHostState.showSnackbar(navController.context.getString(event.messageRes))
                }

                is DownloadLibraryEvent.Navigate -> {
                    navController.navigate(event.route)
                }

                is DownloadLibraryEvent.PlaySong -> {
                    playerConnection?.playQueue(
                        ListQueue(
                            title = event.metadata.title,
                            items = event.queue.map { metadata -> metadata.toMediaItem() },
                            startIndex = event.startIndex,
                        ),
                    )
                }
            }
        }
    }

    DownloadLibraryScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        viewModel = viewModel,
        onBack = navController::navigateUp,
        onBackToMain = navController::backToMain,
    )
}

@Composable
private fun DownloadLibraryScreenContent(
    state: DownloadLibraryScreenState,
    snackbarHostState: SnackbarHostState,
    viewModel: DownloadLibraryViewModel,
    onBack: () -> Unit,
    onBackToMain: () -> Unit,
) {
    val selectedTab = state.selectedTab()
    val query = state.query()
    val isSearchActive = state.isSearchActive()
    val tabs = remember { DownloadLibraryTab.entries }
    val pagerState = rememberPagerState(initialPage = tabs.indexOf(selectedTab), pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    BackHandler(enabled = isSearchActive, onBack = viewModel::closeSearch)

    LaunchedEffect(selectedTab) {
        val targetPage = tabs.indexOf(selectedTab)
        if (pagerState.currentPage != targetPage) pagerState.animateScrollToPage(targetPage)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> viewModel.selectTab(tabs[page]) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DownloadLibraryTopBar(
                isSearchActive = isSearchActive,
                query = query,
                sort = state.sort,
                tabs = tabs,
                pagerState = pagerState,
                scrollBehavior = scrollBehavior,
                onBack = onBack,
                onBackToMain = onBackToMain,
                onActivateSearch = viewModel::activateSearch,
                onCloseSearch = viewModel::closeSearch,
                onSubmitSearch = viewModel::submitSearch,
                onQueryChange = viewModel::updateQuery,
                onClearQuery = viewModel::clearQuery,
                onSelectSortField = viewModel::selectSortField,
                onSelectSortOrder = viewModel::selectSortOrder,
                onSelectTab = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
        when (state) {
            is DownloadLibraryScreenState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }

            is DownloadLibraryScreenState.Error -> {
                EmptyPlaceholder(
                    icon = R.drawable.error,
                    text = stringResource(state.messageRes),
                    modifier = Modifier.padding(innerPadding),
                )
            }

            is DownloadLibraryScreenState.Empty,
            is DownloadLibraryScreenState.Success,
            -> {
                val downloadedSections = (state as? DownloadLibraryScreenState.Success)?.library?.downloadedSections.orEmpty()
                val progressSections = (state as? DownloadLibraryScreenState.Success)?.library?.progressSections.orEmpty()
                DownloadPager(
                    pagerState = pagerState,
                    query = query,
                    downloadedSections = downloadedSections,
                    progressSections = progressSections,
                    contentPadding = innerPadding,
                    viewModel = viewModel,
                )
            }
        }
    }

    state.pendingRemoval?.let { removal ->
        DownloadRemovalConfirmationDialog(
            pendingRemoval = removal,
            onConfirm = viewModel::confirmRemove,
            onDismiss = viewModel::dismissRemoveConfirmation,
        )
    }
}

@Composable
private fun DownloadPager(
    pagerState: PagerState,
    query: String,
    downloadedSections: List<DownloadSectionUiModel>,
    progressSections: List<DownloadSectionUiModel>,
    contentPadding: PaddingValues,
    viewModel: DownloadLibraryViewModel,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.Top,
    ) { page ->
        val inProgress = page == DownloadLibraryTab.PROGRESS.ordinal
        DownloadSections(
            sections = if (inProgress) progressSections else downloadedSections,
            inProgress = inProgress,
            query = query,
            contentPadding = contentPadding,
            onOpenEntry = viewModel::open,
            onPauseEntry = viewModel::pause,
            onResumeEntry = viewModel::resume,
            onRemoveEntry = viewModel::remove,
            onRequestRemoveEntry = viewModel::requestRemove,
            onPauseSection = viewModel::pause,
            onResumeSection = viewModel::resume,
            onRemoveSection = viewModel::remove,
            onRequestRemoveSection = viewModel::requestRemove,
        )
    }
}

private fun DownloadLibraryScreenState.selectedTab(): DownloadLibraryTab =
    when (this) {
        is DownloadLibraryScreenState.Loading -> selectedTab
        is DownloadLibraryScreenState.Success -> selectedTab
        is DownloadLibraryScreenState.Empty -> selectedTab
        is DownloadLibraryScreenState.Error -> selectedTab
    }

private fun DownloadLibraryScreenState.query(): String =
    when (this) {
        is DownloadLibraryScreenState.Loading -> query
        is DownloadLibraryScreenState.Success -> query
        is DownloadLibraryScreenState.Empty -> query
        is DownloadLibraryScreenState.Error -> query
    }

private fun DownloadLibraryScreenState.isSearchActive(): Boolean =
    when (this) {
        is DownloadLibraryScreenState.Loading -> isSearchActive
        is DownloadLibraryScreenState.Success -> isSearchActive
        is DownloadLibraryScreenState.Empty -> isSearchActive
        is DownloadLibraryScreenState.Error -> isSearchActive
    }
