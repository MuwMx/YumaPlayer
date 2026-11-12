/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.browse

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.MusicTwoRowItemRenderer
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.oddElements

internal object BrowseTwoRowItemConverter {
    fun convertMusicTwoRowItem(renderer: MusicTwoRowItemRenderer): YTItem? {
        return try {
            val title =
                renderer.title.runs
                    ?.firstOrNull()
                    ?.text ?: return null
            val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null
            val explicit =
                renderer.subtitleBadges?.any {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } == true

            when {
                renderer.isSong -> {
                    val videoId =
                        renderer.navigationEndpoint.watchEndpoint?.videoId
                            ?: renderer.navigationEndpoint.anyWatchEndpoint?.videoId
                            ?: renderer.thumbnailOverlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.anyWatchEndpoint
                                ?.videoId
                            ?: return null
                    val subtitle = renderer.subtitle?.runs ?: return null
                    SongItem(
                        id = videoId,
                        title = title,
                        artists =
                            subtitle.mapNotNull {
                                it.navigationEndpoint?.browseEndpoint?.browseId?.let { id ->
                                    Artist(name = it.text, id = id)
                                }
                            },
                        thumbnail = thumbnail,
                        explicit = explicit,
                    )
                }

                renderer.isAlbum -> {
                    AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId =
                            renderer.thumbnailOverlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint
                                ?.playlistId ?: return null,
                        title = title,
                        artists =
                            renderer.subtitle?.runs?.oddElements()?.drop(1)?.mapNotNull {
                                it.navigationEndpoint?.browseEndpoint?.browseId?.let { id ->
                                    Artist(name = it.text, id = id)
                                }
                            },
                        year =
                            renderer.subtitle
                                ?.runs
                                ?.lastOrNull()
                                ?.text
                                ?.toIntOrNull(),
                        thumbnail = thumbnail,
                        explicit = explicit,
                    )
                }

                renderer.isPlaylist -> {
                    val playlistId =
                        renderer.navigationEndpoint.browseEndpoint?.browseId
                            ?: renderer.thumbnailOverlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint
                                ?.playlistId
                            ?: return null
                    PlaylistItem(
                        id = playlistId.removePrefix("VL"),
                        title = title,
                        author =
                            renderer.subtitle
                                ?.runs
                                ?.lastOrNull()
                                ?.text
                                ?.let { Artist(name = it, id = null) },
                        songCountText = null,
                        thumbnail = thumbnail,
                        playEndpoint =
                            renderer.thumbnailOverlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint,
                        shuffleEndpoint = null,
                        radioEndpoint = null,
                    )
                }

                renderer.isArtist -> {
                    val artistId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null
                    ArtistItem(
                        id = artistId,
                        title = title,
                        thumbnail = thumbnail,
                        shuffleEndpoint = null,
                        radioEndpoint = null,
                        subscriberCountText = renderer.subtitle?.runs?.firstOrNull()?.text,
                    )
                }

                else -> {
                    null
                }
            }
        } catch (e: Exception) {
            println("Error converting two row item: ${e.message}\n${Json.encodeToString(renderer)}")
            null
        }
    }
}
