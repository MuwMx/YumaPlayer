/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import androidx.media3.datasource.cache.Cache
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.playback.engine.PlayerEngineHolder
import moe.rukamori.archivetune.playback.resolvers.StreamUrl
import moe.rukamori.archivetune.utils.AuthScopedCacheValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SourceIsolatedRecoveryTest {

    private lateinit var engineHolder: PlayerEngineHolder
    private val mediaId = "trackRecovery123"

    @Before
    fun setup() {
        engineHolder = PlayerEngineHolder()
    }

    @Test
    fun testStaleAliasInvalidationSeparation() {
        val ytFormatId = "${mediaId}_${PlaybackSource.YT_MUSIC.name}"
        val flacFormatId = "${mediaId}_${PlaybackSource.FLAC.name}"
        val v2YtKey = ytStreamCacheKey(mediaId)
        val v2FlacKey = flacStreamCacheKey(mediaId)
        val legacyFlacKey = flacCacheKey(mediaId)

        val ytValue = AuthScopedCacheValue("https://yt.example", System.currentTimeMillis() + 100_000L, "auth")
        engineHolder.playbackUrlCache[mediaId] = ytValue
        engineHolder.playbackUrlCache[v2YtKey] = ytValue
        engineHolder.playbackUrlCache[v2FlacKey] = ytValue
        engineHolder.playbackUrlCache[legacyFlacKey] = ytValue
        engineHolder.playbackUrlCache[ytFormatId] = ytValue
        engineHolder.playbackUrlCache[flacFormatId] = ytValue

        val flacValue = StreamUrl("https://flac.example", System.currentTimeMillis() + 100_000L, origin = "qobuz")
        engineHolder.losslessUrlCache.put(mediaId, flacValue)
        engineHolder.losslessUrlCache.put(v2FlacKey, flacValue)
        engineHolder.losslessUrlCache.put(v2YtKey, flacValue)
        engineHolder.losslessUrlCache.put(legacyFlacKey, flacValue)
        engineHolder.losslessUrlCache.put(flacFormatId, flacValue)
        engineHolder.losslessUrlCache.put(ytFormatId, flacValue)

        engineHolder.invalidatePlaybackUrlCache(mediaId)

        assertNull(engineHolder.playbackUrlCache[mediaId])
        assertNull(engineHolder.playbackUrlCache[v2YtKey])
        assertNull(engineHolder.playbackUrlCache[v2FlacKey])
        assertNull(engineHolder.playbackUrlCache[legacyFlacKey])
        assertNull(engineHolder.playbackUrlCache[ytFormatId])
        assertNull(engineHolder.playbackUrlCache[flacFormatId])

        assertEquals(flacValue, engineHolder.losslessUrlCache.get(v2FlacKey))
        assertEquals(flacValue, engineHolder.losslessUrlCache.get(flacFormatId))

        engineHolder.invalidateLosslessUrlCache(mediaId)

        assertNull(engineHolder.losslessUrlCache.get(mediaId))
        assertNull(engineHolder.losslessUrlCache.get(v2FlacKey))
        assertNull(engineHolder.losslessUrlCache.get(v2YtKey))
        assertNull(engineHolder.losslessUrlCache.get(legacyFlacKey))
        assertNull(engineHolder.losslessUrlCache.get(flacFormatId))
        assertNull(engineHolder.losslessUrlCache.get(ytFormatId))
    }

    @Test
    fun testBoundedActualSourceTrackingRetainsCurrentOnNextUpdate() {
        val service = mockk<MusicService>(relaxed = true)
        val sourcesFlow = kotlinx.coroutines.flow.MutableStateFlow<Map<String, PlaybackSource>>(emptyMap())
        every { service.actualPlaybackSources } returns sourcesFlow

        val mediaId1 = "trackCurrent"
        val mediaId2 = "trackNext"

        fun setSource(id: String, source: PlaybackSource) {
            val updated = LinkedHashMap(sourcesFlow.value)
            updated.remove(id)
            updated[id] = source
            while (updated.size > 32) {
                val oldest = updated.keys.firstOrNull() ?: break
                updated.remove(oldest)
            }
            sourcesFlow.value = updated
        }

        setSource(mediaId1, PlaybackSource.FLAC)
        assertEquals(PlaybackSource.FLAC, sourcesFlow.value[mediaId1])

        setSource(mediaId2, PlaybackSource.YT_MUSIC)
        assertEquals(PlaybackSource.FLAC, sourcesFlow.value[mediaId1])
        assertEquals(PlaybackSource.YT_MUSIC, sourcesFlow.value[mediaId2])

        val updatedAfterClear = LinkedHashMap(sourcesFlow.value).apply { remove(mediaId1) }
        sourcesFlow.value = updatedAfterClear
        assertNull(sourcesFlow.value[mediaId1])
        assertEquals(PlaybackSource.YT_MUSIC, sourcesFlow.value[mediaId2])

        for (i in 1..40) {
            setSource("bulk_$i", PlaybackSource.YT_MUSIC)
        }
        assertTrue(sourcesFlow.value.size <= 32)
    }

    @Test
    fun testRecoveryPurgeIsolationPlan() {
        val pCache = mockk<Cache>(relaxed = true)
        val dCache = mockk<Cache>(relaxed = true)

        val v2Flac = flacStreamCacheKey(mediaId)
        val legacyFlac = flacCacheKey(mediaId)
        val v2Yt = ytStreamCacheKey(mediaId)

        fun purgeVersionedFlacPlayerCache() {
            pCache.removeResource(v2Flac)
        }

        purgeVersionedFlacPlayerCache()

        verify { pCache.removeResource(v2Flac) }
        verify(exactly = 0) { pCache.removeResource(legacyFlac) }
        verify(exactly = 0) { dCache.removeResource(v2Flac) }
        verify(exactly = 0) { dCache.removeResource(legacyFlac) }
        verify(exactly = 0) { pCache.removeResource(v2Yt) }
        verify(exactly = 0) { dCache.removeResource(mediaId) }
    }
}
