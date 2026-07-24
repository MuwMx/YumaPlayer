@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.screens.Screens

// ─── DESIGN TOKENS ───────────────────────────────────────────────────────────
private val BarHeight = 68.dp
private val PillHeight = 32.dp
private val PillWidth = 56.dp
private val CornerRadius = 24.dp
private val IconSize = 24.dp
private val LabelFontSize = 11.sp
// ─────────────────────────────────────────────────────────────────────────────

// ─── ЦВЕТА (Готово под замену на DataStore в будущем) ───────────────────────
private object NavBarColors {
    @Composable
    fun container(pureBlack: Boolean) = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer

    @Composable
    fun pill(pureBlack: Boolean) = if (pureBlack) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer

    @Composable
    fun iconActive(pureBlack: Boolean) = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSecondaryContainer

    @Composable
    fun iconInactive(pureBlack: Boolean) = if (pureBlack) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant

    @Composable
    fun labelActive(pureBlack: Boolean) = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurface

    @Composable
    fun labelInactive(pureBlack: Boolean) = if (pureBlack) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun FloatingNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    onShuffleClick: (() -> Unit)? = null,
    shuffleIconRes: Int? = null,
    shuffleContentDescription: String = "",
    onMusicRecognitionClick: (() -> Unit)? = null,
    musicRecognitionContentDescription: String = "",
    onMusicTogetherClick: (() -> Unit)? = null,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)? = null,
) {
    //три точки
//    val hasOverflow = onShuffleClick != null && shuffleIconRes != null
    // СТАЛО: Временно тушим оверфлоу-меню. Табы займут всё свободное место!
    val hasOverflow = false

    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .height(BarHeight)
            .clip(RoundedCornerShape(CornerRadius))
            .border(
                width = 1.dp,
                color = if (pureBlack) {
                    Color.White.copy(alpha = 0.12f) // Элегантный неоновый контур для OLED
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) // Мягкая граница для обычной темы
                },
                shape = RoundedCornerShape(CornerRadius) // Форма должна строго совпадать!
            )
            .background(NavBarColors.container(pureBlack)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Левая часть: Флюидный контейнер с табами
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                FluidTabsContainer(
                    items = items,
                    pureBlack = pureBlack,
                    isSelected = isSelected,
                    onItemClick = onItemClick,
                    onSearchItemDoubleClick = onSearchItemDoubleClick
                )
            }

            // Правая часть: Кнопка «Ещё» (FAB)
            if (hasOverflow) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp, end = 4.dp)
                        .wrapContentSize()
                ) {
                    ToolbarOverflowMenu(
                        pureBlack = pureBlack,
                        onShuffleClick = onShuffleClick,
                        shuffleIconRes = shuffleIconRes,
                        shuffleContentDescription = shuffleContentDescription,
                        onMusicRecognitionClick = onMusicRecognitionClick,
                        musicRecognitionContentDescription = musicRecognitionContentDescription,
                        onMusicTogetherClick = onMusicTogetherClick
                    )
                }
            }
        }
    }
}

@Composable
private fun FluidTabsContainer(
    items: List<Screens>,
    pureBlack: Boolean,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)?
) {
    BoxWithConstraints(modifier = Modifier.fillMaxHeight()) {
        val tabWidth = maxWidth / items.size
        val activeIndex = items.indexOfFirst { isSelected(it) }.coerceAtLeast(0)

        // Математика ползунка: центрируем капсулу внутри активного слота
        val pillOffsetX = (tabWidth * activeIndex) + ((tabWidth - PillWidth) / 2)

        val animatedPillOffsetX by animateDpAsState(
            targetValue = pillOffsetX,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "FluidPillOffsetX"
        )

        Box(modifier = Modifier.fillMaxSize()) {
            // Скользящий задний фон (Pill) - позиционируется в верхней части, где иконки
            Box(
                modifier = Modifier
                    .offset(x = animatedPillOffsetX, y = 10.dp)
                    .width(PillWidth)
                    .height(PillHeight)
                    .background(
                        color = NavBarColors.pill(pureBlack),
                        shape = RoundedCornerShape(16.dp)
                    )
            )

            // Сами табы
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
                items.forEachIndexed { index, screen ->
                    val selected = isSelected(screen)

                    val iconTint by animateColorAsState(
                        targetValue = if (selected) NavBarColors.iconActive(pureBlack) else NavBarColors.iconInactive(pureBlack),
                        animationSpec = tween(250),
                        label = "IconTint_$index"
                    )

                    val labelColor by animateColorAsState(
                        targetValue = if (selected) NavBarColors.labelActive(pureBlack) else NavBarColors.labelInactive(pureBlack),
                        animationSpec = tween(250),
                        label = "LabelTint_$index"
                    )

                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val bounceScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.93f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "TabBounce_$index"
                    )

                    Column(
                        modifier = Modifier
                            .width(tabWidth)
                            .fillMaxHeight()
                            .scale(bounceScale)
                            .clip(RoundedCornerShape(18.dp))
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = null, // Убираем серый ripple
                                role = Role.Tab,
                                onClick = { onItemClick(screen, selected) },
                                onDoubleClick = { if (screen == Screens.Search) onSearchItemDoubleClick?.invoke() }
                            )
                            .padding(top = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(if (selected) screen.iconIdActive else screen.iconIdInactive),
                            contentDescription = stringResource(screen.titleId),
                            tint = iconTint,
                            modifier = Modifier.size(IconSize)
                        )

                        Text(
                            text = stringResource(screen.titleId),
                            color = labelColor,
                            fontSize = LabelFontSize,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarOverflowMenu(
    pureBlack: Boolean,
    onShuffleClick: (() -> Unit)?,
    shuffleIconRes: Int?,
    shuffleContentDescription: String,
    onMusicRecognitionClick: (() -> Unit)?,
    musicRecognitionContentDescription: String,
    onMusicTogetherClick: (() -> Unit)?,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (pureBlack) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription = stringResource(R.string.more)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.music_recognition)) },
                onClick = { expanded = false; onMusicRecognitionClick?.invoke() },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.mic), contentDescription = musicRecognitionContentDescription)
                },
                enabled = onMusicRecognitionClick != null
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.music_together)) },
                onClick = { expanded = false; onMusicTogetherClick?.invoke() },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.multi_user), contentDescription = null)
                },
                enabled = onMusicTogetherClick != null
            )

            if (onShuffleClick != null && shuffleIconRes != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shuffle)) },
                    onClick = { expanded = false; onShuffleClick() },
                    leadingIcon = {
                        Icon(painter = painterResource(shuffleIconRes), contentDescription = shuffleContentDescription)
                    }
                )
            }
        }
    }
}