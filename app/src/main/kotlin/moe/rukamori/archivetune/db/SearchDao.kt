/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.db

import android.util.Log
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RoomWarnings
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song

@Dao
interface SearchDao {
    @Transaction
    @Query("SELECT * FROM song WHERE title LIKE '%' || :query || '%' AND inLibrary IS NOT NULL LIMIT :previewSize")
    fun searchSongsInternal(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Song>>

    fun searchSongs(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Song>> {
        val t0 = System.currentTimeMillis()
        Log.d("DB_STRESS", "SearchDao.searchSongs ENTER query='$query' previewSize=$previewSize th=${Thread.currentThread().name}")
        return searchSongsInternal(query, previewSize).map { list ->
            val dt = System.currentTimeMillis() - t0
            Log.d("DB_STRESS", "SearchDao.searchSongs EMIT query='$query' count=${list.size} dt=${dt}ms th=${Thread.currentThread().name}")
            list
        }
    }

    @Query("SELECT COUNT(1) FROM song WHERE title LIKE '%' || :query || '%' AND inLibrary IS NOT NULL")
    suspend fun searchSongsCount(query: String): Int

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        "SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE name LIKE '%' || :query || '%' AND songCount > 0 LIMIT :previewSize",
    )
    fun searchArtists(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Artist>>

    @Query(
        "SELECT COUNT(1) FROM artist WHERE name LIKE '%' || :query || '%' AND EXISTS(SELECT 1 FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL)",
    )
    suspend fun searchArtistsCount(query: String): Int

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        "SELECT * FROM album WHERE title LIKE '%' || :query || '%' AND EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) LIMIT :previewSize",
    )
    fun searchAlbums(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Album>>

    @Query(
        "SELECT COUNT(1) FROM album WHERE title LIKE '%' || :query || '%' AND EXISTS(SELECT 1 FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL)",
    )
    suspend fun searchAlbumsCount(query: String): Int

    @Transaction
    @Query(
        "SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE name LIKE '%' || :query || '%' LIMIT :previewSize",
    )
    fun searchPlaylists(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Playlist>>

    @Query("SELECT COUNT(1) FROM playlist WHERE name LIKE '%' || :query || '%'")
    suspend fun searchPlaylistsCount(query: String): Int
}
