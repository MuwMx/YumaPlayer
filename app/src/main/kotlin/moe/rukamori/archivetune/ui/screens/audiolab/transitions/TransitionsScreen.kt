/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.audiolab.transitions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AutomixEnabledKey
import moe.rukamori.archivetune.constants.CrossfadeDurationKey
import moe.rukamori.archivetune.constants.CrossfadeEnabledKey
import moe.rukamori.archivetune.constants.CrossfadeGaplessKey
import moe.rukamori.archivetune.constants.CrossmixAlwaysMixKey
import moe.rukamori.archivetune.constants.CrossmixLength
import moe.rukamori.archivetune.constants.CrossmixLengthKey
import moe.rukamori.archivetune.constants.CrossmixStuntsKey
import moe.rukamori.archivetune.constants.CrossmixStyleAuto
import moe.rukamori.archivetune.constants.CrossmixStyleKey
import moe.rukamori.archivetune.constants.CrossmixTempoKey
import moe.rukamori.archivetune.constants.CrossmixTempoMatch
import moe.rukamori.archivetune.constants.TransitionMode
import moe.rukamori.archivetune.constants.TransitionModeKey
import moe.rukamori.archivetune.playback.crossmix.CrossmixStyle
import moe.rukamori.archivetune.playback.crossmix.StyleUse
import moe.rukamori.archivetune.ui.component.MarqueeText
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.ui.screens.audiolab.ListenToAudioTap
import moe.rukamori.archivetune.ui.screens.settings.components.YumaSettingsScaffold
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.roundToInt

private const val MinDurationSeconds = 1f
private const val MaxDurationSeconds = 20f
private val DurationPresets = listOf(4, 8, 12, 16, 20)

private val PinnableStyles: List<CrossmixStyle> =
    listOf(StyleUse.Everyday, StyleUse.Lift, StyleUse.Fall, StyleUse.Flavour)
        .flatMap { use -> CrossmixStyle.entries.filter { it.use == use } }

@Composable
fun TransitionsScreen(navController: NavController) {
    val playerConnection = LocalPlayerConnection.current
    val isPlaying = playerConnection?.isPlaying?.collectAsStateWithLifecycle()?.value ?: false
    val service = playerConnection?.service

    val (storedMode, onStoredModeChange) = rememberEnumPreference(
        TransitionModeKey,
        defaultValue = TransitionMode.OFF,
    )
    val (crossfadeEnabled, onCrossfadeEnabledChange) = rememberPreference(
        CrossfadeEnabledKey,
        defaultValue = false,
    )
    val (automixEnabled, onAutomixEnabledChange) = rememberPreference(
        AutomixEnabledKey,
        defaultValue = false,
    )
    val (crossfadeDuration, onCrossfadeDurationChange) = rememberPreference(
        CrossfadeDurationKey,
        defaultValue = 5f,
    )
    val (gapless, onGaplessChange) = rememberPreference(
        CrossfadeGaplessKey,
        defaultValue = true,
    )

    val selectedMode = when {
        storedMode != TransitionMode.OFF -> storedMode
        automixEnabled -> TransitionMode.AUTOMIX
        crossfadeEnabled -> TransitionMode.CROSSFADE
        else -> TransitionMode.OFF
    }

    fun selectMode(mode: TransitionMode) {
        onStoredModeChange(mode)
        onCrossfadeEnabledChange(mode == TransitionMode.CROSSFADE || mode == TransitionMode.AUTOMIX)
        onAutomixEnabledChange(mode == TransitionMode.AUTOMIX)
    }

    val durationSeconds = crossfadeDuration.coerceIn(MinDurationSeconds, MaxDurationSeconds)
    val overlapFraction = 0.08f + 0.42f * (durationSeconds - MinDurationSeconds) / (MaxDurationSeconds - MinDurationSeconds)

    ListenToAudioTap(active = isPlaying)

    YumaSettingsScaffold(
        title = { Text(stringResource(R.string.transitions_title)) },
        onBackClick = { navController.navigateUp() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
        ) {
            TransitionMasterToggle(
                enabled = selectedMode != TransitionMode.OFF,
                onToggle = { enabled ->
                    selectMode(if (enabled) TransitionMode.CROSSFADE else TransitionMode.OFF)
                },
            )

            TransitionModeGrid(selected = selectedMode) { mode, opened ->
                val active = mode == selectedMode
                when (mode) {
                    TransitionMode.CROSSFADE -> TransitionModeCard(
                        title = stringResource(R.string.transition_mode_crossfade),
                        selected = active,
                        opened = opened,
                        onSelect = { selectMode(TransitionMode.CROSSFADE) },
                        picture = {
                            TransitionPicture(
                                style = TransitionStyle.Crossfade,
                                overlap = overlapFraction,
                                active = active,
                                isPlaying = isPlaying,
                            )
                        },
                    ) {
                        FadeOptions(
                            description = stringResource(R.string.transition_crossfade_desc),
                            durationSeconds = durationSeconds,
                            onDurationChange = onCrossfadeDurationChange,
                            gapless = gapless,
                            onGaplessChange = onGaplessChange,
                        )
                    }

                    TransitionMode.AUTOMIX -> TransitionModeCard(
                        title = stringResource(R.string.transition_mode_automix),
                        selected = active,
                        opened = opened,
                        onSelect = { selectMode(TransitionMode.AUTOMIX) },
                        picture = {
                            TransitionPicture(
                                style = TransitionStyle.Automix,
                                overlap = overlapFraction,
                                active = active,
                                isPlaying = isPlaying,
                            )
                        },
                    ) {
                        FadeOptions(
                            description = stringResource(R.string.transition_automix_desc),
                            durationSeconds = durationSeconds,
                            onDurationChange = onCrossfadeDurationChange,
                            gapless = gapless,
                            onGaplessChange = onGaplessChange,
                        )
                    }

                    TransitionMode.CROSSMIX -> TransitionModeCard(
                        title = stringResource(R.string.transition_mode_crossmix),
                        selected = active,
                        opened = opened,
                        onSelect = { selectMode(TransitionMode.CROSSMIX) },
                        picture = {
                            TransitionPicture(
                                style = TransitionStyle.Crossmix,
                                overlap = 0.42f,
                                active = active,
                                isPlaying = isPlaying,
                            )
                        },
                    ) {
                        CrossmixOptions(
                            statusFlow = service?.crossmixStatus ?: MutableStateFlow(null),
                            canTest = service != null,
                            onTestClick = { service?.testCrossmix() },
                        )
                    }

                    TransitionMode.OFF -> Unit
                }
            }
        }
    }
}

