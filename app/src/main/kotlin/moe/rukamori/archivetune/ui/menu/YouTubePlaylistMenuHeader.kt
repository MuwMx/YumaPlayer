/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.ui.component.YouTubeListItem

@Composable
internal fun YouTubePlaylistMenuHeader(
    playlist: PlaylistItem,
    songs: List<SongItem>,
    dbPlaylist: Playlist?,
    database: MusicDatabase,
    coroutineScope: CoroutineScope,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        YouTubeListItem(
            item = playlist,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            trailingContent = {
                if (playlist.id != "LM" && !playlist.isEditable) {
                    IconButton(
                        onClick = {
                            if (dbPlaylist?.playlist == null) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val fetchedSongs =
                                        songs.ifEmpty {
                                            YouTube
                                                .playlist(playlist.id)
                                                .completed()
                                                .getOrNull()
                                                ?.songs
                                                .orEmpty()
                                        }
                                    database.withTransaction {
                                        val existingPlaylist = playlistEntityByBrowseId(playlist.id)
                                        val targetPlaylistId =
                                            if (existingPlaylist == null) {
                                                val playlistEntity =
                                                    PlaylistEntity(
                                                        name = playlist.title,
                                                        browseId = playlist.id,
                                                        thumbnailUrl = playlist.thumbnail,
                                                        isEditable = false,
                                                        remoteSongCount =
                                                            playlist.songCountText?.let {
                                                                Regex("""\d+""").find(it)?.value?.toIntOrNull()
                                                            },
                                                        playEndpointParams = playlist.playEndpoint?.params,
                                                        shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                                        radioEndpointParams = playlist.radioEndpoint?.params,
                                                    ).toggleLike()
                                                insert(playlistEntity)
                                                playlistEntityByBrowseId(playlist.id)?.id ?: playlistEntity.id
                                            } else {
                                                val refreshedPlaylist =
                                                    existingPlaylist.copy(
                                                        name = playlist.title,
                                                        browseId = playlist.id,
                                                        thumbnailUrl = playlist.thumbnail,
                                                        isEditable = playlist.isEditable,
                                                        remoteSongCount =
                                                            playlist.songCountText?.let {
                                                                Regex("""\d+""").find(it)?.value?.toIntOrNull()
                                                            },
                                                        playEndpointParams = playlist.playEndpoint?.params,
                                                        shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                                        radioEndpointParams = playlist.radioEndpoint?.params,
                                                    )
                                                update(
                                                    if (existingPlaylist.bookmarkedAt == null) {
                                                        refreshedPlaylist.toggleLike()
                                                    } else {
                                                        refreshedPlaylist
                                                    },
                                                )
                                                existingPlaylist.id
                                            }
                                        if (fetchedSongs.isNotEmpty()) {
                                            clearPlaylist(targetPlaylistId)
                                            fetchedSongs.forEach { song -> insert(song.toMediaMetadata()) }
                                            fetchedSongs
                                                .mapIndexed { index, song ->
                                                    PlaylistSongMap(
                                                        songId = song.id,
                                                        playlistId = targetPlaylistId,
                                                        position = index,
                                                        setVideoId = song.setVideoId,
                                                    )
                                                }.forEach(::insert)
                                        }
                                    }
                                }
                            } else {
                                database.transaction {
                                    val currentPlaylist = dbPlaylist.playlist
                                    update(currentPlaylist, playlist)
                                    update(currentPlaylist.toggleLike())
                                }
                            }
                        },
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (dbPlaylist?.playlist?.bookmarkedAt != null) {
                                        R.drawable.favorite
                                    } else {
                                        R.drawable.favorite_border
                                    },
                                ),
                            tint =
                                if (dbPlaylist?.playlist?.bookmarkedAt != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    LocalContentColor.current
                                },
                            contentDescription = null,
                        )
                    }
                }
            },
        )
    }
}
