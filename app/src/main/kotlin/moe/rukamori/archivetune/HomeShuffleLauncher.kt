package moe.rukamori.archivetune

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.LocalAlbumRadio
import moe.rukamori.archivetune.playback.queues.YouTubeAlbumRadio
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.viewmodels.HomeViewModel
import kotlin.random.Random

internal fun launchHomeShuffle(
    coroutineScope: CoroutineScope,
    homeViewModel: HomeViewModel,
    playerConnection: PlayerConnection?,
    database: MusicDatabase?,
) {
    val localItems = homeViewModel.allLocalItems.value
    val ytItems = homeViewModel.allYtItems.value
    val useLocalSource =
        when {
            localItems.isNotEmpty() && ytItems.isNotEmpty() -> {
                Random.nextFloat() < 0.5f
            }

            localItems.isNotEmpty() -> {
                true
            }

            else -> {
                false
            }
        }

    coroutineScope.launch(Dispatchers.Main) {
        if (useLocalSource) {
            when (val luckyItem = localItems.random()) {
                is Song -> {
                    playerConnection?.playQueue(
                        if (luckyItem.song.isLocal) {
                            ListQueue(items = listOf(luckyItem.toMediaItem()))
                        } else {
                            YouTubeQueue.radio(luckyItem.toMediaMetadata())
                        },
                    )
                }

                is Album -> {
                    val albumWithSongs =
                        if (database != null) {
                            withContext(Dispatchers.IO) {
                                database.albumWithSongs(luckyItem.id).first()
                            }
                        } else {
                            null
                        }

                    albumWithSongs?.let {
                        playerConnection?.playQueue(LocalAlbumRadio(it))
                    }
                }

                is Artist, is Playlist -> {}
            }
        } else {
            when (val luckyItem = ytItems.random()) {
                is SongItem -> {
                    playerConnection?.playQueue(
                        YouTubeQueue.radio(luckyItem.toMediaMetadata()),
                    )
                }

                is AlbumItem -> {
                    playerConnection?.playQueue(
                        YouTubeAlbumRadio(luckyItem.playlistId),
                    )
                }

                is ArtistItem -> {
                    luckyItem.radioEndpoint?.let {
                        playerConnection?.playQueue(YouTubeQueue(it))
                    }
                }

                is PlaylistItem -> {
                    luckyItem.playEndpoint?.let {
                        playerConnection?.playQueue(YouTubeQueue.playlist(it))
                    }
                }
            }
        }
    }
}
