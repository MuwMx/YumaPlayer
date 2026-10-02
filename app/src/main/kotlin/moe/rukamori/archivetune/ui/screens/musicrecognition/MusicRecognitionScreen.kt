/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.musicrecognition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.screens.search.onlineSearchResultRoute
import moe.rukamori.archivetune.viewmodels.MusicRecognitionEvent
import moe.rukamori.archivetune.viewmodels.MusicRecognitionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicRecognitionScreen(
    navController: NavHostController,
    viewModel: MusicRecognitionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptics = rememberYumaHaptics()
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val historySheetState by viewModel.historySheetState.collectAsStateWithLifecycle()
    val modalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val onNavigateBack =
        remember(navController) {
            {
                navController.navigateUp()
                Unit
            }
        }
    val onShowHistory = remember(viewModel) { { viewModel.onHistoryVisibilityChanged(true) } }
    val onListen = remember(viewModel) { { viewModel.onListenRequested() } }
    val onCancel = remember(viewModel) { { viewModel.onCancelRecognition() } }
    val onHistoryDismiss = remember(viewModel) { { viewModel.onHistoryVisibilityChanged(false) } }
    val onHistoryQueryChange =
        remember(viewModel) { { query: String -> viewModel.onHistoryQueryChanged(query) } }
    val onSearch =
        remember(viewModel) { { query: String -> viewModel.onTrackSearchRequested(query) } }
    val onOpenUri =
        remember(viewModel) { { uri: String -> viewModel.onExternalUriRequested(uri) } }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onMicrophonePermissionResult(granted)
        }

    LaunchedEffect(viewModel, context, navController, haptics) {
        viewModel.events.collect { event ->
            when (event) {
                MusicRecognitionEvent.RequestMicrophonePermission -> {
                    val permissionGranted =
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                    if (permissionGranted) {
                        viewModel.onMicrophonePermissionResult(true)
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                MusicRecognitionEvent.RecognitionStarted -> {
                    haptics.longPress()
                }

                is MusicRecognitionEvent.Search -> {
                    navController.navigate(onlineSearchResultRoute(event.query))
                }

                is MusicRecognitionEvent.OpenUri -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.uri))
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    }
                }
            }
        }
    }

    MusicRecognitionContent(
        state = screenState,
        onNavigateBack = onNavigateBack,
        onShowHistory = onShowHistory,
        onListen = onListen,
        onCancel = onCancel,
        onAllowPermission = onListen,
        onSearch = onSearch,
        onOpenUri = onOpenUri,
    )

    if (historySheetState.visible) {
        RecognitionHistoryBottomSheet(
            state = historySheetState,
            sheetState = modalSheetState,
            onDismiss = onHistoryDismiss,
            onQueryChange = onHistoryQueryChange,
            onSearch = onSearch,
            onOpenUri = onOpenUri,
        )
    }
}
