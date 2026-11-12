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
import moe.rukamori.archivetune.innertube.models.MusicResponsiveListItemRenderer
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem

internal object BrowseChartItemConverter {
    fun convertToChartItem(renderer: MusicResponsiveListItemRenderer): YTItem? {
        return try {
            val videoId =
                renderer.playlistItemData?.videoId
                    ?: renderer.navigationEndpoint?.anyWatchEndpoint?.videoId
                    ?: renderer.overlay
                        ?.musicItemThumbnailOverlayRenderer
                        ?.content
                        ?.musicPlayButtonRenderer
                        ?.playNavigationEndpoint
                        ?.anyWatchEndpoint
                        ?.videoId

            val firstColumn =
                renderer.flexColumns
                    .getOrNull(0)
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text ?: return null

            val secondColumn =
                renderer.flexColumns
                    .getOrNull(1)
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text

            val titleRun = firstColumn.runs?.firstOrNull() ?: return null
            val title = titleRun.text.takeIf { it.isNotBlank() } ?: return null

            val thirdColumn =
                renderer.flexColumns
                    .getOrNull(2)
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text

            val chartPosition =
                renderer.customIndexColumn
                    ?.musicCustomIndexColumnRenderer
                    ?.text
                    ?.runs
                    ?.firstOrNull()
                    ?.text
                    ?.toIntOrNull()
                    ?: thirdColumn
                        ?.runs
                        ?.firstOrNull()
                        ?.text
                        ?.toIntOrNull()

            val chartChange = thirdColumn?.runs?.getOrNull(1)?.text

            when {
                videoId != null -> {
                    val artists =
                        secondColumn?.runs?.mapNotNull { run ->
                            run.text.takeIf { it.isNotBlank() && it != " • " && it != " & " && it != ", " }?.let { name ->
                                Artist(
                                    name = name,
                                    id = run.navigationEndpoint?.browseEndpoint?.browseId,
                                )
                            }
                        } ?: emptyList()

                    val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null

                    SongItem(
                        id = videoId,
                        title = title,
                        artists = artists,
                        thumbnail = thumbnail,
                        explicit =
                            renderer.badges?.any {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } == true,
                        chartPosition = chartPosition,
                        chartChange = chartChange,
                    )
                }

                renderer.isArtist -> {
                    val artistId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null
                    val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null
                    ArtistItem(
                        id = artistId,
                        title = title,
                        thumbnail = thumbnail,
                        shuffleEndpoint = null,
                        radioEndpoint = null,
                        subscriberCountText = secondColumn?.runs?.firstOrNull()?.text,
                    )
                }

                renderer.isAlbum -> {
                    val albumId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null
                    val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null
                    val playlistId =
                        renderer.overlay
                            ?.musicItemThumbnailOverlayRenderer
                            ?.content
                            ?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint
                            ?.playlistId
                            .orEmpty()
                    AlbumItem(
                        browseId = albumId,
                        playlistId = playlistId,
                        title = title,
                        artists =
                            secondColumn?.runs?.mapNotNull { run ->
                                run.text.takeIf { it.isNotBlank() && it != " • " }?.let { name ->
                                    Artist(name = name, id = run.navigationEndpoint?.browseEndpoint?.browseId)
                                }
                            }.orEmpty(),
                        year = null,
                        thumbnail = thumbnail,
                        explicit =
                            renderer.badges?.any {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } == true,
                    )
                }

                renderer.isPlaylist -> {
                    val playlistId =
                        renderer.navigationEndpoint?.browseEndpoint?.browseId
                            ?: renderer.overlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint
                                ?.playlistId
                            ?: return null
                    val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null
                    PlaylistItem(
                        id = playlistId.removePrefix("VL"),
                        title = title,
                        author = secondColumn?.runs?.firstOrNull()?.text?.let { Artist(name = it, id = null) },
                        songCountText = null,
                        thumbnail = thumbnail,
                        playEndpoint =
                            renderer.overlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint,
                        shuffleEndpoint = null,
                        radioEndpoint = null,
                    )
                }

                else -> {
                    null
                }
            }
        } catch (e: Exception) {
            println("Error converting chart item: ${e.message}\n${Json.encodeToString(renderer)}")
            null
        }
    }
}
