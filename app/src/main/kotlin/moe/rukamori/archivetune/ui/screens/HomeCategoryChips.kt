/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.ui.component.horizontalFadingEdge
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeCategoryChips(
    chips: List<HomePage.Chip>,
    selectedChip: HomePage.Chip?,
    onChipSelected: (HomePage.Chip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYumaColors.current
    val scrollState = rememberScrollState()
    var containerWidth by remember { mutableIntStateOf(0) }
    val chipBoundsMap = remember { mutableStateMapOf<HomePage.Chip, Pair<Int, Int>>() }

    LaunchedEffect(selectedChip, containerWidth) {
        selectedChip?.let { chip ->
            chipBoundsMap[chip]?.let { (xInParent, width) ->
                if (containerWidth > 0) {
                    val targetScroll = (xInParent + width / 2 - containerWidth / 2).coerceIn(0, scrollState.maxValue)
                    scrollState.animateScrollTo(targetScroll)
                }
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .yumaGlassCard(
                    shape = RoundedCornerShape(24.dp),
                    backgroundColor = colors.glassBackground,
                    borderColor = colors.glassBorder,
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { containerWidth = it.width }
                    .horizontalFadingEdge(scrollState = scrollState, length = 20.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
            ) {
                chips.forEach { chip ->
                    val selected = chip == selectedChip

                    val chipBgColor by animateColorAsState(
                        targetValue =
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                colors.glassBorder.copy(alpha = 0.10f)
                            },
                        animationSpec = tween(durationMillis = 250),
                        label = "chipBgColorAnimation",
                    )
                    val chipTextColor by animateColorAsState(
                        targetValue =
                            if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                colors.textPrimary
                            },
                        animationSpec = tween(durationMillis = 250),
                        label = "chipTextColorAnimation",
                    )

                    Box(
                        modifier =
                            Modifier
                                .onGloballyPositioned { coordinates ->
                                    chipBoundsMap[chip] = Pair(coordinates.positionInParent().x.toInt(), coordinates.size.width)
                                }
                                .yumaClickable(pressedScale = 0.94f) { onChipSelected(chip) }
                                .yumaGlassCard(
                                    shape = CircleShape,
                                    backgroundColor = chipBgColor,
                                    borderColor = Color.Transparent,
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AnimatedVisibility(
                                visible = selected,
                                enter = fadeIn(animationSpec = tween(200)) + expandHorizontally(animationSpec = tween(200)),
                                exit = fadeOut(animationSpec = tween(200)) + shrinkHorizontally(animationSpec = tween(200)),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.done),
                                    contentDescription = null,
                                    tint = chipTextColor,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Text(
                                text = chip.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = chipTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
