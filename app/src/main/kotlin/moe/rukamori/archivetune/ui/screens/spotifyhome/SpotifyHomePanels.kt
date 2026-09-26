/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.spotifyhome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.models.SpotifyRecentItem
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.ui.screens.HomeSectionHeader

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeStatePane(
    iconResId: Int?,
    messageResId: Int?,
    modifier: Modifier = Modifier,
    actionResId: Int? = null,
    showLoadingIndicator: Boolean = false,
    onAction: (() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            if (showLoadingIndicator) {
                LoadingIndicator()
            } else {
                iconResId?.let {
                    Icon(
                        painter = painterResource(it),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                }
                messageResId?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (actionResId != null && onAction != null) {
                    Spacer(Modifier.height(20.dp))
                    FilledTonalButton(onClick = onAction) {
                        Text(stringResource(actionResId))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SpotifyRecentPanel(
    recentItems: List<SpotifyRecentItem>,
    frequentArtists: List<SpotifyArtist>,
    onPlaylistClick: (SpotifyRecentItem.Playlist) -> Unit,
    onAlbumClick: (SpotifyRecentItem.Album) -> Unit,
    onArtistClick: (SpotifyArtist) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (recentItems.isNotEmpty()) {
            HomeSectionHeader(
                title = stringResource(R.string.spotify_recently_played),
            )
            SpotifyQuickGrid(
                items = recentItems,
                maxItems = 8,
                columns = 2
            ) { item ->
                when (item) {
                    is SpotifyRecentItem.Playlist -> {
                        SpotifyQuickGridCell(
                            title = item.title,
                            imageUrl = item.thumbnailUrl,
                            onClick = { onPlaylistClick(item) },
                            isArtist = false
                        )
                    }
                    is SpotifyRecentItem.Album -> {
                        SpotifyQuickGridCell(
                            title = item.title,
                            imageUrl = item.thumbnailUrl,
                            onClick = { onAlbumClick(item) },
                            isArtist = false
                        )
                    }
                }
            }
        }

        if (frequentArtists.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            HomeSectionHeader(
                title = stringResource(R.string.spotify_frequently_listened),
            )
            SpotifyQuickGrid(
                items = frequentArtists,
                maxItems = 8,
                columns = 2
            ) { artist ->
                val thumbnail = remember(artist.id) {
                    artist.images.maxByOrNull { it.width ?: 0 }?.url
                        ?: artist.images.firstOrNull()?.url
                }
                SpotifyQuickGridCell(
                    title = artist.name,
                    imageUrl = thumbnail,
                    onClick = { onArtistClick(artist) },
                    isArtist = true
                )
            }
        }
    }
}
