/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.ui.component.PlaylistListItem

@Composable
internal fun AddToPlaylistContent(
    playlists: List<Playlist>,
    selectedPlaylistIds: Set<String>,
    sortOption: AddToPlaylistSortOption,
    searchQuery: String,
    isAddingToPlaylist: Boolean,
    modifier: Modifier = Modifier,
    onSortOptionChange: (AddToPlaylistSortOption) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onTogglePlaylist: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        item(contentType = "sort") {
            AddToPlaylistSortRow(
                sortOption = sortOption,
                onSortOptionChange = onSortOptionChange,
            )
        }

        item(contentType = "create") {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.create_playlist),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCreatePlaylistClick)
                        .padding(horizontal = 8.dp),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        item(contentType = "playlistDivider") {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
        }

        if (playlists.isNotEmpty()) {
            items(
                items = playlists,
                key = { it.id },
                contentType = { "playlist" },
            ) { playlist ->
                val isSelected = selectedPlaylistIds.contains(playlist.id)
                val rowBackground by animateColorAsState(
                    targetValue =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            Color.Transparent
                        },
                    animationSpec = tween(durationMillis = 180),
                    label = "rowBackground",
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(rowBackground)
                            .clickable(enabled = !isAddingToPlaylist) {
                                onTogglePlaylist(playlist.id)
                            },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaylistListItem(
                        playlist = playlist,
                        modifier = Modifier.weight(1f),
                    )

                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
        } else {
            item(contentType = "empty") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp)
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    Text(
                        text =
                            if (searchQuery.isBlank()) {
                                stringResource(R.string.no_playlists_yet)
                            } else {
                                stringResource(R.string.no_matching_playlists)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
