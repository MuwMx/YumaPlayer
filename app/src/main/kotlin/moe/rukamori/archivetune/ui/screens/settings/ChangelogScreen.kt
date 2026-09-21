/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MarkdownText
import moe.rukamori.archivetune.ui.theme.ThemePreviews
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.ReleaseInfo
import moe.rukamori.archivetune.utils.Updater
import java.text.SimpleDateFormat
import java.util.Locale
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import androidx.navigation.compose.rememberNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(
    navController: NavController,
    channel: UpdateChannel = UpdateChannel.STABLE,
) {
    val coroutineScope = rememberCoroutineScope()
    var releases by remember { mutableStateOf<List<ReleaseInfo>>(emptyList()) }
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
            .onSuccess { r ->
                releases = when (channel) {
                    UpdateChannel.DAILY_NIGHTLY -> r
                    else -> r.filter { !it.prerelease }
                }
                error = null
            }.onFailure { e ->
                if (releases.isEmpty()) {
                    error = e.message
                }
            }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        val cachedAll = Updater.getCachedReleases()
        val cachedReleases =
            when (channel) {
                UpdateChannel.DAILY_NIGHTLY -> cachedAll.filter { it.prerelease }
                else -> cachedAll.filter { !it.prerelease }
            }
        if (cachedReleases.isNotEmpty()) {
            releases = cachedReleases
            isLoading = false
        }
        loadReleases(forceRefresh = true)
    }

    SettingsScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.changelog)) },
                    navigationIcon = {
                        IconButton(
                            onClick = navController::navigateUp,
                            onLongClick = navController::backToMain,
                        ) {
                            Icon(
                                painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                )
            },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
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
                                .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.error_loading_changelog),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            isLoading = releases.isEmpty()
                            error = null
                            coroutineScope.launch {
                                loadReleases(forceRefresh = true)
                            }
                        }, shapes = ButtonDefaults.shapes()) {
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
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                        verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }

                        items(
                            items = releases,
                            key = { it.tagName.ifBlank { it.name } + it.publishedAt },
                            contentType = { "changelog_release" }
                        ) { release ->
                            ReleaseCard(release = release)
                        }

                        item { Spacer(modifier = Modifier.height(SettingsDimensions.ScreenBottomPadding)) }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun ReleaseCard(release: ReleaseInfo) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displayDateFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }

    val formattedDate =
        remember(release.publishedAt) {
            try {
                val date = dateFormat.parse(release.publishedAt.substring(0, 10))
                date?.let { displayDateFormat.format(it) } ?: release.publishedAt
            } catch (e: Exception) {
                release.publishedAt
            }
        }

    val colors = LocalYumaColors.current
    val cardShape = RoundedCornerShape(28.dp)
    val imageShape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .yumaGlassCard(
                shape = cardShape,
                backgroundColor = colors.glassBackground,
                borderColor = colors.glassBorder,
                strokeWidth = SettingsDimensions.GlassBorderThickness,
            )
            .padding(16.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!release.imageUrl.isNullOrBlank()) {
                val context = LocalContext.current
                val imageModel = remember(context, release.imageUrl) {
                    ImageRequest.Builder(context)
                        .data(release.imageUrl)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp)
                        .clip(imageShape),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = release.name.ifBlank { release.tagName },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!release.body.isNullOrBlank()) {
                MarkdownText(
                    markdown = release.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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
