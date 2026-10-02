/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongWithStats

@Immutable
internal sealed interface YearInMusicRecapCard {
    val id: String
    val label: String

    data class Empty(
        val year: Int,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "empty_$year"
    }

    data class Intro(
        val year: Int,
        val totalListeningTime: Long,
        val totalSongsPlayed: Long,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "intro_$year"
    }

    data class Totals(
        val totalListeningTime: Long,
        val totalSongsPlayed: Long,
        val topSong: SongWithStats?,
        val topArtist: Artist?,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "totals"
    }

    data class TopSong(
        val song: SongWithStats,
        val originalSong: Song?,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "top_song_${song.id}"
    }

    data class RankedArtists(
        val artists: List<Artist>,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "artists_${artists.joinToString("_") { it.id }}"
    }

    data class RankedAlbums(
        val albums: List<Album>,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "albums_${albums.joinToString("_") { it.id }}"
    }

    data class Summary(
        val year: Int,
        val totalListeningTime: Long,
        val totalSongsPlayed: Long,
        val topSongs: List<SongWithStats>,
        val topArtists: List<Artist>,
        val topAlbums: List<Album>,
        override val label: String,
    ) : YearInMusicRecapCard {
        override val id: String = "summary_$year"
    }
}
