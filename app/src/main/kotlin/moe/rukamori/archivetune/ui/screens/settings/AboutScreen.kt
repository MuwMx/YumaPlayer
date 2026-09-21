/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.AboutScreenEffect
import moe.rukamori.archivetune.viewmodels.AboutScreenState
import moe.rukamori.archivetune.viewmodels.AboutViewModel

@Composable
fun AboutScreen(
    navController: NavController,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = appBarScrollBehavior()

    LaunchedEffect(viewModel, uriHandler) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AboutScreenEffect.OpenUri -> uriHandler.openUri(effect.uri)
            }
        }
    }

    val actions = remember(viewModel, navController) {
        AboutUiActions(
            onNavigateUp = navController::navigateUp,
            onNavigateHome = navController::backToMain,
            onOpenUri = viewModel::openUri,
            onShowOverflowMenu = viewModel::showOverflowMenu,
            onDismissOverflowMenu = viewModel::dismissOverflowMenu,
            onOpenDependencyLicenses = viewModel::openDependencyLicenses,
            onDismissDialog = viewModel::dismissDialog,
            onRetryDependencyLicenses = viewModel::retryDependencyLicenses,
        )
    }

    AboutScreenContent(
        state = state,
        scrollBehavior = scrollBehavior,
        actions = actions,
    )
}

@Composable
internal fun AboutScreenContent(
    state: AboutScreenState,
    scrollBehavior: TopAppBarScrollBehavior,
    actions: AboutUiActions,
) {
    AboutScreenContent(
        state = state,
        scrollBehavior = scrollBehavior,
        onNavigateUp = actions.onNavigateUp,
        onNavigateHome = actions.onNavigateHome,
        onOpenUri = actions.onOpenUri,
        onShowOverflowMenu = actions.onShowOverflowMenu,
        onDismissOverflowMenu = actions.onDismissOverflowMenu,
        onOpenDependencyLicenses = actions.onOpenDependencyLicenses,
        onDismissDialog = actions.onDismissDialog,
        onRetryDependencyLicenses = actions.onRetryDependencyLicenses,
    )
}

@Composable
internal fun AboutScreenContent(
    state: AboutScreenState,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onOpenUri: (String) -> Unit,
    // onRetryContributors: () -> Unit,
    onShowOverflowMenu: () -> Unit,
    onDismissOverflowMenu: () -> Unit,
    // onOpenTranslationContributors: () -> Unit,
    onOpenDependencyLicenses: () -> Unit,
    onDismissDialog: () -> Unit,
    // onRetryTranslationContributors: () -> Unit,
    onRetryDependencyLicenses: () -> Unit,
) {
    val listState = rememberLazyListState()

    SettingsScreenBackground {
        Scaffold(
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.about),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateUp,
                            onLongClick = onNavigateHome,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = stringResource(R.string.back_button_desc),
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
                    actions = {
                        if (state is AboutScreenState.Success) {
                            AboutOverflowMenu(
                                expanded = state.model.isOverflowMenuExpanded,
                                onShowMenu = onShowOverflowMenu,
                                onDismissMenu = onDismissOverflowMenu,
                                // onOpenTranslationContributors = onOpenTranslationContributors,
                                onOpenDependencyLicenses = onOpenDependencyLicenses,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            val stateModifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    )

            when (state) {
                AboutScreenState.Loading -> {
                    AboutLoadingContent(modifier = stateModifier)
                }

                AboutScreenState.Empty -> {
                    AboutMessageContent(
                        message = stringResource(R.string.no_results_found),
                        modifier = stateModifier,
                    )
                }

                is AboutScreenState.Error -> {
                    AboutMessageContent(
                        message = stringResource(state.messageResId),
                        modifier = stateModifier,
                    )
                }

                is AboutScreenState.Success -> {
                    AboutSuccessContent(
                        model = state.model,
                        onOpenUri = onOpenUri,
                        // onRetryContributors = onRetryContributors,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(
                                    LocalPlayerAwareWindowInsets.current.only(
                                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                    ),
                                ),
                        contentPadding =
                            PaddingValues(
                                top = innerPadding.calculateTopPadding() + 8.dp,
                                bottom = SettingsDimensions.ScreenBottomPadding,
                            ),
                        listState = listState,
                    )
                }
            }
        }

        if (state is AboutScreenState.Success) {
            AboutFullScreenDialogs(
                model = state.model,
                onDismiss = onDismissDialog,
                // onRetryTranslationContributors = onRetryTranslationContributors,
                onRetryDependencyLicenses = onRetryDependencyLicenses,
            )
        }
    }
}