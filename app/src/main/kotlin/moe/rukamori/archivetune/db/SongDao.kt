/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.reversed
import java.text.Collator
import java.util.Locale

@Dao
interface SongDao {
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
}
