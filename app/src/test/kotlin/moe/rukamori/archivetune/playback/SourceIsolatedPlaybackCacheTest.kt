/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.playback.engine.PlayerEngineHolder
import moe.rukamori.archivetune.playback.resolvers.StreamUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.NavigableSet
import java.util.TreeSet

class SourceIsolatedPlaybackCacheTest {

    private lateinit var service: MusicService
    private lateinit var downloadCache: Cache
    private lateinit var playerCache: Cache
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var database: MusicDatabase
    private lateinit var engineHolder: PlayerEngineHolder

    private val mediaId = "testSong123"
    private val v2FlacKey = flacStreamCacheKey(mediaId)
    private val v2YtKey = ytStreamCacheKey(mediaId)
    private val legacyFlacKey = flacCacheKey(mediaId)
    private val legacyYtKey = mediaId

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
        connectivityManager = mockk(relaxed = true)
        database = mockk(relaxed = true)
        engineHolder = PlayerEngineHolder()

        every { service.downloadCache } returns downloadCache
        every { service.playerCache } returns playerCache
        every { service.connectivityManager } returns connectivityManager
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

    private fun mockContentMetadata(length: Long): ContentMetadata {
        val meta = mockk<ContentMetadata>(relaxed = true)
        every { meta.get(ContentMetadata.KEY_CONTENT_LENGTH, -1L) } returns length
        return meta
    }

    private fun createSpans(key: String, position: Long, length: Long): NavigableSet<CacheSpan> {
        val set = TreeSet<CacheSpan>()
        val span = CacheSpan(key, position, length, androidx.media3.common.C.TIME_UNSET, null)
        set.add(span)
        return set
    }

    @Test
    fun testFullSelectedSourceCacheHit() {
        val totalLength = 10_000L
        every { downloadCache.getContentMetadata(v2FlacKey) } returns mockContentMetadata(totalLength)
        every { downloadCache.isCached(v2FlacKey, 0L, totalLength) } returns true
        every { downloadCache.getCachedSpans(v2FlacKey) } returns createSpans(v2FlacKey, 0L, totalLength)
        every { playerCache.getCachedSpans(v2FlacKey) } returns TreeSet()

        val dataSpec = DataSpec.Builder().setUri("https://example.com/audio".toUri()).setKey(v2FlacKey).build()
        val hit = service.resolveFromDiskCache(
            dataSpec = dataSpec,
            mediaId = mediaId,
            targetKey = v2FlacKey,
            storedFormat = null,
        )

        assertNotNull(hit)
        assertEquals(v2FlacKey, hit?.key)
        assertEquals(totalLength, hit?.length)
    }

    @Test
    fun testPartialCacheIsNotCompleteAndRemainsReusable() {
        val totalLength = 10_000L
        val partialLength = 3_000L

        every { playerCache.getContentMetadata(v2FlacKey) } returns mockContentMetadata(totalLength)
        every { playerCache.isCached(v2FlacKey, 0L, totalLength) } returns false
        every { downloadCache.isCached(v2FlacKey, 0L, totalLength) } returns false
        every { playerCache.getCachedSpans(v2FlacKey) } returns createSpans(v2FlacKey, 0L, partialLength)
        every { downloadCache.getCachedSpans(v2FlacKey) } returns TreeSet()

        assertFalse(service.isKeyFullyCached(v2FlacKey, mediaId))

        val resolvedSpec = service.resolveCachedDataSpec(
            dataSpec = DataSpec.Builder().setUri("https://example.com/audio".toUri()).setKey(v2FlacKey).build(),
            cacheKey = v2FlacKey,
            knownContentLength = totalLength,
        )
        assertNull(resolvedSpec)

        val streamUrl = StreamUrl("https://example.com/flac_stream", System.currentTimeMillis() + 60_000L, origin = "qobuz")
        val flacDataSpec = service.buildResolvedFlacDataSpec(
            dataSpec = DataSpec.Builder().setUri("https://example.com/placeholder".toUri()).setKey(mediaId).build(),
            mediaId = mediaId,
            streamUrl = streamUrl,
            explicitKey = v2FlacKey,
        )
        assertEquals(v2FlacKey, flacDataSpec.key)
    }

    @Test
    fun testUnknownContentLengthDoesNotTreatSpanSumAsFullLength() {
        every { downloadCache.getContentMetadata(v2FlacKey) } returns mockContentMetadata(-1L)
        every { playerCache.getContentMetadata(v2FlacKey) } returns mockContentMetadata(-1L)
        every { downloadCache.getCachedSpans(v2FlacKey) } returns createSpans(v2FlacKey, 0L, 5_000L)
        every { playerCache.getCachedSpans(v2FlacKey) } returns TreeSet()

        val resolvedLength = service.getOrResolveContentLength(v2FlacKey, mediaId, storedFormat = null)
        assertNull(resolvedLength)
        assertFalse(service.isKeyFullyCached(v2FlacKey, mediaId))
    }

