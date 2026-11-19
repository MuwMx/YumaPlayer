/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu.spotify

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalMarqueeEnabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.main.LocalDatabase
import moe.rukamori.archivetune.ui.main.LocalSyncUtils
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString

/**
 * Header for [moe.rukamori.archivetune.ui.menu.SpotifySongMenu]: the
 * YouTubeSongMenuHeader layout (rounded surface, artwork, marquee title,
 * artists • duration, trailing like heart) with the like pinned to
 * [LikeSource.SPOTIFY] and the state read from `likedSpotify` — the
 * Spotify-side like, matching the row hearts. Toggling prefers the LIVE
 * row and seeds from the placeholder metadata otherwise, so liking a
 * never-played track creates the row (upsert) instead of no-oping.
 */
@Composable
internal fun SpotifySongMenuHeader(
    song: MediaMetadata,
    librarySong: Song?,
) {
    val database = LocalDatabase.current
    val syncUtils = LocalSyncUtils.current
    val coroutineScope = rememberCoroutineScope()
    val marqueeEnabled = LocalMarqueeEnabled.current

    val isLiked = librarySong?.song?.likedSpotify == true

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (marqueeEnabled) Modifier.basicMarquee() else Modifier,
                )
            },
            supportingContent = {
                Text(
                    text =
                        joinByBullet(
                            song.artists.joinToString { it.name },
                            song.duration.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) },
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(ListThumbnailSize).clip(RoundedCornerShape(ThumbnailCornerRadius)),
                ) {
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                    )
                }
            },
            trailingContent = {
                IconButton(
                    onClick = {
                        toggleSpotifyLike(
                            song = song,
                            librarySong = librarySong,
                            database = database,
                            syncUtils = syncUtils,
                            coroutineScope = coroutineScope,
                        )
                    },
                ) {
                    Icon(
                        painter = painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
                        tint = if (isLiked) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

private fun toggleSpotifyLike(
    song: MediaMetadata,
    librarySong: Song?,
    database: MusicDatabase,
    syncUtils: SyncUtils,
    coroutineScope: CoroutineScope,
) {
    coroutineScope.launch(Dispatchers.IO) {
        val live = librarySong?.song ?: song.toSongEntity()
        val updated = live.localToggleLike(LikeSource.SPOTIFY)
        database.upsert(updated)
        // Placeholder ids are "spotify:<raw>"; tolerate "spotify:track:"
        // too. resolveSpotifyId strips the prefixes and wants the raw id.
        val rawSpotifyId = song.id.removePrefix("spotify:track:").removePrefix("spotify:")
        syncUtils.likeSong(updated, LikeSource.SPOTIFY, rawSpotifyId)
    }
}
