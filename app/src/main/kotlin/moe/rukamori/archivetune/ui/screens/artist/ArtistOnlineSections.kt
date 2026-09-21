/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.artist

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import moe.rukamori.archivetune.models.MediaMetadata
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CONTENT_TYPE_HEADER
import moe.rukamori.archivetune.constants.CONTENT_TYPE_LIST
import moe.rukamori.archivetune.constants.CONTENT_TYPE_SONG
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import moe.rukamori.archivetune.innertube.pages.ArtistSectionLayout
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.haptics.YumaHaptics

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.artistOnlineSections(
    artistPage: ArtistPage?,
    artistId: String,
    navController: NavController,
    playerConnection: PlayerConnection,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    menuState: MenuState,
    haptics: YumaHaptics,
    coroutineScope: CoroutineScope,
) {
    artistPage?.sections?.fastForEach { section ->
        if (section.items.isNotEmpty()) {
            item(
                key = "youtube_section_header_${section.title}_${section.items.firstOrNull()?.id.orEmpty()}_${section.moreEndpoint?.browseId.orEmpty()}",
                contentType = CONTENT_TYPE_HEADER,
            ) {
                NavigationTitle(
                    title = section.title,
                    onClick =
                        section.moreEndpoint?.let {
                            {
                                navController.navigate(buildArtistItemsRoute(artistId, it))
                            }
                        },
                )
            }
        }

        if (section.layout == ArtistSectionLayout.LIST && section.items.all { it is SongItem }) {
            itemsIndexed(
                items = section.items.distinctBy { it.id },
                key = { index, song -> "youtube_song_${song.id}_$index" },
                contentType = { _, _ -> CONTENT_TYPE_SONG },
            ) { index, song ->
                YouTubeListItem(
                    item = song as SongItem,
                    isActive = song.isActive(mediaMetadata),
                    isPlaying = isPlaying,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                showYTItemMenu(
                                    menuState = menuState,
                                    item = song,
                                    navController = navController,
                                    coroutineScope = coroutineScope,
                                )
                            },
                            onLongClick = {},
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .combinedClickable(
                                onClick = {
                                    if (song.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        onYTItemClick(
                                            item = song,
                                            navController = navController,
                                            playerConnection = playerConnection,
                                        )
                                    }
                                },
                                onLongClick = {
                                    haptics.longPress()
                                    showYTItemMenu(
                                        menuState = menuState,
                                        item = song,
                                        navController = navController,
                                        coroutineScope = coroutineScope,
                                    )
                                },
                            ).animateItem(),
                )
            }
        } else {
            item(
                key = "youtube_section_grid_${section.title}_${section.items.firstOrNull()?.id.orEmpty()}_${section.moreEndpoint?.browseId.orEmpty()}",
                contentType = CONTENT_TYPE_LIST,
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = section.items.distinctBy { it.id },
                        key = { "youtube_${it.contentKey}" },
                        contentType = { it.contentType },
                    ) { item ->
                        YouTubeGridItem(
                            item = item,
                            isActive = item.isActive(mediaMetadata),
                            isPlaying = isPlaying,
                            coroutineScope = coroutineScope,
                            modifier =
                                Modifier
                                    .combinedClickable(
                                        onClick = {
                                            onYTItemClick(
                                                item = item,
                                                navController = navController,
                                                playerConnection = playerConnection,
                                            )
                                        },
                                        onLongClick = {
                                            haptics.longPress()
                                            showYTItemMenu(
                                                menuState = menuState,
                                                item = item,
                                                navController = navController,
                                                coroutineScope = coroutineScope,
                                            )
                                        },
                                    ).animateItem(),
                        )
                    }
                }
            }
        }
    }
}

fun buildArtistItemsRoute(
    artistId: String,
    endpoint: BrowseEndpoint,
): String {
    val encodedArtistId = Uri.encode(artistId)
    val encodedBrowseId = Uri.encode(endpoint.browseId)
    val encodedParams =
        endpoint.params
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.encode(it) }

    return buildString {
        append("artist/")
        append(encodedArtistId)
        append("/items?browseId=")
        append(encodedBrowseId)
        if (encodedParams != null) {
            append("&params=")
            append(encodedParams)
        }
    }
}
