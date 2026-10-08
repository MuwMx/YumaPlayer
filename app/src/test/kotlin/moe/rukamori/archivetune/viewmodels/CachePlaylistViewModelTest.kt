/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.playback.MediaLibrarySessionCallback
import moe.rukamori.archivetune.playback.extractMediaIdFromCacheKey
import moe.rukamori.archivetune.playback.flacCacheKey
import moe.rukamori.archivetune.playback.flacStreamCacheKey
import moe.rukamori.archivetune.playback.ytStreamCacheKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TreeSet

class CachePlaylistViewModelTest {

    private fun createSpan(key: String, position: Long, length: Long): CacheSpan {
        return CacheSpan(key, position, length, androidx.media3.common.C.TIME_UNSET, null)
    }

    @Test
    fun testKeyExtractionForStreamV2AndLegacy() {
        val mediaId = "track_xyz_789"

        assertEquals(mediaId, extractMediaIdFromCacheKey(ytStreamCacheKey(mediaId)))
        assertEquals(mediaId, extractMediaIdFromCacheKey(flacStreamCacheKey(mediaId)))
        assertEquals(mediaId, extractMediaIdFromCacheKey(flacCacheKey(mediaId)))
        assertEquals(mediaId, extractMediaIdFromCacheKey(mediaId))
        assertEquals("", extractMediaIdFromCacheKey("stream_v2_YT_MUSIC_"))
        assertEquals("", extractMediaIdFromCacheKey("stream_v2_FLAC_"))
        assertEquals("", extractMediaIdFromCacheKey("flac_"))
    }

    @Test
    fun testDualSourceDetectionAndByteAccounting() {
        val mediaId = "dualTrack"
        val playerCache = mockk<Cache>(relaxed = true)
        val downloadCache = mockk<Cache>(relaxed = true)

        val ytKey = ytStreamCacheKey(mediaId)
        val flacKey = flacStreamCacheKey(mediaId)

        val ytSpans = TreeSet<CacheSpan>().apply { add(createSpan(ytKey, 0L, 5_000L)) }
        val flacSpans = TreeSet<CacheSpan>().apply { add(createSpan(flacKey, 0L, 20_000L)) }

        every { playerCache.getCachedSpans(ytKey) } returns ytSpans
        every { downloadCache.getCachedSpans(flacKey) } returns flacSpans
        every { playerCache.getCachedSpans(flacKey) } returns TreeSet()
        every { downloadCache.getCachedSpans(ytKey) } returns TreeSet()

        val evaluation = evaluateSongCache(
            mediaId = mediaId,
            playerCache = playerCache,
            downloadCache = downloadCache,
            storedYtLength = 5_000L,
            storedFlacLength = 20_000L,
        )

        assertTrue(evaluation.hasYt)
        assertTrue(evaluation.hasFlac)
        assertEquals("FLAC + Opus", evaluation.source)
        assertEquals(25_000L, evaluation.totalCachedBytes)
        assertTrue(evaluation.isFullyCached)
    }

    @Test
    fun testSingleSourceOpusAndFlacSeparately() {
        val mediaId = "singleTrack"
        val playerCache = mockk<Cache>(relaxed = true)
        val downloadCache = mockk<Cache>(relaxed = true)
        val ytKey = ytStreamCacheKey(mediaId)

        every { playerCache.getCachedSpans(ytKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(ytKey, 0L, 4_000L))
        }

        val ytEval = evaluateSongCache(
            mediaId = mediaId,
            playerCache = playerCache,
            downloadCache = downloadCache,
            storedYtLength = 4_000L,
        )
        assertTrue(ytEval.hasYt)
        assertFalse(ytEval.hasFlac)
        assertEquals("Opus", ytEval.source)
        assertTrue(ytEval.isFullyCached)

        val flacPlayerCache = mockk<Cache>(relaxed = true)
        val flacDownloadCache = mockk<Cache>(relaxed = true)
        val flacKey = flacStreamCacheKey(mediaId)

        every { flacPlayerCache.getCachedSpans(flacKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(flacKey, 0L, 12_000L))
        }

        val flacEval = evaluateSongCache(
            mediaId = mediaId,
            playerCache = flacPlayerCache,
            downloadCache = flacDownloadCache,
            storedFlacLength = 12_000L,
        )
        assertFalse(flacEval.hasYt)
        assertTrue(flacEval.hasFlac)
        assertEquals("FLAC", flacEval.source)
        assertTrue(flacEval.isFullyCached)
    }

