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
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.playback.MediaLibrarySessionCallback
import moe.rukamori.archivetune.playback.extractMediaIdFromCacheKey
import moe.rukamori.archivetune.playback.flacCacheKey
import moe.rukamori.archivetune.playback.flacStreamCacheKey
import moe.rukamori.archivetune.playback.resolvePreservedFlacContentLength
import moe.rukamori.archivetune.playback.ytStreamCacheKey
import moe.rukamori.archivetune.utils.get
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun testDoesNotMergeSpansAcrossDifferentKeys() {
        val mediaId = "splitKeys"
        val playerCache = mockk<Cache>(relaxed = true)
        val downloadCache = mockk<Cache>(relaxed = true)

        val v2Key = ytStreamCacheKey(mediaId)
        val legacyKey = mediaId

        val v2Spans = TreeSet<CacheSpan>().apply { add(createSpan(v2Key, 0L, 3_000L)) }
        val legacySpans = TreeSet<CacheSpan>().apply { add(createSpan(legacyKey, 2_000L, 3_000L)) }

        every { playerCache.getCachedSpans(v2Key) } returns v2Spans
        every { downloadCache.getCachedSpans(v2Key) } returns TreeSet()
        every { playerCache.getCachedSpans(legacyKey) } returns legacySpans
        every { downloadCache.getCachedSpans(legacyKey) } returns TreeSet()

        val evaluation = evaluateSongCache(
            mediaId = mediaId,
            playerCache = playerCache,
            downloadCache = downloadCache,
            storedYtLength = 5_000L,
        )

        assertEquals(6_000L, evaluation.ytCachedBytes)
        assertFalse(evaluation.isYtFullyCached)
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
    fun testHasUncachedPlayerSourceRecognition() {
        val mediaId = "track123"
        val downloadKeysOnlyYt = setOf(ytStreamCacheKey(mediaId))
        val playerKeysWithFlac = setOf(flacStreamCacheKey(mediaId))

        assertTrue(hasUncachedPlayerSource(mediaId, playerKeysWithFlac, downloadKeysOnlyYt))

        val playerKeysOnlyYt = setOf(ytStreamCacheKey(mediaId))
        assertFalse(hasUncachedPlayerSource(mediaId, playerKeysOnlyYt, downloadKeysOnlyYt))

        val emptyDownloadKeys = emptySet<String>()
        assertTrue(hasUncachedPlayerSource(mediaId, playerKeysOnlyYt, emptyDownloadKeys))
    }

    @Test
    fun testResolvePreservedFlacContentLengthPreservesExistingDbLength() {
        val mediaId = "preservedFlacTrack"
        val targetKey = flacStreamCacheKey(mediaId)
        val length = resolvePreservedFlacContentLength(
            targetKey = targetKey,
            existingLength = 45_000L,
            inMemoryLength = null,
            caches = emptyList(),
        )
        assertEquals(45_000L, length)
    }

    @Test
    fun testResolvePreservedFlacContentLengthUsesInMemoryWhenDbZero() {
        val mediaId = "inMemoryFlacTrack"
        val targetKey = flacStreamCacheKey(mediaId)
        val length = resolvePreservedFlacContentLength(
            targetKey = targetKey,
            existingLength = 0L,
            inMemoryLength = 32_000L,
            caches = emptyList(),
        )
        assertEquals(32_000L, length)
    }

    @Test
    fun testResolvePreservedFlacContentLengthUsesCacheMetadataWhenDbAndMemoryZero() {
        val mediaId = "cacheMetaFlacTrack"
        val targetKey = flacStreamCacheKey(mediaId)
        val cache = mockk<Cache>(relaxed = true)
        val metadata = mockk<ContentMetadata>(relaxed = true)
        every { metadata.get(ContentMetadata.KEY_CONTENT_LENGTH, -1L) } returns 75_000L
        every { cache.getContentMetadata(targetKey) } returns metadata

        val length = resolvePreservedFlacContentLength(
            targetKey = targetKey,
            existingLength = 0L,
            inMemoryLength = 0L,
            caches = listOf(cache),
        )
        assertEquals(75_000L, length)
    }

    @Test
    fun testResolvePreservedFlacContentLengthIgnoresCrossVersionAndYtKeys() {
        val mediaId = "isolatedKeyFlacTrack"
        val v2Key = flacStreamCacheKey(mediaId)
        val legacyKey = flacCacheKey(mediaId)
        val ytKey = ytStreamCacheKey(mediaId)

        val cache = mockk<Cache>(relaxed = true)
        val v2Meta = mockk<ContentMetadata>(relaxed = true)
        val legacyMeta = mockk<ContentMetadata>(relaxed = true)
        val ytMeta = mockk<ContentMetadata>(relaxed = true)

        every { v2Meta.get(ContentMetadata.KEY_CONTENT_LENGTH, -1L) } returns 90_000L
        every { legacyMeta.get(ContentMetadata.KEY_CONTENT_LENGTH, -1L) } returns 80_000L
        every { ytMeta.get(ContentMetadata.KEY_CONTENT_LENGTH, -1L) } returns 50_000L

        every { cache.getContentMetadata(v2Key) } returns v2Meta
        every { cache.getContentMetadata(legacyKey) } returns legacyMeta
        every { cache.getContentMetadata(ytKey) } returns ytMeta

        val v2Resolved = resolvePreservedFlacContentLength(
            targetKey = v2Key,
            existingLength = 0L,
            inMemoryLength = null,
            caches = listOf(cache),
        )
        assertEquals(90_000L, v2Resolved)

        val legacyResolved = resolvePreservedFlacContentLength(
            targetKey = legacyKey,
            existingLength = 0L,
            inMemoryLength = null,
            caches = listOf(cache),
        )
        assertEquals(80_000L, legacyResolved)

        val unknownKey = "stream_v2_FLAC_nonExistent"
        val unknownMeta = mockk<ContentMetadata>(relaxed = true)
        every { unknownMeta.get(ContentMetadata.KEY_CONTENT_LENGTH, -1L) } returns -1L
        every { cache.getContentMetadata(unknownKey) } returns unknownMeta
        val nonExistentResolved = resolvePreservedFlacContentLength(
            targetKey = unknownKey,
            existingLength = 0L,
            inMemoryLength = null,
            caches = listOf(cache),
        )
        assertEquals(0L, nonExistentResolved)
    }

    @Test
    fun testResolvePreservedFlacContentLengthReturnsZeroWhenAllUnknown() {
        val mediaId = "unknownAllTrack"
        val length = resolvePreservedFlacContentLength(
            targetKey = flacStreamCacheKey(mediaId),
            existingLength = null,
            inMemoryLength = null,
            caches = emptyList(),
        )
        assertEquals(0L, length)
    }

    @Test
    fun testResolvePreservedFlacContentLengthCacheReadErrorDiagnostics() {
        val mediaId = "cacheErrorTrack"
        val targetKey = flacStreamCacheKey(mediaId)
        val failingCache = mockk<Cache>(relaxed = true)
        every { failingCache.getContentMetadata(targetKey) } throws java.io.IOException("Disk read failed")

        val result = resolvePreservedFlacContentLength(
            targetKey = targetKey,
            existingLength = 0L,
            inMemoryLength = null,
            caches = listOf(failingCache),
        )
        assertEquals(0L, result)

        val cancelCache = mockk<Cache>(relaxed = true)
        every { cancelCache.getContentMetadata(targetKey) } throws CancellationException("Cancel metadata")
        var cancelPropagated = false
        try {
            resolvePreservedFlacContentLength(
                targetKey = targetKey,
                existingLength = 0L,
                inMemoryLength = null,
                caches = listOf(cancelCache),
            )
        } catch (e: CancellationException) {
            cancelPropagated = true
            assertEquals("Cancel metadata", e.message)
        }
        assertTrue(cancelPropagated)
    }

    @Test
    fun testFullFlacEvaluation() {
        val mediaId = "flacOnly"
        val playerCache = mockk<Cache>(relaxed = true)
        val downloadCache = mockk<Cache>(relaxed = true)
        val flacKey = flacStreamCacheKey(mediaId)

        every { playerCache.getCachedSpans(flacKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(flacKey, 0L, 50_000L))
        }

        val eval = evaluateSongCache(
            mediaId = mediaId,
            playerCache = playerCache,
            downloadCache = downloadCache,
            storedFlacLength = 50_000L,
        )

        assertFalse(eval.hasYt)
        assertTrue(eval.hasFlac)
        assertTrue(eval.isFlacFullyCached)
        assertFalse(eval.isYtFullyCached)
        assertEquals("FLAC", eval.source)
        assertEquals(50_000L, eval.flacCachedBytes)
        assertEquals(50_000L, eval.totalCachedBytes)
        assertTrue(eval.isFullyCached)
    }

    @Test
    fun testFullOpusEvaluation() {
        val mediaId = "opusOnly"
        val playerCache = mockk<Cache>(relaxed = true)
        val downloadCache = mockk<Cache>(relaxed = true)
        val ytKey = ytStreamCacheKey(mediaId)

        every { playerCache.getCachedSpans(ytKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(ytKey, 0L, 10_000L))
        }

        val eval = evaluateSongCache(
            mediaId = mediaId,
            playerCache = playerCache,
            downloadCache = downloadCache,
            storedYtLength = 10_000L,
        )

        assertTrue(eval.hasYt)
        assertFalse(eval.hasFlac)
        assertTrue(eval.isYtFullyCached)
        assertFalse(eval.isFlacFullyCached)
        assertEquals("Opus", eval.source)
        assertEquals(10_000L, eval.ytCachedBytes)
        assertEquals(10_000L, eval.totalCachedBytes)
        assertTrue(eval.isFullyCached)
    }

    @Test
    fun testPartialBothHidden() {
        val mediaId = "partialBothTrack"
        val playerCache = mockk<Cache>(relaxed = true)
        val downloadCache = mockk<Cache>(relaxed = true)
        val ytKey = ytStreamCacheKey(mediaId)
        val flacKey = flacStreamCacheKey(mediaId)

        every { playerCache.getCachedSpans(ytKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(ytKey, 0L, 3_000L))
        }
        every { playerCache.getCachedSpans(flacKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(flacKey, 0L, 8_000L))
        }

        val eval = evaluateSongCache(
            mediaId = mediaId,
            playerCache = playerCache,
            downloadCache = downloadCache,
            storedYtLength = 10_000L,
            storedFlacLength = 30_000L,
        )

        assertTrue(eval.hasYt)
        assertTrue(eval.hasFlac)
        assertFalse(eval.isYtFullyCached)
        assertFalse(eval.isFlacFullyCached)
        assertEquals(11_000L, eval.totalCachedBytes)
        assertFalse(eval.isFullyCached)
    }

    @Test
    fun testUnknownLengthNotFull() {
        val mediaId = "unknownLengthTrack"
        val playerCache = mockk<Cache>(relaxed = true)
        val downloadCache = mockk<Cache>(relaxed = true)
        val ytKey = ytStreamCacheKey(mediaId)

        every { playerCache.getCachedSpans(ytKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(ytKey, 0L, 20_000L))
        }

        val eval = evaluateSongCache(
            mediaId = mediaId,
            playerCache = playerCache,
            downloadCache = downloadCache,
            storedYtLength = -1L,
            storedFlacLength = -1L,
        )

        assertTrue(eval.hasYt)
        assertEquals(20_000L, eval.ytCachedBytes)
        assertFalse(eval.isYtFullyCached)
        assertFalse(eval.isFullyCached)
    }

    @Test
    fun testCodecIsolation() {
        val mediaId = "isolatedCodecsTrack"
        val playerCache = mockk<Cache>(relaxed = true)
        val downloadCache = mockk<Cache>(relaxed = true)
        val ytKey = ytStreamCacheKey(mediaId)
        val flacKey = flacStreamCacheKey(mediaId)

        every { playerCache.getCachedSpans(ytKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(ytKey, 0L, 6_000L))
        }
        every { playerCache.getCachedSpans(flacKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(flacKey, 0L, 5_000L))
        }

        val eval = evaluateSongCache(
            mediaId = mediaId,
            playerCache = playerCache,
            downloadCache = downloadCache,
            storedYtLength = 10_000L,
            storedFlacLength = 20_000L,
        )

        assertEquals(6_000L, eval.ytCachedBytes)
        assertEquals(5_000L, eval.flacCachedBytes)
        assertEquals(11_000L, eval.totalCachedBytes)
        assertFalse(eval.isYtFullyCached)
        assertFalse(eval.isFlacFullyCached)
        assertFalse(eval.isFullyCached)
    }

    @Test
    fun testSameKeyInBothCachesNotExcludedDuePrefetch() {
        val mediaId = "prefetchedTrack"
        val ytKey = ytStreamCacheKey(mediaId)

        val cachedKeys = setOf(ytKey)
        val completedDownloadKeys = emptySet<String>()

        assertTrue(hasUncachedPlayerSource(mediaId, cachedKeys, completedDownloadKeys))
    }

    @Test
    fun testGenuineCompletedUserDownloadExcluded() {
        val mediaId = "downloadedTrack"
        val song = Song(song = SongEntity(id = mediaId, title = "Title"), artists = emptyList())
        val ytKey = ytStreamCacheKey(mediaId)

        val uri = mockk<Uri>(relaxed = true)
        val request = DownloadRequest.Builder(mediaId, uri).build()

        val completedDownload = Download(request, Download.STATE_COMPLETED, 0L, 0L, -1L, 0, 0)
        val completedKeys = extractCompletedDownloadKeys(listOf(completedDownload))
        assertTrue(completedKeys.contains(mediaId))

        assertFalse(findPureCacheIds(setOf(ytKey), completedKeys).contains(mediaId))
        assertFalse(hasUncachedPlayerSource(mediaId, setOf(ytKey), completedKeys))

        val playerCache = mockk<Cache>(relaxed = true)
        val downloadCache = mockk<Cache>(relaxed = true)
        every { downloadCache.getCachedSpans(ytKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(ytKey, 0L, 10_000L))
        }
        val evaluatedWhenCompleted = evaluateEligibleCachedSong(
            song = song,
            playerCache = playerCache,
            downloadCache = downloadCache,
            storedYtLength = 10_000L,
            completedDownloadKeys = completedKeys,
        )
        assertNull(evaluatedWhenCompleted)

        val inProgressDownload = Download(request, Download.STATE_DOWNLOADING, 0L, 0L, -1L, 0, 0)
        val incompleteKeys = extractCompletedDownloadKeys(listOf(inProgressDownload))
        assertFalse(incompleteKeys.contains(mediaId))
        assertTrue(findPureCacheIds(setOf(ytKey), incompleteKeys).contains(mediaId))
        assertTrue(hasUncachedPlayerSource(mediaId, setOf(ytKey), incompleteKeys))

        val flacKey = flacStreamCacheKey(mediaId)
        assertTrue(hasUncachedPlayerSource(mediaId, setOf(ytKey, flacKey), completedKeys))
        assertTrue(findPureCacheIds(setOf(ytKey, flacKey), completedKeys).contains(mediaId))
    }

    @Test
    fun testDownloadedOpusWithPartialFlacIsHidden() {
        val mediaId = "track_opus_dl_flac_part"
        val song = Song(song = SongEntity(id = mediaId, title = "Title"), artists = emptyList())
        val uri = mockk<Uri>(relaxed = true)
        val download = Download(DownloadRequest.Builder(mediaId, uri).build(), Download.STATE_COMPLETED, 0L, 0L, -1L, 0, 0)
        val completedKeys = extractCompletedDownloadKeys(listOf(download))
        assertEquals(setOf(mediaId), completedKeys)

        val ytKey = ytStreamCacheKey(mediaId)
        val flacKey = flacStreamCacheKey(mediaId)
        val allCachedKeys = setOf(ytKey, flacKey)

        assertTrue(findPureCacheIds(allCachedKeys, completedKeys).contains(mediaId))

        val playerCache = mockk<Cache>(relaxed = true)
        val downloadCache = mockk<Cache>(relaxed = true)
        every { downloadCache.getCachedSpans(ytKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(ytKey, 0L, 10_000L))
        }
        every { downloadCache.getCachedSpans(flacKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(flacKey, 0L, 5_000L))
        }

        val result = evaluateEligibleCachedSong(
            song = song,
            playerCache = playerCache,
            downloadCache = downloadCache,
            storedYtLength = 10_000L,
            storedFlacLength = 40_000L,
            completedDownloadKeys = completedKeys,
        )

        assertNull(result)
    }

    @Test
    fun testDownloadedOpusWithFullFlacShowsOnlyEligibleFlac() {
        val mediaId = "track_opus_dl_flac_full"
        val song = Song(song = SongEntity(id = mediaId, title = "Title"), artists = emptyList())
        val uri = mockk<Uri>(relaxed = true)
        val download = Download(DownloadRequest.Builder(mediaId, uri).build(), Download.STATE_COMPLETED, 0L, 0L, -1L, 0, 0)
        val completedKeys = extractCompletedDownloadKeys(listOf(download))
        assertEquals(setOf(mediaId), completedKeys)

        val ytKey = ytStreamCacheKey(mediaId)
        val flacKey = flacStreamCacheKey(mediaId)
        val allCachedKeys = setOf(ytKey, flacKey)

        assertTrue(findPureCacheIds(allCachedKeys, completedKeys).contains(mediaId))

        val playerCache = mockk<Cache>(relaxed = true)
        val downloadCache = mockk<Cache>(relaxed = true)
        every { downloadCache.getCachedSpans(ytKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(ytKey, 0L, 10_000L))
        }
        every { playerCache.getCachedSpans(flacKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(flacKey, 0L, 40_000L))
        }

        val result = evaluateEligibleCachedSong(
            song = song,
            playerCache = playerCache,
            downloadCache = downloadCache,
            storedYtLength = 10_000L,
            storedFlacLength = 40_000L,
            completedDownloadKeys = completedKeys,
        )

        assertNotNull(result)
        assertEquals("FLAC", result!!.source)
        assertEquals(40_000L, result.cachedBytes)
        assertTrue(result.isFullyCached)
    }

    @Test
    fun testExplicitFlacCustomKeyWithRawIdLeavesCachedYtVisible() {
        val mediaId = "flac_custom_key_track"
        val song = Song(song = SongEntity(id = mediaId, title = "Title"), artists = emptyList())
        val uri = mockk<Uri>(relaxed = true)
        val flacCustomKey = flacStreamCacheKey(mediaId)
        val download = Download(
            DownloadRequest.Builder(mediaId, uri).setCustomCacheKey(flacCustomKey).build(),
            Download.STATE_COMPLETED,
            0L,
            0L,
            -1L,
            0,
            0,
        )
        val completedKeys = extractCompletedDownloadKeys(listOf(download))
        assertEquals(setOf(flacCustomKey), completedKeys)

        val ytKey = ytStreamCacheKey(mediaId)
        val allCachedKeys = setOf(ytKey)

        assertTrue(findPureCacheIds(allCachedKeys, completedKeys).contains(mediaId))

        val playerCache = mockk<Cache>(relaxed = true)
        val downloadCache = mockk<Cache>(relaxed = true)
        every { playerCache.getCachedSpans(ytKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(ytKey, 0L, 12_000L))
        }

        val result = evaluateEligibleCachedSong(
            song = song,
            playerCache = playerCache,
            downloadCache = downloadCache,
            storedYtLength = 12_000L,
            storedFlacLength = -1L,
            completedDownloadKeys = completedKeys,
        )

        assertNotNull(result)
        assertEquals("Opus", result!!.source)
        assertEquals(12_000L, result.cachedBytes)
        assertTrue(result.isFullyCached)
    }

    @Test
    fun test400IdBatchingForLargeSet() = runBlocking {
        val ids = (1..850).map { "song_$it" }
        val database = mockk<MusicDatabase>()

        val capturedBatches = mutableListOf<List<String>>()
        coEvery { database.getSongsByIds(capture(capturedBatches)) } answers {
            firstArg<List<String>>().map { id ->
                Song(song = SongEntity(id = id, title = "Title $id"), artists = emptyList())
            }
        }

        val result = fetchSongsInBatches(database, ids, batchSize = 400)

        assertEquals(850, result.size)
        assertEquals(3, capturedBatches.size)
        assertEquals(400, capturedBatches[0].size)
        assertEquals(400, capturedBatches[1].size)
        assertEquals(50, capturedBatches[2].size)
        coVerify(exactly = 3) { database.getSongsByIds(any()) }
    }

    @Test
    fun testFailureThenPollingResumesAndCancellationPropagates() = runBlocking {
        var iterations = 0
        var delayCalls = 0

        try {
            runPollingLoop(
                delayMs = 1000L,
                delayProvider = { delayMs ->
                    delayCalls++
                    assertEquals(1000L, delayMs)
                    if (delayCalls >= 2) throw CancellationException("Stop loop")
                },
                step = {
                    iterations++
                    if (iterations == 1) {
                        throw RuntimeException("Simulated DB error")
                    }
                },
            )
        } catch (e: CancellationException) {
            assertEquals("Stop loop", e.message)
        }

        assertEquals(2, iterations)
        assertEquals(2, delayCalls)

        var cancellationPropagated = false
        try {
            runPollingLoop(
                delayMs = 1000L,
                delayProvider = {},
                step = { throw CancellationException("Propagate cancel") },
            )
        } catch (e: CancellationException) {
            cancellationPropagated = true
            assertEquals("Propagate cancel", e.message)
        }
        assertTrue(cancellationPropagated)
    }

    @Test
    fun testFullOpusWithPartialFlacReportsOpusOnly() {
        val mediaId = "fullOpusPartialFlac"
        val playerCache = mockk<Cache>(relaxed = true)
        val downloadCache = mockk<Cache>(relaxed = true)
        val ytKey = ytStreamCacheKey(mediaId)
        val flacKey = flacStreamCacheKey(mediaId)

        every { playerCache.getCachedSpans(ytKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(ytKey, 0L, 10_000L))
        }
        every { playerCache.getCachedSpans(flacKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(flacKey, 0L, 5_000L))
        }

        val eval = evaluateSongCache(
            mediaId = mediaId,
            playerCache = playerCache,
            downloadCache = downloadCache,
            storedYtLength = 10_000L,
            storedFlacLength = 40_000L,
        )

        assertTrue(eval.isFullyCached)
        assertTrue(eval.isYtFullyCached)
        assertFalse(eval.isFlacFullyCached)
        assertEquals("Opus", eval.source)
        assertEquals(15_000L, eval.totalCachedBytes)
    }

    @Test
    fun testFullFlacWithPartialOpusReportsFlacOnly() {
        val mediaId = "fullFlacPartialOpus"
        val playerCache = mockk<Cache>(relaxed = true)
        val downloadCache = mockk<Cache>(relaxed = true)
        val ytKey = ytStreamCacheKey(mediaId)
        val flacKey = flacStreamCacheKey(mediaId)

        every { playerCache.getCachedSpans(ytKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(ytKey, 0L, 2_000L))
        }
        every { playerCache.getCachedSpans(flacKey) } returns TreeSet<CacheSpan>().apply {
            add(createSpan(flacKey, 0L, 40_000L))
        }

        val eval = evaluateSongCache(
            mediaId = mediaId,
            playerCache = playerCache,
            downloadCache = downloadCache,
            storedYtLength = 10_000L,
            storedFlacLength = 40_000L,
        )

        assertTrue(eval.isFullyCached)
        assertFalse(eval.isYtFullyCached)
        assertTrue(eval.isFlacFullyCached)
        assertEquals("FLAC", eval.source)
        assertEquals(42_000L, eval.totalCachedBytes)
    }

    @Test
    fun testCacheReadFailureThenNextSuccessfulPolling() = runBlocking {
        var iterations = 0
        var delayCalls = 0
        val playerCache = mockk<Cache>(relaxed = true)
        val downloadCache = mockk<Cache>(relaxed = true)
        val mediaId = "cacheFailureTrack"
        val ytKey = ytStreamCacheKey(mediaId)

        every { playerCache.keys } returns setOf(ytKey)
        every { downloadCache.keys } returns emptySet()

        every { playerCache.getContentMetadata(ytKey).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L) } returns 10_000L
        every { downloadCache.getContentMetadata(ytKey).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L) } returns -1L

        every { playerCache.getCachedSpans(ytKey) } answers {
            if (iterations == 1) {
                throw java.io.IOException("Simulated cache disk read error")
            } else {
                TreeSet<CacheSpan>().apply { add(createSpan(ytKey, 0L, 10_000L)) }
            }
        }
        every { downloadCache.getCachedSpans(ytKey) } returns TreeSet()
        every { playerCache.isCached(ytKey, 0L, 10_000L) } returns true
        every { downloadCache.isCached(ytKey, 0L, 10_000L) } returns false

        var finalResult: KeyCacheResult? = null
        try {
            runPollingLoop(
                delayMs = 1000L,
                delayProvider = {
                    delayCalls++
                    if (delayCalls >= 2) throw CancellationException("Stop after recovery")
                },
                step = {
                    iterations++
                    finalResult = evaluateKeyCache(ytKey, playerCache, downloadCache, 10_000L)
                },
            )
        } catch (e: CancellationException) {
            assertEquals("Stop after recovery", e.message)
        }

        assertEquals(2, iterations)
        assertEquals(2, delayCalls)
        assertNotNull(finalResult)
        assertTrue(finalResult!!.isFullyCached)
        assertEquals(10_000L, finalResult!!.cachedBytes)
    }

    @Test
    fun testRemoveSongFromCacheClearsOnlyPlayerCache() {
        val playerCache = mockk<Cache>(relaxed = true)
        val mediaId = "testSong99"

        removeSongResources(mediaId, playerCache)

        verify(exactly = 1) { playerCache.removeResource(ytStreamCacheKey(mediaId)) }
        verify(exactly = 1) { playerCache.removeResource(flacStreamCacheKey(mediaId)) }
        verify(exactly = 1) { playerCache.removeResource(mediaId) }
        verify(exactly = 1) { playerCache.removeResource(flacCacheKey(mediaId)) }
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
