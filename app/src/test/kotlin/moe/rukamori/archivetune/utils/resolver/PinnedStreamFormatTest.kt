package moe.rukamori.archivetune.utils.resolver

import io.mockk.every
import io.mockk.mockk
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedStreamFormatTest {
    private fun format(itag: Int, mimeType: String, bitrate: Int, contentLength: Long) =
        PlayerResponse.StreamingData.Format(
            itag = itag, url = "https://example.googlevideo.com/videoplayback?itag=$itag", mimeType = mimeType,
            bitrate = bitrate, width = null, height = null, contentLength = contentLength, quality = "tiny",
            fps = null, qualityLabel = null, averageBitrate = null, audioQuality = null, approxDurationMs = null,
            audioSampleRate = 48000, audioChannels = 2, loudnessDb = null, lastModified = null,
            signatureCipher = null, cipher = null,
        )

    private val response =
        mockk<PlayerResponse> {
            every { streamingData } returns PlayerResponse.StreamingData(
                formats = null,
                adaptiveFormats = listOf(
                    format(139, "audio/mp4; codecs=\"mp4a.40.5\"", 52561, 15193771),
                    format(140, "audio/mp4; codecs=\"mp4a.40.2\"", 133215, 40323075),
                    format(249, "audio/webm; codecs=\"opus\"", 72208, 17910464),
                    format(250, "audio/webm; codecs=\"opus\"", 94480, 23647131),
                    format(251, "audio/webm; codecs=\"opus\"", 174632, 45630603),
                ),
            )
        }

    private fun firstItag(quality: AudioQuality, metered: Boolean, pinned: PinnedStreamFormat?) =
        PlaybackFormatSelector.selectAudioFormatCandidates(response, quality, metered, pinned).first().itag

    @Test
    fun autoPicksADifferentFileOnWifiAndOnMobileData() {
        assertEquals(251, firstItag(AudioQuality.AUTO, metered = false, pinned = null))
        assertEquals(140, firstItag(AudioQuality.AUTO, metered = true, pinned = null))
    }

    @Test
    fun aSongWithCachedBytesKeepsTheFileItWasCachedFrom() {
        val cachedOpus = PinnedStreamFormat(itag = 251, contentLength = 45630603)

        assertEquals(251, firstItag(AudioQuality.AUTO, metered = true, pinned = cachedOpus))
        assertEquals(251, firstItag(AudioQuality.HIGH, metered = false, pinned = cachedOpus))
        assertEquals(140, firstItag(AudioQuality.HIGHEST, metered = false, pinned = PinnedStreamFormat(140, 40323075)))
    }

    @Test
    fun aPinIsIgnoredWhenThatFileIsNotOffered() {
        assertEquals(140, firstItag(AudioQuality.HIGH, metered = false, pinned = PinnedStreamFormat(251, 1000)))
        assertEquals(140, firstItag(AudioQuality.HIGH, metered = false, pinned = PinnedStreamFormat(774, 0)))
    }

    @Test
    fun noCandidateIsLostWhenOneIsMovedFirst() {
        val candidates =
            PlaybackFormatSelector.selectAudioFormatCandidates(
                response, AudioQuality.HIGH, false, PinnedStreamFormat(251, 45630603),
            )

        assertEquals(listOf(251, 140, 250, 249, 139), candidates.map { it.itag })
    }
}
