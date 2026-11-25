/*
 * YumaPlayer (2026) | Original work by MuwMix
 * https://github.com/MuwMx/YumaPlayer
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.queue_0

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.sett.SettingsMenuRow
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.darkYumaColorScheme
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

private val QueueMenuMaxWidth = 560.dp
private val QueueMenuDismissThreshold = 64.dp
private val QueueMenuHeaderDragHeight = 24.dp
private const val QueueMenuExitTranslatePx = 300f

@Composable
fun QueueOptionsMenu(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onAction: (PlayerAction) -> Unit,
    onSaveAsPlaylist: () -> Unit,
    state: PlayerUiState,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberYumaHaptics()
    val font = LocalArchiveTuneFontFamily.current

    var dismissRequested by remember { mutableStateOf(false) }

    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentVisible by rememberUpdatedState(isVisible)
    val requestDismiss: () -> Unit = remember {
        {
            if (!dismissRequested) {
                dismissRequested = true
                currentOnDismiss()
            }
        }
    }

    val density = LocalDensity.current
    val dismissThresholdPx = remember(density) { with(density) { QueueMenuDismissThreshold.toPx() } }
    val scope = rememberCoroutineScope()
    var panelOffsetPx by remember {
        mutableFloatStateOf(if (isVisible) 0f else QueueMenuExitTranslatePx)
    }
    var offsetAnimationJob by remember { mutableStateOf<Job?>(null) }
    val panelSpring = remember { spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessLow) }

    fun animatePanelOffsetTo(target: Float) {
        offsetAnimationJob?.cancel()
        if (panelOffsetPx == target) return
        offsetAnimationJob = scope.launch {
            animate(
                initialValue = panelOffsetPx,
                targetValue = target,
                animationSpec = panelSpring,
            ) { value, _ ->
                panelOffsetPx = value
            }
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            dismissRequested = false
        } else {
            dismissRequested = true
        }
        animatePanelOffsetTo(
            if (isVisible) 0f else maxOf(panelOffsetPx, QueueMenuExitTranslatePx)
        )
    }

    if (isVisible) {
        BackHandler {
            requestDismiss()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300, easing = LinearEasing),
        label = "QueueMenuAlpha",
    )

    val panelShape = remember { RoundedCornerShape(SettingsDimensions.BottomSheetCornerRadius) }

    if (isVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            sheetMaxWidth = QueueMenuMaxWidth,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            dragHandle = null,
            modifier = modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 48.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.BottomSheetHorizontalPadding),
                shape = panelShape,
                color = Color(state.darkMutedColor).copy(alpha = 1f),
                shadowElevation = 10.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SettingsDimensions.BottomSheetContentPaddingH)
                        .padding(
                            top = SettingsDimensions.BottomSheetContentPaddingTop,
                            bottom = SettingsDimensions.BottomSheetContentPaddingBottom,
                        ),
                ) {
                    val darkScheme = darkColorScheme()
                    MaterialTheme(colorScheme = darkScheme) {
                        CompositionLocalProvider(
                            LocalContentColor provides Color.White,
                            LocalYumaColors provides darkYumaColorScheme(darkScheme),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(
                                                width = SettingsDimensions.BottomSheetDragHandleWidth,
                                                height = SettingsDimensions.BottomSheetDragHandleHeight,
                                            )
                                            .clip(CircleShape)
                                            .background(
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = SettingsDimensions.YumaRowArrowAlpha,
                                                ),
                                            ),
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false)
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    Text(
                                        text = stringResource(R.string.queue),
                                        color = Color.White,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = font,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
                                    )

                                    val count = 3

                                    SettingsMenuRow(
                                        title = stringResource(R.string.clear_queue),
                                        subtitle = stringResource(R.string.clear_queue_desc),
                                        iconResId = R.drawable.clear_all,
                                        onClick = {
                                            haptics.click()
                                            onAction(PlayerAction.ClearQueue)
                                            requestDismiss()
                                        },
                                        index = 0,
                                        count = count,
                                    )

                                    Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

                                    SettingsMenuRow(
                                        title = stringResource(R.string.shuffle),
                                        subtitle = stringResource(R.string.shuffle_queue_desc),
                                        iconResId = R.drawable.shuffle,
                                        onClick = {
                                            haptics.click()
                                            onAction(PlayerAction.ShuffleQueue)
                                            requestDismiss()
                                        },
                                        index = 1,
                                        count = count,
                                    )

                                    Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))

                                    SettingsMenuRow(
                                        title = stringResource(R.string.save_as_playlist),
                                        subtitle = stringResource(R.string.save_as_playlist_desc),
                                        iconResId = R.drawable.playlist_add,
                                        onClick = {
                                            haptics.click()
                                            onSaveAsPlaylist()
                                        },
                                        index = 2,
                                        count = count,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
