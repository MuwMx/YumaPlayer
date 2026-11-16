/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.component.YumaEmptyState
import moe.rukamori.archivetune.ui.component.YumaMood
import moe.rukamori.archivetune.ui.component.shimmer.ButtonPlaceholder
import moe.rukamori.archivetune.ui.component.shimmer.ListItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.ui.screens.playlist.shared.PlaylistSearchStateHolder
import moe.rukamori.archivetune.ui.screens.playlist.shared.rememberPlaylistSearchStateHolder

/**
 * The screen's search/selection state — title/artist match for every
 * origin plus the SPOTIFY album-name arm (legacy SpotifyPlaylistScreen
 * 154-158 matched album names too; pinned).
 */
@Composable
internal fun rememberPlaylistDetailSearchState(
    tracks: List<PlaylistTrack>,
): PlaylistSearchStateHolder<PlaylistTrack> =
    rememberPlaylistSearchStateHolder(
        items = tracks,
        filter = { track, query ->
            track.title.contains(query, ignoreCase = true) ||
                track.artists.any { it.contains(query, ignoreCase = true) } ||
                (track.origin as? SpotifyTrack)?.album?.name
                    ?.contains(query, ignoreCase = true) == true
        },
    )

/**
 * Loading shimmer. Mirrors the LOADED screen's shape — hero skeleton
 * (title, author, metadata chips, action row) then track rows — the
 * legacy OnlinePlaylistListSection.onlinePlaylistShimmerItem
 * (82-155): blank rows alone made the header "jump in" after load.
 */
internal fun LazyListScope.playlistDetailShimmerItem() {
    item(key = PLAYLIST_DETAIL_KEY_SHIMMER, contentType = CONTENT_TYPE_PLAYLIST_DETAIL_EMPTY) {
        ShimmerHost {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Title (headlineSmall) + author (titleMedium)
                TextPlaceholder(
                    height = 28.dp,
                    modifier = Modifier.fillMaxWidth(0.6f).padding(horizontal = 32.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextPlaceholder(
                    height = 20.dp,
                    modifier = Modifier.fillMaxWidth(0.4f),
                )
                Spacer(modifier = Modifier.height(16.dp))

                // MetadataChip row (song count + duration)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    repeat(2) {
                        TextPlaceholder(
                            height = 32.dp,
                            modifier = Modifier.width(80.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // Hero action row: download circle, Play pill, Shuffle pill
                // (PlaylistDetailHeroActionsHost's shape).
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .shimmer()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface),
                    )
                    ButtonPlaceholder(modifier = Modifier.weight(1f).height(48.dp))
                    ButtonPlaceholder(modifier = Modifier.weight(1f).height(48.dp))
                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .shimmer()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface),
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            repeat(6) { ListItemPlaceHolder() }
        }
    }
}

/** Hero item; skipped (no item emitted) while searching or headerless. */
internal fun LazyListScope.playlistDetailHeroItem(
    header: moe.rukamori.archivetune.playlist.source.PlaylistHeaderInfo?,
    systemBarsTopPadding: androidx.compose.ui.unit.Dp,
    headerProgress: () -> Float,
    disableBlur: Boolean,
    actions: (@Composable () -> Unit)? = null,
    artworkOverlay: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    onAuthorClick: ((authorId: String) -> Unit)? = null,
) {
    if (header == null) return
    item(key = PLAYLIST_DETAIL_KEY_HEADER, contentType = CONTENT_TYPE_PLAYLIST_DETAIL_HEADER) {
        PlaylistDetailHeroSection(
            header = header,
            systemBarsTopPadding = systemBarsTopPadding,
            headerProgress = headerProgress,
            disableBlur = disableBlur,
            actions = actions,
            artworkOverlay = artworkOverlay,
            onAuthorClick = onAuthorClick,
        )
    }
}

/**
 * Empty state: "playlist is empty" for an empty playlist, the legacy Cache
 * "no results" wording when a search filters everything out (line 279-285).
 * Spotify routes pass their own pair (spotify_no_tracks /
 * ai_model_no_results, SpotifyPlaylistScreen.kt:261-275) via
 * [rememberPlaylistDetailEmptyTexts].
 */
internal fun LazyListScope.playlistDetailEmptyItem(
    isSearching: Boolean,
    emptyText: String? = null,
    searchEmptyText: String? = null,
) {
    item(key = PLAYLIST_DETAIL_KEY_EMPTY, contentType = CONTENT_TYPE_PLAYLIST_DETAIL_EMPTY) {
        YumaEmptyState(
            title =
                when {
                    isSearching -> searchEmptyText ?: stringResource(R.string.no_results_found)
                    else -> emptyText ?: stringResource(R.string.playlist_is_empty)
                },
            mood = YumaMood.Shrug,
        )
    }
}

/**
 * SPOTIFY PLAYLIST SLOT — the empty-state wordings the legacy Spotify screen
 * shows (SpotifyPlaylistScreen.kt:261-275): "No Spotify tracks found." for
 * an empty playlist, "No models match your search" when filtering empties
 * it. Null keeps the shared default pair on every other route.
 */
internal data class PlaylistDetailEmptyTexts(
    val empty: String,
    val searchEmpty: String,
)

@Composable
internal fun rememberPlaylistDetailEmptyTexts(
    viewModel: PlaylistDetailViewModel,
): PlaylistDetailEmptyTexts? {
    if (viewModel.source !is moe.rukamori.archivetune.playlist.source.SpotifyPlaylistSource) return null
    return PlaylistDetailEmptyTexts(
        empty = stringResource(R.string.spotify_no_tracks),
        searchEmpty = stringResource(R.string.ai_model_no_results),
    )
}

/** Loading-more footer for paginated sources (InnerTube, Spotify). */
internal fun LazyListScope.playlistDetailLoadingMoreItem() {
    item(key = PLAYLIST_DETAIL_KEY_LOADING_MORE, contentType = CONTENT_TYPE_PLAYLIST_DETAIL_LOADING_MORE) {
        ShimmerHost {
            repeat(2) { ListItemPlaceHolder() }
        }
    }
}
