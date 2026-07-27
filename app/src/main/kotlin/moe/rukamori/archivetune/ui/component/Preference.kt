/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import moe.rukamori.archivetune.ui.player.player_0.scroll.AutoScrollingTextOnDemand
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HISTORY_DURATION_DEFAULT
import moe.rukamori.archivetune.constants.HISTORY_DURATION_RANGE
import moe.rukamori.archivetune.ui.screens.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import kotlin.math.roundToInt

val LocalPreferenceInGroup = compositionLocalOf { false }

enum class PreferenceGroupPosition { Single, First, Middle, Last }

val LocalPreferenceGroupPosition = compositionLocalOf<PreferenceGroupPosition?> { null }

private val PreferenceGroupLargeCorner = SettingsDimensions.GroupCardCornerRadius
private val PreferenceGroupSmallCorner = 4.dp
private val PreferenceGroupHorizontalPadding = SettingsDimensions.ScreenHorizontalPadding
private val PreferenceEntryMinHeight = 0.dp
private val PreferenceEntryHorizontalPadding = SettingsDimensions.RowHorizontalPadding
private val PreferenceEntryVerticalPadding = SettingsDimensions.RowVerticalPadding

@Composable
private fun rememberPreferenceIconShape(): Shape = MaterialShapes.Ghostish.toShape()

private fun segmentedPreferenceItemShape(
    index: Int,
    count: Int,
): Shape {
    val large = PreferenceGroupLargeCorner
    val small = PreferenceGroupSmallCorner
    return when {
        count <= 1 -> {
            RoundedCornerShape(large)
        }

        index == 0 -> {
            RoundedCornerShape(
                topStart = large,
                topEnd = large,
                bottomEnd = small,
                bottomStart = small,
            )
        }

        index == count - 1 -> {
            RoundedCornerShape(
                topStart = small,
                topEnd = small,
                bottomEnd = large,
                bottomStart = large,
            )
        }

        else -> {
            RoundedCornerShape(small)
        }
    }
}

private fun preferenceItemShapeForPosition(position: PreferenceGroupPosition?): Shape =
    when (position) {
        null,
        PreferenceGroupPosition.Single,
        -> segmentedPreferenceItemShape(index = 0, count = 1)

        PreferenceGroupPosition.First -> segmentedPreferenceItemShape(index = 0, count = 2)

        PreferenceGroupPosition.Middle -> segmentedPreferenceItemShape(index = 1, count = 3)

        PreferenceGroupPosition.Last -> segmentedPreferenceItemShape(index = 1, count = 2)
    }

@Composable
fun PreferenceEntry(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    content: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    isEnabled: Boolean = true,
    shape: Shape? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && isEnabled && onClick != null) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "prefScale",
    )

    val cardShape = RoundedCornerShape(SettingsDimensions.GroupCardCornerRadius)
    val colors = LocalYumaColors.current
    val isInGroup = LocalPreferenceInGroup.current

    val boxModifier = if (isInGroup) {
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isEnabled && onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .alpha(if (isEnabled) 1f else 0.5f)
            .padding(
                horizontal = SettingsDimensions.RowHorizontalPadding,
                vertical = SettingsDimensions.RowVerticalPadding
            )
    } else {
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(cardShape)
            .background(colors.glassBackground)
            .border(1.dp, colors.glassBorder, cardShape)
            .then(
                if (isEnabled && onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .alpha(if (isEnabled) 1f else 0.5f)
            .padding(
                horizontal = SettingsDimensions.RowHorizontalPadding,
                vertical = SettingsDimensions.RowVerticalPadding
            )
    }

    Box(modifier = boxModifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.CenterVertically),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides Color.White.copy(alpha = 0.8f)) {
                        icon()
                    }
                }
                Spacer(Modifier.width(12.dp))
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, color = Color.White)) {
                    title()
                }
                if (description != null) {
                    Spacer(Modifier.height(2.dp))
                    AutoScrollingTextOnDemand(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.65f)),
                    )
                }
                content?.invoke()
            }

            if (trailingContent != null) {
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                    trailingContent()
                }
            }
        }
    }

    /*
    val inGroup = LocalPreferenceInGroup.current
    val groupPosition = LocalPreferenceGroupPosition.current
    val preferenceIconShape = rememberPreferenceIconShape()
    val preferenceItemShape =
        remember(groupPosition) {
            preferenceItemShapeForPosition(groupPosition)
        }
    val resolvedShape = shape ?: preferenceItemShape

    Card(
        shape = resolvedShape,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (inGroup) 0.dp else 16.dp,
                    vertical = if (inGroup) 0.dp else 3.dp,
                ).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
    ) {
        rowContent()
    }
    */
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    selectedValue: T,
    values: List<T>,
    valueText: @Composable (T) -> String,
    onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        isEnabled = isEnabled,
        content = {
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                values.forEachIndexed { index, value ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = values.size),
                        onClick = { onValueSelected(value) },
                        selected = value == selectedValue,
                        enabled = isEnabled,
                        colors =
                            SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    ) {
                        Text(
                            text = valueText(value),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        },
    )
}

