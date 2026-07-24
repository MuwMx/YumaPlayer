/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity

@Immutable
@Entity(
    tableName = "playCount",
    primaryKeys = ["song", "year", "month"],
)
class PlayCountEntity(
    val song: String, // song id
    val year: Int = -1,
    val month: Int = -1,
    val count: Int = -1,
)
