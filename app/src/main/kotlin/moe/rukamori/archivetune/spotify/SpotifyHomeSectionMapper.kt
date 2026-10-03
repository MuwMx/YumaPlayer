/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeedItem
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeedSection
import moe.rukamori.archivetune.spotify.models.SpotifyImage
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistOwner
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylistTracksRef

internal fun convertHomeSection(feedSection: SpotifyHomeFeedSection): SpotifyHomeSection? {
    val title = feedSection.title ?: return null

    val playlists = feedSection.items.filterIsInstance<SpotifyHomeFeedItem.Playlist>()
    val albums = feedSection.items.filterIsInstance<SpotifyHomeFeedItem.Album>()
    val artists = feedSection.items.filterIsInstance<SpotifyHomeFeedItem.Artist>()

    val counts =
        arrayOf(
            SectionType.PLAYLISTS to playlists.size,
            SectionType.ALBUMS to albums.size,
            SectionType.ARTISTS to artists.size,
        )
    val (dominant, size) = counts.maxByOrNull { it.second } ?: return null
    if (size == 0) return null

    return when (dominant) {
        SectionType.PLAYLISTS ->
            SpotifyHomeSection(
                title = title,
                type = SectionType.PLAYLISTS,
                playlists =
                    playlists.map {
                        SpotifyPlaylist(
                            id = it.id,
                            name = it.name,
                            description = it.description,
                            images = listOfNotNull(it.imageUrl?.let { url -> SpotifyImage(url, null, null) }),
                            owner = it.ownerName?.let { owner -> SpotifyPlaylistOwner(id = "", displayName = owner) },
                            tracks = SpotifyPlaylistTracksRef(total = it.totalCount),
                            uri = it.uri,
                        )
                    },
            )
        SectionType.ALBUMS ->
            SpotifyHomeSection(
                title = title,
                type = SectionType.ALBUMS,
                albums =
                    albums.map {
                        SpotifyAlbum(
                            id = it.id,
                            name = it.name,
                            albumType = it.albumType,
                            artists = it.artists,
                            images = listOfNotNull(it.imageUrl?.let { url -> SpotifyImage(url, null, null) }),
                            uri = it.uri,
                        )
                    },
            )
        SectionType.ARTISTS ->
            SpotifyHomeSection(
                title = title,
                type = SectionType.ARTISTS,
                artists =
                    artists.map {
                        SpotifyArtist(
                            id = it.id,
                            name = it.name,
                            images = listOfNotNull(it.imageUrl?.let { url -> SpotifyImage(url, null, null) }),
                            uri = it.uri,
                        )
                    },
            )
        else -> null
    }
}