@Composable
inline fun <reified T : Enum<T>> EnumSegmentedPreference(
    modifier: Modifier = Modifier,
    noinline title: @Composable () -> Unit,
    description: String? = null,
    noinline icon: (@Composable () -> Unit)? = null,
    selectedValue: T,
    noinline valueText: @Composable (T) -> String,
    noinline onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    val values = remember { enumValues<T>().toList() }

    SegmentedPreference(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        selectedValue = selectedValue,
        values = values,
        valueText = valueText,
        onValueSelected = onValueSelected,
        isEnabled = isEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ListPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    selectedValue: T,
    values: List<T>,
    valueText: @Composable (T) -> String,
    valueDescription: (@Composable (T) -> String)? = null,
    onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    var showBottomSheet by remember {
        mutableStateOf(false)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    if (showBottomSheet) {
        PreferenceSelectionBottomSheet(
            title = title,
            values = values,
            selectedValue = selectedValue,
            valueText = valueText,
            valueDescription = valueDescription,
            sheetState = sheetState,
            onDismiss = { showBottomSheet = false },
            onValueSelected = { value ->
                onValueSelected(value)
                coroutineScope
                    .launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        showBottomSheet = false
                    }
            },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        content = {
            Spacer(Modifier.height(10.dp))
            PreferenceValueChip(valueText(selectedValue))
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        },
        onClick = { showBottomSheet = true },
        isEnabled = isEnabled,
    )
}

@Composable
inline fun <reified T : Enum<T>> EnumListPreference(
    modifier: Modifier = Modifier,
    noinline title: @Composable () -> Unit,
    description: String? = null,
    noinline icon: (@Composable () -> Unit)?,
    selectedValue: T,
    noinline valueText: @Composable (T) -> String,
    noinline onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    val values = remember { enumValues<T>().toList() }

    ListPreference(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        selectedValue = selectedValue,
        values = values,
        valueText = valueText,
        onValueSelected = onValueSelected,
        isEnabled = isEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PreferenceSelectionBottomSheet(
    title: @Composable () -> Unit,
    values: List<T>,
    selectedValue: T,
    valueText: @Composable (T) -> String,
    valueDescription: (@Composable (T) -> String)? = null,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onValueSelected: (T) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                width = 48.dp,
                height = 5.dp,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            )
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
        ) {
            ProvideTextStyle(
                MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 16.dp),
                ) {
                    title()
                }
            }

            val glassShape = RoundedCornerShape(24.dp)
            val colors = LocalYumaColors.current

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(glassShape)
                        .background(colors.glassBackground)
                        .border(1.dp, colors.glassBorder, glassShape),
            ) {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp),
                ) {
                    itemsIndexed(
                        items = values,
                        key = { index, value -> preferenceOptionKey(index, value) },
                        contentType = { _, _ -> "preference_option" },
                    ) { index, value ->
                        PreferenceSelectionOption(
                            text = valueText(value),
                            description = valueDescription?.invoke(value),
                            selected = value == selectedValue,
                            onClick = { onValueSelected(value) },
                        )
                        if (index < values.size - 1) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceSelectionOption(
    text: String,
    description: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
    val contentColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val descriptionColor =
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(containerColor)
                .yumaClickable(pressedScale = 0.97f, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = descriptionColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (selected) {
                Spacer(Modifier.width(12.dp))
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun PreferenceValueChip(text: String) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun preferenceOptionKey(
    index: Int,
    value: Any?,
): String {
    val valueKey =
        when (value) {
            is Enum<*> -> value.name
            null -> "null"
            else -> value.hashCode().toString()
        }
    return "${value?.javaClass?.name.orEmpty()}:$valueKey:$index"
}

@Composable
fun SwitchPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isEnabled: Boolean = true,
) {
    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = isEnabled,
                thumbContent = {
                    AnimatedContent(
                        targetState = checked,
                        transitionSpec = {
                            fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                        },
                        label = "switchThumbIcon",
                    ) { isChecked ->
                        Icon(
                            painter =
                                painterResource(
                                    id = if (isChecked) R.drawable.check else R.drawable.close,
                                ),
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                },
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedIconColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            )
        },
        onClick = { onCheckedChange(!checked) },
        isEnabled = isEnabled,
    )
}

@Composable
fun EditTextPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    isInputValid: (String) -> Boolean = { it.isNotEmpty() },
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    if (showDialog) {
        TextFieldDialog(
            title = title,
            initialTextFieldValue =
                TextFieldValue(
                    text = value,
                    selection = TextRange(value.length),
                ),
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            isInputValid = isInputValid,
            onDone = onValueChange,
            onDismiss = { showDialog = false },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = value,
        icon = icon,
        onClick = { showDialog = true },
        isEnabled = isEnabled,
    )
}

@Composable
fun NumberEditTextPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: Int,
    onValueChange: (Int) -> Unit,
    isInputValid: (String) -> Boolean = { it.toIntOrNull() != null },
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    if (showDialog) {
        TextFieldDialog(
            title = title,
            initialTextFieldValue =
                TextFieldValue(
                    text = value.toString(),
                    selection = TextRange(value.toString().length),
                ),
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            isInputValid = isInputValid,
            onDone = { it.toIntOrNull()?.let(onValueChange) },
            onDismiss = { showDialog = false },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = value.toString(),
        icon = icon,
        onClick = { showDialog = true },
        isEnabled = isEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: Int,
    onValueChange: (Int) -> Unit,
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    var sliderValue by remember(value) {
        mutableFloatStateOf(value.toFloat())
    }

    if (showDialog) {
        ActionPromptDialog(
            titleBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.history_duration),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            },
            onDismiss = { showDialog = false },
            onConfirm = {
                showDialog = false
                onValueChange.invoke(sliderValue.roundToInt())
            },
            onCancel = {
                sliderValue = value.toFloat()
                showDialog = false
            },
            onReset = {
                sliderValue = HISTORY_DURATION_DEFAULT.toFloat()
            },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.seconds,
                                sliderValue.roundToInt(),
                                sliderValue.roundToInt(),
                            ),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.history_duration_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )

                    Spacer(Modifier.height(16.dp))

                    val sliderState =
                        rememberSliderState(
                            value = sliderValue,
                            valueRange = HISTORY_DURATION_RANGE,
                            onValueChangeFinished = {},
                        )
                    sliderState.onValueChange = { sliderValue = it }
                    sliderState.value = sliderValue

                    Slider(
                        state = sliderState,
                        modifier = Modifier.fillMaxWidth(),
                        track = {
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                trackCornerSize = 12.dp,
                            )
                        },
                    )
                }
            },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = stringResource(R.string.history_duration_seconds, value),
        icon = icon,
        onClick = { showDialog = true },
        isEnabled = isEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossfadeSliderPreference(
    modifier: Modifier = Modifier,
    valueSeconds: Float,
    onValueChange: (Float) -> Unit,
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    var sliderValue by remember {
        mutableFloatStateOf(valueSeconds.coerceIn(0f, 10f))
    }

    if (showDialog) {
        ActionPromptDialog(
            titleBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.audio_crossfade_dialog_title),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            },
            onDismiss = { showDialog = false },
            onConfirm = {
                val rounded =
                    ((sliderValue * 2f).roundToInt().toFloat() / 2f)
                        .coerceIn(0f, 10f)
                sliderValue = rounded
                showDialog = false
                onValueChange.invoke(rounded)
            },
            onCancel = {
                sliderValue = valueSeconds.coerceIn(0f, 10f)
                showDialog = false
            },
            onReset = {
                sliderValue = 5f
            },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val rounded =
                        ((sliderValue * 2f).roundToInt().toFloat() / 2f)
                            .coerceIn(0f, 10f)
                    val isWhole =
                        (rounded - rounded.roundToInt().toFloat()).let { delta ->
                            kotlin.math.abs(delta) < 0.001f
                        }
                    val displayValue =
                        if (isWhole) rounded.roundToInt().toString() else String.format(java.util.Locale.getDefault(), "%.1f", rounded)
                    Text(
                        text =
                            if (rounded <= 0f) {
                                stringResource(R.string.dark_theme_off)
                            } else {
                                stringResource(R.string.audio_crossfade_seconds, displayValue)
                            },
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.audio_crossfade_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )

                    Spacer(Modifier.height(16.dp))

                    val crossfadeSliderState =
                        rememberSliderState(
                            value = sliderValue,
                            steps = 19,
                            valueRange = 0f..10f,
                            onValueChangeFinished = {},
                        )
                    crossfadeSliderState.onValueChange = { sliderValue = it.coerceIn(0f, 10f) }
                    crossfadeSliderState.value = sliderValue

                    Slider(
                        state = crossfadeSliderState,
                        modifier = Modifier.fillMaxWidth(),
                        track = {
                            SliderDefaults.Track(
                                sliderState = crossfadeSliderState,
                                trackCornerSize = 12.dp,
                            )
                        },
                    )
                }
            },
        )
    }

    val rounded =
        ((valueSeconds * 2f).roundToInt().toFloat() / 2f)
            .coerceIn(0f, 10f)
    val isWhole =
        (rounded - rounded.roundToInt().toFloat()).let { delta ->
            kotlin.math.abs(delta) < 0.001f
        }
    val displayValue =
        if (isWhole) rounded.roundToInt().toString() else String.format(java.util.Locale.getDefault(), "%.1f", rounded)
    val descriptionText =
        if (rounded <= 0f) {
            stringResource(R.string.dark_theme_off)
        } else {
            stringResource(R.string.audio_crossfade_seconds, displayValue)
        }

    PreferenceEntry(
        modifier = modifier,
        title = { Text(stringResource(R.string.audio_crossfade_title)) },
        description = descriptionText,
        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
        onClick = { if (isEnabled) showDialog = true },
        isEnabled = isEnabled,
    )
}

