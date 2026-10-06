/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils.resolver

import android.net.ConnectivityManager
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.utils.StreamClientUtils
import timber.log.Timber

object PlaybackFormatSelector {
    private const val logTag = "PlaybackFormatSelector"

    fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        // optional override from user preference; if non-null, use this instead of ConnectivityManager
        networkMetered: Boolean? = null,
    ): PlayerResponse.StreamingData.Format? {
        val isMetered = networkMetered ?: connectivityManager.isActiveNetworkMetered
        return selectAudioFormatCandidates(
            playerResponse,
            audioQuality,
            isMetered,
        ).firstOrNull()
    }

    fun selectAudioFormatCandidates(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        networkMetered: Boolean,
    ): List<PlayerResponse.StreamingData.Format> {
        Timber.tag(logTag).i("Finding format with audioQuality: $audioQuality, network metered: $networkMetered")

        val audioFormats =
            playerResponse.streamingData
                ?.adaptiveFormats
                ?.asSequence()
                ?.filter { it.isAudio && it.bitrate > 0 }
                ?.filter { it.url != null || it.signatureCipher != null || it.cipher != null }
                ?.toList()
                .orEmpty()

        if (audioFormats.isEmpty()) return emptyList()

        val effectiveQuality =
            when (audioQuality) {
                AudioQuality.AUTO -> if (networkMetered) AudioQuality.HIGH else AudioQuality.HIGHEST
                else -> audioQuality
            }

        val targetBitrateBps =
            when (effectiveQuality) {
                AudioQuality.LOW -> 70_000
                AudioQuality.HIGH -> 160_000
                AudioQuality.HIGHEST -> 320_000
                AudioQuality.AUTO -> null
            }

        val preferHigher =
            compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
                .thenByDescending { it.bitrate }
                .thenByDescending { codecRank(extractCodec(it.mimeType)) }
                .thenByDescending { it.audioSampleRate ?: 0 }

        val preferLowerAboveTarget =
            compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
                .thenBy { it.bitrate }
                .thenByDescending { codecRank(extractCodec(it.mimeType)) }
                .thenByDescending { it.audioSampleRate ?: 0 }

        val candidates =
            when {
                targetBitrateBps == null || effectiveQuality == AudioQuality.HIGHEST -> {
                    audioFormats.sortedWith(preferHigher)
                }

                else -> {
                    val preferred =
                        audioFormats
                            .filter { it.bitrate <= targetBitrateBps }
                            .sortedWith(preferHigher)
                    val fallback =
                        audioFormats
                            .filter { it.bitrate > targetBitrateBps }
                            .sortedWith(preferLowerAboveTarget)

                    preferred + fallback
                }
            }

        Timber
            .tag(logTag)
            .v(
                "Available audio formats: ${
                    candidates.take(12).map {
                        val codec = extractCodec(it.mimeType)
                        val direct = if (it.url != null) "direct" else "cipher"
                        "${it.mimeType} ($direct, codec=${codec ?: "unknown"}) @ ${it.bitrate}bps"
                    }
                }",
            )

        return candidates
    }

    internal fun extractCodec(mimeType: String): String? {
        val match = Regex("""codecs="([^"]+)"""").find(mimeType) ?: return null
        return match.groupValues
            .getOrNull(1)
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
    }

    internal fun isCipheredFormat(format: PlayerResponse.StreamingData.Format): Boolean =
        format.url == null && (format.signatureCipher != null || format.cipher != null)

    internal fun shouldSkipCipheredWebCandidate(
        client: YouTubeClient,
        format: PlayerResponse.StreamingData.Format,
        authState: PlaybackAuthState,
    ): Boolean {
        val isWebClient = StreamClientUtils.isWebClient(client.clientName)
        val isCiphered = isCipheredFormat(format)
        val hasGvsPoToken = !authState.resolveGvsPoToken(client).isNullOrBlank()
        if (
            !PlaybackAuthCoordinator.shouldSkipCipheredWebPlaybackCandidate(
                webClientPoTokenEnabled = authState.webClientPoTokenEnabled,
                isWebClient = isWebClient,
                isCiphered = isCiphered,
                hasGvsPoToken = hasGvsPoToken,
            )
        ) {
            return false
        }

        Timber.tag(logTag).w(
            "Skipping ciphered %s stream candidate because Web PoToken playback is enabled but no GVS token is available",
            client.clientName,
        )
        return true
    }

    internal fun codecRank(codec: String?): Int =
        when {
            codec.isNullOrBlank() -> 0
            codec.contains("opus", ignoreCase = true) -> 3
            codec.contains("mp4a", ignoreCase = true) -> 2
            else -> 1
        }

    internal fun isLikelyPreview(
        format: PlayerResponse.StreamingData.Format,
        expectedDurationMs: Long,
    ): Boolean {
        val approx = format.approxDurationMs?.toLongOrNull() ?: return false
        if (expectedDurationMs < 90_000L) return false
        return approx in 1L..(minOf(90_000L, (expectedDurationMs * 9L) / 10L))
    }
}