@Composable
private fun TransitionMasterToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val cardShape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SettingsDimensions.SegmentedItemMinHeight)
            .yumaClickable { onToggle(!enabled) }
            .yumaGlassCard(shape = cardShape)
            .clip(cardShape)
            .padding(
                horizontal = SettingsDimensions.SegmentedItemPaddingHorizontal,
                vertical = SettingsDimensions.SegmentedItemPaddingVertical,
            ),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            MarqueeText(
                text = stringResource(R.string.transitions_enabled_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(SettingsDimensions.RowTextSpacing))
            Text(
                text = stringResource(if (enabled) R.string.transitions_enabled_desc else R.string.transitions_off_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        YumaSwitch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun FadeOptions(
    description: String,
    durationSeconds: Float,
    onDurationChange: (Float) -> Unit,
    gapless: Boolean,
    onGaplessChange: (Boolean) -> Unit,
) {
    val selectedInt = durationSeconds.roundToInt()

    Spacer(Modifier.height(10.dp))
    Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.transition_duration_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.transition_duration_seconds, selectedInt),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DurationPresets.forEach { preset ->
            val isSelected = selectedInt == preset
            val chipShape = RoundedCornerShape(12.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(chipShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
                        },
                    )
                    .yumaClickable { onDurationChange(preset.toFloat()) }
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.transition_duration_seconds, preset),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }

    Spacer(Modifier.height(4.dp))
    Slider(
        value = durationSeconds,
        onValueChange = { onDurationChange(it.roundToInt().toFloat()) },
        valueRange = MinDurationSeconds..MaxDurationSeconds,
        steps = (MaxDurationSeconds - MinDurationSeconds).toInt() - 1,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(4.dp))
    InlineSwitchRow(
        title = stringResource(R.string.transition_gapless_title),
        subtitle = stringResource(R.string.transition_gapless_desc),
        checked = gapless,
        onCheckedChange = onGaplessChange,
    )
}

@Composable
private fun CrossmixOptions(
    statusFlow: kotlinx.coroutines.flow.StateFlow<String?>,
    canTest: Boolean,
    onTestClick: () -> Unit,
) {
    val (styleName, onStyleNameChange) = rememberPreference(CrossmixStyleKey, defaultValue = CrossmixStyleAuto)
    val (length, onLengthChange) = rememberEnumPreference(CrossmixLengthKey, defaultValue = CrossmixLength.MEDIUM)
    val (tempoMatch, onTempoMatchChange) = rememberEnumPreference(CrossmixTempoKey, defaultValue = CrossmixTempoMatch.FULL)
    val (stunts, onStuntsChange) = rememberPreference(CrossmixStuntsKey, defaultValue = true)
    val (alwaysMix, onAlwaysMixChange) = rememberPreference(CrossmixAlwaysMixKey, defaultValue = true)
    val status by statusFlow.collectAsStateWithLifecycle(null)
    var showStyleSheet by remember { mutableStateOf(false) }

    val selectedStyleLabel = remember(styleName) {
        PinnableStyles.firstOrNull { it.name == styleName }?.label
    } ?: stringResource(R.string.crossmix_style_auto)

    if (showStyleSheet) {
        CrossmixStylePickerSheet(
            selectedStyleName = styleName,
            onSelect = {
                onStyleNameChange(it)
                showStyleSheet = false
            },
            onDismiss = { showStyleSheet = false },
        )
    }

    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.crossmix_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(14.dp))
    OptionHeading(
        title = stringResource(R.string.crossmix_length_title),
        subtitle = stringResource(R.string.crossmix_length_desc),
    )
    Spacer(Modifier.height(8.dp))
    SegmentedOptionRow(
        options = listOf(
            stringResource(R.string.crossmix_length_short),
            stringResource(R.string.crossmix_length_medium),
            stringResource(R.string.crossmix_length_long),
        ),
        selectedIndex = length.ordinal,
        onSelect = { onLengthChange(CrossmixLength.entries[it]) },
    )

    Spacer(Modifier.height(14.dp))
    OptionHeading(
        title = stringResource(R.string.crossmix_tempo_title),
        subtitle = stringResource(R.string.crossmix_tempo_desc),
    )
    Spacer(Modifier.height(8.dp))
    SegmentedOptionRow(
        options = listOf(
            stringResource(R.string.crossmix_tempo_off),
            stringResource(R.string.crossmix_tempo_gentle),
            stringResource(R.string.crossmix_tempo_full),
        ),
        selectedIndex = tempoMatch.ordinal,
        onSelect = { onTempoMatchChange(CrossmixTempoMatch.entries[it]) },
    )

    Spacer(Modifier.height(14.dp))
    OptionHeading(
        title = stringResource(R.string.crossmix_style_title),
        subtitle = stringResource(R.string.crossmix_style_desc),
    )
    Spacer(Modifier.height(8.dp))
    val pickerShape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(pickerShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f))
            .yumaClickable { showStyleSheet = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        MarqueeText(
            text = selectedStyleLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(SettingsDimensions.ChevronSize),
        )
    }

    Spacer(Modifier.height(10.dp))
    InlineSwitchRow(
        title = stringResource(R.string.crossmix_stunts_title),
        subtitle = stringResource(R.string.crossmix_stunts_desc),
        checked = stunts,
        onCheckedChange = onStuntsChange,
    )

    Spacer(Modifier.height(8.dp))
    InlineSwitchRow(
        title = stringResource(R.string.crossmix_always_mix_title),
        subtitle = stringResource(R.string.crossmix_always_mix_desc),
        checked = alwaysMix,
        onCheckedChange = onAlwaysMixChange,
    )

    Spacer(Modifier.height(12.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.36f))
            .padding(12.dp),
    ) {
        Text(
            text = stringResource(R.string.crossmix_status_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = status?.ifBlank { null } ?: stringResource(R.string.crossmix_status_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onTestClick,
            enabled = canTest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.crossmix_test_title))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.crossmix_test_desc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val StyleCategories: List<Pair<StyleUse, Int>> = listOf(
    StyleUse.Everyday to R.string.crossmix_style_category_everyday,
    StyleUse.Lift to R.string.crossmix_style_category_lift,
    StyleUse.Fall to R.string.crossmix_style_category_fall,
    StyleUse.Flavour to R.string.crossmix_style_category_flavour,
)

@Composable
private fun CrossmixStylePickerSheet(
    selectedStyleName: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val hideSheet: (() -> Unit) -> Unit = remember(coroutineScope, sheetState) {
        { completion ->
            coroutineScope.launch {
                sheetState.hide()
            }.invokeOnCompletion {
                completion()
            }
        }
    }

    val autoLabel = stringResource(R.string.crossmix_style_auto)
    val groupedStyles = remember {
        StyleCategories.mapNotNull { (use, titleRes) ->
            val styles = CrossmixStyle.entries.filter { it.use == use }
            if (styles.isEmpty()) null else titleRes to styles
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.BottomSheetHorizontalPadding)
                .padding(bottom = SettingsDimensions.BottomSheetBottomPadding)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(SettingsDimensions.BottomSheetCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                width = SettingsDimensions.GlassBorderThickness,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
            tonalElevation = SettingsDimensions.BottomSheetTonalElevation,
            shadowElevation = SettingsDimensions.BottomSheetShadowElevation,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.BottomSheetContentPaddingH)
                    .padding(
                        top = SettingsDimensions.BottomSheetContentPaddingTop,
                        bottom = SettingsDimensions.BottomSheetContentPaddingBottom,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = SettingsDimensions.BottomSheetDragHandleBottomPadding)
                        .size(
                            width = SettingsDimensions.BottomSheetDragHandleWidth,
                            height = SettingsDimensions.BottomSheetDragHandleHeight,
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )

                Text(
                    text = stringResource(R.string.crossmix_style_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = SettingsDimensions.SegmentGap),
                )

                Text(
                    text = stringResource(R.string.crossmix_style_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = SettingsDimensions.BottomSheetTitleBottomPadding),
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = SettingsDimensions.BottomSheetListMaxHeight),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(
                        key = CrossmixStyleAuto,
                        span = { GridItemSpan(2) },
                    ) {
                        StyleGridTile(
                            label = autoLabel,
                            selected = selectedStyleName == CrossmixStyleAuto,
                            onClick = {
                                hideSheet {
                                    onSelect(CrossmixStyleAuto)
                                }
                            },
                        )
                    }

                    groupedStyles.forEach { (titleRes, styles) ->
                        item(
                            key = "header_$titleRes",
                            span = { GridItemSpan(2) },
                        ) {
                            Text(
                                text = stringResource(titleRes),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                            )
                        }

                        items(
                            items = styles,
                            key = { it.name },
                        ) { style ->
                            StyleGridTile(
                                label = style.label,
                                selected = selectedStyleName == style.name,
                                onClick = {
                                    hideSheet {
                                        onSelect(style.name)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleGridTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalYumaColors.current
    val tileShape = RoundedCornerShape(12.dp)
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        colors.glassBackground
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        colors.glassBorder
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .yumaClickable(onClick = onClick)
            .yumaGlassCard(
                shape = tileShape,
                backgroundColor = backgroundColor,
                borderColor = borderColor,
            )
            .clip(tileShape)
            .padding(horizontal = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OptionHeading(
    title: String,
    subtitle: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SegmentedOptionRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    if (options.isEmpty()) return

    val colors = LocalYumaColors.current
    val clampedIndex = selectedIndex.coerceIn(0, options.lastIndex)
    val indicatorOffset by animateFloatAsState(
        targetValue = clampedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "CrossmixSegmentedPillOffset",
    )

    val barShape = RoundedCornerShape(16.dp)
    val pillShape = RoundedCornerShape(12.dp)
    val itemHeight = 40.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(barShape)
            .background(colors.glassBackground)
            .border(SettingsDimensions.GlassBorderThickness, colors.glassBorder, barShape),
        contentAlignment = Alignment.CenterStart,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
        ) {
            val tabWidth = maxWidth / options.size

            Surface(
                shape = pillShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(width = tabWidth, height = itemHeight)
                    .offset { IntOffset((tabWidth.toPx() * indicatorOffset).roundToInt(), 0) },
            ) {}

            Row(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, label ->
                    val selected = index == clampedIndex
                    val labelColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(150),
                        label = "CrossmixSegmentedLabelColor",
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(itemHeight)
                            .clip(pillShape)
                            .yumaClickable { onSelect(index) }
                            .padding(horizontal = 6.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = labelColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .yumaClickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        YumaSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun YumaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = {
            AnimatedContent(
                targetState = checked,
                transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) },
                label = "transitionsSwitchThumb",
            ) { isChecked ->
                Icon(
                    painter = painterResource(if (isChecked) R.drawable.check else R.drawable.close),
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedIconColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}
