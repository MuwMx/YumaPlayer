/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.source

import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.models.toMediaMetadata

/**
 * ONLINE PLAYLIST SLOT — the playlist-BOOKMARK toggle, ported verbatim from
 * the legacy toggleOnlinePlaylistLike (OnlinePlaylistActions.kt:51-116) so
 * the unified hero's leading heart keeps saving online playlists into the
 * Room library:
 *  - not bookmarked yet -> create a PlaylistEntity keyed by browseId (with
 *    the playlist's play/shuffle/radio endpoint params), toggle it liked,
 *    and copy every loaded song into playlist_song_map so the saved copy is
 *    complete;
 *  - already bookmarked -> refresh the stored metadata (title/thumbnail/
 *    editability/endpoints follow the server) and toggle like off.
 * [dbPlaylist] (the live Room row looked up by browseId) decides which
 * branch runs, exactly like the legacy screen's dbPlaylist state.
 */
internal suspend fun InnerTubePlaylistSource.togglePlaylistBookmark(
    database: MusicDatabase,
    dbPlaylist: Playlist?,
): Result<Unit> =
    runCatching {
        val playlist = playlistItem ?: return@runCatching
        if (dbPlaylist?.playlist == null) {
            database.transaction {
                val existingPlaylist = playlistEntityByBrowseId(playlist.id)
                val targetPlaylistId =
                    if (existingPlaylist == null) {
                        val playlistEntity =
                            PlaylistEntity(
                                name = playlist.title,
                                browseId = playlist.id,
                                thumbnailUrl = playlist.thumbnail,
                                isEditable = playlist.isEditable,
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
                val songs = this@togglePlaylistBookmark.currentSongItems()
                if (songs.isNotEmpty()) {
                    clearPlaylist(targetPlaylistId)
                    songs
                        .onEach { song -> insert(song.toMediaMetadata()) }
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
        } else {
            database.transaction {
                val currentPlaylist = dbPlaylist.playlist
                // Two-step, verbatim legacy line 110-111: refresh the stored
                // metadata from the innertube PlaylistItem, then toggle like.
                update(currentPlaylist, playlist)
                update(currentPlaylist.toggleLike())
            }
        }
    }

/** The Room bookmark row for this playlist (library "saved" state). */
internal suspend fun InnerTubePlaylistSource.dbPlaylist(database: MusicDatabase): Playlist? =
    database.playlistByBrowseId(browseId).first()
