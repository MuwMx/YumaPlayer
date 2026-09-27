/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_analysis")
data class TrackAnalysisEntity(
    @PrimaryKey val trackId: String,
    val bpm: Double = 0.0,
    val beatInterval: Double = 0.0,
    val firstBeat: Double = 0.0,
    val mixInTime: Double = 0.0,
    val mixOutTime: Double = 0.0,
    val contentEndTime: Double = 0.0,
    val dynamicRangeDb: Double = 0.0,
    val loudnessLufs: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis(),
)
