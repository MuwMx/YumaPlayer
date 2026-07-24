package moe.rukamori.archivetune.ui.player.lyrics_0

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import moe.rukamori.archivetune.ui.state.PlayerUiState
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun LyricsColumn(
    state: PlayerUiState,
    animateProgressProvider: () -> Float,
    onCloseClick: () -> Unit,
    onMoreClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayPauseClick: () -> Unit,
    onLineClick: (Long) -> Unit,
    swipeOffsetY: Float,
    onSwipeOffsetChange: (Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val screenHeightPx = remember { context.resources.displayMetrics.heightPixels.toFloat() }
    val density = context.resources.displayMetrics.density

    val lazyListState = rememberLazyListState()
    val viewportOffset = -(screenHeightPx * 0.25f).toInt()

    // 1. Убираем делегат 'by' и оставляем чистый объект стейта, чтобы читать .value
    val isAtTopState = remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
        }
    }

    // 2. Создаем живую ссылку на внешний отступ, которая будет обновляться внутри pointerInput
    val currentSwipeOffset = rememberUpdatedState(swipeOffsetY)



    // ====================================================================
    // СИСТЕМНЫЙ ЖЕСТ "НАЗАД" (Матрёшка жестов)
    // ====================================================================
    BackHandler(enabled = state.isLyricsVisible) {
        onCloseClick()
    }

    // Наш сдвиг свайпа
//    var swipeOffsetY by remember { mutableStateOf(0f) }

    // ФИКС «ПРИЗРАКА СВАЙПА»:
    // Как только лирика переходит в статус видимой (isLyricsVisible == true),
    // мы мгновенно сбрасываем сдвиг свайпа в ноль, пока экран еще полностью прозрачен.
    // СБРОС СВАЙПА ПРИ ОТКРЫТИИ
    LaunchedEffect(state.isLyricsVisible) {
        if (state.isLyricsVisible) {
            onSwipeOffsetChange(0f)
        }
    }

    // УЛУЧШЕННЫЙ АВТОСКРОЛЛ (YT Music style):
    // Теперь он срабатывает не только при смене индекса строки,
    // но и при повторном открытии экрана (isLyricsVisible), возвращая фокус на активную строку.
    LaunchedEffect(state.currentLineIndex, state.isLyricsVisible) {
        if (state.isLyricsVisible && state.isSynced && state.currentLineIndex >= 0 && state.currentLineIndex < state.lyricsList.size) {
            if (!lazyListState.isScrollInProgress) {
                lazyListState.animateScrollToItem(
                    index = state.currentLineIndex,
                    scrollOffset = viewportOffset
                )
            }
        }
    }

    // Проверяем, находится ли список в самом верху, чтобы разрешить свайп вниз
    val isAtTop by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                // Сдвигаем весь экран лирики вниз при свайпе
                translationY = swipeOffsetY
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val threshold = 120 * density
                        // Читаем СВЕЖИЙ стейт на момент отпускания пальца
                        val finalOffset = currentSwipeOffset.value

                        coroutineScope.launch {
                            if (finalOffset > threshold) {
                                animate(
                                    initialValue = finalOffset,
                                    targetValue = screenHeightPx * 0.35f,
                                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                                ) { value, _ ->
                                    onSwipeOffsetChange(value)
                                }
                                onCloseClick()
                            } else {
                                animate(
                                    initialValue = finalOffset,
                                    targetValue = 0f,
                                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
                                ) { value, _ ->
                                    onSwipeOffsetChange(value)
                                }
                            }
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        // Читаем СВЕЖИЙ стейт прямо во время движения
                        val currentOffset = currentSwipeOffset.value

                        if (isAtTopState.value && (dragAmount > 0 || currentOffset > 0)) {
                            change.consume()

                            val tensionLimit = 150 * density
                            val calculatedOffset = if (currentOffset < tensionLimit) {
                                currentOffset + dragAmount
                            } else {
                                currentOffset + (dragAmount * 0.25f)
                            }

                            onSwipeOffsetChange(calculatedOffset.coerceIn(0f, screenHeightPx * 0.35f))
                        }
                    }
                )
            }
    ) {
        // 1. ФОНОВЫЙ СЛОЙ (Использует то же самое оптимизированное размытие/градиент, что и FullPlayer)
        val animatedBgColor = Color(state.darkMutedColor)
        val gradientBrush = remember(animatedBgColor) {
            Brush.verticalGradient(
                colors = listOf(animatedBgColor, Color(0xFF121212))
            )
        }
        moe.rukamori.archivetune.ui.player.player_0.PlayerBackgroundLayers(state = state, gradientBrush = gradientBrush)
        // 2. КАРТОЧКА С ТЕКСТОМ (Выезжает снизу)
        LyricsContentCard(
            state = state,
            animateProgressProvider = animateProgressProvider,
            onSearchClick = onSearchClick,
            lazyListState = lazyListState,
            onLineClick = onLineClick
        )
        // 3. ПАРЯЩАЯ ШАПКА-КАПСУЛА (Поверх всего)
        LyricsHeader(
            state = state,
            animateProgressProvider = animateProgressProvider,
            onCloseClick = onCloseClick,
            onPlayPauseClick = onPlayPauseClick,
            onMoreClick = onMoreClick,
        )
    }
}
