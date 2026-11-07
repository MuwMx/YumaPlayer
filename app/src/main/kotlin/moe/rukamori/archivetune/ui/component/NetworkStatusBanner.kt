/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.network.NetworkBannerUiState

private data class NetworkBannerVisuals(
    val message: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
)

@Composable
fun NetworkStatusBanner(
    state: NetworkBannerUiState,
    modifier: Modifier = Modifier,
) {
    var lastVisibleState by remember { mutableStateOf<NetworkBannerUiState>(NetworkBannerUiState.Offline) }

    var dismissed by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val dismissThreshold = with(LocalDensity.current) { 48.dp.toPx() }

    LaunchedEffect(state) {
        dismissed = false
        dragOffset = Offset.Zero
    }

    if (state != NetworkBannerUiState.Hidden) {
        lastVisibleState = state
    }

    val visuals =
        when (lastVisibleState) {
            NetworkBannerUiState.Hidden,
            NetworkBannerUiState.Offline,
            -> {
                NetworkBannerVisuals(
                    message = "No internet connection",
                    icon = Icons.Default.CloudOff,
                    containerColor = Color(0xFF7F1D1D),
                    contentColor = Color.White,
                )
            }

            NetworkBannerUiState.BackOnline -> {
                NetworkBannerVisuals(
                    message = "Back online",
                    icon = Icons.Default.CloudDone,
                    containerColor = Color(0xFF1E8E3E),
                    contentColor = Color.White,
                )
            }
        }

    AnimatedVisibility(
        visible = state != NetworkBannerUiState.Hidden && !dismissed,
        modifier = modifier,
        enter =
            slideInVertically(animationSpec = tween(durationMillis = 250)) { -it } +
                fadeIn(animationSpec = tween(durationMillis = 180)),
        exit =
            slideOutVertically(animationSpec = tween(durationMillis = 220)) { -it } +
                fadeOut(animationSpec = tween(durationMillis = 180)),
        label = "networkStatusBanner",
    ) {
        Surface(
            modifier =
                Modifier
                    .graphicsLayer {
                        translationX = dragOffset.x
                        translationY = dragOffset.y
                    }
                    .pointerInput(state, dismissThreshold) {
                        detectDragGestures(
                            onDragEnd = {
                                if (!dismissed) dragOffset = Offset.Zero
                            },
                            onDragCancel = {
                                if (!dismissed) dragOffset = Offset.Zero
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            if (!dismissed) {
                                dragOffset += dragAmount
                                if (dragOffset.getDistance() >= dismissThreshold) {
                                    dismissed = true
                                }
                            }
                        }
                    },
            shape = RoundedCornerShape(14.dp),
            color = visuals.containerColor,
            contentColor = visuals.contentColor,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier =
                    Modifier
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = visuals.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = visuals.contentColor,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = visuals.message,
                    color = visuals.contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}
