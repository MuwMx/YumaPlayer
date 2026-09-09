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
import androidx.room.RawQuery
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.RoomWarnings
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.ArtistSongSortType
import moe.rukamori.archivetune.constants.PlaylistSortType
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.AlbumArtistMap
import moe.rukamori.archivetune.db.entities.AlbumEntity
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistPlayCount
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.db.entities.RelatedSongMap
import moe.rukamori.archivetune.db.entities.SetVideoIdEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongAlbumMap
import moe.rukamori.archivetune.db.entities.SongArtistMap
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.db.entities.SongWithStats
import moe.rukamori.archivetune.db.entities.SpotifyMatchEntity
import moe.rukamori.archivetune.db.entities.TagEntity
import moe.rukamori.archivetune.extensions.reversed
import moe.rukamori.archivetune.extensions.toSQLiteQuery
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.AlbumPage
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import java.text.Collator
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

@Dao
interface DatabaseDao {
    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY rowId")
    fun songsByRowIdAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY inLibrary")
    fun songsByCreateDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY title")
    fun songsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY totalPlayTime")
    fun songsByPlayTimeAsc(): Flow<List<Song>>

    fun songs(
        sortType: SongSortType,
        descending: Boolean,
        filterVideo: Boolean = false,
    ) = when (sortType) {
        SongSortType.CREATE_DATE -> {
            if (filterVideo) {
                songsByCreateDateAscNoVideo()
            } else {
                songsByCreateDateAsc()
            }
        }

        SongSortType.NAME -> {
            (
                if (filterVideo) {
                    songsByNameAscNoVideo()
                } else {
                    songsByNameAsc()
                }
            ).map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs.sortedWith(compareBy(collator) { it.song.title })
            }
        }

