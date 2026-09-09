/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomWarnings
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.PlaylistSortType
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistPlayCount
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale

@Dao
interface PlaylistDao {
    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        "SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE bookmarkedAt IS NOT NULL ORDER BY rowId",
    )
    fun playlistsByCreateDateAsc(): Flow<List<Playlist>>

    @Transaction
    @Query(
        "SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE bookmarkedAt IS NOT NULL ORDER BY lastUpdateTime",
    )
    fun playlistsByUpdatedDateAsc(): Flow<List<Playlist>>

    @Transaction
    @Query(
        "SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE bookmarkedAt IS NOT NULL ORDER BY name",
    )
    fun playlistsByNameAsc(): Flow<List<Playlist>>

    @Transaction
    @Query(
        "SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE bookmarkedAt IS NOT NULL ORDER BY songCount",
    )
    fun playlistsBySongCountAsc(): Flow<List<Playlist>>

    @Transaction
    @Query(
        "SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE bookmarkedAt IS NOT NULL ORDER BY COALESCE(customOrder, rowId), rowId",
    )
    fun playlistsByCustomOrderAsc(): Flow<List<Playlist>>

    fun playlists(
        sortType: PlaylistSortType,
        descending: Boolean,
    ) = when (sortType) {
        PlaylistSortType.CREATE_DATE -> {
            playlistsByCreateDateAsc()
        }

        PlaylistSortType.NAME -> {
            playlistsByNameAsc().map { playlists ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                playlists.sortedWith(compareBy(collator) { it.playlist.name })
            }
        }

        PlaylistSortType.SONG_COUNT -> {
            playlistsBySongCountAsc()
        }

        PlaylistSortType.LAST_UPDATED -> {
            playlistsByUpdatedDateAsc()
        }

        PlaylistSortType.CUSTOM -> {
            playlistsByCustomOrderAsc()
        }
    }.map { list ->
        if (descending && sortType != PlaylistSortType.CUSTOM) list.asReversed() else list
    }

    @Query("UPDATE playlist SET customOrder = :customOrder WHERE id = :playlistId")
    fun setPlaylistCustomOrder(
        playlistId: String,
        customOrder: Int?,
    )

    @Query("UPDATE playlist SET songSortType = :sortType, songSortDescending = :descending WHERE id = :playlistId")
    fun updatePlaylistSortPreference(
        playlistId: String,
        sortType: String?,
        descending: Boolean?,
    )

    @Query("SELECT MAX(customOrder) FROM playlist WHERE bookmarkedAt IS NOT NULL")
    fun maxPlaylistCustomOrder(): Int?

    @Transaction
    @Query(
        "SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE id = :playlistId",
    )
    fun playlist(playlistId: String): Flow<Playlist?>

    @Transaction
    @Query(
        "SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE id = :playlistId LIMIT 1",
    )
    suspend fun getPlaylistById(playlistId: String): Playlist?

    @Transaction
    @Query(
        "SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE id = :playlistId LIMIT 1",
    )
    fun getPlaylistByIdBlocking(playlistId: String): Playlist?

    @Transaction
    @Query(
        "SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE isEditable AND bookmarkedAt IS NOT NULL ORDER BY rowId",
    )
    fun editablePlaylistsByCreateDateAsc(): Flow<List<Playlist>>

    @Query(
        """
        SELECT
            playlist_song_map.playlistId AS playlistId,
            COALESCE(SUM(playCount.count), 0) AS playCount
        FROM playlist_song_map
        LEFT JOIN playCount ON playCount.song = playlist_song_map.songId
        GROUP BY playlist_song_map.playlistId
        """,
    )
    fun playlistPlayCounts(): Flow<List<PlaylistPlayCount>>

    @Transaction
    @Query(
        "SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE browseId = :browseId",
    )
    fun playlistByBrowseId(browseId: String): Flow<Playlist?>

    @Query("SELECT * FROM playlist WHERE browseId = :browseId LIMIT 1")
    fun playlistEntityByBrowseId(browseId: String): PlaylistEntity?

    @Transaction
    @Query("SELECT COUNT(*) from playlist_song_map WHERE playlistId = :playlistId AND songId = :songId LIMIT 1")
    fun checkInPlaylist(
        playlistId: String,
        songId: String,
    ): Int

    @Query("SELECT songId from playlist_song_map WHERE playlistId = :playlistId AND songId IN (:songIds)")
    fun playlistDuplicates(
        playlistId: String,
        songIds: List<String>,
    ): List<String>

    @Transaction
    fun addSongToPlaylist(
        playlist: Playlist,
        songIds: List<String>,
    ) {
        addSongEntriesToPlaylist(
            playlist = playlist,
            songEntries = songIds.map { songId -> songId to null },
        )
    }

    @Transaction
    fun addSongEntriesToPlaylist(
        playlist: Playlist,
        songEntries: List<Pair<String, String?>>,
    ) {
        var position = playlist.songCount
        songEntries.forEach { (songId, setVideoId) ->
            insert(
                PlaylistSongMap(
                    songId = songId,
                    playlistId = playlist.id,
                    position = position++,
                    setVideoId = setVideoId,
                ),
            )
        }
        if (songEntries.isNotEmpty()) {
            update(playlist.playlist.copy(lastUpdateTime = LocalDateTime.now()))
        }
    }

    @Transaction
    @Query(
        """
        UPDATE playlist_song_map SET position = 
            CASE 
                WHEN position < :fromPosition THEN position + 1
                WHEN position > :fromPosition THEN position - 1
                ELSE :toPosition
            END 
        WHERE playlistId = :playlistId AND position BETWEEN MIN(:fromPosition, :toPosition) AND MAX(:fromPosition, :toPosition)
    """,
    )
    fun move(
        playlistId: String,
        fromPosition: Int,
        toPosition: Int,
    )

    @Transaction
    @Query("DELETE FROM playlist_song_map WHERE playlistId = :playlistId")
    fun clearPlaylist(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: PlaylistSongMap)

    @Update
    fun update(playlist: PlaylistEntity)

    @Update
    fun update(map: PlaylistSongMap)

    @Update
    fun update(
        playlistEntity: PlaylistEntity,
        playlistItem: PlaylistItem,
    ) {
        update(
            playlistEntity.copy(
                name = playlistItem.title,
                browseId = playlistItem.id,
                thumbnailUrl = playlistItem.thumbnail,
                isEditable = playlistItem.isEditable,
                remoteSongCount = playlistItem.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
                playEndpointParams = playlistItem.playEndpoint?.params,
                shuffleEndpointParams = playlistItem.shuffleEndpoint?.params,
                radioEndpointParams = playlistItem.radioEndpoint?.params,
            ),
        )
    }

    @Delete
    fun delete(playlist: PlaylistEntity)

    @Delete
    fun delete(playlistSongMap: PlaylistSongMap)

    @Query("DELETE FROM playlist WHERE browseId = :browseId")
    fun deletePlaylistById(browseId: String)

    @Transaction
    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId ORDER BY position")
    fun playlistSongs(playlistId: String): Flow<List<PlaylistSong>>

    @Transaction
    @Query("SELECT * FROM playlist_song_map WHERE songId = :songId")
    fun playlistSongMaps(songId: String): List<PlaylistSongMap>

    @Transaction
    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId AND position >= :from ORDER BY position")
    fun playlistSongMaps(
        playlistId: String,
        from: Int,
    ): List<PlaylistSongMap>

    @Query("SELECT MAX(position) FROM playlist_song_map WHERE playlistId = :playlistId")
    fun maxPlaylistSongPosition(playlistId: String): Int?
}
