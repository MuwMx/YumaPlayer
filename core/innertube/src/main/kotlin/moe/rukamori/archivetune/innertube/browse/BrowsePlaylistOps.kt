/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.browse

import io.ktor.client.call.body
import moe.rukamori.archivetune.innertube.InnerTube
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.MusicShelfRenderer
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.getContinuation
import moe.rukamori.archivetune.innertube.models.getItems
import moe.rukamori.archivetune.innertube.models.response.BrowseResponse
import moe.rukamori.archivetune.innertube.network.playlistSongContents
import moe.rukamori.archivetune.innertube.network.playlistSongContinuation
import moe.rukamori.archivetune.innertube.pages.PlaylistContinuationPage
import moe.rukamori.archivetune.innertube.pages.PlaylistPage

internal object BrowsePlaylistOps {
    private inline val innerTube: InnerTube get() = YouTube.innerTube

    suspend fun playlist(playlistId: String): Result<PlaylistPage> =
        runCatching {
            val cleanBrowseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        browseId = cleanBrowseId,
                        setLogin = true,
                    ).body<BrowseResponse>()
            val primarySection =
                response.contents
                    ?.twoColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
            val allFirstColumnContents = primarySection?.contents.orEmpty()
            val base =
                allFirstColumnContents.firstOrNull {
                    it.musicResponsiveHeaderRenderer != null || it.musicEditablePlaylistDetailHeaderRenderer != null
                }
            val header =
                base?.musicResponsiveHeaderRenderer
                    ?: base?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer
            if (header == null) throw IllegalStateException("PLAYLIST_PRIVATE")

            val title =
                header.title.runs
                    ?.firstOrNull()
                    ?.text ?: throw IllegalStateException("PLAYLIST_PRIVATE")
            val thumbnail =
                header.thumbnail
                    ?.musicThumbnailRenderer
                    ?.thumbnail
                    ?.thumbnails
                    ?.lastOrNull()
                    ?.normalizedUrl
                    ?: throw IllegalStateException("PLAYLIST_PRIVATE")

            val editable = base?.musicEditablePlaylistDetailHeaderRenderer != null

            val headerMenuItems =
                header.buttons
                    .firstOrNull { it.menuRenderer != null }
                    ?.menuRenderer
                    ?.items
                    .orEmpty()

            val description =
                base
                    ?.musicEditablePlaylistDetailHeaderRenderer
                    ?.header
                    ?.musicDetailHeaderRenderer
                    ?.description
                    ?.runs
                    ?.joinToString("") { it.text }
                    ?: allFirstColumnContents.firstNotNullOfOrNull {
                        it.musicDescriptionShelfRenderer
                            ?.description
                            ?.runs
                            ?.joinToString("") { run -> run.text }
                    }
            val secondarySection =
                response.contents
                    ?.twoColumnBrowseResultsRenderer
                    ?.secondaryContents
                    ?.sectionListRenderer
            val secondaryContents = secondarySection?.contents.orEmpty()
            val songContents =
                buildList {
                    secondaryContents.forEach { content ->
                        addAll(content.playlistSongContents())
                    }
                    allFirstColumnContents.forEach { content ->
                        addAll(content.playlistSongContents())
                    }
                }
            val songsContinuation =
                secondaryContents.firstNotNullOfOrNull { content ->
                    content.playlistSongContinuation()
                } ?: allFirstColumnContents.firstNotNullOfOrNull { content ->
                    content.playlistSongContinuation()
                }

            PlaylistPage(
                playlist =
                    PlaylistItem(
                        id = playlistId,
                        title = title,
                        author =
                            header.straplineTextOne?.runs?.firstOrNull()?.let {
                                Artist(
                                    name = it.text,
                                    id = it.navigationEndpoint?.browseEndpoint?.browseId,
                                )
                            },
                        songCountText =
                            header.secondSubtitle
                                ?.runs
                                ?.firstOrNull()
                                ?.text,
                        thumbnail = thumbnail,
                        description = description,
                        playEndpoint =
                            header.buttons
                                .firstOrNull()
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.anyWatchEndpoint,
                        shuffleEndpoint =
                            headerMenuItems
                                .firstOrNull()
                                ?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint,
                        radioEndpoint =
                            headerMenuItems
                                .find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                                }?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint,
                        isEditable = editable,
                    ),
                songs =
                    songContents.getItems().mapNotNull {
                        PlaylistPage.fromMusicResponsiveListItemRenderer(it, playlistId)
                    },
                songsContinuation = songsContinuation,
                continuation =
                    secondarySection?.continuations?.getContinuation()
                        ?: primarySection?.continuations?.getContinuation(),
            )
        }

    suspend fun playlistContinuation(
        continuation: String,
        playlistId: String? = null,
    ): Result<PlaylistContinuationPage> =
        runCatching {
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        continuation = continuation,
                        browseId = "",
                        setLogin = true,
                    ).body<BrowseResponse>()

            playlistContinuationPageFromResponse(response, playlistId)
        }

    internal fun playlistContinuationPageFromResponse(
        response: BrowseResponse,
        playlistId: String? = null,
    ): PlaylistContinuationPage {
        val appendedContents =
            response.onResponseReceivedActions
                ?.firstOrNull()
                ?.appendContinuationItemsAction
                ?.continuationItems
                .orEmpty()

        val candidates =
            listOf(
                PlaylistContinuationCandidate(
                    contents =
                        buildList {
                            response.continuationContents
                                ?.sectionListContinuation
                                ?.contents
                                .orEmpty()
                                .forEach { sectionContent ->
                                    addAll(sectionContent.playlistSongContents())
                                }
                            addAll(appendedContents)
                        },
                    continuation =
                        response.continuationContents
                            ?.sectionListContinuation
                            ?.continuations
                            ?.getContinuation()
                            ?: appendedContents.getContinuation(),
                ),
                PlaylistContinuationCandidate(
                    contents =
                        response.continuationContents
                            ?.musicPlaylistShelfContinuation
                            ?.contents
                            .orEmpty(),
                    continuation =
                        response.continuationContents
                            ?.musicPlaylistShelfContinuation
                            ?.continuations
                            ?.getContinuation(),
                ),
                PlaylistContinuationCandidate(
                    contents =
                        response.continuationContents
                            ?.musicShelfContinuation
                            ?.contents
                            .orEmpty(),
                    continuation =
                        response.continuationContents
                            ?.musicShelfContinuation
                            ?.continuations
                            ?.getContinuation(),
                ),
                PlaylistContinuationCandidate(
                    contents = appendedContents,
                    continuation = appendedContents.getContinuation(),
                ),
            ).map { candidate ->
                candidate.copy(
                    songs =
                        candidate.contents
                            .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                            .mapNotNull { renderer ->
                                PlaylistPage.fromMusicResponsiveListItemRenderer(renderer, playlistId)
                            },
                )
            }

        val selectedCandidate =
            candidates.firstOrNull { it.songs.isNotEmpty() }
                ?: candidates.firstOrNull { it.contents.isNotEmpty() }

        return PlaylistContinuationPage(
            songs = selectedCandidate?.songs.orEmpty(),
            continuation = selectedCandidate?.continuation?.takeUnless(String::isBlank),
        )
    }

    private data class PlaylistContinuationCandidate(
        val contents: List<MusicShelfRenderer.Content>,
        val continuation: String?,
        val songs: List<SongItem> = emptyList(),
    )
}
