/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.border
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.utils.ColorExtractor
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
// Закомментированы неиспользуемые сейчас импорты для чистоты
// import moe.rukamori.archivetune.viewmodels.AboutContributorUiCollection
// import moe.rukamori.archivetune.viewmodels.AboutContributorsUiState
import moe.rukamori.archivetune.viewmodels.AboutDependencyLicenseUiCollection
import moe.rukamori.archivetune.viewmodels.AboutDependencyLicensesUiState
import moe.rukamori.archivetune.viewmodels.AboutDialog
import moe.rukamori.archivetune.viewmodels.AboutLinkCollection
import moe.rukamori.archivetune.viewmodels.AboutScreenEffect
import moe.rukamori.archivetune.viewmodels.AboutScreenState
// import moe.rukamori.archivetune.viewmodels.AboutTranslationContributorUiCollection
// import moe.rukamori.archivetune.viewmodels.AboutTranslationContributorsUiState
import moe.rukamori.archivetune.viewmodels.AboutUiModel
import moe.rukamori.archivetune.viewmodels.AboutViewModel
import moe.rukamori.archivetune.viewmodels.TeamMember
// import moe.rukamori.archivetune.viewmodels.TeamMemberCollection
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews

@OptIn(ExperimentalMaterial3Api::class)
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

    AboutScreenContent(
        state = state,
        scrollBehavior = scrollBehavior,
        onNavigateUp = navController::navigateUp,
        onNavigateHome = navController::backToMain,
        onOpenUri = viewModel::openUri,
        // onRetryContributors = viewModel::retryContributors,
        onShowOverflowMenu = viewModel::showOverflowMenu,
        onDismissOverflowMenu = viewModel::dismissOverflowMenu,
        // onOpenTranslationContributors = viewModel::openTranslationContributors,
        onOpenDependencyLicenses = viewModel::openDependencyLicenses,
        onDismissDialog = viewModel::dismissDialog,
        // onRetryTranslationContributors = viewModel::retryTranslationContributors,
        onRetryDependencyLicenses = viewModel::retryDependencyLicenses,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreenContent(
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

@Composable
private fun AboutOverflowMenu(
    expanded: Boolean,
    onShowMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    // onOpenTranslationContributors: () -> Unit,
    onOpenDependencyLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        IconButton(
            onClick = onShowMenu,
            onLongClick = {},
        ) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = stringResource(R.string.more_options),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissMenu,
        ) {
            /*
             * ЗАКОММЕНТИРОВАНО: Секция переводчиков
             * Раскомментировать, когда появятся свои контрибьюторы
             */
            /*
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.about_contributor_translation)) },
                onClick = onOpenTranslationContributors,
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.translate),
                        contentDescription = null,
                    )
                },
            )
            */

            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.about_license)) },
                onClick = onOpenDependencyLicenses,
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_about),
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

@Composable
private fun AboutLoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun AboutMessageContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutFullScreenDialogs(
    model: AboutUiModel,
    onDismiss: () -> Unit,
    // onRetryTranslationContributors: () -> Unit,
    onRetryDependencyLicenses: () -> Unit,
) {
    when (model.activeDialog) {
        AboutDialog.NONE -> {
            Unit
        }

        /*
         * ЗАКОММЕНТИРОВАНО: Диалог переводчиков
         */
        /*
        AboutDialog.TRANSLATION_CONTRIBUTORS -> {
            AboutFullScreenDialog(
                title = stringResource(R.string.about_contributor_translation),
                onDismiss = onDismiss,
            ) { modifier ->
                TranslationContributorsDialogContent(
                    state = model.translationContributorsState,
                    onRetry = onRetryTranslationContributors,
                    modifier = modifier,
                )
            }
        }
        */

        AboutDialog.DEPENDENCY_LICENSES -> {
            AboutFullScreenDialog(
                title = stringResource(R.string.about_license),
                onDismiss = onDismiss,
            ) { modifier ->
                DependencyLicensesDialogContent(
                    state = model.dependencyLicensesState,
                    onRetry = onRetryDependencyLicenses,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun AboutFullScreenDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        androidx.compose.material3.IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.close_dialog),
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                )
            },
        ) { innerPadding ->
            content(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
            )
        }
    }
}

/*
 * ============================================================================
 * ЗАКОММЕНТИРОВАНО: Функции для отображения переводчиков
 * Раскомментировать вместе с AboutDialog.TRANSLATION_CONTRIBUTORS
 * ============================================================================
 */
