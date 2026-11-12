/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.playback

import moe.rukamori.archivetune.innertube.models.response.PlayerResponse.StreamingData
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse.StreamingData.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerFormatResolverTest {
    private fun createFormat(
        itag: Int,
        isAudio: Boolean,
        bitrate: Int = 128000,
        url: String? = null,
    ) = Format(
        itag = itag,
        url = url,
        mimeType = if (isAudio) "audio/mp4" else "video/mp4",
        bitrate = bitrate,
        width = if (isAudio) null else 1920,
        height = if (isAudio) null else 1080,
        contentLength = 1024L,
        quality = "medium",
        fps = if (isAudio) null else 30,
        qualityLabel = if (isAudio) null else "1080p",
        averageBitrate = bitrate,
        audioQuality = if (isAudio) "AUDIO_QUALITY_MEDIUM" else null,
        approxDurationMs = "1000",
        audioSampleRate = if (isAudio) 44100 else null,
        audioChannels = if (isAudio) 2 else null,
        loudnessDb = null,
        lastModified = null,
        signatureCipher = null,
        cipher = null,
    )

    @Test
    fun getAdaptiveAudioFormats_filtersAudioOnly() {
        val audioFormat = createFormat(140, isAudio = true)
        val videoFormat = createFormat(137, isAudio = false)
        val streamingData = StreamingData(
            formats = emptyList(),
            adaptiveFormats = listOf(audioFormat, videoFormat),
            expiresInSeconds = 21600,
        )

        val audios = PlayerFormatResolver.getAdaptiveAudioFormats(streamingData)
        assertEquals(1, audios.size)
        assertEquals(140, audios.first().itag)

        val videos = PlayerFormatResolver.getAdaptiveVideoFormats(streamingData)
        assertEquals(1, videos.size)
        assertEquals(137, videos.first().itag)
    }

    @Test
    fun findAudioFormatByItag_findsCorrectFormat() {
        val audio140 = createFormat(140, isAudio = true)
        val audio251 = createFormat(251, isAudio = true)
        val streamingData = StreamingData(
            formats = emptyList(),
            adaptiveFormats = listOf(audio140, audio251),
        )

        val found = PlayerFormatResolver.findAudioFormatByItag(streamingData, 251)
        assertNotNull(found)
        assertEquals(251, found?.itag)

        val notFound = PlayerFormatResolver.findAudioFormatByItag(streamingData, 999)
        assertNull(notFound)
    }

    @Test
    fun parseExpiresInSeconds_readsExpiry() {
        val streamingData = StreamingData(
            formats = emptyList(),
            adaptiveFormats = emptyList(),
            expiresInSeconds = 3600,
        )
        assertEquals(3600, PlayerFormatResolver.parseExpiresInSeconds(streamingData))
    }

    @Test
    fun parseExpirationTimestampAndItagFromUrl() {
        val testUrl = "https://rr1---sn.googlevideo.com/videoplayback?expire=1731456000&ei=abc&itag=140&ratebypass=yes"
        assertEquals(1731456000L, PlayerFormatResolver.parseExpirationTimestampFromUrl(testUrl))
        assertEquals(140, PlayerFormatResolver.parseItagFromUrl(testUrl))

        val invalidUrl = "https://example.com/stream"
        assertNull(PlayerFormatResolver.parseExpirationTimestampFromUrl(invalidUrl))
        assertNull(PlayerFormatResolver.parseItagFromUrl(invalidUrl))
    }

    @Test
    fun isUrlExpired_detectsExpiredAndActiveUrls() {
        val farFutureExpire = (System.currentTimeMillis() / 1000L) + 3600
        val activeUrl = "https://googlevideo.com/videoplayback?expire=$farFutureExpire"
        assertFalse(PlayerFormatResolver.isUrlExpired(activeUrl))

        val pastExpire = (System.currentTimeMillis() / 1000L) - 100
        val expiredUrl = "https://googlevideo.com/videoplayback?expire=$pastExpire"
        assertTrue(PlayerFormatResolver.isUrlExpired(expiredUrl))
    }
}
