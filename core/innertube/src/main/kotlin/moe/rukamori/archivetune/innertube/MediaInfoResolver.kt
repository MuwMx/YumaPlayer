/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube

import io.ktor.client.call.body
import moe.rukamori.archivetune.innertube.models.MediaInfo
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.NextResponse

object MediaInfoResolver {
    suspend fun getMediaInfo(
        innerTube: InnerTube,
        videoId: String,
    ): Result<MediaInfo> = runCatching {
        val response =
            innerTube
                .next(
                    client = YouTubeClient.WEB,
                    videoId = videoId,
                    playlistId = null,
                    playlistSetVideoId = null,
                    index = null,
                    params = null,
                    continuation = null,
                ).body<NextResponse>()

        val baseForInfo =
            response.contents.twoColumnWatchNextResults
                ?.results
                ?.results
                ?.content
                ?.find {
                    it?.videoSecondaryInfoRenderer != null
                }?.videoSecondaryInfoRenderer

        val baseForTitle =
            response.contents.twoColumnWatchNextResults
                ?.results
                ?.results
                ?.content
                ?.find {
                    it?.videoPrimaryInfoRenderer != null
                }?.videoPrimaryInfoRenderer

        val returnYouTubeDislikeResponse = DislikeClient.getDislikes(innerTube.http, videoId)

        MediaInfo(
            videoId = videoId,
            title =
                baseForTitle
                    ?.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text,
            author =
                baseForInfo
                    ?.owner
                    ?.videoOwnerRenderer
                    ?.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text,
            authorId =
                baseForInfo
                    ?.owner
                    ?.videoOwnerRenderer
                    ?.navigationEndpoint
                    ?.browseEndpoint
                    ?.browseId,
            authorThumbnail =
                baseForInfo
                    ?.owner
                    ?.videoOwnerRenderer
                    ?.thumbnail
                    ?.thumbnails
                    ?.find {
                        it.height == 48
                    }?.url
                    ?.replace("s48", "s960"),
            description = baseForInfo?.attributedDescription?.content,
            subscribers =
                baseForInfo
                    ?.owner
                    ?.videoOwnerRenderer
                    ?.subscriberCountText
                    ?.simpleText
                    ?.split(" ")
                    ?.firstOrNull(),
            uploadDate = baseForTitle?.dateText?.simpleText,
            viewCount = returnYouTubeDislikeResponse.viewCount,
            like = returnYouTubeDislikeResponse.likes,
            dislike = returnYouTubeDislikeResponse.dislikes,
        )
    }
}