/*
@Composable
private fun TranslationContributorsDialogContent(
    state: AboutTranslationContributorsUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        AboutTranslationContributorsUiState.Loading -> {
            DialogStatusContent(
                message = stringResource(R.string.loading),
                showRetry = false,
                onRetry = onRetry,
                modifier = modifier,
            )
        }

        AboutTranslationContributorsUiState.Empty -> {
            DialogStatusContent(
                message = stringResource(R.string.no_results_found),
                showRetry = true,
                onRetry = onRetry,
                modifier = modifier,
            )
        }

        is AboutTranslationContributorsUiState.Error -> {
            DialogStatusContent(
                message = stringResource(state.messageResId),
                showRetry = true,
                onRetry = onRetry,
                modifier = modifier,
            )
        }

        is AboutTranslationContributorsUiState.Success -> {
            TranslationContributorList(
                contributors = state.contributors,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun TranslationContributorList(
    contributors: AboutTranslationContributorUiCollection,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            count = contributors.size,
            key = { index -> contributors[index].language },
            contentType = { "translation_contributor" },
        ) { index ->
            val contributor = contributors[index]
            SegmentedListItemSurface(
                index = index,
                itemCount = contributors.size,
            ) {
                TranslationContributorListItem(
                    language = contributor.language,
                    contributors = contributor.contributors,
                )
            }
        }
    }
}

@Composable
private fun TranslationContributorListItem(
    language: String,
    contributors: String?,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.heightIn(min = if (contributors == null) 56.dp else 72.dp),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.language),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        headlineContent = {
            Text(
                text = language,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent =
            contributors?.let { contributorNames ->
                {
                    Text(
                        text = contributorNames,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
    )
}
*/

