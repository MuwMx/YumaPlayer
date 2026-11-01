/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.YumaSegmentPosition
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

@Composable
fun FeaturedAlbumCard(
    album: Album,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val yumaColors = LocalYumaColors.current

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .yumaClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = onClick,
                )
                .yumaGlassCard(
                    shape = RoundedCornerShape(SettingsDimensions.LibrarySheetRadius),
                    backgroundColor = yumaColors.glassBackground,
                    borderColor = yumaColors.glassBorder,
                    position = YumaSegmentPosition.Single,
                )
                .clip(RoundedCornerShape(SettingsDimensions.LibrarySheetRadius))
                .padding(20.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = album.album.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(SettingsDimensions.LibraryCardRadius)),
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.featured_album_badge),
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = album.album.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = album.artists.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onPlayClick,
                    shape = CircleShape,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.play),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.play),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }

                IconButton(
                    onClick = onMenuClick,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.more_vert),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun FeaturedAlbumCard(
    album: Album,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current

    FeaturedAlbumCard(
        album = album,
        onClick = { navController.navigate("album/${album.id}") },
        onPlayClick = {
            coroutineScope.launch {
                database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                    playerConnection?.playQueue(LocalAlbumRadio(albumWithSongs))
                }
            }
        },
        onMenuClick = {
            menuState.show {
                AlbumMenu(
                    originalAlbum = album,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        modifier = modifier,
    )
}
