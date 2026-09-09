/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.RoomWarnings
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.ArtistSongSortType
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.AlbumArtistMap
import moe.rukamori.archivetune.db.entities.AlbumEntity
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.RelatedSongMap
import moe.rukamori.archivetune.db.entities.SetVideoIdEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongAlbumMap
import moe.rukamori.archivetune.db.entities.SongArtistMap
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.db.entities.SongWithStats
import moe.rukamori.archivetune.db.entities.SpotifyMatchEntity
import moe.rukamori.archivetune.db.entities.TagEntity
import moe.rukamori.archivetune.extensions.reversed
import moe.rukamori.archivetune.extensions.toSQLiteQuery
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.AlbumPage
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import java.text.Collator
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

@Dao
interface DatabaseDao {
    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId LIMIT 1")
    fun getSongByIdBlockingInternal(songId: String): Song?

    @Transaction
    @Query("SELECT * FROM song_artist_map WHERE songId = :songId")
    fun songArtistMapInternal(songId: String): List<SongArtistMap>

    @Transaction
    @Query("SELECT * FROM album_artist_map WHERE albumId = :albumId")
    fun albumArtistMapsInternal(albumId: String): List<AlbumArtistMap>

    @Transaction
    @Query("SELECT * FROM artist WHERE name = :name")
    fun artistByNameInternal(name: String): ArtistEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSongInternal(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertArtist(artist: ArtistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAlbum(album: AlbumEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSongArtistMap(map: SongArtistMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSongAlbumMap(map: SongAlbumMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAlbumArtistMap(map: AlbumArtistMap)

    @Transaction
    fun insert(
        mediaMetadata: MediaMetadata,
        block: (SongEntity) -> SongEntity = { it },
    ) {
        if (insertSongInternal(mediaMetadata.toSongEntity().let(block)) == -1L) return

        if (mediaMetadata.setVideoId != null) {
            insert(
                SetVideoIdEntity(
                    videoId = mediaMetadata.id,
                    setVideoId = mediaMetadata.setVideoId,
                ),
            )
        }

        if (!mediaMetadata.spotifyTrackId.isNullOrBlank()) {
            insertSpotifyMatch(
                SpotifyMatchEntity(
                    spotifyId = mediaMetadata.spotifyTrackId,
                    youtubeId = mediaMetadata.id,
                    title = mediaMetadata.title,
                    artist = mediaMetadata.artists.joinToString { it.name },
                    matchScore = 1.0,
                ),
            )
        }

        mediaMetadata.artists.forEachIndexed { index, artist ->
            val artistId = artist.id ?: artistByNameInternal(artist.name)?.id ?: ArtistEntity.generateArtistId()

            insertArtist(
                ArtistEntity(
                    id = artistId,
                    name = artist.name,
                    channelId = artist.id,
                ),
            )

            insertSongArtistMap(
                SongArtistMap(
                    songId = mediaMetadata.id,
                    artistId = artistId,
                    position = index,
                ),
            )
        }
    }

    @Transaction
    fun insert(albumPage: AlbumPage) {
        if (insertAlbum(
                AlbumEntity(
                    id = albumPage.album.browseId,
                    playlistId = albumPage.album.playlistId,
                    title = albumPage.album.title,
                    year = albumPage.album.year,
                    thumbnailUrl = albumPage.album.thumbnail,
                    songCount = albumPage.songs.size,
                    duration = albumPage.songs.sumOf { song -> song.duration ?: 0 },
                    explicit = albumPage.album.explicit || albumPage.songs.any { it.explicit },
                ),
            ) == -1L
        ) {
            return
        }
        albumPage.songs
            .map(SongItem::toMediaMetadata)
            .onEach(::insert)
            .onEach {
                val existingSong = getSongByIdBlockingInternal(it.id)
                if (existingSong != null) {
                    update(existingSong, it)
                }
            }.mapIndexed { index, song ->
                SongAlbumMap(
                    songId = song.id,
                    albumId = albumPage.album.browseId,
                    index = index,
                )
            }.forEach(::upsertSongAlbumMap)
        albumPage.album.artists
            ?.map { artist ->
                ArtistEntity(
                    id =
                        artist.id ?: artistByNameInternal(artist.name)?.id
                            ?: ArtistEntity.generateArtistId(),
                    name = artist.name,
                )
            }?.onEach(::insertArtist)
            ?.mapIndexed { index, artist ->
                AlbumArtistMap(
                    albumId = albumPage.album.browseId,
                    artistId = artist.id,
                    order = index,
                )
            }?.forEach(::insertAlbumArtistMap)
    }

    @Transaction
    fun update(
        song: Song,
        mediaMetadata: MediaMetadata,
    ) {
        updateSongInternal(
            song.song.copy(
                title = mediaMetadata.title,
                duration = mediaMetadata.duration,
                thumbnailUrl = mediaMetadata.thumbnailUrl,
                albumId = mediaMetadata.album?.id,
                albumName = mediaMetadata.album?.title,
            ),
        )
        songArtistMapInternal(song.id).forEach(::deleteSongArtistMap)
        mediaMetadata.artists.forEachIndexed { index, artist ->
            val artistId = artist.id ?: artistByNameInternal(artist.name)?.id ?: ArtistEntity.generateArtistId()

            insertArtist(
                ArtistEntity(
                    id = artistId,
                    name = artist.name,
                    channelId = artist.id,
                ),
            )
            insertSongArtistMap(
                SongArtistMap(
                    songId = song.id,
                    artistId = artistId,
                    position = index,
                ),
            )
        }

        if (!mediaMetadata.spotifyTrackId.isNullOrBlank()) {
            insertSpotifyMatch(
                SpotifyMatchEntity(
                    spotifyId = mediaMetadata.spotifyTrackId,
                    youtubeId = song.id,
                    title = mediaMetadata.title,
                    artist = mediaMetadata.artists.joinToString { it.name },
                    matchScore = 1.0,
                ),
            )
        }
    }

    @Update
    fun updateSongInternal(song: SongEntity)

    @Update
    fun updateAlbum(album: AlbumEntity)

    @Transaction
    fun update(
        album: AlbumEntity,
        albumPage: AlbumPage,
        artists: List<ArtistEntity>? = emptyList(),
    ) {
        updateAlbum(
            album.copy(
                id = albumPage.album.browseId,
                playlistId = albumPage.album.playlistId,
                title = albumPage.album.title,
                year = albumPage.album.year,
                thumbnailUrl = albumPage.album.thumbnail,
                songCount = albumPage.songs.size,
                duration = albumPage.songs.sumOf { song -> song.duration ?: 0 },
                explicit = albumPage.album.explicit || albumPage.songs.any { it.explicit },
            ),
        )
        if (artists?.size != albumPage.album.artists?.size) {
            artists?.forEach(::deleteArtist)
        }
        albumPage.songs
            .map(SongItem::toMediaMetadata)
            .onEach(::insert)
            .onEach {
                val existingSong = getSongByIdBlockingInternal(it.id)
                if (existingSong != null) {
                    update(existingSong, it)
                }
            }.mapIndexed { index, song ->
                SongAlbumMap(
                    songId = song.id,
                    albumId = albumPage.album.browseId,
                    index = index,
                )
            }.forEach(::upsertSongAlbumMap)

        albumPage.album.artists?.let { artists ->
            // Recreate album artists
            albumArtistMapsInternal(album.id).forEach(::deleteAlbumArtistMap)
            artists
                .map { artist ->
                    ArtistEntity(
                        id =
                            artist.id ?: artistByNameInternal(artist.name)?.id
                                ?: ArtistEntity.generateArtistId(),
                        name = artist.name,
                    )
                }.onEach(::insertArtist)
                .mapIndexed { index, artist ->
                    AlbumArtistMap(
                        albumId = albumPage.album.browseId,
                        artistId = artist.id,
                        order = index,
                    )
                }.forEach(::insertAlbumArtistMap)
        }
    }

    @Upsert
    fun upsertSongAlbumMap(map: SongAlbumMap)

    @Delete
    fun deleteSongArtistMap(songArtistMap: SongArtistMap)

    @Delete
    fun deleteArtist(artist: ArtistEntity)

    @Delete
    fun deleteAlbumArtistMap(albumArtistMap: AlbumArtistMap)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSpotifyMatch(spotifyMatch: SpotifyMatchEntity)

    @RawQuery
    fun raw(supportSQLiteQuery: SupportSQLiteQuery): Int

    fun checkpoint() {
        raw("PRAGMA wal_checkpoint(FULL)".toSQLiteQuery())
    }
}