    @Test
    fun testContinuousRangeCompletenessFullVsPartial() {
        val completeSpans = listOf(CacheSpan("k", 0L, 10_000L, androidx.media3.common.C.TIME_UNSET, null))
        val completeRanges = mergeSpans(completeSpans)
        assertTrue(isContinuousRangeComplete(completeRanges, 10_000L))
        assertFalse(isContinuousRangeComplete(completeRanges, 15_000L))

        val partialSpans = listOf(CacheSpan("k", 0L, 3_000L, androidx.media3.common.C.TIME_UNSET, null))
        val partialRanges = mergeSpans(partialSpans)
        assertFalse(isContinuousRangeComplete(partialRanges, 10_000L))

        val disconnectedSpans = listOf(
            CacheSpan("k", 0L, 4_000L, androidx.media3.common.C.TIME_UNSET, null),
            CacheSpan("k", 6_000L, 4_000L, androidx.media3.common.C.TIME_UNSET, null),
        )
        val disconnectedRanges = mergeSpans(disconnectedSpans)
        assertEquals(2, disconnectedRanges.size)
        assertFalse(isContinuousRangeComplete(disconnectedRanges, 10_000L))

        assertFalse(isContinuousRangeComplete(completeRanges, -1L))
        assertFalse(isContinuousRangeComplete(emptyList(), 10_000L))
    }

    @Test
    fun testSpanMergeWithoutDoubleCountingOverlaps() {
        val overlappingSpans = listOf(
            CacheSpan("k", 0L, 4_000L, androidx.media3.common.C.TIME_UNSET, null),
            CacheSpan("k", 2_000L, 5_000L, androidx.media3.common.C.TIME_UNSET, null),
            CacheSpan("k", 6_000L, 2_000L, androidx.media3.common.C.TIME_UNSET, null),
        )
        val merged = mergeSpans(overlappingSpans)
        assertEquals(1, merged.size)
        assertEquals(0L, merged[0].start)
        assertEquals(8_000L, merged[0].end)
        assertEquals(8_000L, merged.sumOf { it.end - it.start })
    }

    @Test
    fun testRemoveSongFromCacheClearsAllFourKeysAcrossCaches() {
        val playerCache = mockk<Cache>(relaxed = true)
        val downloadCache = mockk<Cache>(relaxed = true)
        val mediaId = "testSong99"

        removeSongResources(mediaId, playerCache, downloadCache)

        verify(exactly = 1) { playerCache.removeResource(ytStreamCacheKey(mediaId)) }
        verify(exactly = 1) { playerCache.removeResource(flacStreamCacheKey(mediaId)) }
        verify(exactly = 1) { playerCache.removeResource(mediaId) }
        verify(exactly = 1) { playerCache.removeResource(flacCacheKey(mediaId)) }

        verify(exactly = 1) { downloadCache.removeResource(ytStreamCacheKey(mediaId)) }
        verify(exactly = 1) { downloadCache.removeResource(flacStreamCacheKey(mediaId)) }
        verify(exactly = 1) { downloadCache.removeResource(mediaId) }
        verify(exactly = 1) { downloadCache.removeResource(flacCacheKey(mediaId)) }
    }

    @Test
    fun testCachedSongIdsReturnsCleanMediaIds() {
        val downloadUtil = mockk<DownloadUtil>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val database = mockk<MusicDatabase>(relaxed = true)

        val uri = mockk<Uri>(relaxed = true)
        val request = DownloadRequest.Builder("stream_v2_YT_MUSIC_song1", uri).build()
        val completedDownload = Download(request, Download.STATE_COMPLETED, 0L, 0L, -1L, 0, 0)

        every { downloadUtil.downloads } returns MutableStateFlow(
            mapOf("stream_v2_YT_MUSIC_song1" to completedDownload),
        )

        val mockDownloadCache = mockk<Cache>(relaxed = true)
        every { mockDownloadCache.keys } returns setOf("stream_v2_FLAC_song2", "flac_song3")
        every { downloadUtil.downloadCache } returns mockDownloadCache

        val mockPlayerCache = mockk<Cache>(relaxed = true)
        every { mockPlayerCache.keys } returns setOf("stream_v2_YT_MUSIC_song4", "song5")
        every { downloadUtil.playerCache } returns mockPlayerCache

        val callback = MediaLibrarySessionCallback(
            context = context,
            database = database,
            downloadUtil = downloadUtil,
        )

        val ids = callback.cachedSongIds()

        assertEquals(listOf("song1", "song2", "song3", "song4", "song5"), ids.sorted())
    }
}