@Composable
private fun DependencyLicensesDialogContent(
    state: AboutDependencyLicensesUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        AboutDependencyLicensesUiState.Loading -> {
            DialogStatusContent(
                message = stringResource(R.string.loading),
                showRetry = false,
                onRetry = onRetry,
                modifier = modifier,
            )
        }

        AboutDependencyLicensesUiState.Empty -> {
            DialogStatusContent(
                message = stringResource(R.string.no_results_found),
                showRetry = true,
                onRetry = onRetry,
                modifier = modifier,
            )
        }

        is AboutDependencyLicensesUiState.Error -> {
            DialogStatusContent(
                message = stringResource(state.messageResId),
                showRetry = true,
                onRetry = onRetry,
                modifier = modifier,
            )
        }

        is AboutDependencyLicensesUiState.Success -> {
            DependencyLicenseList(
                licenses = state.licenses,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun DialogStatusContent(
    message: String,
    showRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!showRetry) {
            LoadingIndicator(modifier = Modifier.size(40.dp))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (showRetry) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text(text = stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun DependencyLicenseList(
    licenses: AboutDependencyLicenseUiCollection,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            count = licenses.size,
            key = { index -> "${licenses[index].name}:${licenses[index].version.orEmpty()}:$index" },
            contentType = { "dependency_license" },
        ) { index ->
            val dependency = licenses[index]
            SegmentedListItemSurface(
                index = index,
                itemCount = licenses.size,
            ) {
                DependencyLicenseListItem(
                    name = dependency.name,
                    version = dependency.version,
                    licenses = dependency.licenses,
                )
            }
        }
    }
}

@Composable
private fun DependencyLicenseListItem(
    name: String,
    version: String?,
    licenses: String?,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.heightIn(min = 72.dp),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_about),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        headlineContent = {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                version?.let { versionName ->
                    Text(
                        text = versionName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = licenses ?: stringResource(R.string.about_license_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

@Composable
private fun SegmentedListItemSurface(
    index: Int,
    itemCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape =
            segmentedListItemShape(
                index = index,
                itemCount = itemCount,
            ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        content = content,
    )
}

private fun segmentedListItemShape(
    index: Int,
    itemCount: Int,
): Shape {
    val outerCorner = 24.dp
    val innerCorner = 4.dp
    return when {
        itemCount <= 1 -> {
            RoundedCornerShape(outerCorner)
        }

        index == 0 -> {
            RoundedCornerShape(
                topStart = outerCorner,
                topEnd = outerCorner,
                bottomEnd = innerCorner,
                bottomStart = innerCorner,
            )
        }

        index == itemCount - 1 -> {
            RoundedCornerShape(
                topStart = innerCorner,
                topEnd = innerCorner,
                bottomEnd = outerCorner,
                bottomStart = outerCorner,
            )
        }

        else -> {
            RoundedCornerShape(innerCorner)
        }
    }
}

@Composable
private fun AboutSuccessContent(
    model: AboutUiModel,
    onOpenUri: (String) -> Unit,
    // onRetryContributors: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "identity", contentType = "about_identity") {
            AboutContentContainer {
                AboutIdentityCard(
                    model = model,
                    onOpenUri = onOpenUri,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "lead_developer", contentType = "about_lead_developer") {
            AboutContentContainer {
                LeadDeveloperSection(
                    member = model.leadDeveloper,
                    onOpenUri = onOpenUri,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }




        /*
         * ====================================================================
         * ЗАКОММЕНТИРОВАНО: Секции команды, респектеров и контрибьюторов
         * Раскомментировать, когда появятся свои люди
         * ====================================================================
         */

        /*
        item(key = "team", contentType = "about_team_section") {
            AboutContentContainer {
                TeamMemberSection(
                    title = stringResource(R.string.about_archive_tune_team),
                    members = model.collaborators,
                    onOpenUri = onOpenUri,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "respecters", contentType = "about_team_section") {
            AboutContentContainer {
                TeamMemberSection(
                    title = stringResource(R.string.about_respecter),
                    members = model.respecters,
                    onOpenUri = onOpenUri,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "contributors", contentType = "about_contributors") {
            AboutContentContainer {
                ContributorsSection(
                    state = model.contributorsState,
                    readMoreUrl = model.contributorsReadMoreUrl,
                    onOpenProfile = onOpenUri,
                    onRetry = onRetryContributors,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        */
    }
}

@Composable
private fun AboutContentContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutIdentityCard(
    model: AboutUiModel,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYumaColors.current
    val cardShape = MaterialTheme.shapes.extraLarge
    val isDark = isSystemInDarkTheme()

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val blendMode = if (isDark) BlendMode.Plus else BlendMode.SrcOver

    val spot1Alpha = if (isDark) 0.50f else 0.35f
    val spot1AlphaMid = if (isDark) 0.20f else 0.12f

    val spot2Alpha = if (isDark) 0.55f else 0.40f
    val spot2AlphaMid = if (isDark) 0.22f else 0.15f

    val transition = rememberInfiniteTransition(label = "meshGlow")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .drawWithCache {
                val spot1X = size.width * (0.5f + 0.35f * kotlin.math.cos(time))
                val spot1Y = size.height * (0.5f + 0.30f * kotlin.math.sin(time))

                val spot2X = size.width * (0.5f + 0.40f * kotlin.math.sin(time + 1.8f))
                val spot2Y = size.height * (0.5f + 0.35f * kotlin.math.cos(time + 1.8f))

                val spot1Gradient = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = spot1Alpha),
                        primaryColor.copy(alpha = spot1AlphaMid),
                        Color.Transparent
                    ),
                    center = Offset(spot1X, spot1Y),
                    radius = size.width * 0.85f
                )

                val spot2Gradient = Brush.radialGradient(
                    colors = listOf(
                        tertiaryColor.copy(alpha = spot2Alpha),
                        tertiaryColor.copy(alpha = spot2AlphaMid),
                        Color.Transparent
                    ),
                    center = Offset(spot2X, spot2Y),
                    radius = size.width * 0.90f
                )

                onDrawBehind {
                    drawRect(color = colors.glassBackground)
                    drawRect(brush = spot1Gradient, blendMode = blendMode)
                    drawRect(brush = spot2Gradient, blendMode = blendMode)
                }
            }
            .border(1.dp, colors.glassBorder, cardShape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SurfaceAppIcon()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(model.appNameResId),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AboutMetadataBadge(text = model.versionName)
                    model.buildHash?.let { buildHash ->
                        AboutMetadataBadge(text = buildHash)
                    }
                    AboutMetadataBadge(text = model.buildVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LinkChipRow(
                links = model.primaryLinks,
                onOpenUri = onOpenUri,
            )

            Text(
                text = "Based on ArchiveTune by Rukamori.\nSource code available under GPL-3.0.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SurfaceAppIcon(modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        val iconTint = MaterialTheme.colorScheme.onPrimaryContainer
        val iconColorFilter = remember(iconTint) { ColorFilter.tint(iconTint) }
        Image(
            painter = painterResource(R.drawable.about_splash),
            contentDescription = null,
            colorFilter = iconColorFilter,
            modifier =
                Modifier
                    .padding(16.dp)
                    .size(64.dp),
        )
    }
}

@Composable
private fun AboutMetadataBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Badge(
        modifier = modifier.heightIn(min = 32.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkChipRow(
    links: AboutLinkCollection,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val solanaAddress = "DT3ckdbNuiQMR1mrCpBXCMrhLB19GckVv3YfxsLLiF8z"

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(links.size) { index ->
            val link = links[index]
            val label = stringResource(link.labelResId)
            val onClick = remember(link.url, onOpenUri) { { onOpenUri(link.url) } }

            InteractiveLinkChip(
                label = label,
                iconResId = link.iconResId,
                onClick = onClick,
            )
        }
        val solanaCopiedMessage = stringResource(R.string.solana_address_copied)

        InteractiveLinkChip(
            label = "Solana (SOL)",
            iconResId = R.drawable.ic_solana,
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Solana Address", solanaAddress)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, solanaCopiedMessage, Toast.LENGTH_SHORT).show()
            },
        )
    }
}
@Composable
private fun InteractiveLinkChip(
    label: String,
    iconResId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYumaColors.current
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier =
            modifier
                .yumaClickable(pressedScale = 0.93f, onClick = onClick)
                .yumaGlassCard(
                    shape = shape,
                    backgroundColor = colors.glassBackground,
                    borderColor = colors.glassBorder,
                ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LeadDeveloperSection(
    member: TeamMember,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var extractedColorHex by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AboutSectionHeader(title = stringResource(R.string.about_lead_developer))

        val colors = LocalYumaColors.current
        val cardShape = MaterialTheme.shapes.extraLarge

        val extractedColor = remember(extractedColorHex) {
            extractedColorHex?.let {
                try {
                    Color(android.graphics.Color.parseColor(it))
                } catch (e: Exception) {
                    null
                }
            }
        }

        val gradientStart = extractedColor?.copy(alpha = 0.35f)
            ?: MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            gradientStart,
                            colors.glassBackground,
                        )
                    )
                )
                .border(1.dp, colors.glassBorder, cardShape)
        ) {
            TeamMemberListItem(
                member = member,
                onOpenUri = onOpenUri,
                containerColor = Color.Transparent,
                extractedColor = extractedColor,
                onAvatarPixelsReady = { pixels ->
                    scope.launch(Dispatchers.IO) {
                        val hex = ColorExtractor.extractVibrantHex(pixels)
                        withContext(Dispatchers.Main) {
                            extractedColorHex = hex
                        }
                    }
                },
                avatarSize = 72.dp,
                minHeight = 104.dp,
            )
        }
    }
}

/*
 * ====================================================================
 * ЗАКОММЕНТИРОВАНО: Компоненты для списка участников команды
 * ====================================================================
 */
/*
@Composable
private fun TeamMemberSection(
    title: String,
    members: TeamMemberCollection,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AboutSectionHeader(title = title)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column {
                repeat(members.size) { index ->
                    TeamMemberListItem(
                        member = members[index],
                        onOpenUri = onOpenUri,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    )

                    if (index < members.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 88.dp),
                            thickness = SettingsDimensions.DividerThickness,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}
*/

@Composable
private fun AboutSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
        modifier =
            modifier.padding(
                horizontal = SettingsDimensions.SectionHeaderHorizontalPadding,
                vertical = SettingsDimensions.SectionHeaderBottomPadding,
            ),
    )
}

@Composable
private fun TeamMemberListItem(
    member: TeamMember,
    onOpenUri: (String) -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    extractedColor: Color? = null,
    onAvatarPixelsReady: ((IntArray) -> Unit)? = null,
    avatarSize: Dp = 56.dp,
    minHeight: Dp = 88.dp,
) {
    val profileUrl = member.profileUrl
    val itemClickModifier =
        remember(profileUrl, onOpenUri) {
            if (profileUrl.isNullOrBlank()) {
                Modifier
            } else {
                Modifier.clickable { onOpenUri(profileUrl) }
            }
        }
    val context = LocalContext.current

    ListItem(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .then(itemClickModifier),
        colors = ListItemDefaults.colors(containerColor = containerColor),
        leadingContent = {
            Box(
                modifier =
                    Modifier
                        .size(avatarSize)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        (extractedColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.20f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                                    ),
                            ),
                        ).border(
                            width = 1.5.dp,
                            brush =
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            (extractedColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.70f),
                                            (extractedColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.20f),
                                        ),
                                ),
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(member.avatarUrl)
                        .size(128, 128)
                        .allowHardware(false)
                        .build(),
                    contentDescription = member.name,
                    onSuccess = { success ->
                        if (onAvatarPixelsReady != null) {
                            val bmp = success.result.image.toBitmap()
                            val w = bmp.width
                            val h = bmp.height
                            if (w > 0 && h > 0) {
                                val pixels = IntArray(w * h)
                                bmp.getPixels(pixels, 0, w, 0, 0, w, h)
                                onAvatarPixelsReady(pixels)
                            }
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
        },
        headlineContent = {
            Text(
                text = member.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(member.positionResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            MemberLinkActions(
                links = member.links,
                onOpenUri = onOpenUri,
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberLinkActions(
    links: AboutLinkCollection,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.widthIn(max = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(links.size) { index ->
            val link = links[index]
            val onClick =
                remember(link.url, onOpenUri) {
                    { onOpenUri(link.url) }
                }

            FilledTonalIconButton(
                onClick = onClick,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(link.iconResId),
                    contentDescription = stringResource(link.labelResId),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/*
 * ====================================================================
 * ЗАКОММЕНТИРОВАНО: Секция внешних контрибьюторов GitHub
 * ====================================================================
 */
/*
@Composable
private fun ContributorsSection(
    state: AboutContributorsUiState,
    readMoreUrl: String,
    onOpenProfile: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AboutSectionHeader(title = stringResource(R.string.about_contributors))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            when (state) {
                AboutContributorsUiState.Loading -> {
                    ContributorStatusContent(
                        message = stringResource(R.string.loading),
                        showRetry = false,
                        onRetry = onRetry,
                    )
                }

                AboutContributorsUiState.Empty -> {
                    ContributorStatusContent(
                        message = stringResource(R.string.no_results_found),
                        showRetry = true,
                        onRetry = onRetry,
                    )
                }

                is AboutContributorsUiState.Error -> {
                    ContributorStatusContent(
                        message = stringResource(state.messageResId),
                        showRetry = true,
                        onRetry = onRetry,
                    )
                }

                is AboutContributorsUiState.Success -> {
                    ContributorList(
                        contributors = state.contributors,
                        readMoreUrl = readMoreUrl,
                        onOpenProfile = onOpenProfile,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContributorStatusContent(
    message: String,
    showRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!showRetry) {
            LoadingIndicator(modifier = Modifier.size(32.dp))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showRetry) {
            TextButton(onClick = onRetry) {
                Text(text = stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun ContributorList(
    contributors: AboutContributorUiCollection,
    readMoreUrl: String,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(contributors.size) { index ->
            val contributor = contributors[index]

            ContributorListItem(
                login = contributor.login,
                avatarUrl = contributor.avatarUrl,
                profileUrl = contributor.profileUrl,
                onOpenProfile = onOpenProfile,
            )

            if (index < contributors.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    thickness = SettingsDimensions.DividerThickness,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 72.dp),
            thickness = SettingsDimensions.DividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        ContributorReadMoreListItem(
            readMoreUrl = readMoreUrl,
            onOpenProfile = onOpenProfile,
        )
    }
}

@Composable
private fun ContributorListItem(
    login: String,
    avatarUrl: String,
    profileUrl: String,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemClickModifier =
        remember(profileUrl, onOpenProfile) {
            if (profileUrl.isBlank()) {
                Modifier
            } else {
                Modifier.clickable { onOpenProfile(profileUrl) }
            }
        }

    ListItem(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .then(itemClickModifier),
        colors =
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        leadingContent = {
            AsyncImage(
                model = avatarUrl,
                contentDescription = login,
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        },
        headlineContent = {
            Text(
                text = login,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun ContributorReadMoreListItem(
    readMoreUrl: String,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onClick =
        remember(readMoreUrl, onOpenProfile) {
            { onOpenProfile(readMoreUrl) }
        }

    ListItem(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(onClick = onClick),
        colors =
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.add_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        },
        headlineContent = {
            Text(
                text = stringResource(R.string.more),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
*/