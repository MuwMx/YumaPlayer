package moe.rukamori.archivetune.ui.scaffold

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.viewmodels.NewsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaffoldTopAppBar(
    visible: Boolean,
    navController: NavHostController,
    navBackStackEntry: NavBackStackEntry?,
    scrollController: ScaffoldScrollController,
    useRail: Boolean,
    pureBlack: Boolean,
    splashDone: Boolean,
    updateState: UpdateState,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val newsViewModel: NewsViewModel = hiltViewModel()
    val hasUnreadNews by newsViewModel.hasUnreadNews.collectAsStateWithLifecycle()
    val isLibraryRoute = navBackStackEntry?.destination?.route == Screens.Library.route
    val currentRoute = navBackStackEntry?.destination?.route
    val shouldUseFloatingTopBar = remember(currentRoute) {
        currentRoute == Screens.Home.route || currentRoute == Screens.Search.route || currentRoute == Screens.Library.route
    }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val currentScrollBehavior = scrollController.currentScrollBehavior(currentRoute)

    var headerHeightPx by remember { mutableStateOf(0) }
    LaunchedEffect(currentScrollBehavior, headerHeightPx) {
        if (headerHeightPx > 0 && !isLibraryRoute) {
            val limit = -headerHeightPx.toFloat()
            val state = currentScrollBehavior.state
            if (state.heightOffsetLimit != limit) {
                state.heightOffsetLimit = limit
                state.heightOffset = state.heightOffset.coerceIn(limit, 0f)
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { if (it.height > 0) headerHeightPx = it.height }
            .offset { IntOffset(0, if (isLibraryRoute) 0 else currentScrollBehavior.state.heightOffset.roundToInt()) },
    ) {
        if (shouldUseFloatingTopBar) {
            val appBarHeightPx = with(LocalDensity.current) { AppBarHeight.toPx() }
            val statusBarTop = with(LocalDensity.current) { WindowInsets.systemBars.getTop(this).toDp() }
            Box(
                modifier = Modifier
                    .offset {
                        if (isLibraryRoute) IntOffset(0, 0) else {
                            val raw = currentScrollBehavior.state.heightOffset
                            IntOffset(0, (raw.coerceAtLeast(-appBarHeightPx) - raw).roundToInt())
                        }
                    }
                    .fillMaxWidth()
                    .height(AppBarHeight + statusBarTop)
                    .background(
                        Brush.verticalGradient(
                            listOf(surfaceColor.copy(alpha = 0.95f), surfaceColor.copy(alpha = 0.85f), surfaceColor.copy(alpha = 0.6f), Color.Transparent),
                        ),
                    ),
            )
        }

        TopAppBar(
            windowInsets = WindowInsets.safeDrawing.only(
                (if (useRail) WindowInsetsSides.Right else WindowInsetsSides.Horizontal) + WindowInsetsSides.Top,
            ),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.about_appbar), null, Modifier.size(35.dp).padding(end = 3.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            actions = {
                IconButton(onClick = { navController.navigate("history") }, onLongClick = {}) {
                    Icon(painterResource(R.drawable.history), stringResource(R.string.history))
                }
                TooltipBox(
                    positionProvider = if (hasUnreadNews) TooltipDefaults.rememberRichTooltipPositionProvider() else TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = {
                        if (hasUnreadNews) {
                            RichTooltip(title = { Text(stringResource(R.string.news_tooltip_title)) }) { Text(stringResource(R.string.news_tooltip_body)) }
                        } else {
                            PlainTooltip { Text(stringResource(R.string.news)) }
                        }
                    },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = { navController.navigate("news") }, onLongClick = {}) {
                        BadgedBox(badge = { if (hasUnreadNews) Badge() }) {
                            Icon(painterResource(R.drawable.newspaper), stringResource(R.string.news))
                        }
                    }
                }
                IconButton(onClick = { navController.navigate("new_release") }, onLongClick = {}) {
                    Icon(painterResource(R.drawable.new_release), stringResource(R.string.new_release_albums))
                }
                IconButton(onClick = { navController.navigate("settings") }, onLongClick = {}) {
                    BadgedBox(badge = {
                        if (splashDone && (updateState is UpdateState.SoftUpdate || updateState is UpdateState.CriticalUpdate)) {
                            Badge()
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings), Modifier.size(24.dp))
                    }
                }
            },
            scrollBehavior = if (isLibraryRoute || shouldUseFloatingTopBar) null else scrollController.topAppBarScrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (shouldUseFloatingTopBar) Color.Transparent else if (pureBlack) Color.Black else surfaceColor,
                scrolledContainerColor = if (shouldUseFloatingTopBar) Color.Transparent else if (pureBlack) Color.Black else surfaceColor,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}
