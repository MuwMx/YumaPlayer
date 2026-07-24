package moe.rukamori.archivetune.ui.player.lyrics_0

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.ui.state.PlayerUiState
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import moe.rukamori.archivetune.ui.state.LyricLine
import moe.rukamori.archivetune.ui.theme.SoftTextShadow

@Composable
fun LyricsContentCard(
    state: PlayerUiState,
    animateProgressProvider: () -> Float,
    onSearchClick: () -> Unit,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    onLineClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val screenHeightPx = remember { context.resources.displayMetrics.heightPixels.toFloat() }

    // Анимируем базовые цвета палитры
    val animatedDarkMuted by animateColorAsState(
        targetValue = Color(state.darkMutedColor),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "LyricsBgAnimation"
    )

    // 3-точечный градиент с затемнением 70/30 под шапкой
    val cardBackgroundBrush = remember(animatedDarkMuted) {
        val startColor = lerp(animatedDarkMuted, Color.Black, 0.7f)
        val midColor = animatedDarkMuted
        val endColor = Color(0xFF121212)

        Brush.verticalGradient(
            0.0f to startColor,
            0.2f to midColor,
            1.0f to endColor
        )
    }

    val searchInteractionSource = remember { MutableInteractionSource() }
    val searchPressed by searchInteractionSource.collectIsPressedAsState()
    val searchScale by animateFloatAsState(if (searchPressed) 0.94f else 1f, spring(dampingRatio = 0.5f))

    Column(modifier = modifier.fillMaxSize()) {
        // Отступ под парящую шапку
        Spacer(modifier = Modifier.height(120.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer {
                    translationY = screenHeightPx * (1f - animateProgressProvider())
                }
                .clipToBounds()
        ) {

            // Внутренний контейнер карточки текста
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight() // ФИКС: Растягиваем на всю высоту, чтобы карта не схлопывалась при лоадере
                    .layout { measurable, constraints ->
                        val borderPx = 1.dp.roundToPx()
                        // Расширяем контейнер по бокам и снизу, чтобы спрятать швы бордера
                        val expandedConstraints = constraints.copy(
                            minWidth = constraints.maxWidth + borderPx * 2,
                            maxWidth = constraints.maxWidth + borderPx * 2,
                            minHeight = constraints.maxHeight + borderPx,
                            maxHeight = constraints.maxHeight + borderPx
                        )
                        val placeable = measurable.measure(expandedConstraints)
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            // Теперь контейнер гарантированно шире экрана на 2px и центрируется ровно
                            placeable.place(-borderPx, 0)
                        }
                    }
                    // Рисуем аккуратную верхнюю обводку (бока и низ уйдут за экран благодаря .layout)
                    .border(BorderStroke(1.dp, Color(0x22FFFFFF)), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(
                        if (state.isBlurBackgroundEnabled) {
                            Color.Black.copy(alpha = 0.2f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .then(
                        if (!state.isBlurBackgroundEnabled) {
                            Modifier.background(cardBackgroundBrush)
                        } else Modifier
                    )
            ) {

                if (state.lyricsList.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (state.isLoadingLyrics || state.isRefreshingLyrics) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 4.dp,
                                modifier = Modifier.size(48.dp)
                            )
                        } else {
                            Button(
                                onClick = onSearchClick,
                                interactionSource = searchInteractionSource,
                                shape = RoundedCornerShape(28.dp),
                                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0x20FFFFFF),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                                modifier = Modifier.graphicsLayer { scaleX = searchScale; scaleY = searchScale }
                            ) {
                                Text("Search lyrics for this track", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }

                            if (state.lyricsError != null) {
                                Text(
                                    text = state.lyricsError,
                                    color = Color(0x80FFFFFF),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    val topFadePx = 48.dp.toPx()
                                    val bottomFadePx = 100.dp.toPx()
                                    val size = this.size

                                    // Прозрачное сглаживание сверху
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black),
                                            startY = 0f,
                                            endY = topFadePx
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                    // Прозрачное сглаживание снизу
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.Black, Color.Transparent),
                                            startY = size.height - bottomFadePx,
                                            endY = size.height
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                },
                            contentPadding = PaddingValues(top = 16.dp, bottom = 220.dp, start = 0.dp, end = 0.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            itemsIndexed(
                                items = state.lyricsList,
                                key = { _, line -> line.timeMs }
                            ) { index, line ->
                                val isActive = index == state.currentLineIndex

                                val animatedAlpha by animateFloatAsState(
                                    targetValue = if (isActive) 1f else 0.35f,
                                    animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
                                    label = "LyricsLineAlpha"
                                )

                                val animatedScale by animateFloatAsState(
                                    targetValue = if (isActive) 1.08f else 1f,
                                    animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
                                    label = "LyricsLineScale"
                                )

                                Text(
                                    text = line.text,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp, horizontal = 24.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            onLineClick(line.timeMs)
                                        }
                                        .graphicsLayer {
                                            alpha = animatedAlpha
                                            scaleX = animatedScale
                                            scaleY = animatedScale
                                        },
                                    style = TextStyle(
                                        fontSize = 24.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                        shadow = SoftTextShadow
                                    )
                                )
                            }
                        }

                        if (state.isRefreshingLyrics) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 4.dp,
                                modifier = Modifier
                                    .size(48.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}
