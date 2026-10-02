/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.cast.CastScreenState
import moe.rukamori.archivetune.cast.CastUiState
import moe.rukamori.archivetune.cast.CastViewModel
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@Composable
fun rememberCastPlayerMenuAction(): NewAction? {
    val viewModel: CastViewModel = viewModel()
    val routePickerViewModel: CastRoutePickerViewModel = viewModel()
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val isRoutePickerVisible by viewModel.isRoutePickerVisible.collectAsStateWithLifecycle()
    val routePickerState by routePickerViewModel.screenState.collectAsStateWithLifecycle()
    val castState = (screenState as? CastScreenState.Success)?.uiState ?: return null
    if (!castState.isAvailable) return null

    if (isRoutePickerVisible) {
        CastRoutePickerBottomSheet(
            castState = castState,
            screenState = routePickerState,
            onDismissRequest = viewModel::hideRoutePicker,
            onStartDiscovery = routePickerViewModel::startDiscovery,
            onStopDiscovery = routePickerViewModel::stopDiscovery,
            onRouteClick =
                remember(routePickerViewModel, viewModel) {
                    { routeId: String ->
                        if (routePickerViewModel.selectRoute(routeId)) {
                            viewModel.hideRoutePicker()
                        }
                    }
                },
            onDisconnect =
                remember(viewModel) {
                    {
                        viewModel.disconnect()
                        viewModel.hideRoutePicker()
                    }
                },
        )
    }

    val text = stringResource(R.string.cast)
    return NewAction(
        icon = {
            Icon(
                painter = painterResource(androidx.media3.cast.R.drawable.media_route_button_disconnected),
                contentDescription = null,
                tint = Color.White.copy(alpha = SettingsDimensions.YumaRowIconAlpha),
                modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
            )
        },
        text = text,
        onClick = viewModel::showRoutePicker,
    )
}

@Composable
private fun CastRoutePickerBottomSheet(
    castState: CastUiState,
    screenState: CastRoutePickerScreenState,
    onDismissRequest: () -> Unit,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onRouteClick: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    DisposableEffect(onStartDiscovery, onStopDiscovery) {
        onStartDiscovery()
        onDispose(onStopDiscovery)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 12.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )
                Column(
                    modifier =
                        Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    CastRoutePickerHeader(onDismissRequest = onDismissRequest)

                    CastRoutePickerStatus(
                        castState = castState,
                        screenState = screenState,
                        onDisconnect = onDisconnect,
                    )

                    CastRoutePickerContent(
                        screenState = screenState,
                        onRouteClick = onRouteClick,
                    )
                }
            }
        }
    }
}
