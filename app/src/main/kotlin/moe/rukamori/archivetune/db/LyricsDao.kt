/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity

@Dao
interface LyricsDao {
    @Transaction
    @Query("SELECT * FROM format WHERE id = :id")
    fun format(id: String?): Flow<FormatEntity?>

    @Transaction
    @Query("SELECT * FROM lyrics WHERE id = :id")
    fun lyrics(id: String?): Flow<LyricsEntity?>

    @Transaction
    @Query("SELECT * FROM lyrics WHERE id = :id LIMIT 1")
    suspend fun getLyricsById(id: String): LyricsEntity?

    @Transaction
    @Query("SELECT * FROM lyrics WHERE id IN (:ids)")
    suspend fun getLyricsByIds(ids: List<String>): List<LyricsEntity>

    @Query("DELETE FROM lyrics")
    fun clearAllLyrics()

    @Upsert
    fun upsert(lyrics: LyricsEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(lyrics: LyricsEntity): Long

    @Transaction
    fun insertLyricsIfAbsent(
        id: String,
        lyrics: String,
        source: String = LyricsEntity.Source.REMOTE.value,
        updatedAt: Long = System.currentTimeMillis(),
    ) {
        insert(
            LyricsEntity(
                id = id,
                lyrics = lyrics,
                source = source,
                updatedAt = updatedAt,
            ),
        )
    }

    @Transaction
    fun replaceLyrics(
        id: String,
        lyrics: String,
        source: String,
        updatedAt: Long = System.currentTimeMillis(),
    ) {
        upsert(
            LyricsEntity(
                id = id,
                lyrics = lyrics,
                source = source,
                updatedAt = updatedAt,
            ),
        )
    }

    @Upsert
    fun upsert(format: FormatEntity)
}
