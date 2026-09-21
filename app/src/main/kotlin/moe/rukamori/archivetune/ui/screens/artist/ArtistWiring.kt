/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.artist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.shimmer.ButtonPlaceholder
import moe.rukamori.archivetune.ui.component.shimmer.ListItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.viewmodels.ArtistAction
import moe.rukamori.archivetune.viewmodels.ArtistBlockState
import moe.rukamori.archivetune.viewmodels.ArtistEvent
import moe.rukamori.archivetune.viewmodels.ArtistViewModel

@Composable
fun rememberArtistUiState(
    loadedArtistPage: ArtistPage?,
    libraryArtist: Artist?,
    loadedLibrarySongs: List<Song>,
    loadedLibraryAlbums: List<Album>,
    blockState: ArtistBlockState,
    showLocal: Boolean,
): ArtistUiState {
    val isArtistBlocked = (blockState as? ArtistBlockState.Success)?.isBlocked == true
    val artistPage =
        remember(loadedArtistPage, isArtistBlocked) {
            if (isArtistBlocked) {
                loadedArtistPage?.copy(
                    artist =
                        loadedArtistPage.artist.copy(
                            playEndpoint = null,
                            shuffleEndpoint = null,
                            radioEndpoint = null,
                        ),
                    sections = emptyList(),
                )
            } else {
                loadedArtistPage
            }
        }
    val librarySongs = remember(loadedLibrarySongs, isArtistBlocked) { loadedLibrarySongs.takeUnless { isArtistBlocked }.orEmpty() }
    val libraryAlbums = remember(loadedLibraryAlbums, isArtistBlocked) { loadedLibraryAlbums.takeUnless { isArtistBlocked }.orEmpty() }

    return remember(artistPage, libraryArtist, librarySongs, libraryAlbums, blockState, showLocal) {
        ArtistUiState(
            artistPage = artistPage,
            libraryArtist = libraryArtist,
            librarySongs = librarySongs,
            libraryAlbums = libraryAlbums,
            blockState = blockState,
            showLocal = showLocal,
        )
    }
}

@Composable
fun rememberArtistActions(
    viewModel: ArtistViewModel,
    onShowLocalChange: (Boolean) -> Unit = {},
): ArtistActions {
    return remember(viewModel, onShowLocalChange) {
        ArtistActions(
            onBlock = { viewModel.onAction(ArtistAction.ToggleBlock) },
            onShare = { viewModel.onAction(ArtistAction.Share) },
            onShowLocal = onShowLocalChange,
        )
    }
}

@Composable
fun ArtistEffects(
    viewModel: ArtistViewModel,
    snackbarHostState: SnackbarHostState,
    context: Context,
    libraryArtist: Artist?,
    onShowLocalChange: (Boolean) -> Unit,
) {
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ArtistEvent.Share -> {
                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, event.link)
                        }
                    context.startActivity(Intent.createChooser(shareIntent, null))
                }

                is ArtistEvent.CopyLink -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.copy_link), event.link))
                    Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                }

                is ArtistEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
                }
            }
        }
    }

    LaunchedEffect(libraryArtist) {
        onShowLocalChange(libraryArtist?.artist?.isLocal == true)
    }
}

@Composable
fun rememberArtistGradientState(
    thumbnailUrl: String?,
    context: Context,
    fallbackColor: Int,
    lazyListState: LazyListState,
): Pair<List<Color>, Float> {
    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }

    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl != null) {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(thumbnailUrl)
                    .size(Size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE))
                    .allowHardware(false)
                    .build()

            val result =
                runCatching {
                    context.imageLoader.execute(request)
                }.getOrNull()

            if (result != null) {
                val bitmap = result.image?.toBitmap()
                if (bitmap != null) {
                    val palette =
                        withContext(Dispatchers.Default) {
                            Palette
                                .from(bitmap)
                                .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                .generate()
                        }

                    val extractedColors =
                        PlayerColorExtractor.extractGradientColors(
                            palette = palette,
                            fallbackColor = fallbackColor,
                        )
                    gradientColors = extractedColors
                }
            }
        } else {
            gradientColors = emptyList()
        }
    }

    val gradientAlpha by remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex == 0) {
                val offset = lazyListState.firstVisibleItemScrollOffset
                (1f - (offset / 800f)).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    return gradientColors to gradientAlpha
}

