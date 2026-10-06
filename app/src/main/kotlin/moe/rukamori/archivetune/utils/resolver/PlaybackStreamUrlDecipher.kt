/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils.resolver

import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.innertube.pages.NewPipeUtils
import moe.rukamori.archivetune.utils.StreamClientUtils
import moe.rukamori.archivetune.utils.isJavaScriptPlayerExtractorFailure
import moe.rukamori.archivetune.utils.reportException
import timber.log.Timber

object PlaybackStreamUrlDecipher {
    private const val logTag = "PlaybackStreamUrlDecipher"

    /**
     * Wrapper around the [NewPipeUtils.getSignatureTimestamp] function which reports exceptions
     */
    internal suspend fun getSignatureTimestampOrNull(videoId: String): Int? {
        Timber.tag(logTag).i("Getting signature timestamp for videoId: $videoId")
        return NewPipeUtils
            .getSignatureTimestamp(videoId)
            .onSuccess { Timber.tag(logTag).i("Signature timestamp obtained: $it") }
            .onFailure {
                Timber.tag(logTag).e(it, "Failed to get signature timestamp")
                reportException(it)
            }.getOrNull()
    }

    /**
     * Wrapper around the [NewPipeUtils.getStreamUrl] function which reports exceptions.
     * Also patches cver to match the client version.
     */
    internal suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient? = null,
        authState: PlaybackAuthState,
    ): String? {
        Timber.tag(logTag).i("Finding stream URL for format: ${format.mimeType}, videoId: $videoId")
        var url =
            NewPipeUtils
                .getStreamUrl(format, videoId, client, authState)
                .onSuccess { Timber.tag(logTag).i("Stream URL obtained successfully") }
                .onFailure {
                    if (it.isJavaScriptPlayerExtractorFailure()) {
                        Timber.tag(logTag).w(it, "Skipping stream candidate because YouTube JavaScript decipher failed")
                    } else {
                        Timber.tag(logTag).e(it, "Failed to get stream URL")
                        reportException(it)
                    }
                }.getOrNull() ?: return null

        // Patch cver in the URL to match the client we actually used
        if (client != null) {
            url = StreamClientUtils.patchClientVersion(url, client.clientVersion)
        }

        return url
    }
}
