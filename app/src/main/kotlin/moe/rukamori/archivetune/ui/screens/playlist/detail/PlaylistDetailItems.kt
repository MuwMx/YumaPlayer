/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.YumaEmptyState
import moe.rukamori.archivetune.ui.component.YumaMood
import moe.rukamori.archivetune.ui.component.shimmer.ListItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost

/** Loading shimmer, reusing the Online screen's shape. */
internal fun LazyListScope.playlistDetailShimmerItem() {
    item(key = PLAYLIST_DETAIL_KEY_SHIMMER, contentType = CONTENT_TYPE_PLAYLIST_DETAIL_EMPTY) {
        ShimmerHost {
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
) {
    if (header == null) return
    item(key = PLAYLIST_DETAIL_KEY_HEADER, contentType = CONTENT_TYPE_PLAYLIST_DETAIL_HEADER) {
        PlaylistDetailHeroSection(
            header = header,
            systemBarsTopPadding = systemBarsTopPadding,
            headerProgress = headerProgress,
            disableBlur = disableBlur,
            actions = actions,
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

/** Error state with retry; errors from the sources render their message. */
internal fun LazyListScope.playlistDetailErrorItem(
    error: String?,
    onRetry: () -> Unit,
) {
    item(key = PLAYLIST_DETAIL_KEY_ERROR, contentType = CONTENT_TYPE_PLAYLIST_DETAIL_ERROR) {
        Text(
            text =
                if (error != null) stringResource(R.string.error_unknown) else "",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp),
        )
        if (error != null) {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}
