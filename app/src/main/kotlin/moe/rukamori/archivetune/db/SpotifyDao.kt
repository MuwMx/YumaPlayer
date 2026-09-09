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
import kotlinx.coroutines.flow.Flow
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.SpotifyMatchEntity

@Dao
interface SpotifyDao {
    @Query("SELECT * FROM spotify_match WHERE spotifyId = :spotifyId")
    fun spotifyMatch(spotifyId: String): Flow<SpotifyMatchEntity?>

    @Query("SELECT * FROM spotify_match WHERE youtubeId = :youtubeId")
    fun spotifyMatchByYouTubeId(youtubeId: String): Flow<SpotifyMatchEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(spotifyMatch: SpotifyMatchEntity)

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE spotifyId = :spotifyId")
    fun playlistBySpotifyId(spotifyId: String): Flow<Playlist?>

    @Query("SELECT * FROM spotify_match WHERE youtubeId IN (:youtubeIds)")
    fun rawGetSpotifyMatchesByYouTubeIds(youtubeIds: List<String>): List<SpotifyMatchEntity>

    fun getSpotifyMatchesByYouTubeIds(youtubeIds: List<String>): List<SpotifyMatchEntity> {
        return youtubeIds.chunked(500).flatMap { chunk ->
            rawGetSpotifyMatchesByYouTubeIds(chunk)
        }
    }
}
