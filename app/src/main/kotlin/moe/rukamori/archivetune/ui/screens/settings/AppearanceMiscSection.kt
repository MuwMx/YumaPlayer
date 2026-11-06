/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LibraryFilter
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference

@Composable
fun AppearanceMiscSection(
    state: AppearanceUiState,
    actions: AppearanceUiActions,
    modifier: Modifier = Modifier,
) {
    AppearanceMiscSection(
        quickPicksDisplayMode = state.quickPicksDisplayMode,
        onQuickPicksDisplayModeChange = actions.onQuickPicksDisplayModeChange,
        defaultOpenTab = state.defaultOpenTab,
        onDefaultOpenTabChange = actions.onDefaultOpenTabChange,
        defaultChip = state.defaultChip,
        onDefaultChipChange = actions.onDefaultChipChange,
        showHomeCategoryChips = state.showHomeCategoryChips,
        onShowHomeCategoryChipsChange = actions.onShowHomeCategoryChipsChange,
        showTagsInLibrary = state.showTagsInLibrary,
        onShowTagsInLibraryChange = actions.onShowTagsInLibraryChange,
        swipeToSong = state.swipeToSong,
        onSwipeToSongChange = actions.onSwipeToSongChange,
        marqueeEnabled = state.marqueeEnabled,
        onMarqueeEnabledChange = actions.onMarqueeEnabledChange,
        modifier = modifier,
    )
}

@Composable
fun AppearanceMiscSection(
    quickPicksDisplayMode: QuickPicksDisplayMode,
    onQuickPicksDisplayModeChange: (QuickPicksDisplayMode) -> Unit,
    defaultOpenTab: NavigationTab,
    onDefaultOpenTabChange: (NavigationTab) -> Unit,
    defaultChip: LibraryFilter,
    onDefaultChipChange: (LibraryFilter) -> Unit,
    showHomeCategoryChips: Boolean,
    onShowHomeCategoryChipsChange: (Boolean) -> Unit,
    showTagsInLibrary: Boolean,
    onShowTagsInLibraryChange: (Boolean) -> Unit,
    swipeToSong: Boolean,
    onSwipeToSongChange: (Boolean) -> Unit,
    marqueeEnabled: Boolean,
    onMarqueeEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceGroup(
        title = stringResource(R.string.misc),
        modifier = modifier,
    ) {
        item {
            EnumListPreference(
                title = { Text(stringResource(R.string.quick_picks_display_mode)) },
                icon = { Icon(painterResource(R.drawable.grid_view), null, modifier = Modifier.size(24.dp)) },
                selectedValue = quickPicksDisplayMode,
                onValueSelected = onQuickPicksDisplayModeChange,
                valueText = {
                    when (it) {
                        QuickPicksDisplayMode.HERO -> stringResource(R.string.quick_picks_display_mode_hero)
                        QuickPicksDisplayMode.CARD -> stringResource(R.string.quick_picks_display_mode_card)
                        QuickPicksDisplayMode.LIST -> stringResource(R.string.quick_picks_display_mode_list)
                    }
                },
            )
        }

        item {
            EnumListPreference(
                title = { Text(stringResource(R.string.default_open_tab)) },
                icon = { Icon(painterResource(R.drawable.nav_bar), null, modifier = Modifier.size(24.dp)) },
                selectedValue = defaultOpenTab,
                onValueSelected = onDefaultOpenTabChange,
                valueText = {
                    when (it) {
                        NavigationTab.HOME -> stringResource(R.string.home)
                        NavigationTab.SEARCH -> stringResource(R.string.search)
                        NavigationTab.MOODANDGENRES -> stringResource(R.string.mood_and_genres)
                        NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                    }
                },
            )
        }

        item {
            ListPreference(
                title = { Text(stringResource(R.string.default_lib_chips)) },
                icon = { Icon(painterResource(R.drawable.tab), null, modifier = Modifier.size(24.dp)) },
                selectedValue = defaultChip,
                values =
                    listOf(
                        LibraryFilter.LIBRARY,
                        LibraryFilter.PLAYLISTS,
                        LibraryFilter.SONGS,
                        LibraryFilter.ALBUMS,
                        LibraryFilter.ARTISTS,
                    ),
                valueText = {
                    when (it) {
                        LibraryFilter.SONGS -> stringResource(R.string.songs)
                        LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                        LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                        LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                        LibraryFilter.SPOTIFY -> stringResource(R.string.spotify_playlists)
                        LibraryFilter.LIBRARY -> stringResource(R.string.filter_library)
                    }
                },
                onValueSelected = onDefaultChipChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.show_home_category_chips)) },
                description = stringResource(R.string.show_home_category_chips_desc),
                icon = { Icon(painterResource(R.drawable.ic_home_outline), null, modifier = Modifier.size(24.dp)) },
                checked = showHomeCategoryChips,
                onCheckedChange = onShowHomeCategoryChipsChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.show_tags_in_library)) },
                description = stringResource(R.string.show_tags_in_library_desc),
                icon = { Icon(painterResource(R.drawable.filter_alt), null, modifier = Modifier.size(24.dp)) },
                checked = showTagsInLibrary,
                onCheckedChange = onShowTagsInLibraryChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.swipe_song_to_add)) },
                icon = { Icon(painterResource(R.drawable.swipe), null, modifier = Modifier.size(24.dp)) },
                checked = swipeToSong,
                onCheckedChange = onSwipeToSongChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.marquee_enabled)) },
                description = stringResource(R.string.marquee_enabled_desc),
                icon = { Icon(painterResource(R.drawable.text_fields), null, modifier = Modifier.size(24.dp)) },
                checked = marqueeEnabled,
                onCheckedChange = onMarqueeEnabledChange,
            )
        }
    }
}
