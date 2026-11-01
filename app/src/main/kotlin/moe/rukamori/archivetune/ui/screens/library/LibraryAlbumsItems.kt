/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.main.LocalDatabase
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.playback.queues.LocalAlbumRadio
import moe.rukamori.archivetune.ui.component.LibraryEmptyState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.YumaSegmentPosition
import moe.rukamori.archivetune.ui.theme.yumaCombinedClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

@Composable
fun AlbumGridItem(
    album: Album,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SettingsDimensions.LibraryCardRadius))
                .yumaCombinedClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(SettingsDimensions.LibraryCardRadius)),
        ) {
            AsyncImage(
                model = album.album.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.play),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.album.title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.artists.joinToString(", ") { it.name },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AlbumGridItem(
    album: Album,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberYumaHaptics()
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current

    AlbumGridItem(
        album = album,
        onClick = { navController.navigate("album/${album.id}") },
        onLongClick = {
            haptics.longPress()
            menuState.show {
                AlbumMenu(
                    originalAlbum = album,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        onPlayClick = {
            coroutineScope.launch {
                database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                    playerConnection?.playQueue(LocalAlbumRadio(albumWithSongs))
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
fun AlbumListRow(
    album: Album,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val yumaColors = LocalYumaColors.current

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .yumaCombinedClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .yumaGlassCard(
                    shape = RoundedCornerShape(SettingsDimensions.LibraryCardRadius),
                    backgroundColor = yumaColors.glassBackground,
                    borderColor = yumaColors.glassBorder,
                    position = YumaSegmentPosition.Single,
                )
                .clip(RoundedCornerShape(SettingsDimensions.LibraryCardRadius))
                .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = album.album.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(SettingsDimensions.LibrarySmallRadius)),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.album.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = album.artists.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(
            onClick = onPlayClick,
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            modifier = Modifier.size(SettingsDimensions.LibraryChipHeight),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.play),
                contentDescription = stringResource(R.string.play),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun AlbumListRow(
    album: Album,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberYumaHaptics()
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current

    AlbumListRow(
        album = album,
        onClick = { navController.navigate("album/${album.id}") },
        onLongClick = {
            haptics.longPress()
            menuState.show {
                AlbumMenu(
                    originalAlbum = album,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        onPlayClick = {
            coroutineScope.launch {
                database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                    playerConnection?.playQueue(LocalAlbumRadio(albumWithSongs))
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
fun AlbumsEmptySection(modifier: Modifier = Modifier) {
    LibraryEmptyState(
        iconRes = R.drawable.album,
        titleRes = R.string.no_results_found,
        subtitleRes = R.string.library_albums_subtitle,
        modifier = modifier.padding(vertical = 24.dp),
    )
}
