package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.theme.yumaCombinedClickable

import moe.rukamori.archivetune.constants.NavigationTabContentPaddingVertical
import moe.rukamori.archivetune.constants.NavigationTabIconSize
import moe.rukamori.archivetune.constants.NavigationTabLabelSize
import moe.rukamori.archivetune.constants.NavigationTabLabelTopPadding
import moe.rukamori.archivetune.constants.NavigationTabPressedScale
import moe.rukamori.archivetune.constants.NavigationTabSelectorInitialScale
import moe.rukamori.archivetune.constants.NavigationTabSelectorVisibleThreshold
import moe.rukamori.archivetune.constants.NavigationTabSelectorAlpha
import moe.rukamori.archivetune.constants.NavigationTabSelectionDurationMs
import moe.rukamori.archivetune.constants.NavigationTabContentDurationMs
import moe.rukamori.archivetune.constants.NavigationTabSlotPaddingHorizontal
import moe.rukamori.archivetune.constants.NavigationTabSlotPaddingVertical

@Composable
internal fun RowScope.NavigationTabItem(
    screen: Screens,
    selected: Boolean,
    pureBlack: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val selectionFactor by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(
            durationMillis = NavigationTabSelectionDurationMs,
            easing = LinearOutSlowInEasing,
        ),
        label = "SelectionFactor",
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            NavBarColors.iconActive(pureBlack)
        } else {
            NavBarColors.iconInactive(pureBlack)
        },
        animationSpec = tween(durationMillis = NavigationTabContentDurationMs),
        label = "ContentColor",
    )

    Box(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(
                horizontal = NavigationTabSlotPaddingHorizontal,
                vertical = NavigationTabSlotPaddingVertical,
            )
            .clip(CircleShape)
            .yumaCombinedClickable(
                pressedScale = NavigationTabPressedScale,
                onClick = onClick,
                onDoubleClick = onDoubleClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selectionFactor > NavigationTabSelectorVisibleThreshold) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = lerp(NavigationTabSelectorInitialScale, 1.0f, selectionFactor)
                        scaleX = scale
                        scaleY = scale
                        alpha = selectionFactor
                    }
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = NavigationTabSelectorAlpha),
                        shape = CircleShape,
                    ),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = NavigationTabContentPaddingVertical),
        ) {
            Icon(
                painter = painterResource(if (selected) screen.iconIdActive else screen.iconIdInactive),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(NavigationTabIconSize),
            )
            Text(
                text = stringResource(screen.titleId),
                color = contentColor,
                fontSize = NavigationTabLabelSize,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(top = NavigationTabLabelTopPadding),
            )
        }
    }
}