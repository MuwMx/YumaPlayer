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
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.db.entities.Event
import moe.rukamori.archivetune.db.entities.EventWithSong
import moe.rukamori.archivetune.db.entities.LibraryTopMixEntity
import moe.rukamori.archivetune.db.entities.LibraryTopMixSongMap
import moe.rukamori.archivetune.db.entities.ListeningBySlot
import moe.rukamori.archivetune.db.entities.ListeningTotals
import moe.rukamori.archivetune.db.entities.PlayCountEntity
import moe.rukamori.archivetune.db.entities.SearchHistory
import moe.rukamori.archivetune.db.entities.Song
import java.time.LocalDateTime
import java.time.ZoneOffset

@Dao
interface HistoryDao {
    @Transaction
    @Query("SELECT * FROM event ORDER BY rowId DESC")
    fun events(): Flow<List<EventWithSong>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM song
        JOIN (
            SELECT songId, MAX(rowId) AS latestRowId
            FROM event
            GROUP BY songId
            ORDER BY latestRowId DESC
            LIMIT :limit
        ) recent ON song.id = recent.songId
        ORDER BY recent.latestRowId DESC
        """,
    )
    fun recentSongs(limit: Int = 100): Flow<List<Song>>

    @Query("SELECT * FROM library_top_mix ORDER BY position LIMIT :limit")
    fun libraryTopMixes(limit: Int): Flow<List<LibraryTopMixEntity>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM song
        INNER JOIN library_top_mix_song_map ON library_top_mix_song_map.songId = song.id
        WHERE library_top_mix_song_map.mixId = :mixId
        ORDER BY library_top_mix_song_map.position
        """,
    )
    fun libraryTopMixSongs(mixId: String): List<Song>

    @Query(
        """
        SELECT CAST(strftime('%H', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS slot,
               SUM(playTime) AS timeListened
        FROM event
        WHERE timestamp > :fromTimestamp AND timestamp <= :toTimestamp
        GROUP BY slot
        ORDER BY slot
        """,
    )
    fun listeningByHour(
        fromTimestamp: Long,
        toTimestamp: Long,
    ): Flow<List<ListeningBySlot>>

    @Query(
        """
        SELECT CAST(strftime('%w', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS slot,
               SUM(playTime) AS timeListened
        FROM event
        WHERE timestamp > :fromTimestamp AND timestamp <= :toTimestamp
        GROUP BY slot
        ORDER BY slot
        """,
    )
    fun listeningByDayOfWeek(
        fromTimestamp: Long,
        toTimestamp: Long,
    ): Flow<List<ListeningBySlot>>

    @Query(
        """
        SELECT COUNT(1) AS totalPlayCount,
               COALESCE(SUM(playTime), 0) AS totalTimeListened
        FROM event
        WHERE timestamp > :fromTimestamp AND timestamp <= :toTimestamp
        """,
    )
    fun listeningTotals(
        fromTimestamp: Long,
        toTimestamp: Long,
    ): Flow<ListeningTotals>

    @Transaction
    @Query("SELECT * FROM event ORDER BY rowId ASC LIMIT 1")
    fun firstEvent(): Flow<EventWithSong?>

    @Query("SELECT songId FROM event ORDER BY rowId DESC LIMIT 1")
    fun lastEventSongId(): Flow<String?>

    @Transaction
    @Query("DELETE FROM event")
    fun clearListenHistory()

    @Transaction
    @Query("DELETE FROM event WHERE id IN (:eventIds)")
    fun deleteEventsByIds(eventIds: List<Long>)

    @Transaction
    @Query("SELECT * FROM search_history WHERE `query` LIKE :query || '%' ORDER BY id DESC")
    fun searchHistory(query: String = ""): Flow<List<SearchHistory>>

    @Transaction
    @Query("DELETE FROM search_history")
    fun clearSearchHistory()

    @Query("UPDATE song SET totalPlayTime = totalPlayTime + :playTime WHERE id = :songId")
    fun incrementTotalPlayTime(
        songId: String,
        playTime: Long,
    )

    @Query("SELECT sum(count) from playCount WHERE song = :songId")
    fun getLifetimePlayCount(songId: String?): Flow<Int>

    @Query("SELECT sum(count) from playCount WHERE song = :songId AND year = :year")
    fun getPlayCountByYear(
        songId: String?,
        year: Int,
    ): Flow<Int>

    @Query("SELECT count from playCount WHERE song = :songId AND year = :year AND month = :month")
    fun getPlayCountByMonth(
        songId: String?,
        year: Int,
        month: Int,
    ): Flow<Int>

    @Query("UPDATE playCount SET count = count + 1 WHERE song = :songId AND year = :year AND month = :month")
    suspend fun incrementPlayCount(
        songId: String,
        year: Int,
        month: Int,
    )

    /**
     * Increment by one the play count with today's year and month.
     */
    suspend fun incrementPlayCount(songId: String) {
        val time = LocalDateTime.now().atOffset(ZoneOffset.UTC)
        val oldCount = getPlayCountByMonth(songId, time.year, time.monthValue).first()

        // add new
        if (oldCount <= 0) {
            insert(PlayCountEntity(songId, time.year, time.monthValue, 0))
        }
        incrementPlayCount(songId, time.year, time.monthValue)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(searchHistory: SearchHistory)

    @Delete
    fun delete(searchHistory: SearchHistory)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(event: Event): Long

    @Delete
    fun delete(event: Event)

    @Query("UPDATE event SET playTime = :playTime WHERE id = :eventId")
    fun updateEventPlayTime(
        eventId: Long,
        playTime: Long,
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(playCountEntity: PlayCountEntity): Long

    @Query("DELETE FROM playCount WHERE song NOT IN (SELECT id FROM song)")
    fun prunePlayCounts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(libraryTopMix: LibraryTopMixEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(libraryTopMixSongMap: LibraryTopMixSongMap)

    @Query("DELETE FROM library_top_mix")
    fun deleteLibraryTopMixes()
}
