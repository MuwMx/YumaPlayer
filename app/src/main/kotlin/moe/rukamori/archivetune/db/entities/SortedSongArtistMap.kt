/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.db.entities

import androidx.room.ColumnInfo
import androidx.room.DatabaseView

@DatabaseView(
    viewName = "sorted_song_artist_map",
    value = "SELECT * FROM song_artist_map ORDER BY position",
)
data class SortedSongArtistMap(
    @ColumnInfo(index = true) val songId: String,
    @ColumnInfo(index = true) val artistId: String,
    val position: Int,
)
