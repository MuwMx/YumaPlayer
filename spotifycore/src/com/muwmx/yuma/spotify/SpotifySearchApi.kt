/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import moe.rukamori.archivetune.spotify.Spotify.SpotifyException
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import moe.rukamori.archivetune.spotify.models.SpotifySearchResult

object SpotifySearchApi {
    // ── Search (GQL: searchDesktop) ─────────────────────────────────────

    suspend fun search(
        query: String,
        types: List<String> = listOf("track"),
        limit: Int = 20,
        offset: Int = 0,
    ): Result<SpotifySearchResult> =
        runCatching {
            val vars =
                buildJsonObject {
                    put("searchTerm", query)
                    put("offset", offset)
                    put("limit", limit)
                    put("numberOfTopResults", 5)
                    put("includeAudiobooks", false)
                    put("includeArtistHasConcertsField", false)
                    put("includePreReleases", false)
                    put("includeLocalConcertsField", false)
                    put("includeAuthors", false)
                }

            val response =
                SpotifyGraphqlClient.graphqlPost(
                    operationName = "searchDesktop",
                    variables = vars,
                )

            val searchData =
                response.obj("data")?.obj("searchV2")
                    ?: throw SpotifyException(500, "Invalid searchDesktop response")

            val tracksSection = searchData.obj("tracksV2")
            val trackItems =
                tracksSection?.arr("items")?.mapNotNull { elem ->
                    val itemWrapper = elem.jsonObject.obj("item") ?: return@mapNotNull null
                    if (itemWrapper.str("__typename") != "TrackResponseWrapper") return@mapNotNull null
                    val data = itemWrapper.obj("data") ?: return@mapNotNull null
                    if (data.str("__typename") != "Track") return@mapNotNull null
                    val wrapperUri = itemWrapper.str("_uri") ?: itemWrapper.str("uri")
                    SpotifyParsers.parseGqlTrack(data, uriOverride = wrapperUri)
                } ?: emptyList()

            val albumsSection = searchData.obj("albumsV2")
            val albumItems =
                albumsSection?.arr("items")?.mapNotNull { elem ->
                    val wrapper = elem.jsonObject
                    if (wrapper.str("__typename") != "AlbumResponseWrapper") return@mapNotNull null
                    val data = wrapper.obj("data") ?: return@mapNotNull null
                    if (data.str("__typename") != "Album") return@mapNotNull null
                    SpotifyParsers.parseGqlSearchAlbum(data)
                } ?: emptyList()

            val artistsSection = searchData.obj("artists")
            val artistItems =
                artistsSection?.arr("items")?.mapNotNull { elem ->
                    val wrapper = elem.jsonObject
                    if (wrapper.str("__typename") != "ArtistResponseWrapper") return@mapNotNull null
                    val data = wrapper.obj("data") ?: return@mapNotNull null
                    if (data.str("__typename") != "Artist") return@mapNotNull null
                    SpotifyParsers.parseGqlSearchArtist(data)
                } ?: emptyList()

            val playlistsSection = searchData.obj("playlists")
            val playlistItems =
                playlistsSection?.arr("items")?.mapNotNull { elem ->
                    val wrapper = elem.jsonObject
                    if (wrapper.str("__typename") != "PlaylistResponseWrapper") return@mapNotNull null
                    val data = wrapper.obj("data") ?: return@mapNotNull null
                    if (data.str("__typename") != "Playlist") return@mapNotNull null
                    SpotifyParsers.parseGqlSearchPlaylist(data)
                } ?: emptyList()

            SpotifySearchResult(
                tracks =
                    SpotifyPaging(
                        items = trackItems,
                        total = tracksSection?.int("totalCount") ?: 0,
                        limit = limit,
                        offset = offset,
                    ),
                albums =
                    if (albumItems.isNotEmpty()) {
                        SpotifyPaging(items = albumItems, total = albumsSection?.int("totalCount") ?: 0, limit = limit, offset = offset)
                    } else {
                        null
                    },
                artists =
                    if (artistItems.isNotEmpty()) {
                        SpotifyPaging(items = artistItems, total = artistsSection?.int("totalCount") ?: 0, limit = limit, offset = offset)
                    } else {
                        null
                    },
                playlists =
                    if (playlistItems.isNotEmpty()) {
                        SpotifyPaging(
                            items = playlistItems,
                            total = playlistsSection?.int("totalCount") ?: 0,
                            limit = limit,
                            offset = offset,
                        )
                    } else {
                        null
                    },
            )
        }
}
