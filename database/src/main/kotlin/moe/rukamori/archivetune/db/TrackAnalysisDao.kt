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
import moe.rukamori.archivetune.db.entities.TrackAnalysisEntity

@Dao
interface TrackAnalysisDao {
    @Query("SELECT * FROM track_analysis WHERE trackId = :trackId")
    suspend fun get(trackId: String): TrackAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrackAnalysisEntity)
}
