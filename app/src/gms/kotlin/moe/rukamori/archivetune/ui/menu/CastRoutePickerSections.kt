/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.cast.CastUiState

@Composable
internal fun CastRoutePickerHeader(onDismissRequest: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                painter = painterResource(androidx.media3.cast.R.drawable.media_route_button_disconnected),
                contentDescription = null,
                modifier =
                    Modifier
                        .padding(12.dp)
                        .size(28.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.cast_devices),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.cast_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalIconButton(onClick = onDismissRequest) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.close_dialog),
            )
        }
    }
}

@Composable
internal fun CastRoutePickerStatus(
    castState: CastUiState,
    screenState: CastRoutePickerScreenState,
    onDisconnect: () -> Unit,
) {
    val connectedDeviceName = castState.device?.name

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(androidx.media3.cast.R.drawable.media_route_button_disconnected),
                contentDescription = null,
                tint =
                    if (castState.isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text =
                        if (castState.isConnected && connectedDeviceName != null) {
                            stringResource(R.string.cast_connected_to, connectedDeviceName)
                        } else {
                            screenState.statusText()
                        },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (castState.isConnected) {
                TextButton(onClick = onDisconnect) {
                    Text(text = stringResource(R.string.cast_disconnect))
                }
            }
        }
    }
}

@Composable
internal fun CastRoutePickerContent(
    screenState: CastRoutePickerScreenState,
    onRouteClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.cast_available_devices),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        when (screenState) {
            CastRoutePickerScreenState.Loading -> CastRoutePickerLoading()
            is CastRoutePickerScreenState.Empty -> CastRoutePickerEmpty(screenState.reason)
            is CastRoutePickerScreenState.Error -> CastRoutePickerError(messageResId = screenState.messageResId)
            is CastRoutePickerScreenState.Success -> CastRoutePickerRouteList(screenState.routes, onRouteClick)
        }
    }
}

@Composable
internal fun CastRoutePickerScreenState.statusText(): String =
    when (this) {
        CastRoutePickerScreenState.Loading -> stringResource(R.string.cast_searching_devices)
        is CastRoutePickerScreenState.Empty ->
            when (reason) {
                CastEmptyReason.NO_DEVICES -> stringResource(R.string.cast_no_devices)
                CastEmptyReason.NO_PLAY_SERVICES -> stringResource(R.string.cast_no_play_services)
                CastEmptyReason.NO_NETWORK -> stringResource(R.string.cast_no_network)
            }
        is CastRoutePickerScreenState.Error -> stringResource(messageResId)
        is CastRoutePickerScreenState.Success -> stringResource(R.string.cast_available_device_count, routes.size)
    }
