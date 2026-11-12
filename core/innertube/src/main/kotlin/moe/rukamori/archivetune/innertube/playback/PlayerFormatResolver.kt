/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.playback

import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse.StreamingData
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse.StreamingData.Format

object PlayerFormatResolver {
    fun getAdaptiveAudioFormats(streamingData: StreamingData?): List<Format> =
        streamingData?.adaptiveFormats.orEmpty().filter { it.isAudio }

    fun getAdaptiveVideoFormats(streamingData: StreamingData?): List<Format> =
        streamingData?.adaptiveFormats.orEmpty().filter { !it.isAudio }

    fun findFormatByItag(streamingData: StreamingData?, itag: Int): Format? =
        streamingData?.adaptiveFormats?.firstOrNull { it.itag == itag }
            ?: streamingData?.formats?.firstOrNull { it.itag == itag }

    fun findAudioFormatByItag(streamingData: StreamingData?, itag: Int): Format? =
        streamingData?.adaptiveFormats?.firstOrNull { it.isAudio && it.itag == itag }

    fun parseExpiresInSeconds(streamingData: StreamingData?): Int? =
        streamingData?.expiresInSeconds

    fun parseExpirationTimestampFromUrl(url: String): Long? {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return null
        return query.split('&')
            .firstOrNull { it.startsWith("expire=") }
            ?.substringAfter("expire=")
            ?.toLongOrNull()
    }

    fun parseItagFromUrl(url: String): Int? {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return null
        return query.split('&')
            .firstOrNull { it.startsWith("itag=") }
            ?.substringAfter("itag=")
            ?.toIntOrNull()
    }

    fun isUrlExpired(url: String, clockSkewToleranceSeconds: Long = 60L): Boolean {
        val expireSec = parseExpirationTimestampFromUrl(url) ?: return false
        val currentSec = System.currentTimeMillis() / 1000L
        return currentSec >= (expireSec - clockSkewToleranceSeconds)
    }
}

fun PlayerResponse.audioFormats(): List<Format> =
    PlayerFormatResolver.getAdaptiveAudioFormats(streamingData)

fun PlayerResponse.findAudioFormat(itag: Int): Format? =
    PlayerFormatResolver.findAudioFormatByItag(streamingData, itag)
