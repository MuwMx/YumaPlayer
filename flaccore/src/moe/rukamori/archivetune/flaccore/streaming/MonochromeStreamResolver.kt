/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.flaccore.streaming

import moe.rukamori.archivetune.flaccore.FlacLogger
import moe.rukamori.archivetune.flaccore.model.TrackQuery
import moe.rukamori.archivetune.flaccore.monochrome.MonochromeConfig
import moe.rukamori.archivetune.flaccore.monochrome.MonochromeResolveResult
import moe.rukamori.archivetune.flaccore.monochrome.MonochromeSource

class MonochromeStreamResolver(
    private val source: MonochromeSource,
    private val config: MonochromeConfig,
) {
    private val logger = FlacLogger(TAG)

    suspend fun resolve(query: TrackQuery, requestedQuality: Int): FlacStreamUrl? {
        if (!config.monochromeEnabled()) {
            logger.d("monochrome disabled in config")
            return null
        }
        val result = source.resolve(query)
        return when (result) {
            is MonochromeResolveResult.Found -> {
                val now = System.currentTimeMillis()
                val expiresAtMs = now + DEFAULT_TTL_MS
                logger.d("resolved origin=$ORIGIN expiresInSec=${DEFAULT_TTL_MS / 1000}")
                FlacStreamUrl(
                    url = result.url,
                    expiresAtMs = expiresAtMs,
                    codec = "FLAC",
                    bitsPerSample = result.flacInfo.bitsPerSample,
                    sampleRateHz = result.flacInfo.sampleRateHz,
                    bitrateKbps = null,
                    coverArtUrl = null,
                    origin = ORIGIN,
                )
            }
            is MonochromeResolveResult.NotFound -> {
                logger.d("not found: ${result.reason}")
                null
            }
            is MonochromeResolveResult.Unavailable -> {
                logger.w("unavailable: ${result.reason}")
                null
            }
        }
    }

    companion object {
        const val ORIGIN = "monochrome"
        private const val TAG = "MonochromeResolver"
        private const val DEFAULT_TTL_MS = 30L * 60 * 1000
    }
}
