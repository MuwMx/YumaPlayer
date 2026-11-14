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
) {
    if (header == null) return
    item(key = PLAYLIST_DETAIL_KEY_HEADER, contentType = CONTENT_TYPE_PLAYLIST_DETAIL_HEADER) {
        PlaylistDetailHeroSection(
            header = header,
            systemBarsTopPadding = systemBarsTopPadding,
            headerProgress = headerProgress,
            disableBlur = disableBlur,
        )
    }
}

/** Empty state — the local wording (works for every source). */
internal fun LazyListScope.playlistDetailEmptyItem() {
    item(key = PLAYLIST_DETAIL_KEY_EMPTY, contentType = CONTENT_TYPE_PLAYLIST_DETAIL_EMPTY) {
        YumaEmptyState(
            title = stringResource(R.string.playlist_is_empty),
            mood = YumaMood.Shrug,
        )
    }
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
