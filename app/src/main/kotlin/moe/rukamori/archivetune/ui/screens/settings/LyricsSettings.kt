/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableBetterLyricsKey
import moe.rukamori.archivetune.constants.EnableKugouKey
import moe.rukamori.archivetune.constants.EnableLrcLibKey
import moe.rukamori.archivetune.constants.EnablePaxsenixAppleMusicLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixMusixmatchLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixNeteaseLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixSpotifyLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixYouTubeLyricsKey
import moe.rukamori.archivetune.constants.EnableSimpMusicLyricsKey
import moe.rukamori.archivetune.constants.EnableUnisonLyricsKey
import moe.rukamori.archivetune.constants.EnableYouLyPlusLyricsKey
import moe.rukamori.archivetune.constants.LyricsClickKey
import moe.rukamori.archivetune.constants.LyricsLineBlurKey
import moe.rukamori.archivetune.constants.LyricsLineSpacingKey
import moe.rukamori.archivetune.constants.LyricsProviderOrderKey
import moe.rukamori.archivetune.constants.LyricsRomanizeChineseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeHindiKey
import moe.rukamori.archivetune.constants.LyricsRomanizeJapaneseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeKoreanKey
import moe.rukamori.archivetune.constants.LyricsRomanizeOtherLanguagesKey
import moe.rukamori.archivetune.constants.LyricsScrollKey
import moe.rukamori.archivetune.constants.LyricsTextSizeKey
import moe.rukamori.archivetune.constants.PreloadQueueLyricsEnabledKey
import moe.rukamori.archivetune.constants.QueueLyricsPreloadCountKey
import moe.rukamori.archivetune.constants.deserializeLyricsProviderOrder
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.ContentSettingsViewModel

