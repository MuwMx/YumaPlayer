/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "spotify_match", indices = [Index(value = ["youtubeId"])])
data class SpotifyMatchEntity(
    @PrimaryKey val spotifyId: String,
    val youtubeId: String,
    val title: String,
    val artist: String,
    val matchScore: Double,
    val cachedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val isManualOverride: Boolean = false,
)
