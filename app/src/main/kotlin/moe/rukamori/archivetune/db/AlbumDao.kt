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
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.AlbumSortType
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.AlbumArtistMap
import moe.rukamori.archivetune.db.entities.AlbumEntity
import moe.rukamori.archivetune.db.entities.AlbumWithSongs
import moe.rukamori.archivetune.db.entities.SongAlbumMap
import moe.rukamori.archivetune.extensions.reversed
import java.text.Collator
import java.util.Locale

@Dao
interface AlbumDao {
    @Query("SELECT * FROM album WHERE id IN (:ids)")
    suspend fun getAlbumEntitiesByIds(ids: List<String>): List<AlbumEntity>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        "SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY rowId",
    )
    fun albumsByCreateDateAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        "SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY title",
    )
    fun albumsByNameAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        "SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY year",
    )
    fun albumsByYearAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        "SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY songCount",
    )
    fun albumsBySongCountAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        "SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY duration",
    )
    fun albumsByLengthAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT album.*
        FROM album
                 JOIN song
                      ON song.albumId = album.id
        WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL)
        GROUP BY album.id
        ORDER BY SUM(song.totalPlayTime)
    """,
    )
    fun albumsByPlayTimeAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY rowId")
    fun albumsLikedByCreateDateAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY title")
    fun albumsLikedByNameAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY year")
    fun albumsLikedByYearAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY songCount")
    fun albumsLikedBySongCountAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY duration")
    fun albumsLikedByLengthAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT album.*
        FROM album
                 JOIN song
                      ON song.albumId = album.id
        WHERE bookmarkedAt IS NOT NULL
        GROUP BY album.id
        ORDER BY SUM(song.totalPlayTime)
    """,
    )
    fun albumsLikedByPlayTimeAsc(): Flow<List<Album>>

    fun albums(
        sortType: AlbumSortType,
        descending: Boolean,
    ) = when (sortType) {
        AlbumSortType.CREATE_DATE -> {
            albumsByCreateDateAsc()
        }

        AlbumSortType.NAME -> {
            albumsByNameAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { it.album.title })
            }
        }

        AlbumSortType.ARTIST -> {
            albumsByCreateDateAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { album -> album.artists.joinToString("") { artist -> artist.name } })
            }
        }

        AlbumSortType.YEAR -> {
            albumsByYearAsc()
        }

        AlbumSortType.SONG_COUNT -> {
            albumsBySongCountAsc()
        }

        AlbumSortType.LENGTH -> {
            albumsByLengthAsc()
        }

        AlbumSortType.PLAY_TIME -> {
            albumsByPlayTimeAsc()
        }
    }.map { albums ->
        albums.filter { album -> album.artists.none { it.blockedAt != null } }.reversed(descending)
    }

    fun albumsLiked(
        sortType: AlbumSortType,
        descending: Boolean,
    ) = when (sortType) {
        AlbumSortType.CREATE_DATE -> {
            albumsLikedByCreateDateAsc()
        }

        AlbumSortType.NAME -> {
            albumsLikedByNameAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { it.album.title })
            }
        }

        AlbumSortType.ARTIST -> {
            albumsLikedByCreateDateAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { album -> album.artists.joinToString("") { artist -> artist.name } })
            }
        }

        AlbumSortType.YEAR -> {
            albumsLikedByYearAsc()
        }

        AlbumSortType.SONG_COUNT -> {
            albumsLikedBySongCountAsc()
        }

        AlbumSortType.LENGTH -> {
            albumsLikedByLengthAsc()
        }

        AlbumSortType.PLAY_TIME -> {
            albumsLikedByPlayTimeAsc()
        }
    }.map { albums ->
        albums.filter { album -> album.artists.none { it.blockedAt != null } }.reversed(descending)
    }

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE id = :id")
    fun album(id: String): Flow<Album?>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE id IN (:ids) ORDER BY rowId")
    fun albumsByRowId(ids: Collection<String>): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE id IN (:ids) ORDER BY title")
    fun albumsByTitle(ids: Collection<String>): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE id IN (:ids) ORDER BY year")
    fun albumsByYear(ids: Collection<String>): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE id IN (:ids) ORDER BY songCount")
    fun albumsBySongCount(ids: Collection<String>): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE id IN (:ids) ORDER BY duration")
    fun albumsByDuration(ids: Collection<String>): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT album.*
        FROM album
        LEFT JOIN song ON song.albumId = album.id
        WHERE album.id IN (:ids)
        GROUP BY album.id
        ORDER BY COALESCE(SUM(song.totalPlayTime), 0)
        """,
    )
    fun albumsByPlayTime(ids: Collection<String>): Flow<List<Album>>

    fun albumsByIds(
        ids: Collection<String>,
        sortType: AlbumSortType,
        descending: Boolean,
    ): Flow<List<Album>> {
        if (ids.isEmpty()) return flowOf(emptyList())
        return when (sortType) {
            AlbumSortType.CREATE_DATE -> {
                albumsByRowId(ids)
            }

            AlbumSortType.NAME -> {
                albumsByTitle(ids).map { albums ->
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    albums.sortedWith(compareBy(collator) { it.album.title })
                }
            }

            AlbumSortType.ARTIST -> {
                albumsByRowId(ids).map { albums ->
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    albums.sortedWith(compareBy(collator) { album -> album.artists.joinToString("") { artist -> artist.name } })
                }
            }

            AlbumSortType.YEAR -> {
                albumsByYear(ids)
            }

            AlbumSortType.SONG_COUNT -> {
                albumsBySongCount(ids)
            }

            AlbumSortType.LENGTH -> {
                albumsByDuration(ids)
            }

            AlbumSortType.PLAY_TIME -> {
                albumsByPlayTime(ids)
            }
        }.map { albums ->
            albums.filter { album -> album.artists.none { it.blockedAt != null } }.reversed(descending)
        }
    }

    @Transaction
    @Query("SELECT * FROM album WHERE id = :albumId")
    fun albumWithSongs(albumId: String): Flow<AlbumWithSongs?>

    @Transaction
    @Query("SELECT * FROM album_artist_map WHERE albumId = :albumId")
    fun albumArtistMaps(albumId: String): List<AlbumArtistMap>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(album: AlbumEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: SongAlbumMap)

    @Update
    fun update(album: AlbumEntity)

    @Upsert
    fun upsert(album: AlbumEntity)

    @Upsert
    fun upsert(map: SongAlbumMap)

    @Query("DELETE FROM song_album_map WHERE songId = :songId")
    fun deleteSongAlbumMaps(songId: String)

    @Query(
        "DELETE FROM album WHERE isLocal = 1 AND id NOT IN (SELECT DISTINCT albumId FROM song WHERE isLocal = 1 AND albumId IS NOT NULL)",
    )
    fun pruneLocalAlbums()

    @Delete
    fun delete(album: AlbumEntity)
}
