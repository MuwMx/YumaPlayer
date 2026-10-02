package moe.rukamori.archivetune.ui.scaffold

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.util.fastAny
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.ACTION_SEARCH
import moe.rukamori.archivetune.constants.SearchSource
import moe.rukamori.archivetune.constants.SearchSourceKey
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultArgument
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultRoutePrefix
import moe.rukamori.archivetune.ui.screens.search.decodeOnlineSearchQuery
import moe.rukamori.archivetune.ui.screens.search.onlineSearchResultRoute
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

@Composable
fun rememberVoiceSearchLauncher(
    searchState: ScaffoldSearchState,
): ManagedActivityResultLauncher<Intent, ActivityResult> {
    return rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                searchState.onQueryChange(TextFieldValue(spokenText, TextRange(spokenText.length)))
                searchState.onSearch(spokenText)
            }
        }
    }
}

@Composable
fun rememberOnlineSearchSort(
    navBackStackEntry: NavBackStackEntry?,
): Pair<OnlineSearchViewModel?, State<OnlineSearchSort>> {
    val currentRoute = navBackStackEntry?.destination?.route
    val viewModel: OnlineSearchViewModel? =
        if (currentRoute?.startsWith(OnlineSearchResultRoutePrefix) == true && navBackStackEntry != null) {
            hiltViewModel(navBackStackEntry)
        } else {
            null
        }
    val sortState = viewModel?.sort?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(OnlineSearchSort.DEFAULT) }
    return viewModel to sortState
}
