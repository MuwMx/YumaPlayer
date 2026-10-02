package moe.rukamori.archivetune.ui.scaffold

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.constants.SearchSource
import moe.rukamori.archivetune.ui.screens.search.LocalSearchScreen
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchScreen
import moe.rukamori.archivetune.ui.screens.search.onlineSearchResultRoute

@Composable
fun ScaffoldSearchResults(
    searchSource: SearchSource,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onDismiss: () -> Unit,
    navController: NavHostController,
    isMiniPlayerVisible: Boolean,
    disableAnimations: Boolean,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = searchSource,
        animationSpec = tween(durationMillis = if (disableAnimations) 0 else 300),
        label = "SearchResultsCrossfade",
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = if (isMiniPlayerVisible) MiniPlayerHeight else 0.dp)
            .navigationBarsPadding(),
    ) { source ->
        when (source) {
            SearchSource.LOCAL -> LocalSearchScreen(
                query = query.text,
                navController = navController,
                onDismiss = onDismiss,
                pureBlack = pureBlack,
            )
            SearchSource.ONLINE -> OnlineSearchScreen(
                query = query.text,
                onQueryChange = onQueryChange,
                navController = navController,
                onSearch = { onlineQuery -> navController.navigate(onlineSearchResultRoute(onlineQuery)) },
                onDismiss = onDismiss,
                pureBlack = pureBlack,
            )
        }
    }
}
