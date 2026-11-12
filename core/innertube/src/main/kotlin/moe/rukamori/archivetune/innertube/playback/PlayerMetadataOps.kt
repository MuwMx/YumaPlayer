/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.playback

import io.ktor.client.call.body
import moe.rukamori.archivetune.innertube.InnerTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.response.BrowseResponse
import moe.rukamori.archivetune.innertube.models.response.GetTranscriptResponse
import moe.rukamori.archivetune.innertube.pages.RelatedPage

internal object PlayerMetadataOps {
    suspend fun lyrics(
        innerTube: InnerTube,
        endpoint: BrowseEndpoint,
    ): Result<String?> = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
        response.contents
            ?.sectionListRenderer
            ?.contents
            ?.firstOrNull()
            ?.musicDescriptionShelfRenderer
            ?.description
            ?.runs
            ?.firstOrNull()
            ?.text
    }

    suspend fun related(
        innerTube: InnerTube,
        endpoint: BrowseEndpoint,
    ): Result<RelatedPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId).body<BrowseResponse>()
        val songs = mutableListOf<SongItem>()
        val albums = mutableListOf<AlbumItem>()
        val artists = mutableListOf<ArtistItem>()
        val playlists = mutableListOf<PlaylistItem>()
        response.contents?.sectionListRenderer?.contents?.forEach { sectionContent ->
            sectionContent.musicCarouselShelfRenderer?.contents?.forEach { content ->
                when (
                    val item =
                        content.musicResponsiveListItemRenderer?.let(RelatedPage.Companion::fromMusicResponsiveListItemRenderer)
                            ?: content.musicTwoRowItemRenderer?.let(RelatedPage.Companion::fromMusicTwoRowItemRenderer)
                ) {
                    is SongItem -> {
                        if (content.musicResponsiveListItemRenderer
                                ?.overlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchEndpoint
                                ?.watchEndpointMusicSupportedConfigs
                                ?.watchEndpointMusicConfig
                                ?.musicVideoType == MUSIC_VIDEO_TYPE_ATV
                        ) {
                            songs.add(item)
                        }
                    }

                    is AlbumItem -> albums.add(item)
                    is ArtistItem -> artists.add(item)
                    is PlaylistItem -> playlists.add(item)
                    null -> {}
                }
            }
        }
        RelatedPage(songs, albums, artists, playlists)
    }

    suspend fun transcript(
        innerTube: InnerTube,
        videoId: String,
    ): Result<String> = runCatching {
        val response = innerTube.getTranscript(WEB, videoId).body<GetTranscriptResponse>()
        response.actions
            ?.firstOrNull()
            ?.updateEngagementPanelAction
            ?.content
            ?.transcriptRenderer
            ?.body
            ?.transcriptBodyRenderer
            ?.cueGroups
            ?.joinToString(separator = "\n") { group ->
                val time =
                    group.transcriptCueGroupRenderer.cues[0]
                        .transcriptCueRenderer.startOffsetMs
                val text =
                    group.transcriptCueGroupRenderer.cues[0]
                        .transcriptCueRenderer.cue.simpleText
                        .trim('♪')
                        .trim(' ')
                "[%02d:%02d.%03d]$text".format(time / 60000, (time / 1000) % 60, time % 1000)
            }!!
    }
}
