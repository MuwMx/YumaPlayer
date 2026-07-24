/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "library_top_mix")
data class LibraryTopMixEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    @ColumnInfo(index = true) val position: Int,
    val createdAt: LocalDateTime,
)