@Composable
fun NumberPickerPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: Int,
    onValueChange: (Int) -> Unit,
    minValue: Int = 0,
    maxValue: Int = 10,
    valueText: (Int) -> String = { it.toString() },
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    var sliderValue by remember {
        mutableFloatStateOf(value.toFloat())
    }

    if (showDialog) {
        ActionPromptDialog(
            titleBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    title()
                }
            },
            onDismiss = { showDialog = false },
            onConfirm = {
                val rounded = sliderValue.roundToInt().coerceIn(minValue, maxValue)
                sliderValue = rounded.toFloat()
                showDialog = false
                onValueChange.invoke(rounded)
            },
            onCancel = {
                sliderValue = value.toFloat()
                showDialog = false
            },
            onReset = {
                sliderValue = minValue.toFloat()
            },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val rounded = sliderValue.roundToInt().coerceIn(minValue, maxValue)
                    Text(
                        text = valueText(rounded),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(16.dp))

                    val pickerSliderState =
                        rememberSliderState(
                            value = sliderValue,
                            steps = maxValue - minValue - 1,
                            valueRange = minValue.toFloat()..maxValue.toFloat(),
                            onValueChangeFinished = {},
                        )
                    pickerSliderState.onValueChange = {
                        sliderValue = it.coerceIn(minValue.toFloat(), maxValue.toFloat())
                    }
                    pickerSliderState.value = sliderValue

                    Slider(
                        state = pickerSliderState,
                        modifier = Modifier.fillMaxWidth(),
                        track = {
                            SliderDefaults.Track(
                                sliderState = pickerSliderState,
                                trackCornerSize = 12.dp,
                            )
                        },
                    )
                }
            },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = valueText(value),
        icon = icon,
        onClick = { if (isEnabled) showDialog = true },
        isEnabled = isEnabled,
    )
}

