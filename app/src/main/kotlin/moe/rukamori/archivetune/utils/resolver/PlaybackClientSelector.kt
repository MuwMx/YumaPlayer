/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils.resolver

import moe.rukamori.archivetune.constants.PlayerStreamClient
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.ANDROID_MUSIC
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.ANDROID_TESTSUITE
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.ANDROID_UNPLUGGED
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.IOS
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.IOS_MUSIC
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.IPADOS
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.MOBILE
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.TVHTML5
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.VISIONOS
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.utils.StreamClientUtils
import moe.rukamori.archivetune.utils.StreamUrlCache

object PlaybackClientSelector {
    /**
     * The main client is used for metadata and initial streams.
     * Do not use other clients for this because it can result in inconsistent metadata.
     * For example other clients can have different normalization targets (loudnessDb).
     *
     * [moe.rukamori.archivetune.innertube.models.YouTubeClient.WEB_REMIX] should be preferred here because currently it is the only client which provides:
     * - the correct metadata (like loudnessDb)
     * - premium formats
     */
    val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    /**
     * Clients used for fallback streams in case the streams of the main client do not work.
     */
    val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> =
        arrayOf(
            IOS,
            MOBILE,
            ANDROID_MUSIC,
            IOS_MUSIC,
            ANDROID_VR_NO_AUTH,
            ANDROID_VR_1_61_48,
            ANDROID_VR_1_43_32,
            ANDROID_CREATOR,
            ANDROID_TESTSUITE,
            ANDROID_UNPLUGGED,
            IPADOS,
            VISIONOS,
            TVHTML5,
            TVHTML5_SIMPLY_EMBEDDED_PLAYER,
            WEB,
            WEB_CREATOR,
            WEB_REMIX,
        )

    internal val downloadPreferredStreamClientAttempts: List<PlayerStreamClient> =
        buildList {
            add(PlayerStreamClient.WEB_REMIX)
            addAll(
                PlayerStreamClient
                    .values()
                    .filterNot {
                        it == PlayerStreamClient.WEB_REMIX ||
                            it == PlayerStreamClient.ANDROID_VR ||
                            it == PlayerStreamClient.ARCHIVETUNE_EXTRACTOR
                    },
            )
            add(PlayerStreamClient.ANDROID_VR)
        }.distinct()

    fun resolvePreferredPlaybackClient(
        preferredStreamClient: PlayerStreamClient,
        authState: PlaybackAuthState,
    ): YouTubeClient {
        if (
            PlaybackAuthCoordinator.shouldPreferWebRemixForLoggedInPlayback(
                preferredStreamClient = preferredStreamClient,
                isLoggedIn = authState.hasPlaybackLoginContext,
                webClientPoTokenEnabled = authState.webClientPoTokenEnabled,
                hasPlayerPoToken = !authState.resolvePlayerPoToken(WEB_REMIX).isNullOrBlank(),
                hasGvsPoToken = !authState.resolveGvsPoToken(WEB_REMIX).isNullOrBlank(),
            )
        ) {
            return WEB_REMIX
        }

        return when (preferredStreamClient) {
            PlayerStreamClient.ANDROID_VR -> {
                if (authState.hasPlaybackLoginContext) ANDROID_MUSIC else ANDROID_VR_NO_AUTH
            }

            PlayerStreamClient.WEB_REMIX -> {
                WEB_REMIX
            }

            PlayerStreamClient.ARCHIVETUNE_EXTRACTOR -> {
                if (authState.hasPlaybackLoginContext) ANDROID_MUSIC else ANDROID_VR_NO_AUTH
            }

            PlayerStreamClient.HI_RES_LOSSLESS -> {
                WEB_REMIX
            }

            PlayerStreamClient.IOS -> {
                IOS
            }

            PlayerStreamClient.TVHTML5 -> {
                TVHTML5
            }

            PlayerStreamClient.ANDROID_MUSIC -> {
                ANDROID_MUSIC
            }
        }
    }

    fun buildStreamClientOrder(
        preferredStreamClient: PlayerStreamClient,
        authState: PlaybackAuthState,
    ): List<YouTubeClient> {
        val preferredYouTubeClient = resolvePreferredPlaybackClient(preferredStreamClient, authState)
        val lastSuccessfulClient =
            StreamUrlCache.lastSuccessfulClientKey?.let { key ->
                STREAM_FALLBACK_CLIENTS.find { StreamClientUtils.buildClientKey(it) == key }
            }

        val orderedFallbackClients =
            if (authState.hasPlaybackLoginContext) {
                STREAM_FALLBACK_CLIENTS.filter { it.loginSupported } + STREAM_FALLBACK_CLIENTS.filterNot { it.loginSupported }
            } else {
                STREAM_FALLBACK_CLIENTS.toList()
            }

        return buildList {
            lastSuccessfulClient?.let { add(it) }
            if (authState.hasPlaybackLoginContext && PlaybackAuthCoordinator.hasCompleteWebPlaybackPoToken(authState)) {
                add(WEB_REMIX)
            }
            add(preferredYouTubeClient)
            addAll(orderedFallbackClients)
            if (preferredYouTubeClient != MAIN_CLIENT) add(MAIN_CLIENT)
            if (preferredStreamClient == PlayerStreamClient.WEB_REMIX) {
                addAll(STREAM_FALLBACK_CLIENTS)
            }
        }.distinct()
    }

    internal fun describeClient(client: YouTubeClient): String = "${client.clientName}@${client.clientVersion}"
}