@Composable
fun ArtistGradientBackground(
    gradientColors: List<Color>,
    gradientAlpha: Float,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
) {
    if (gradientColors.isNotEmpty() && gradientAlpha > 0f) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.65f)
                    .zIndex(-1f)
                    .drawBehind {
                        val width = size.width
                        val height = size.height

                        if (gradientColors.size >= 3) {
                            val c0 = gradientColors[0]
                            val c1 = gradientColors[1]
                            val c2 = gradientColors[2]
                            val c3 = gradientColors.getOrElse(3) { c0 }
                            val c4 = gradientColors.getOrElse(4) { c1 }
                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c0.copy(alpha = gradientAlpha * 0.72f),
                                                c0.copy(alpha = gradientAlpha * 0.4f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.5f, height * 0.2f),
                                        radius = width * 0.7f,
                                    ),
                            )
                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c1.copy(alpha = gradientAlpha * 0.56f),
                                                c1.copy(alpha = gradientAlpha * 0.3f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.15f, height * 0.35f),
                                        radius = width * 0.6f,
                                    ),
                            )
                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c2.copy(alpha = gradientAlpha * 0.52f),
                                                c2.copy(alpha = gradientAlpha * 0.26f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.85f, height * 0.45f),
                                        radius = width * 0.65f,
                                    ),
                            )
                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c3.copy(alpha = gradientAlpha * 0.34f),
                                                c3.copy(alpha = gradientAlpha * 0.18f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.35f, height * 0.6f),
                                        radius = width * 0.8f,
                                    ),
                            )
                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                c4.copy(alpha = gradientAlpha * 0.28f),
                                                c4.copy(alpha = gradientAlpha * 0.14f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.55f, height * 0.85f),
                                        radius = width * 0.95f,
                                    ),
                            )
                        } else if (gradientColors.isNotEmpty()) {
                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                gradientColors[0].copy(alpha = gradientAlpha * 0.6f),
                                                gradientColors[0].copy(alpha = gradientAlpha * 0.3f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.5f, height * 0.3f),
                                        radius = width * 0.8f,
                                    ),
                            )
                        }

                        drawRect(
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Transparent,
                                            Color.Transparent,
                                            surfaceColor.copy(alpha = gradientAlpha * 0.22f),
                                            surfaceColor.copy(alpha = gradientAlpha * 0.55f),
                                            surfaceColor,
                                        ),
                                    startY = height * 0.4f,
                                    endY = height,
                                ),
                        )
                    },
        )
    }
}

fun LazyListScope.artistShimmerItem(
    expandedHeight: Dp,
    topPadding: Dp,
) {
    item(key = ARTIST_KEY_SHIMMER, contentType = "shimmer") {
        ShimmerHost {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = topPadding),
            ) {
                Spacer(modifier = Modifier.height(expandedHeight * 0.55f))

                TextPlaceholder(
                    height = 32.dp,
                    modifier =
                        Modifier
                            .fillMaxWidth(0.5f)
                            .align(Alignment.CenterHorizontally),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    repeat(3) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            TextPlaceholder(
                                height = 20.dp,
                                modifier = Modifier.width(40.dp),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TextPlaceholder(
                                height = 14.dp,
                                modifier = Modifier.width(50.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                ) {
                    ButtonPlaceholder(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp),
                    )
                    ButtonPlaceholder(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp),
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            repeat(5) {
                ListItemPlaceHolder()
            }
        }
    }
}

fun showArtistOverflowMenu(
    menuState: MenuState,
    uiState: ArtistUiState,
    onAction: (ArtistAction) -> Unit,
) {
    val isArtistBlocked = (uiState.blockState as? ArtistBlockState.Success)?.isBlocked == true
    val blockActionEnabled =
        uiState.blockState !is ArtistBlockState.Loading &&
            (
                uiState.artistPage?.artist?.title.orEmpty().isNotBlank() ||
                    uiState.libraryArtist?.artist?.name.orEmpty().isNotBlank()
            )
    menuState.show {
        ArtistOverflowMenu(
            isBlocked = isArtistBlocked,
            blockActionEnabled = blockActionEnabled,
            onAction = { action ->
                onAction(action)
                menuState.dismiss()
            },
        )
    }
}

@Composable
fun ArtistOverflowMenu(
    isBlocked: Boolean,
    blockActionEnabled: Boolean,
    onAction: (ArtistAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ArtistOverflowMenuItem(
            text = stringResource(R.string.share),
            iconRes = R.drawable.ic_share,
            index = 0,
            count = ArtistOverflowMenuItemCount,
            onClick = { onAction(ArtistAction.Share) },
        )
        ArtistOverflowMenuItem(
            text = stringResource(R.string.copy_link),
            iconRes = R.drawable.copy,
            index = 1,
            count = ArtistOverflowMenuItemCount,
            onClick = { onAction(ArtistAction.CopyLink) },
        )
        ArtistOverflowMenuItem(
            text = stringResource(if (isBlocked) R.string.unblock_artist else R.string.block_artist),
            iconRes = R.drawable.block,
            index = 2,
            count = ArtistOverflowMenuItemCount,
            enabled = blockActionEnabled,
            onClick = { onAction(ArtistAction.ToggleBlock) },
        )
    }
}

@Composable
private fun ArtistOverflowMenuItem(
    text: String,
    iconRes: Int,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        onClick = onClick,
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
            )
        },
    ) {
        Text(text = text)
    }
}

private const val ArtistOverflowMenuItemCount = 3