@Composable
fun LyricsSettings(
    navController: NavController,
    viewModel: ContentSettingsViewModel = hiltViewModel(),
) {
    var showClearLyricsDialog by remember { mutableStateOf(false) }
    var showPaxsenixStatsDialog by remember { mutableStateOf(false) }
    var showProviderOrderDialog by rememberSaveable { mutableStateOf(false) }
    var showLyricsTextSizeDialog by rememberSaveable { mutableStateOf(false) }
    var showLyricsLineSpacingDialog by rememberSaveable { mutableStateOf(false) }

    val (lyricsClick, onLyricsClickChange) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsScroll, onLyricsScrollChange) = rememberPreference(LyricsScrollKey, defaultValue = true)
    val (lyricsTextSize, onLyricsTextSizeChange) =
        rememberPreference(LyricsTextSizeKey, defaultValue = LyricsContract.DEFAULT_TEXT_SIZE)
    val (lyricsLineSpacing, onLyricsLineSpacingChange) =
        rememberPreference(LyricsLineSpacingKey, defaultValue = LyricsContract.DEFAULT_LINE_SPACING)
    val (enableLrclib, onEnableLrclibChange) = rememberPreference(key = EnableLrcLibKey, defaultValue = true)
    val (enableKugou, onEnableKugouChange) = rememberPreference(key = EnableKugouKey, defaultValue = true)
    val (enableBetterLyrics, onEnableBetterLyricsChange) = rememberPreference(key = EnableBetterLyricsKey, defaultValue = true)
    val (enableYouLyPlusLyrics, onEnableYouLyPlusLyricsChange) =
        rememberPreference(key = EnableYouLyPlusLyricsKey, defaultValue = true)
    val (enableSimpMusicLyrics, onEnableSimpMusicLyricsChange) =
        rememberPreference(key = EnableSimpMusicLyricsKey, defaultValue = true)
    val (enablePaxsenixLyrics, onEnablePaxsenixLyricsChange) =
        rememberPreference(key = EnablePaxsenixLyricsKey, defaultValue = true)
    val (enablePaxsenixAppleMusicLyrics, onEnablePaxsenixAppleMusicLyricsChange) =
        rememberPreference(
            key = EnablePaxsenixAppleMusicLyricsKey,
            defaultValue = true,
        )
    val (enablePaxsenixNeteaseLyrics, onEnablePaxsenixNeteaseLyricsChange) =
        rememberPreference(
            key = EnablePaxsenixNeteaseLyricsKey,
            defaultValue = true,
        )
    val (enablePaxsenixSpotifyLyrics, onEnablePaxsenixSpotifyLyricsChange) =
        rememberPreference(
            key = EnablePaxsenixSpotifyLyricsKey,
            defaultValue = true,
        )
    val (enablePaxsenixMusixmatchLyrics, onEnablePaxsenixMusixmatchLyricsChange) =
        rememberPreference(
            key = EnablePaxsenixMusixmatchLyricsKey,
            defaultValue = true,
        )
    val (enablePaxsenixYouTubeLyrics, onEnablePaxsenixYouTubeLyricsChange) =
        rememberPreference(
            key = EnablePaxsenixYouTubeLyricsKey,
            defaultValue = true,
        )
    val (enableUnisonLyrics, onEnableUnisonLyricsChange) =
        rememberPreference(key = EnableUnisonLyricsKey, defaultValue = true)
    val (providerOrderStr, onProviderOrderStrChange) =
        rememberPreference(
            key = LyricsProviderOrderKey,
            defaultValue = "",
        )
    val providerOrder =
        remember(providerOrderStr) {
            deserializeLyricsProviderOrder(providerOrderStr)
        }
    val (lyricsLineBlur, onLyricsLineBlurChange) = rememberPreference(LyricsLineBlurKey, defaultValue = true)
    val (lyricsRomanizeJapanese, onLyricsRomanizeJapaneseChange) =
        rememberPreference(LyricsRomanizeJapaneseKey, defaultValue = true)
    val (lyricsRomanizeKorean, onLyricsRomanizeKoreanChange) =
        rememberPreference(LyricsRomanizeKoreanKey, defaultValue = true)
    val (lyricsRomanizeChinese, onLyricsRomanizeChineseChange) =
        rememberPreference(LyricsRomanizeChineseKey, defaultValue = true)
    val (lyricsRomanizeHindi, onLyricsRomanizeHindiChange) =
        rememberPreference(LyricsRomanizeHindiKey, defaultValue = true)
    val (lyricsRomanizeOtherLanguages, onLyricsRomanizeOtherLanguagesChange) =
        rememberPreference(
            LyricsRomanizeOtherLanguagesKey,
            defaultValue = true,
        )
    val (preloadQueueLyricsEnabled, onPreloadQueueLyricsEnabledChange) =
        rememberPreference(
            PreloadQueueLyricsEnabledKey,
            defaultValue = true,
        )
    val (queueLyricsPreloadCount, onQueueLyricsPreloadCountChange) =
        rememberPreference(
            QueueLyricsPreloadCountKey,
            defaultValue = LyricsContract.DEFAULT_QUEUE_PRELOAD_COUNT,
        )

    if (showClearLyricsDialog) {
        ClearLyricsDialog(
            onDismiss = { showClearLyricsDialog = false },
            onConfirm = {
                viewModel.clearLyricsCache()
                showClearLyricsDialog = false
            },
        )
    }

    if (showPaxsenixStatsDialog) {
        val statsState by viewModel.paxsenixStatsState.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            viewModel.fetchPaxsenixStats()
        }

        PaxsenixStatsDialog(
            state = statsState,
            onDismiss = { showPaxsenixStatsDialog = false },
            onRetry = { viewModel.fetchPaxsenixStats() },
        )
    }

    if (showProviderOrderDialog) {
        LyricsProviderOrderDialog(
            initialOrder = providerOrder,
            onDismiss = { showProviderOrderDialog = false },
            onConfirm = { newOrder ->
                onProviderOrderStrChange(newOrder.joinToString(",") { it.name })
                showProviderOrderDialog = false
            },
        )
    }

    if (showLyricsTextSizeDialog) {
        LyricsTextSizeDialog(
            initialTextSize = lyricsTextSize,
            onDismiss = { showLyricsTextSizeDialog = false },
            onConfirm = { newTextSize ->
                onLyricsTextSizeChange(newTextSize)
                showLyricsTextSizeDialog = false
            },
        )
    }

    if (showLyricsLineSpacingDialog) {
        LyricsLineSpacingDialog(
            initialLineSpacing = lyricsLineSpacing,
            onDismiss = { showLyricsLineSpacingDialog = false },
            onConfirm = { newLineSpacing ->
                onLyricsLineSpacingChange(newLineSpacing)
                showLyricsLineSpacingDialog = false
            },
        )
    }

    val state =
        LyricsSettingsUiState(
            lyricsClick = lyricsClick,
            lyricsScroll = lyricsScroll,
            lyricsLineBlur = lyricsLineBlur,
            lyricsTextSize = lyricsTextSize,
            lyricsLineSpacing = lyricsLineSpacing,
            enableBetterLyrics = enableBetterLyrics,
            enableYouLyPlusLyrics = enableYouLyPlusLyrics,
            enableLrclib = enableLrclib,
            enableKugou = enableKugou,
            enableUnisonLyrics = enableUnisonLyrics,
            enableSimpMusicLyrics = enableSimpMusicLyrics,
            enablePaxsenixLyrics = enablePaxsenixLyrics,
            enablePaxsenixAppleMusicLyrics = enablePaxsenixAppleMusicLyrics,
            enablePaxsenixNeteaseLyrics = enablePaxsenixNeteaseLyrics,
            enablePaxsenixSpotifyLyrics = enablePaxsenixSpotifyLyrics,
            enablePaxsenixMusixmatchLyrics = enablePaxsenixMusixmatchLyrics,
            enablePaxsenixYouTubeLyrics = enablePaxsenixYouTubeLyrics,
            providerOrder = providerOrder,
            lyricsRomanizeJapanese = lyricsRomanizeJapanese,
            lyricsRomanizeKorean = lyricsRomanizeKorean,
            lyricsRomanizeChinese = lyricsRomanizeChinese,
            lyricsRomanizeHindi = lyricsRomanizeHindi,
            lyricsRomanizeOtherLanguages = lyricsRomanizeOtherLanguages,
            preloadQueueLyricsEnabled = preloadQueueLyricsEnabled,
            queueLyricsPreloadCount = queueLyricsPreloadCount,
        )

    val actions =
        LyricsSettingsUiActions(
            onNavigateUp = navController::navigateUp,
            onNavigateHome = navController::backToMain,
            onLyricsClickChange = onLyricsClickChange,
            onLyricsScrollChange = onLyricsScrollChange,
            onLyricsLineBlurChange = onLyricsLineBlurChange,
            onLyricsTextSizeChange = onLyricsTextSizeChange,
            onLyricsLineSpacingChange = onLyricsLineSpacingChange,
            onOpenTextSizeDialog = { showLyricsTextSizeDialog = true },
            onOpenLineSpacingDialog = { showLyricsLineSpacingDialog = true },
            onEnableBetterLyricsChange = onEnableBetterLyricsChange,
            onEnableYouLyPlusLyricsChange = onEnableYouLyPlusLyricsChange,
            onEnableLrclibChange = onEnableLrclibChange,
            onEnableKugouChange = onEnableKugouChange,
            onEnableUnisonLyricsChange = onEnableUnisonLyricsChange,
            onEnableSimpMusicLyricsChange = onEnableSimpMusicLyricsChange,
            onEnablePaxsenixLyricsChange = onEnablePaxsenixLyricsChange,
            onEnablePaxsenixAppleMusicLyricsChange = onEnablePaxsenixAppleMusicLyricsChange,
            onEnablePaxsenixNeteaseLyricsChange = onEnablePaxsenixNeteaseLyricsChange,
            onEnablePaxsenixSpotifyLyricsChange = onEnablePaxsenixSpotifyLyricsChange,
            onEnablePaxsenixMusixmatchLyricsChange = onEnablePaxsenixMusixmatchLyricsChange,
            onEnablePaxsenixYouTubeLyricsChange = onEnablePaxsenixYouTubeLyricsChange,
            onOpenPaxsenixStats = { showPaxsenixStatsDialog = true },
            onOpenProviderOrderDialog = { showProviderOrderDialog = true },
            onLyricsRomanizeJapaneseChange = onLyricsRomanizeJapaneseChange,
            onLyricsRomanizeKoreanChange = onLyricsRomanizeKoreanChange,
            onLyricsRomanizeChineseChange = onLyricsRomanizeChineseChange,
            onLyricsRomanizeHindiChange = onLyricsRomanizeHindiChange,
            onLyricsRomanizeOtherLanguagesChange = onLyricsRomanizeOtherLanguagesChange,
            onPreloadQueueLyricsEnabledChange = onPreloadQueueLyricsEnabledChange,
            onQueueLyricsPreloadCountChange = onQueueLyricsPreloadCountChange,
            onClearLyricsCacheClick = { showClearLyricsDialog = true },
        )

    LyricsSettingsScreen(
        state = state,
        actions = actions,
    )
}

@Composable
internal fun LyricsSettingsScreen(
    state: LyricsSettingsUiState,
    actions: LyricsSettingsUiActions,
    modifier: Modifier = Modifier,
) {
    SettingsScreenBackground(modifier = modifier) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.lyrics)) },
                    navigationIcon = {
                        IconButton(
                            onClick = actions.onNavigateUp,
                            onLongClick = actions.onNavigateHome,
                        ) {
                            Icon(
                                painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
                )
            },
        ) { innerPadding ->
            LyricsSettingsContent(
                state = state,
                actions = actions,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            LocalPlayerAwareWindowInsets.current.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                            ),
                        )
                        .padding(top = innerPadding.calculateTopPadding())
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = SettingsDimensions.ScreenBottomPadding),
            )
        }
    }
}
