/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.ActionPromptDialog
import moe.rukamori.archivetune.ui.screens.settings.components.YumaSettingsScaffold
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews
import moe.rukamori.archivetune.ui.utils.backToMain

@Composable
fun CanvasSettings(
    navController: NavController,
    viewModel: CanvasSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event.targetState.isAtLeast(Lifecycle.State.STARTED)) {
                viewModel.setScreenActive(true)
            } else if (event == Lifecycle.Event.ON_STOP) {
                viewModel.setScreenActive(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setScreenActive(false)
        }
    }

    CanvasSettingsScreen(
        state = state,
        onIntent = onIntent,
        onErrorDismissed = viewModel::onErrorDismissed,
        onBackClick = navController::navigateUp,
        onBackLongClick = navController::backToMain,
    )
}

@Composable
internal fun CanvasSettingsScreen(
    state: CanvasSettingsUiState,
    onIntent: (CanvasSettingsUiIntent) -> Unit,
    onErrorDismissed: () -> Unit,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.errorMessageRes) {
        val errorRes = state.errorMessageRes ?: return@LaunchedEffect
        val message = context.getString(errorRes)
        snackbarHostState.showSnackbar(message)
        onErrorDismissed()
    }

    Box(modifier = modifier.fillMaxSize()) {
        YumaSettingsScaffold(
            title = { Text(stringResource(R.string.archivetune_canvas)) },
            onBackClick = onBackClick,
            onBackLongClick = onBackLongClick,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) {
            CanvasGeneralSection(
                enabled = state.enabled,
                source = state.source,
                isBusy = state.isBusy,
                onIntent = onIntent,
            )

            if (state.enabled) {
                CanvasHealthSection(
                    health = state.health,
                    selectedSource = state.source,
                    isBusy = state.isBusy,
                    onRefresh = { onIntent(CanvasSettingsUiIntent.RefreshHealth) },
                )

                CanvasCacheSection(
                    cacheLimitMb = state.cacheLimitMb,
                    cacheBytes = state.cacheBytes,
                    isBusy = state.isBusy,
                    onLimitSelected = { onIntent(CanvasSettingsUiIntent.SetCacheLimit(it)) },
                    onClearClick = { showClearDialog = true },
                )
            }
        }

        if (showClearDialog) {
            ActionPromptDialog(
                title = stringResource(R.string.clear_canvas_cache),
                onDismiss = { showClearDialog = false },
                onConfirm = {
                    onIntent(CanvasSettingsUiIntent.ClearCache)
                    showClearDialog = false
                },
                onCancel = { showClearDialog = false },
                content = {
                    Text(text = stringResource(R.string.clear_canvas_cache_dialog))
                },
            )
        }
    }
}

@ThemePreviews
@Composable
private fun CanvasSettingsScreenPreview() {
    TestThemeWrapper {
        CanvasSettingsScreen(
            state = CanvasSettingsUiState(enabled = true),
            onIntent = {},
            onErrorDismissed = {},
            onBackClick = {},
            onBackLongClick = {},
        )
    }
}
