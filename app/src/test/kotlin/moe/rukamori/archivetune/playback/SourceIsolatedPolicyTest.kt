/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.playback.crossfade.isSourceFullyCached
import moe.rukamori.archivetune.playback.engine.PlayerEngineHolder
import moe.rukamori.archivetune.playback.resolvers.StreamUrl
import moe.rukamori.archivetune.utils.YTPlayerUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.TreeSet

class SourceIsolatedPolicyTest {

    private lateinit var service: MusicService
    private lateinit var downloadCache: Cache
    private lateinit var playerCache: Cache
    private lateinit var database: MusicDatabase
    private lateinit var engineHolder: PlayerEngineHolder

    private val mediaId = "trackABC"
    private val flacKey = flacCacheKey(mediaId)

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val str = firstArg<String>()
            val uri = mockk<Uri>(relaxed = true)
            every { uri.toString() } returns str
            every { uri.scheme } returns "https"
            uri
        }

        service = mockk(relaxed = true)
        downloadCache = mockk(relaxed = true)
        playerCache = mockk(relaxed = true)
        database = mockk(relaxed = true)
        engineHolder = PlayerEngineHolder()

        every { service.downloadCache } returns downloadCache
        every { service.playerCache } returns playerCache
        every { service.database } returns database
        every { service.contentLengthCache } returns engineHolder.contentLengthCache
        every { service.playbackUrlCache } returns engineHolder.playbackUrlCache
        every { service.losslessUrlCache } returns engineHolder.losslessUrlCache
        every { service.enableMemoryCache } returns true
        every { service.scope } returns kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun createPlaybackData(contentLength: Long, bitrate: Int): YTPlayerUtils.PlaybackData {
        val format = mockk<PlayerResponse.StreamingData.Format>(relaxed = true) {
            every { this@mockk.contentLength } returns contentLength
            every { this@mockk.itag } returns 140
            every { this@mockk.mimeType } returns "audio/mp4; codecs=\"mp4a.40.2\""
            every { this@mockk.bitrate } returns bitrate
            every { this@mockk.audioSampleRate } returns 44100
        }
        return YTPlayerUtils.PlaybackData(
            audioConfig = null, videoDetails = null, playbackTracking = null,
            format = format, streamUrl = "https://yt.example/stream.mp4",
            streamExpiresInSeconds = 3600, authFingerprint = "auth_fp",
        )
    }

    @Test
    fun testNewWritesUseVersionedKeys() {
        val flacStream = StreamUrl("https://qobuz.example/song.flac", System.currentTimeMillis() + 600_000L, origin = "qobuz")
        val baseSpec = DataSpec.Builder().setUri("https://example.com/item".toUri()).setKey(mediaId).build()

        val flacSpec = service.buildResolvedFlacDataSpec(
            dataSpec = baseSpec,
            mediaId = mediaId,
            streamUrl = flacStream,
        )
        assertEquals(flacStreamCacheKey(mediaId), flacSpec.key)

        val ytData = createPlaybackData(contentLength = 4_000_000L, bitrate = 128_000)
        val ytSpec = service.persistPlaybackFormat(
            dataSpec = baseSpec,
            mediaId = mediaId,
            networkCacheKey = "${mediaId}_YT_MUSIC",
            knownContentLength = null,
            playbackData = ytData,
        )
        assertEquals(ytStreamCacheKey(mediaId), ytSpec.key)
    }

    @Test
    fun testFlacYtFlacKeepsIndependentUrlContentLengthFormatEntityIds() {
        val savedFormats = mutableListOf<FormatEntity>()
        every { database.query(any<MusicDatabase.() -> Unit>()) } answers {
            val block = firstArg<MusicDatabase.() -> Unit>()
            val dbScope = mockk<MusicDatabase>(relaxed = true)
            every { dbScope.upsert(any<FormatEntity>()) } answers {
                savedFormats.add(firstArg())
            }
            dbScope.block()
        }

        val flacStream = StreamUrl("https://qobuz.example/song.flac", System.currentTimeMillis() + 600_000L, origin = "qobuz")
        val baseSpec = DataSpec.Builder().setUri("https://example.com/item".toUri()).setKey(mediaId).build()

        val flacSpec = service.buildResolvedFlacDataSpec(
            dataSpec = baseSpec,
            mediaId = mediaId,
            streamUrl = flacStream,
        )
        assertEquals(flacStreamCacheKey(mediaId), flacSpec.key)
        val savedFlac = savedFormats.lastOrNull { it.id.endsWith(PlaybackSource.FLAC.name) }
        assertNotNull(savedFlac)
        assertEquals("${mediaId}_FLAC", savedFlac?.id)

        engineHolder.losslessUrlCache.put("${mediaId}_FLAC", flacStream)

        val ytData = createPlaybackData(contentLength = 4_500_000L, bitrate = 128_000)
        val ytSpec = service.persistPlaybackFormat(
            dataSpec = baseSpec,
            mediaId = mediaId,
            networkCacheKey = "${mediaId}_YT_MUSIC",
            knownContentLength = null,
            playbackData = ytData,
        )
        assertEquals(ytStreamCacheKey(mediaId), ytSpec.key)
        val savedYt = savedFormats.lastOrNull { it.id.endsWith(PlaybackSource.YT_MUSIC.name) }
        assertNotNull(savedYt)
        assertEquals("${mediaId}_YT_MUSIC", savedYt?.id)

        val flacResolved = service.resolveFromMemoryUrlPolicy(
            dataSpec = baseSpec,
            mediaId = mediaId,
            authFingerprint = "auth_fp",
            effectiveSource = PlaybackSource.FLAC,
            knownContentLength = null,
            storedFormat = savedFlac,
        )
        assertNotNull(flacResolved)
        assertEquals(flacStreamCacheKey(mediaId), flacResolved?.key)

        val ytResolved = service.resolveFromMemoryUrlPolicy(
            dataSpec = baseSpec,
            mediaId = mediaId,
            authFingerprint = "auth_fp",
            effectiveSource = PlaybackSource.YT_MUSIC,
            knownContentLength = 4_500_000L,
            storedFormat = savedYt,
        )
        assertNotNull(ytResolved)
        assertEquals(ytStreamCacheKey(mediaId), ytResolved?.key)
    }

    @Test
    fun testNoCrossCodecCopyOrMirroring() {
        val ytData = createPlaybackData(contentLength = 5_000_000L, bitrate = 128_000)
        val baseSpec = DataSpec.Builder().setUri("https://example.com/item".toUri()).setKey(mediaId).build()

        service.persistPlaybackFormat(
            dataSpec = baseSpec,
            mediaId = mediaId,
            networkCacheKey = "${mediaId}_YT_MUSIC",
            knownContentLength = null,
            playbackData = ytData,
        )

        assertEquals(5_000_000L, engineHolder.contentLengthCache[ytStreamCacheKey(mediaId)])
        assertNull(engineHolder.contentLengthCache[mediaId])
        assertNull(engineHolder.contentLengthCache[flacStreamCacheKey(mediaId)])

        val ytFormat = FormatEntity(
            id = "${mediaId}_YT_MUSIC",
            itag = 140,
            mimeType = "audio/mp4",
            codecs = "mp4a.40.2",
            bitrate = 128_000,
            sampleRate = 44100,
            contentLength = 5_000_000L,
            loudnessDb = null,
            playbackUrl = null,
        )
        val resolvedFlacLength = service.getOrResolveContentLength(flacStreamCacheKey(mediaId), mediaId, storedFormat = ytFormat)
        assertNull(resolvedFlacLength)

        val legacyFlacFormat = ytFormat.copy(
            id = mediaId,
            itag = 0,
            mimeType = "audio/flac",
            codecs = "flac",
            contentLength = 30_000_000L,
        )
        val resolvedYtLength = service.getOrResolveContentLength(mediaId, mediaId, storedFormat = legacyFlacFormat)
        assertNull(resolvedYtLength)
    }

    @Test
    fun testValidatedLegacySpanContinuesUnderLegacyKey() {
        val flacHeader = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte())
        val tempFile = File.createTempFile("legacy_flac_span", ".bin").apply {
            deleteOnExit()
            writeBytes(flacHeader)
        }

        val span = CacheSpan(flacKey, 0L, 4L, androidx.media3.common.C.TIME_UNSET, tempFile)
        every { downloadCache.getCachedSpans(flacStreamCacheKey(mediaId)) } returns TreeSet()
        every { playerCache.getCachedSpans(flacStreamCacheKey(mediaId)) } returns TreeSet()
        every { downloadCache.getCachedSpans(flacKey) } returns TreeSet(listOf(span))
        every { playerCache.getCachedSpans(flacKey) } returns TreeSet()

        val targetKey = service.resolveTargetDataKey(mediaId, PlaybackSource.FLAC)
        assertEquals(flacKey, targetKey)
    }

    @Test
    fun testSourceSpecificPrefetchNoSuppression() {
        val v2Yt = ytStreamCacheKey(mediaId)
        val v2Flac = flacStreamCacheKey(mediaId)

        every { downloadCache.getContentMetadata(v2Yt) } returns mockk { every { get(any<String>(), any<Long>()) } returns 20_000L }
        every { downloadCache.isCached(v2Yt, 0L, 20_000L) } returns true
        every { downloadCache.getCachedSpans(v2Yt) } returns TreeSet(listOf(CacheSpan(v2Yt, 0L, 20_000L, androidx.media3.common.C.TIME_UNSET, null)))

        every { downloadCache.getCachedSpans(v2Flac) } returns TreeSet()
        every { playerCache.getCachedSpans(v2Flac) } returns TreeSet()
        every { downloadCache.getCachedSpans(flacKey) } returns TreeSet()
        every { playerCache.getCachedSpans(flacKey) } returns TreeSet()

        assertTrue(service.isSourceFullyCached(mediaId, PlaybackSource.YT_MUSIC))
        assertFalse(service.isSourceFullyCached(mediaId, PlaybackSource.FLAC))
    }
}