        SongSortType.ARTIST -> {
            (
                if (filterVideo) {
                    songsByRowIdAscNoVideo()
                } else {
                    songsByRowIdAsc()
                }
            ).map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs.sortedWith(
                    compareBy(collator) { song ->
                        song.artists.joinToString("") { artist -> artist.name }
                    },
                )
            }
        }

        SongSortType.PLAY_TIME -> {
            songsByPlayTimeAsc()
        }
    }.map { songs ->
        songs.filter { song -> song.artists.none { it.blockedAt != null } }.reversed(descending)
    }

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM song
        LEFT JOIN set_video_id ON set_video_id.videoId = song.id
        WHERE song.inLibrary IS NOT NULL AND set_video_id.setVideoId IS NULL
        ORDER BY song.id
        """,
    )
    fun songsByRowIdAscNoVideo(): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM song
        LEFT JOIN set_video_id ON set_video_id.videoId = song.id
        WHERE song.inLibrary IS NOT NULL AND set_video_id.setVideoId IS NULL
        ORDER BY inLibrary
        """,
    )
    fun songsByCreateDateAscNoVideo(): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM song
        LEFT JOIN set_video_id ON set_video_id.videoId = song.id
        WHERE song.inLibrary IS NOT NULL AND set_video_id.setVideoId IS NULL
        ORDER BY title
        """,
    )
    fun songsByNameAscNoVideo(): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM song
        LEFT JOIN set_video_id ON set_video_id.videoId = song.id
        WHERE song.inLibrary IS NOT NULL AND set_video_id.setVideoId IS NULL
        ORDER BY totalPlayTime
        """,
    )
    fun songsByPlayTimeAscNoVideo(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY rowId")
    fun likedSongsByRowIdAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY likedDate, rowId")
    fun likedSongsByCreateDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY title")
    fun likedSongsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY totalPlayTime")
    fun likedSongsByPlayTimeAsc(): Flow<List<Song>>

    fun likedSongs(
        sortType: SongSortType,
        descending: Boolean,
        filterVideo: Boolean = false,
    ) = when (sortType) {
        SongSortType.CREATE_DATE -> {
            if (filterVideo) {
                likedSongsByCreateDateAscNoVideo()
            } else {
                likedSongsByCreateDateAsc()
            }
        }

        SongSortType.NAME -> {
            (
                if (filterVideo) {
                    likedSongsByNameAscNoVideo()
                } else {
                    likedSongsByNameAsc()
                }
            ).map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs.sortedWith(compareBy(collator) { it.song.title })
            }
        }

        SongSortType.ARTIST -> {
            (
                if (filterVideo) {
                    likedSongsByRowIdAscNoVideo()
                } else {
                    likedSongsByRowIdAsc()
                }
            ).map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs.sortedWith(
                    compareBy(collator) { song ->
                        song.artists.joinToString("") { artist -> artist.name }
                    },
                )
            }
        }

        SongSortType.PLAY_TIME -> {
            likedSongsByPlayTimeAsc()
        }
    }.map { songs ->
        songs.filter { song -> song.artists.none { it.blockedAt != null } }.reversed(descending)
    }

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM song
        LEFT JOIN set_video_id ON set_video_id.videoId = song.id
        WHERE liked AND set_video_id.setVideoId IS NULL
        ORDER BY song.rowid
        """,
    )
    fun likedSongsByRowIdAscNoVideo(): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM song
        LEFT JOIN set_video_id ON set_video_id.videoId = song.id
        WHERE liked AND set_video_id.setVideoId IS NULL
        ORDER BY likedDate, song.rowid
        """,
    )
    fun likedSongsByCreateDateAscNoVideo(): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM song
        LEFT JOIN set_video_id ON set_video_id.videoId = song.id
        WHERE liked AND set_video_id.setVideoId IS NULL
        ORDER BY title
        """,
    )
    fun likedSongsByNameAscNoVideo(): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM song
        LEFT JOIN set_video_id ON set_video_id.videoId = song.id
        WHERE liked AND set_video_id.setVideoId IS NULL
        ORDER BY totalPlayTime
        """,
    )
    fun likedSongsByPlayTimeAscNoVideo(): Flow<List<Song>>

    @Transaction
    @Query("SELECT COUNT(1) FROM song WHERE liked")
    fun likedSongsCount(): Flow<Int>

    @Transaction
    @Query("SELECT song.* FROM song JOIN song_album_map ON song.id = song_album_map.songId WHERE song_album_map.albumId = :albumId")
    fun albumSongs(albumId: String): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId ORDER BY position")
    fun playlistSongs(playlistId: String): Flow<List<PlaylistSong>>

    @Transaction
    @Query(
        "SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL ORDER BY inLibrary",
    )
    fun artistSongsByCreateDateAsc(artistId: String): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL ORDER BY title",
    )
    fun artistSongsByNameAsc(artistId: String): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL ORDER BY totalPlayTime",
    )
    fun artistSongsByPlayTimeAsc(artistId: String): Flow<List<Song>>

    fun artistSongs(
        artistId: String,
        sortType: ArtistSongSortType,
        descending: Boolean,
    ) = when (sortType) {
        ArtistSongSortType.CREATE_DATE -> {
            artistSongsByCreateDateAsc(artistId)
        }

        ArtistSongSortType.NAME -> {
            artistSongsByNameAsc(artistId).map { artistSongs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                artistSongs.sortedWith(compareBy(collator) { it.song.title })
            }
        }

        ArtistSongSortType.PLAY_TIME -> {
            artistSongsByPlayTimeAsc(artistId)
        }
    }.map { songs ->
        songs.filter { song -> song.artists.none { it.blockedAt != null } }.reversed(descending)
    }

    @Transaction
    @Query(
        "SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL LIMIT :previewSize",
    )
    fun artistSongsPreview(
        artistId: String,
        previewSize: Int = 3,
    ): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM (SELECT *, COUNT(1) AS referredCount
              FROM related_song_map
              GROUP BY relatedSongId) map
                 JOIN song ON song.id = map.relatedSongId
        WHERE songId IN (SELECT songId
                         FROM (SELECT songId
                               FROM event
                               ORDER BY ROWID DESC
                               LIMIT 5)
                         UNION
                         SELECT songId
                         FROM (SELECT songId
                               FROM event
                               WHERE timestamp > :now - 86400000 * 7
                               GROUP BY songId
                               ORDER BY SUM(playTime) DESC
                               LIMIT 5)
                         UNION
                         SELECT id
                         FROM (SELECT id
                               FROM song
                               ORDER BY totalPlayTime DESC
                               LIMIT 10))
        ORDER BY referredCount DESC
        LIMIT 100
    """,
    )
    fun quickPicks(now: Long = System.currentTimeMillis()): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT
            song.*
        FROM
            event
        JOIN
            song ON event.songId = song.id
        WHERE
            event.timestamp > (:now - 86400000 * 7 * 2)
        GROUP BY
            song.albumId
        HAVING
            song.albumId IS NOT NULL
        ORDER BY
            sum(event.playTime) DESC
        LIMIT :limit
        OFFSET :offset
        
        """,
    )
    fun getRecommendationAlbum(
        now: Long = System.currentTimeMillis(),
        limit: Int = 5,
        offset: Int = 0,
    ): Flow<List<Song>>

    @Transaction
    @Query(
        """
             SELECT song.id, song.title, song.thumbnailUrl,
               (SELECT COUNT(1)
                FROM event
                WHERE songId = song.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS songCountListened,
               (SELECT SUM(event.playTime)
                FROM event
                WHERE songId = song.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS timeListened
        FROM song
        JOIN (SELECT songId
                     FROM event
                     WHERE timestamp > :fromTimeStamp
                     AND timestamp <= :toTimeStamp
                     GROUP BY songId
                     ORDER BY SUM(playTime) DESC
                     LIMIT :limit)
        ON song.id = songId
        WHERE NOT EXISTS (
            SELECT 1
            FROM song_artist_map
            JOIN artist ON artist.id = song_artist_map.artistId
            WHERE song_artist_map.songId = song.id
              AND artist.blockedAt IS NOT NULL
        )
        LIMIT :limit
        OFFSET :offset
    """,
    )
    fun mostPlayedSongsStats(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
    ): Flow<List<SongWithStats>>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT song.*,
               (SELECT COUNT(1)
                FROM event
                WHERE songId = song.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS songCountListened,
               (SELECT SUM(event.playTime)
                FROM event
                WHERE songId = song.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS timeListened
        FROM song
        JOIN (SELECT songId
                     FROM event
                     WHERE timestamp > :fromTimeStamp
                     AND timestamp <= :toTimeStamp
                     GROUP BY songId
                     ORDER BY SUM(playTime) DESC
                     LIMIT :limit)
        ON song.id = songId
        WHERE NOT EXISTS (
            SELECT 1
            FROM song_artist_map
            JOIN artist ON artist.id = song_artist_map.artistId
            WHERE song_artist_map.songId = song.id
              AND artist.blockedAt IS NOT NULL
        )
        LIMIT :limit
        OFFSET :offset
    """,
    )
    fun mostPlayedSongs(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
    ): Flow<List<Song>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT artist.*,
               (SELECT COUNT(1)
                FROM song_artist_map
                         JOIN event ON song_artist_map.songId = event.songId
                WHERE artistId = artist.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS songCount,
               (SELECT SUM(event.playTime)
                FROM song_artist_map
                         JOIN event ON song_artist_map.songId = event.songId
                WHERE artistId = artist.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS timeListened
        FROM artist
                 JOIN(SELECT artistId, SUM(songTotalPlayTime) AS totalPlayTime
                      FROM song_artist_map
                               JOIN (SELECT songId, SUM(playTime) AS songTotalPlayTime
                                     FROM event
                                     WHERE timestamp > :fromTimeStamp
                                     AND timestamp <= :toTimeStamp
                                     GROUP BY songId) AS e
                                    ON song_artist_map.songId = e.songId
                      GROUP BY artistId
                      ORDER BY totalPlayTime DESC
                      LIMIT :limit
                      OFFSET :offset)
                     ON artist.id = artistId
        WHERE artist.blockedAt IS NULL
    """,
    )
    fun mostPlayedArtists(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
    ): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
    SELECT album.*,
           COUNT(DISTINCT song_album_map.songId) as downloadCount,
           (SELECT COUNT(1)
            FROM song_album_map
                     JOIN event e ON song_album_map.songId = e.songId
            WHERE albumId = album.id
              AND e.timestamp > :fromTimeStamp 
              AND e.timestamp <= :toTimeStamp) AS songCountListened,
           (SELECT SUM(e.playTime)
            FROM song_album_map
                     JOIN event e ON song_album_map.songId = e.songId
            WHERE albumId = album.id
              AND e.timestamp > :fromTimeStamp 
              AND e.timestamp <= :toTimeStamp) AS timeListened
    FROM album
    JOIN song_album_map ON album.id = song_album_map.albumId
    WHERE album.id IN (
        SELECT sam.albumId
        FROM event
                 JOIN song_album_map sam ON event.songId = sam.songId
        WHERE event.timestamp > :fromTimeStamp
          AND event.timestamp <= :toTimeStamp
        GROUP BY sam.albumId
        HAVING sam.albumId IS NOT NULL
    )
      AND NOT EXISTS (
          SELECT 1
          FROM album_artist_map blocked_album_artist
          JOIN artist ON artist.id = blocked_album_artist.artistId
          WHERE blocked_album_artist.albumId = album.id
            AND artist.blockedAt IS NOT NULL
      )
    GROUP BY album.id
    ORDER BY timeListened DESC
    LIMIT :limit OFFSET :offset
    """,
    )
    fun mostPlayedAlbums(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
    ): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT album.*, count(song.dateDownload) downloadCount
        FROM album_artist_map 
            JOIN album ON album_artist_map.albumId = album.id
            JOIN song ON album_artist_map.albumId = song.albumId
        WHERE artistId = :artistId
        GROUP BY album.id
        LIMIT :previewSize
    """,
    )
    fun artistAlbumsPreview(
        artistId: String,
        previewSize: Int = 6,
    ): Flow<List<Album>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM (SELECT n.songId      AS eid,
                     SUM(playTime) AS oldPlayTime,
                     newPlayTime
              FROM event
                       JOIN
                   (SELECT songId, SUM(playTime) AS newPlayTime
                    FROM event
                    WHERE timestamp > (:now - 86400000 * 30 * 1)
                    GROUP BY songId
                    ORDER BY newPlayTime) as n
                   ON event.songId = n.songId
              WHERE timestamp < (:now - 86400000 * 30 * 1)
              GROUP BY n.songId
              ORDER BY oldPlayTime) AS t
                 JOIN song on song.id = t.eid
        WHERE 0.2 * t.oldPlayTime > t.newPlayTime
        LIMIT 100
    """,
    )
    fun forgottenFavorites(now: Long = System.currentTimeMillis()): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM event
                 JOIN
             song ON event.songId = song.id
        WHERE event.timestamp > (:now - 86400000 * 7 * 2)
        GROUP BY song.albumId
        HAVING song.albumId IS NOT NULL
        ORDER BY sum(event.playTime) DESC
        LIMIT :limit
        OFFSET :offset
        """,
    )
    fun recommendedAlbum(
        now: Long = System.currentTimeMillis(),
        limit: Int = 5,
        offset: Int = 0,
    ): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId")
    fun song(songId: String?): Flow<Song?>

    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId LIMIT 1")
    suspend fun getSongById(songId: String): Song?

    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId LIMIT 1")
    fun getSongByIdBlocking(songId: String): Song?

    @Transaction
    @Query("SELECT * FROM song WHERE id IN (:songIds)")
    suspend fun getSongsByIds(songIds: List<String>): List<Song>

    @Transaction
    @Query("SELECT * FROM song_artist_map WHERE songId = :songId")
    fun songArtistMap(songId: String): List<SongArtistMap>

    @Transaction
    @Query("SELECT * FROM song")
    fun allSongs(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE isLocal = 1 ORDER BY title COLLATE NOCASE, id")
    fun localSongs(): Flow<List<Song>>

    @Query("SELECT id FROM song WHERE isLocal = 1")
    suspend fun localSongIds(): List<String>

    @Query("SELECT * FROM set_video_id WHERE videoId = :videoId")
    suspend fun getSetVideoId(videoId: String): SetVideoIdEntity?

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
    @Query("UPDATE song SET inLibrary = :inLibrary WHERE id = :songId")
    fun inLibrary(
        songId: String,
        inLibrary: LocalDateTime?,
    )

    @Transaction
    @Query("SELECT COUNT(1) FROM related_song_map WHERE songId = :songId LIMIT 1")
    fun hasRelatedSongs(songId: String): Boolean

    @Transaction
    @Query(
        "SELECT song.* FROM (SELECT * from related_song_map GROUP BY relatedSongId) map JOIN song ON song.id = map.relatedSongId where songId = :songId",
    )
    fun getRelatedSongs(songId: String): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM (SELECT *
              FROM related_song_map
              GROUP BY relatedSongId) map
                 JOIN
             song
             ON song.id = map.relatedSongId
        WHERE songId = :songId
        """,
    )
    fun relatedSongs(songId: String): List<Song>

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

    @Transaction
    @Query("SELECT * FROM album_artist_map WHERE albumId = :albumId")
    fun albumArtistMapsInternal(albumId: String): List<AlbumArtistMap>

    @Transaction
    @Query("SELECT * FROM artist WHERE name = :name")
    fun artistByNameInternal(name: String): ArtistEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertArtist(artist: ArtistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAlbum(album: AlbumEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSongArtistMap(map: SongArtistMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSongAlbumMap(map: SongAlbumMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAlbumArtistMap(map: AlbumArtistMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: PlaylistSongMap)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(setVideoIdEntity: SetVideoIdEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: RelatedSongMap)

    @Transaction
    fun insert(
        mediaMetadata: MediaMetadata,
        block: (SongEntity) -> SongEntity = { it },
    ) {
        if (insert(mediaMetadata.toSongEntity().let(block)) == -1L) return

        if (mediaMetadata.setVideoId != null) {
            insert(
                SetVideoIdEntity(
                    videoId = mediaMetadata.id,
                    setVideoId = mediaMetadata.setVideoId,
                ),
            )
        }

        if (!mediaMetadata.spotifyTrackId.isNullOrBlank()) {
            insertSpotifyMatch(
                SpotifyMatchEntity(
                    spotifyId = mediaMetadata.spotifyTrackId,
                    youtubeId = mediaMetadata.id,
                    title = mediaMetadata.title,
                    artist = mediaMetadata.artists.joinToString { it.name },
                    matchScore = 1.0,
                ),
            )
        }

        mediaMetadata.artists.forEachIndexed { index, artist ->
            val artistId = artist.id ?: artistByNameInternal(artist.name)?.id ?: ArtistEntity.generateArtistId()

            insertArtist(
                ArtistEntity(
                    id = artistId,
                    name = artist.name,
                    channelId = artist.id,
                ),
            )

            insertSongArtistMap(
                SongArtistMap(
                    songId = mediaMetadata.id,
                    artistId = artistId,
                    position = index,
                ),
            )
        }
    }

    @Transaction
    fun insert(albumPage: AlbumPage) {
        if (insertAlbum(
                AlbumEntity(
                    id = albumPage.album.browseId,
                    playlistId = albumPage.album.playlistId,
                    title = albumPage.album.title,
                    year = albumPage.album.year,
                    thumbnailUrl = albumPage.album.thumbnail,
                    songCount = albumPage.songs.size,
                    duration = albumPage.songs.sumOf { song -> song.duration ?: 0 },
                    explicit = albumPage.album.explicit || albumPage.songs.any { it.explicit },
                ),
            ) == -1L
        ) {
            return
        }
        albumPage.songs
            .map(SongItem::toMediaMetadata)
            .onEach(::insert)
            .onEach {
                val existingSong = getSongByIdBlocking(it.id)
                if (existingSong != null) {
                    update(existingSong, it)
                }
            }.mapIndexed { index, song ->
                SongAlbumMap(
                    songId = song.id,
                    albumId = albumPage.album.browseId,
                    index = index,
                )
            }.forEach(::upsertSongAlbumMap)
        albumPage.album.artists
            ?.map { artist ->
                ArtistEntity(
                    id =
                        artist.id ?: artistByNameInternal(artist.name)?.id
                            ?: ArtistEntity.generateArtistId(),
                    name = artist.name,
                )
            }?.onEach(::insertArtist)
            ?.mapIndexed { index, artist ->
                AlbumArtistMap(
                    albumId = albumPage.album.browseId,
                    artistId = artist.id,
                    order = index,
                )
            }?.forEach(::insertAlbumArtistMap)
    }

    @Transaction
    fun update(
        song: Song,
        mediaMetadata: MediaMetadata,
    ) {
        update(
            song.song.copy(
                title = mediaMetadata.title,
                duration = mediaMetadata.duration,
                thumbnailUrl = mediaMetadata.thumbnailUrl,
                albumId = mediaMetadata.album?.id,
                albumName = mediaMetadata.album?.title,
            ),
        )
        songArtistMap(song.id).forEach(::deleteSongArtistMap)
        mediaMetadata.artists.forEachIndexed { index, artist ->
            val artistId = artist.id ?: artistByNameInternal(artist.name)?.id ?: ArtistEntity.generateArtistId()

            insertArtist(
                ArtistEntity(
                    id = artistId,
                    name = artist.name,
                    channelId = artist.id,
                ),
            )
            insertSongArtistMap(
                SongArtistMap(
                    songId = song.id,
                    artistId = artistId,
                    position = index,
                ),
            )
        }

        if (!mediaMetadata.spotifyTrackId.isNullOrBlank()) {
            insertSpotifyMatch(
                SpotifyMatchEntity(
                    spotifyId = mediaMetadata.spotifyTrackId,
                    youtubeId = song.id,
                    title = mediaMetadata.title,
                    artist = mediaMetadata.artists.joinToString { it.name },
                    matchScore = 1.0,
                ),
            )
        }
    }

    @Update
    fun update(song: SongEntity)

    @Update
    fun updateAlbum(album: AlbumEntity)

    @Update
    fun update(playlist: PlaylistEntity)

    @Update
    fun update(map: PlaylistSongMap)

    @Transaction
    fun update(
        album: AlbumEntity,
        albumPage: AlbumPage,
        artists: List<ArtistEntity>? = emptyList(),
    ) {
        updateAlbum(
            album.copy(
                id = albumPage.album.browseId,
                playlistId = albumPage.album.playlistId,
                title = albumPage.album.title,
                year = albumPage.album.year,
                thumbnailUrl = albumPage.album.thumbnail,
                songCount = albumPage.songs.size,
                duration = albumPage.songs.sumOf { song -> song.duration ?: 0 },
                explicit = albumPage.album.explicit || albumPage.songs.any { it.explicit },
            ),
        )
        if (artists?.size != albumPage.album.artists?.size) {
            artists?.forEach(::deleteArtist)
        }
        albumPage.songs
            .map(SongItem::toMediaMetadata)
            .onEach(::insert)
            .onEach {
                val existingSong = getSongByIdBlocking(it.id)
                if (existingSong != null) {
                    update(existingSong, it)
                }
            }.mapIndexed { index, song ->
                SongAlbumMap(
                    songId = song.id,
                    albumId = albumPage.album.browseId,
                    index = index,
                )
            }.forEach(::upsertSongAlbumMap)

        albumPage.album.artists?.let { artists ->
            // Recreate album artists
            albumArtistMapsInternal(album.id).forEach(::deleteAlbumArtistMap)
            artists
                .map { artist ->
                    ArtistEntity(
                        id =
                            artist.id ?: artistByNameInternal(artist.name)?.id
                                ?: ArtistEntity.generateArtistId(),
                        name = artist.name,
                    )
                }.onEach(::insertArtist)
                .mapIndexed { index, artist ->
                    AlbumArtistMap(
                        albumId = albumPage.album.browseId,
                        artistId = artist.id,
                        order = index,
                    )
                }.forEach(::insertAlbumArtistMap)
        }
    }

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

    @Upsert
    fun upsertSongAlbumMap(map: SongAlbumMap)

    @Upsert
    fun upsert(song: SongEntity)

    @Query("DELETE FROM song WHERE id IN (:songIds)")
    fun deleteSongsByIds(songIds: List<String>)

    @Query("DELETE FROM song WHERE isLocal = 1")
    fun clearLocalSongs()

    @Query("DELETE FROM song_artist_map WHERE songId = :songId")
    fun deleteSongArtistMaps(songId: String)

    @Query("DELETE FROM song_album_map WHERE songId = :songId")
    fun deleteSongAlbumMaps(songId: String)

    @Query("DELETE FROM album_artist_map WHERE albumId IN (:albumIds)")
    fun deleteAlbumArtistMapsByAlbumIds(albumIds: List<String>)

    @Delete
    fun delete(song: SongEntity)

    @Delete
    fun deleteSongArtistMap(songArtistMap: SongArtistMap)

    @Delete
    fun deleteArtist(artist: ArtistEntity)

    @Delete
    fun deleteAlbumArtistMap(albumArtistMap: AlbumArtistMap)

    @Delete
    fun delete(playlist: PlaylistEntity)

    @Delete
    fun delete(playlistSongMap: PlaylistSongMap)

    @Query("DELETE FROM playlist WHERE browseId = :browseId")
    fun deletePlaylistById(browseId: String)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSpotifyMatch(spotifyMatch: SpotifyMatchEntity)

    @RawQuery
    fun raw(supportSQLiteQuery: SupportSQLiteQuery): Int

    fun checkpoint() {
        raw("PRAGMA wal_checkpoint(FULL)".toSQLiteQuery())
    }
}