class PreferenceGroupScope internal constructor() {
    internal val items = mutableListOf<@Composable () -> Unit>()

    fun item(
        visible: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        if (visible) {
            items.add(content)
        }
    }
}

@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: PreferenceGroupScope.() -> Unit,
) {
    val scope = PreferenceGroupScope().apply(content)
    val itemCount = scope.items.size

    if (itemCount == 0) return

    val colors = LocalYumaColors.current
    val cardShape = RoundedCornerShape(SettingsDimensions.GroupCardCornerRadius)

    Column(modifier = modifier) {
        if (title != null) {
            PreferenceGroupTitle(
                title = title,
                modifier = Modifier.padding(horizontal = PreferenceGroupHorizontalPadding),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PreferenceGroupHorizontalPadding)
                .clip(cardShape)
                .background(colors.glassBackground)
                .border(1.dp, colors.glassBorder, cardShape)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                scope.items.forEachIndexed { index, itemContent ->
                    val position =
                        when {
                            itemCount == 1 -> PreferenceGroupPosition.Single
                            index == 0 -> PreferenceGroupPosition.First
                            index == itemCount - 1 -> PreferenceGroupPosition.Last
                            else -> PreferenceGroupPosition.Middle
                        }
                    CompositionLocalProvider(
                        LocalPreferenceInGroup provides true,
                        LocalPreferenceGroupPosition provides position,
                    ) {
                        itemContent()
                    }
                    if (index < itemCount - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = SettingsDimensions.DividerStartIndent),
                            thickness = SettingsDimensions.DividerThickness,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PreferenceGroupDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = 60.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    )
}

@Composable
fun PreferenceGroupTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    AutoScrollingTextOnDemand(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}