    @Test
    fun testOnlineSourceSettingDoesNotCrossFallbackBeforeResolver() {
        every { connectivityManager.activeNetwork } returns mockk<Network>()
        every { downloadCache.getContentMetadata(v2YtKey) } returns mockContentMetadata(20_000L)
        every { downloadCache.isCached(v2YtKey, 0L, 20_000L) } returns true
        every { downloadCache.getCachedSpans(v2YtKey) } returns createSpans(v2YtKey, 0L, 20_000L)

        every { downloadCache.getContentMetadata(v2FlacKey) } returns mockContentMetadata(-1L)
        every { downloadCache.getCachedSpans(v2FlacKey) } returns TreeSet()
        every { playerCache.getCachedSpans(v2FlacKey) } returns TreeSet()
        every { downloadCache.getCachedSpans(legacyFlacKey) } returns TreeSet()
        every { playerCache.getCachedSpans(legacyFlacKey) } returns TreeSet()

        val dataSpec = DataSpec.Builder().setUri("https://example.com/audio".toUri()).setKey(v2FlacKey).build()
        val shortCircuit = service.probeDiskCacheShortCircuit(
            dataSpec = dataSpec,
            mediaId = mediaId,
            currentSource = PlaybackSource.FLAC,
            storedFormat = null,
            allowCacheShortCircuit = true,
        )
        assertNull(shortCircuit)
    }

    @Test
    fun testOfflineFallbackUsesOnlyCompleteAlternativeSourceCache() {
        every { connectivityManager.activeNetwork } returns null

        every { downloadCache.getContentMetadata(v2FlacKey) } returns mockContentMetadata(-1L)
        every { downloadCache.getCachedSpans(v2FlacKey) } returns TreeSet()
        every { playerCache.getCachedSpans(v2FlacKey) } returns TreeSet()
        every { downloadCache.getCachedSpans(legacyFlacKey) } returns TreeSet()
        every { playerCache.getCachedSpans(legacyFlacKey) } returns TreeSet()

        every { downloadCache.getContentMetadata(v2YtKey) } returns mockContentMetadata(20_000L)
        every { downloadCache.isCached(v2YtKey, 0L, 20_000L) } returns false
        every { playerCache.isCached(v2YtKey, 0L, 20_000L) } returns false
        every { downloadCache.getCachedSpans(v2YtKey) } returns createSpans(v2YtKey, 0L, 5_000L)
        every { playerCache.getCachedSpans(v2YtKey) } returns TreeSet()
        every { downloadCache.getCachedSpans(legacyYtKey) } returns TreeSet()
        every { playerCache.getCachedSpans(legacyYtKey) } returns TreeSet()

        val dataSpec = DataSpec.Builder().setUri("https://example.com/audio".toUri()).setKey(v2FlacKey).build()
        val partialFallback = service.probeDiskCacheShortCircuit(
            dataSpec = dataSpec,
            mediaId = mediaId,
            currentSource = PlaybackSource.FLAC,
            storedFormat = null,
            allowCacheShortCircuit = true,
        )
        assertNull(partialFallback)

        every { downloadCache.isCached(v2YtKey, 0L, 20_000L) } returns true
        every { downloadCache.getCachedSpans(v2YtKey) } returns createSpans(v2YtKey, 0L, 20_000L)

        val completeFallback = service.probeDiskCacheShortCircuit(
            dataSpec = dataSpec,
            mediaId = mediaId,
            currentSource = PlaybackSource.FLAC,
            storedFormat = null,
            allowCacheShortCircuit = true,
        )
        assertNotNull(completeFallback)
        assertEquals(v2YtKey, completeFallback?.key)
    }

    @Test
    fun testDisconnectedSpansNeverTreatedAsFullyCached() {
        val totalLength = 8_000L
        every { downloadCache.getContentMetadata(v2FlacKey) } returns mockContentMetadata(totalLength)
        every { playerCache.getContentMetadata(v2FlacKey) } returns mockContentMetadata(totalLength)
        every { downloadCache.isCached(v2FlacKey, 0L, totalLength) } returns false
        every { playerCache.isCached(v2FlacKey, 0L, totalLength) } returns false

        val spans = TreeSet<CacheSpan>().apply {
            add(CacheSpan(v2FlacKey, 0L, 3_000L, androidx.media3.common.C.TIME_UNSET, null))
            add(CacheSpan(v2FlacKey, 5_000L, 3_000L, androidx.media3.common.C.TIME_UNSET, null))
        }
        every { downloadCache.getCachedSpans(v2FlacKey) } returns spans
        every { playerCache.getCachedSpans(v2FlacKey) } returns TreeSet()

        assertFalse(service.isKeyFullyCached(v2FlacKey, mediaId))

        val resolved = service.resolveCachedDataSpec(
            dataSpec = DataSpec.Builder().setUri("https://example.com/audio".toUri()).setKey(v2FlacKey).build(),
            cacheKey = v2FlacKey,
            knownContentLength = totalLength,
        )
        assertNull(resolved)
    }
}
