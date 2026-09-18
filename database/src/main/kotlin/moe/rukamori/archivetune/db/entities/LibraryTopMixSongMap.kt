/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Immutable
@Entity(
    tableName = "library_top_mix_song_map",
    primaryKeys = ["mixId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = LibraryTopMixEntity::class,
            parentColumns = ["id"],
            childColumns = ["mixId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LibraryTopMixSongMap(
    @ColumnInfo(index = true) val mixId: String,
    @ColumnInfo(index = true) val songId: String,
    val position: Int,
)
