/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.ui.screens.settings.components.YumaSettingsScaffold
import moe.rukamori.archivetune.ui.screens.settings.components.playerAwareBottomInset
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.Updater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(
    navController: NavController,
    channel: UpdateChannel = UpdateChannel.STABLE,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var releases by remember { mutableStateOf<List<ChangelogRelease>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun loadReleases(forceRefresh: Boolean) {
        val result =
            if (channel == UpdateChannel.DAILY_NIGHTLY) {
                Updater.getAllCanaryReleases()
            } else {
                Updater.getAllReleases(forceRefresh = forceRefresh)
            }
        result
            .onSuccess { rawReleases ->
                val filteredReleases =
                    if (channel == UpdateChannel.DAILY_NIGHTLY) {
                        rawReleases
                    } else {
                        rawReleases.filter { !it.prerelease }
                    }
                releases = toChangelogReleases(filteredReleases)
                error = null
            }.onFailure { throwable ->
                if (releases.isEmpty()) {
                    error = throwable.message
                }
            }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        val cachedReleases =
            if (channel == UpdateChannel.DAILY_NIGHTLY) {
                Updater.getCachedCanaryReleases()
            } else {
                Updater.getCachedReleases().filter { !it.prerelease }
            }
        if (cachedReleases.isNotEmpty()) {
            releases = toChangelogReleases(cachedReleases)
            isLoading = false
        }
        loadReleases(forceRefresh = true)
    }

    val timelineState =
        remember(releases, channel) {
            buildReleaseTimelineState(
                releases = releases,
                channel = channel,
                installedVersion = BuildConfig.VERSION_NAME,
            )
        }

    val actions =
        ReleaseTimelineActions(
            onOpenRelease = { url ->
                runCatching { uriHandler.openUri(url) }
                    .onFailure {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.release_open_failed))
                        }
                    }
            },
        )

    val hazeState = remember { HazeState() }
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 48.dp.toPx() } }

    YumaSettingsScaffold(
        title = { Text(stringResource(R.string.changelog)) },
        onBackClick = navController::navigateUp,
        onBackLongClick = navController::backToMain,
        scrollable = false,
        hazeState = hazeState,
        scrollProgress = {
            if (listState.firstVisibleItemIndex > 0) 1f else (listState.firstVisibleItemScrollOffset / thresholdPx).coerceIn(0f, 1f)
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { headerPadding ->
        val headerTopPadding = headerPadding.calculateTopPadding() + 8.dp
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
                when {
                    isLoading -> {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    error != null && releases.isEmpty() -> {
                        Column(
                            modifier =
                                Modifier
                                    .align(Alignment.Center)
                                    .padding(SettingsDimensions.ScreenHorizontalPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
                        ) {
                            Text(
                                text = stringResource(R.string.error_loading_changelog),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(
                                onClick = {
                                    isLoading = releases.isEmpty()
                                    error = null
                                    coroutineScope.launch {
                                        loadReleases(forceRefresh = true)
                                    }
                                },
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }

                    releases.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.no_releases),
                            modifier =
                                Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    else -> {
                        ReleaseTimeline(
                            state = timelineState,
                            actions = actions,
                            listState = listState,
                            topPadding = headerTopPadding,
                            bottomPadding = SettingsDimensions.ScreenBottomPadding + playerAwareBottomInset(),
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                                    .hazeSource(hazeState),
                        )
                    }
                }
            }
        }
}

@ThemePreviews
@Composable
private fun ChangelogScreenPreview() {
    TestThemeWrapper {
        ChangelogScreen(navController = rememberNavController())
    }
}
