package moe.rukamori.archivetune.ui.scaffold

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.ACTION_SEARCH
import moe.rukamori.archivetune.OnlineSearchSortMenu
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SearchSource
import moe.rukamori.archivetune.constants.SearchSourceKey
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.TopSearch
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultArgument
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultRoutePrefix
import moe.rukamori.archivetune.ui.screens.search.decodeOnlineSearchQuery
import moe.rukamori.archivetune.ui.screens.search.onlineSearchResultRoute
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.viewmodels.OnlineSearchSort
import moe.rukamori.archivetune.viewmodels.OnlineSearchViewModel

@Stable
class ScaffoldSearchState(
    val query: TextFieldValue,
    val onQueryChange: (TextFieldValue) -> Unit,
    val active: Boolean,
    val onActiveChange: (Boolean) -> Unit,
    val searchSource: SearchSource,
    val onSearchSourceChange: (SearchSource) -> Unit,
    val searchBarFocusRequester: FocusRequester,
    val tvRailFocusRequester: FocusRequester,
    val contentAreaFocusRequester: FocusRequester,
    val openSearch: () -> Unit,
    val onSearch: (String) -> Unit,
)

@Composable
fun rememberScaffoldSearchState(
    activity: ComponentActivity,
    navController: NavHostController,
    navBackStackEntry: NavBackStackEntry?,
    navigationItems: List<Screens>,
    topLevelScreens: List<String>,
    playerViewModel: PlayerViewModel,
    focusManager: FocusManager,
): ScaffoldSearchState {
    val (query, onQueryChange) = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var active by rememberSaveable { mutableStateOf(false) }
    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
    val searchBarFocusRequester = remember { FocusRequester() }
    val tvRailFocusRequester = remember { FocusRequester() }
    val contentAreaFocusRequester = remember { FocusRequester() }

    val onActiveChange: (Boolean) -> Unit = { newActive ->
        active = newActive
        if (!newActive) {
            focusManager.clearFocus()
            if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                onQueryChange(TextFieldValue())
            }
        }
    }

    val openSearch: () -> Unit = {
        onActiveChange(true)
        searchBarFocusRequester.requestFocus()
    }

    val onSearch: (String) -> Unit = { queryText ->
        if (queryText.isNotEmpty()) {
            onActiveChange(false)
            navController.navigate(onlineSearchResultRoute(queryText))
            playerViewModel.addSearchHistory(queryText)
        }
    }

    var openSearchImmediately by remember { mutableStateOf(activity.intent?.action == ACTION_SEARCH) }
    val shouldShowSearchBar = remember(active, navBackStackEntry) {
        active || navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
            navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == true
    }

    LaunchedEffect(shouldShowSearchBar, openSearchImmediately) {
        if (shouldShowSearchBar && openSearchImmediately) {
            onActiveChange(true)
            runCatching {
                delay(100)
                searchBarFocusRequester.requestFocus()
            }
            openSearchImmediately = false
        }
    }

    val openSearchFromRoute by navBackStackEntry?.savedStateHandle?.getStateFlow("openSearch", false)?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }

    LaunchedEffect(openSearchFromRoute) {
        if (openSearchFromRoute) {
            navBackStackEntry?.savedStateHandle?.set("openSearch", false)
            openSearch()
        }
    }

    LaunchedEffect(active) {
        if (active) searchBarFocusRequester.requestFocus()
    }

    LaunchedEffect(navBackStackEntry) {
        if (navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == true) {
            val searchQuery = decodeOnlineSearchQuery(navBackStackEntry.arguments?.getString(OnlineSearchResultArgument).orEmpty())
            onQueryChange(TextFieldValue(searchQuery, TextRange(searchQuery.length)))
        } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } || navBackStackEntry?.destination?.route in topLevelScreens) {
            onQueryChange(TextFieldValue())
        }
    }

    return remember(query, active, searchSource, searchBarFocusRequester, tvRailFocusRequester, contentAreaFocusRequester) {
        ScaffoldSearchState(
            query = query,
            onQueryChange = onQueryChange,
            active = active,
            onActiveChange = onActiveChange,
            searchSource = searchSource,
            onSearchSourceChange = { searchSource = it },
            searchBarFocusRequester = searchBarFocusRequester,
            tvRailFocusRequester = tvRailFocusRequester,
            contentAreaFocusRequester = contentAreaFocusRequester,
            openSearch = openSearch,
            onSearch = onSearch,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaffoldSearchBarHost(
    searchState: ScaffoldSearchState,
    navController: NavHostController,
    navBackStackEntry: NavBackStackEntry?,
    navigationItems: List<Screens>,
    searchHazeState: HazeState?,
    isMiniPlayerVisible: Boolean,
    disableAnimations: Boolean,
    pureBlack: Boolean,
    blurRadius: Float,
    modifier: Modifier = Modifier,
) {
    val voiceSearchLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                searchState.onQueryChange(TextFieldValue(spokenText, TextRange(spokenText.length)))
                searchState.onSearch(spokenText)
            }
        }
    }

    val currentRoute = navBackStackEntry?.destination?.route
    val onlineSearchViewModel: OnlineSearchViewModel? =
        if (currentRoute?.startsWith(OnlineSearchResultRoutePrefix) == true && navBackStackEntry != null) {
            hiltViewModel(navBackStackEntry)
        } else {
            null
        }
    val onlineSearchSort by onlineSearchViewModel?.sort?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(OnlineSearchSort.DEFAULT) }

    AnimatedVisibility(
        visible = searchState.active || currentRoute?.startsWith(OnlineSearchResultRoutePrefix) == true,
        enter = fadeIn(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 300)),
        exit = fadeOut(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 200)),
    ) {
        TopSearch(
            query = searchState.query,
            onQueryChange = searchState.onQueryChange,
            onSearch = searchState.onSearch,
            active = searchState.active,
            onActiveChange = searchState.onActiveChange,
            placeholder = {
                Text(
                    text = stringResource(
                        when (searchState.searchSource) {
                            SearchSource.LOCAL -> R.string.search_library
                            SearchSource.ONLINE -> R.string.search_yt_music
                        },
                    ),
                )
            },
            leadingIcon = {
                val iconMotionDuration = if (disableAnimations) 0 else 300
                IconButton(
                    onClick = {
                        if (searchState.active) searchState.onActiveChange(false) else searchState.onActiveChange(true)
                    },
                    onLongClick = {
                        if (!searchState.active && !navigationItems.fastAny { it.route == currentRoute }) navController.backToMain()
                    },
                ) {
                    AnimatedContent(
                        targetState = searchState.active,
                        transitionSpec = {
                            (fadeIn(tween(iconMotionDuration)) + scaleIn(initialScale = 0.8f, animationSpec = tween(iconMotionDuration)))
                                .togetherWith(fadeOut(tween(iconMotionDuration)) + scaleOut(targetScale = 0.8f, animationSpec = tween(iconMotionDuration)))
                        },
                        label = "LeadingIconMorph",
                    ) { isExpanded ->
                        Icon(painterResource(if (isExpanded) R.drawable.arrow_back else R.drawable.ic_search), null)
                    }
                }
            },
            trailingIcon = {
                val iconMotionDuration = if (disableAnimations) 0 else 300
                AnimatedContent(
                    targetState = searchState.active,
                    transitionSpec = {
                        (fadeIn(tween(iconMotionDuration)) + scaleIn(initialScale = 0.85f, animationSpec = tween(iconMotionDuration)))
                            .togetherWith(fadeOut(tween(iconMotionDuration)) + scaleOut(targetScale = 0.85f, animationSpec = tween(iconMotionDuration)))
                    },
                    label = "TrailingIconMorph",
                ) { isExpanded ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isExpanded) {
                            if (searchState.query.text.isNotEmpty()) {
                                IconButton(onClick = { searchState.onQueryChange(TextFieldValue("")) }) {
                                    Icon(painterResource(R.drawable.close), null)
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        }
                                        runCatching { voiceSearchLauncher.launch(intent) }
                                    },
                                ) {
                                    Icon(painterResource(R.drawable.mic), stringResource(R.string.voice_search))
                                }
                            }
                            IconButton(
                                onClick = {
                                    searchState.onSearchSourceChange(
                                        if (searchState.searchSource == SearchSource.ONLINE) SearchSource.LOCAL else SearchSource.ONLINE,
                                    )
                                },
                            ) {
                                Icon(
                                    painterResource(
                                        when (searchState.searchSource) {
                                            SearchSource.LOCAL -> R.drawable.library_music
                                            SearchSource.ONLINE -> R.drawable.language
                                        },
                                    ),
                                    null,
                                )
                            }
                        } else if (onlineSearchViewModel != null) {
                            OnlineSearchSortMenu(selectedSort = onlineSearchSort, onSortSelected = onlineSearchViewModel::updateSort)
                        }
                    }
                }
            },
            modifier = modifier.focusRequester(searchState.searchBarFocusRequester),
            focusRequester = searchState.searchBarFocusRequester,
            leftFocusRequester = searchState.tvRailFocusRequester,
            colors = if (pureBlack && searchState.active) {
                SearchBarDefaults.colors(
                    containerColor = Color.Black,
                    dividerColor = Color.DarkGray,
                    inputFieldColors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.Gray,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            } else {
                SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            },
            hazeState = searchHazeState,
            pureBlack = pureBlack,
            blurRadius = blurRadius,
        ) {
            ScaffoldSearchResults(
                searchSource = searchState.searchSource,
                query = searchState.query,
                onQueryChange = searchState.onQueryChange,
                onDismiss = { searchState.onActiveChange(false) },
                navController = navController,
                isMiniPlayerVisible = isMiniPlayerVisible,
                disableAnimations = disableAnimations,
                pureBlack = pureBlack,
            )
        }
    }
}
