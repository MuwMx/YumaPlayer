/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.db.entities.FormatEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.NavigableSet
import java.util.TreeSet

class SourceIsolatedValidatorTest {

    private val mediaId = "testValidatorSong"
    private val v2FlacKey = flacStreamCacheKey(mediaId)
    private val v2YtKey = ytStreamCacheKey(mediaId)
    private val legacyFlacKey = flacCacheKey(mediaId)

    @Before
    fun setup() {
        mockkStatic(android.net.Uri::class)
        every { android.net.Uri.parse(any()) } answers {
            val str = firstArg<String>()
            val uri = mockk<android.net.Uri>(relaxed = true)
            every { uri.toString() } returns str
            every { uri.scheme } returns "https"
            uri
        }
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun testCentralizedKeyGeneration() {
        assertEquals("stream_v2_YT_MUSIC_$mediaId", ytStreamCacheKey(mediaId))
        assertEquals("stream_v2_FLAC_$mediaId", flacStreamCacheKey(mediaId))
        assertEquals("flac_$mediaId", flacCacheKey(mediaId))

        assertEquals(v2YtKey, streamCacheKey(mediaId, PlaybackSource.YT_MUSIC))
        assertEquals(v2FlacKey, streamCacheKey(mediaId, PlaybackSource.FLAC))

        assertEquals(mediaId, extractMediaIdFromCacheKey(v2YtKey))
        assertEquals(mediaId, extractMediaIdFromCacheKey(v2FlacKey))
        assertEquals(mediaId, extractMediaIdFromCacheKey(legacyFlacKey))
        assertEquals(mediaId, extractMediaIdFromCacheKey(mediaId))
    }

    @Test
    fun testLegacyHeaderSignatureAcceptanceAndRejection() {
        val flacBytes = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte(), 0, 0, 0, 0)
        assertTrue(validateLegacyHeader(flacBytes, PlaybackSource.FLAC))
        assertFalse(validateLegacyHeader(flacBytes, PlaybackSource.YT_MUSIC))

        val ebmlBytes = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte())
        assertTrue(validateLegacyHeader(ebmlBytes, PlaybackSource.YT_MUSIC))
        assertFalse(validateLegacyHeader(ebmlBytes, PlaybackSource.FLAC))

        val oggBytes = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte())
        assertTrue(validateLegacyHeader(oggBytes, PlaybackSource.YT_MUSIC))
        assertFalse(validateLegacyHeader(oggBytes, PlaybackSource.FLAC))

        val mp4Bytes = byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
        assertTrue(validateLegacyHeader(mp4Bytes, PlaybackSource.YT_MUSIC))
        assertFalse(validateLegacyHeader(mp4Bytes, PlaybackSource.FLAC))

        val garbageBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        assertFalse(validateLegacyHeader(garbageBytes, PlaybackSource.FLAC))
        assertFalse(validateLegacyHeader(garbageBytes, PlaybackSource.YT_MUSIC))
    }

    @Test
    fun testNestedCachesStrictValidation() {
        val flacFile = File.createTempFile("valid_flac", ".bin").apply {
            deleteOnExit()
            writeBytes(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))
        }
        val corruptFile = File.createTempFile("corrupt_flac", ".bin").apply {
            deleteOnExit()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        fun createSpans(key: String, file: File): NavigableSet<CacheSpan> {
            val set = TreeSet<CacheSpan>()
            set.add(CacheSpan(key, 0L, 4L, androidx.media3.common.C.TIME_UNSET, file))
            return set
        }

        val dCache = mockk<Cache>(relaxed = true)
        val pCache = mockk<Cache>(relaxed = true)

        every { dCache.getCachedSpans(legacyFlacKey) } returns TreeSet()
        every { pCache.getCachedSpans(legacyFlacKey) } returns TreeSet()
        assertFalse(validateLegacyCacheKeyAcrossCaches(dCache, pCache, legacyFlacKey, PlaybackSource.FLAC))

        every { pCache.getCachedSpans(legacyFlacKey) } returns createSpans(legacyFlacKey, flacFile)
        assertTrue(validateLegacyCacheKeyAcrossCaches(dCache, pCache, legacyFlacKey, PlaybackSource.FLAC))

        every { dCache.getCachedSpans(legacyFlacKey) } returns createSpans(legacyFlacKey, corruptFile)
        assertFalse(validateLegacyCacheKeyAcrossCaches(dCache, pCache, legacyFlacKey, PlaybackSource.FLAC))

        every { pCache.getCachedSpans(legacyFlacKey) } returns TreeSet()
        assertFalse(validateLegacyCacheKeyAcrossCaches(dCache, pCache, legacyFlacKey, PlaybackSource.FLAC))

        every { dCache.getCachedSpans(legacyFlacKey) } returns createSpans(legacyFlacKey, flacFile)
        assertTrue(validateLegacyCacheKeyAcrossCaches(dCache, pCache, legacyFlacKey, PlaybackSource.FLAC))
    }

    @Test
    fun testUnverifiableLegacyPreservedAsMiss() {
        val tempFile = File.createTempFile("corrupt_span", ".bin").apply {
            deleteOnExit()
            writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6))
        }
        val span = CacheSpan(legacyFlacKey, 0L, 6L, androidx.media3.common.C.TIME_UNSET, tempFile)
        val dCache = mockk<Cache>(relaxed = true)
        every { dCache.getCachedSpans(legacyFlacKey) } returns TreeSet(listOf(span))

        assertFalse(validateLegacyCacheKey(dCache, legacyFlacKey, PlaybackSource.FLAC))
        assertTrue(tempFile.exists())
    }

    @Test
    fun testLegacyFormatEntityFallbackOnlyWhenCodecMatchesRequestedSource() {
        val legacyFlac = FormatEntity(
            id = mediaId,
            itag = 0,
            mimeType = "audio/flac",
            codecs = "flac",
            bitrate = 1411_000,
            sampleRate = 44100,
            contentLength = 30_000_000L,
            loudnessDb = null,
            playbackUrl = null,
        )
        assertTrue(legacyFlac.matchesSource(PlaybackSource.FLAC))
        assertFalse(legacyFlac.matchesSource(PlaybackSource.YT_MUSIC))

        val legacyYt = FormatEntity(
            id = mediaId,
            itag = 140,
            mimeType = "audio/mp4",
            codecs = "mp4a.40.2",
            bitrate = 128_000,
            sampleRate = 44100,
            contentLength = 4_000_000L,
            loudnessDb = null,
            playbackUrl = null,
        )
        assertTrue(legacyYt.matchesSource(PlaybackSource.YT_MUSIC))
        assertFalse(legacyYt.matchesSource(PlaybackSource.FLAC))
    }

    @Test
    fun testExplicitDownloadManagerLegacyKeyCompatibility() {
        assertEquals(mediaId, legacyDataKey(mediaId, PlaybackSource.YT_MUSIC))
        val ytRequest = DataSpec.Builder().setUri("https://yt.example/audio".toUri()).setKey(mediaId).build()
        assertEquals(mediaId, ytRequest.key)
    }
}
