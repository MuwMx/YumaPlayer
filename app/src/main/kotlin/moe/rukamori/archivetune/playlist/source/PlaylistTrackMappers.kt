/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.source

import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.screens.playlist.CachedSong

/**
 * Conversions from each playlist screen's origin item into the unified
 * [PlaylistTrack]. Each keeps [PlaylistTrack.origin] pointing at the original
 * typed item so menus/badges/queue stay type-accurate downstream.
 */

fun PlaylistSong.toPlaylistTrack(): PlaylistTrack =
    PlaylistTrack(
        id = song.id,
        title = song.song.title,
        artists = song.artists.map { it.name },
        durationMs = song.song.duration * 1000L,
        thumbnailUrl = song.song.thumbnailUrl,
        explicit = song.song.explicit,
        origin = this,
    )

fun Song.toPlaylistTrack(): PlaylistTrack =
    PlaylistTrack(
        id = id,
        title = song.title,
        artists = artists.map { it.name },
        durationMs = song.duration * 1000L,
        thumbnailUrl = song.thumbnailUrl,
        explicit = song.explicit,
        origin = this,
    )

fun SongItem.toPlaylistTrack(): PlaylistTrack =
    PlaylistTrack(
        id = id,
        title = title,
        artists = artists.map { it.name },
        durationMs = duration?.times(1000L),
        thumbnailUrl = thumbnail,
        explicit = explicit,
        origin = this,
    )

fun SpotifyTrack.toPlaylistTrack(): PlaylistTrack =
    PlaylistTrack(
        id = id,
        title = name,
        artists = artists.map { it.name },
        durationMs = durationMs.takeIf { it > 0 }?.toLong(),
        thumbnailUrl = album?.images?.firstOrNull()?.url,
        explicit = explicit,
        origin = this,
    )

fun CachedSong.toPlaylistTrack(): PlaylistTrack =
    PlaylistTrack(
        id = id,
        title = title,
        artists = artists.map { it.name },
        durationMs = duration * 1000L,
        thumbnailUrl = thumbnailUrl,
        explicit = song.song.explicit,
        origin = this,
    )
