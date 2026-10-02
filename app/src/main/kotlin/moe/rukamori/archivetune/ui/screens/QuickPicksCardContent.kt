/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.SongMenu

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun QuickPicksCardContent(
    distinctQuickPicks: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
) {
    val haptics = rememberYumaHaptics()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val heroHeight =
            when {
                maxWidth >= 840.dp -> 380.dp
                maxWidth >= 600.dp -> 356.dp
                else -> 332.dp
            }
        val heroMaxWidth =
            (maxWidth - 48.dp)
                .coerceAtLeast(232.dp)
                .coerceAtMost(440.dp)
        val density = LocalDensity.current
        val requestWidthPx = with(density) { heroMaxWidth.roundToPx().coerceAtLeast(1) }
        val requestHeightPx = with(density) { heroHeight.roundToPx().coerceAtLeast(1) }

        HorizontalCenteredHeroCarousel(
            state = rememberCarouselState { distinctQuickPicks.size },
            maxItemWidth = heroMaxWidth,
            itemSpacing = 10.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight),
        ) { index ->
            val song = distinctQuickPicks[index]
            val isActive = song.id == mediaMetadata?.id
            val context = LocalContext.current
            val imageRequest =
                remember(song.song.thumbnailUrl, requestWidthPx, requestHeightPx) {
                    ImageRequest
                        .Builder(context)
                        .data(song.song.thumbnailUrl)
                        .size(Size(requestWidthPx, requestHeightPx))
                        .crossfade(true)
                        .build()
                }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .maskClip(MaterialTheme.shapes.extraLarge)
                        .maskBorder(
                            BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                            ),
                            MaterialTheme.shapes.extraLarge,
                        ).focusable()
                        .combinedClickable(
                            onClick = {
                                if (isActive) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        if (song.song.isLocal) {
                                            ListQueue(items = listOf(song.toMediaItem()))
                                        } else {
                                            YouTubeQueue.radio(song.toMediaMetadata())
                                        },
                                    )
                                }
                            },
                            onLongClick = {
                                haptics.longPress()
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ),
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.48f to Color.Black.copy(alpha = 0.08f),
                                    1f to Color.Black.copy(alpha = 0.84f),
                                ),
                            ),
                )

                if (isActive && isPlaying) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        tonalElevation = 2.dp,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(14.dp)
                                .size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.volume_up),
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp),
                ) {
                    Text(
                        text = song.song.title,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artists.joinToString { it.name },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
