/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

/**
 * Error state, faithful to the legacy online variants
 * (OnlinePlaylistListSection.kt:272-337):
 *  - "PLAYLIST_PRIVATE" sentinel -> anime_blank art + private title/desc;
 *  - error != null -> error_unknown title + the error detail + retry;
 *  - error == null (loaded nothing, no error) -> playlist_not_found pair.
 */
internal fun LazyListScope.playlistDetailErrorItem(
    error: String?,
    onRetry: () -> Unit,
) {
    val isPrivatePlaylist = error?.contains("PLAYLIST_PRIVATE") == true
    item(key = PLAYLIST_DETAIL_KEY_ERROR, contentType = CONTENT_TYPE_PLAYLIST_DETAIL_ERROR) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isPrivatePlaylist) {
                Image(
                    painter = painterResource(R.drawable.anime_blank),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.playlist_private_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.playlist_private_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text =
                        if (error != null) {
                            stringResource(R.string.error_unknown)
                        } else {
                            stringResource(R.string.playlist_not_found)
                        },
                    style = MaterialTheme.typography.titleLarge,
                    color =
                        if (error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        if (error != null) {
                            error
                        } else {
                            stringResource(R.string.playlist_not_found_desc)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}
