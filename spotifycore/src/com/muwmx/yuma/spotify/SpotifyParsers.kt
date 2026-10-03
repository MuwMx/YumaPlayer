/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import moe.rukamori.archivetune.spotify.parser.SpotifyHomeParser
import moe.rukamori.archivetune.spotify.parser.SpotifyPlaylistParser
import moe.rukamori.archivetune.spotify.parser.SpotifyTrackParser
import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeedItem
import moe.rukamori.archivetune.spotify.models.SpotifyHomeFeedSection
import moe.rukamori.archivetune.spotify.models.SpotifyImage
import moe.rukamori.archivetune.spotify.models.SpotifyLibraryFolder
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifySimpleAlbum
import moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

internal object SpotifyParsers {
    fun parseGqlImage(source: JsonObject): SpotifyImage? = SpotifyTrackParser.parseGqlImage(source)

    fun parseGqlImages(sources: JsonArray?): List<SpotifyImage> = SpotifyTrackParser.parseGqlImages(sources)

    fun parseGqlSimpleArtist(artistObj: JsonObject): SpotifySimpleArtist? =
        SpotifyTrackParser.parseGqlSimpleArtist(artistObj)

    fun parseGqlTrack(
        trackData: JsonObject,
        albumOverride: SpotifySimpleAlbum? = null,
        uriOverride: String? = null,
    ): SpotifyTrack = SpotifyTrackParser.parseGqlTrack(trackData, albumOverride, uriOverride)

    fun parseGqlTrackDurationMs(trackData: JsonObject): Int =
        SpotifyTrackParser.parseGqlTrackDurationMs(trackData)

    fun parseGqlPlaylistImages(imagesObj: JsonObject?): List<SpotifyImage> =
        SpotifyPlaylistParser.parseGqlPlaylistImages(imagesObj)

    fun parsePlaylistWrapper(wrapper: JsonObject): SpotifyPlaylist? =
        SpotifyPlaylistParser.parsePlaylistWrapper(wrapper)

    fun parsePlaylistTrackCount(data: JsonObject): Int? =
        SpotifyPlaylistParser.parsePlaylistTrackCount(data)

    fun parseFolderWrapper(wrapper: JsonObject): SpotifyLibraryFolder? =
        SpotifyPlaylistParser.parseFolderWrapper(wrapper)

    fun parseGqlSearchAlbum(data: JsonObject): SpotifyAlbum =
        SpotifyHomeParser.parseGqlSearchAlbum(data)

    fun parseGqlSearchArtist(data: JsonObject): SpotifyArtist =
        SpotifyHomeParser.parseGqlSearchArtist(data)

    fun parseGqlSearchPlaylist(data: JsonObject): SpotifyPlaylist =
        SpotifyPlaylistParser.parseGqlSearchPlaylist(data)

    fun parseHomeSection(sectionObj: JsonObject): SpotifyHomeFeedSection? =
        SpotifyHomeParser.parseHomeSection(sectionObj)

    fun parseHomeItem(itemObj: JsonObject): SpotifyHomeFeedItem? =
        SpotifyHomeParser.parseHomeItem(itemObj)

    fun parseHomePlaylist(data: JsonObject): SpotifyHomeFeedItem.Playlist? =
        SpotifyHomeParser.parseHomePlaylist(data)

    fun parseHomeAlbum(data: JsonObject): SpotifyHomeFeedItem.Album? =
        SpotifyHomeParser.parseHomeAlbum(data)

    fun parseHomeArtist(data: JsonObject): SpotifyHomeFeedItem.Artist? =
        SpotifyHomeParser.parseHomeArtist(data)
}
