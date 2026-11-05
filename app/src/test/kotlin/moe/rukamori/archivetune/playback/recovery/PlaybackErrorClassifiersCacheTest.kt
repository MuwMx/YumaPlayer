package moe.rukamori.archivetune.playback.recovery

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.util.TreeSet

class PlaybackErrorClassifiersCacheTest {
    private fun cacheWithSpansUnder(key: String): Cache {
        val cache = mockk<Cache>()
        every { cache.getCachedSpans(any()) } returns TreeSet()
        every { cache.getCachedSpans(key) } returns TreeSet(listOf(CacheSpan(key, 0L, 10L)))
        return cache
    }

    private class SourceError(cause: Throwable) :
        PlaybackException("Source error", cause, ERROR_CODE_IO_UNSPECIFIED, mockk(relaxed = true), 0L)

    private fun sourceError(cause: Throwable): PlaybackException = SourceError(cause)

    @Test
    fun aSongCachedUnderTheStreamKeyCountsAsCached() {
        val cache = cacheWithSpansUnder("stream_v2_YT_MUSIC_yt-1")

        assertTrue(PlaybackErrorClassifiers.hasAnyCachedData("yt-1", isFullyCached = false, cache))
    }

    @Test
    fun aSongCachedUnderTheLegacyKeyCountsAsCached() {
        val cache = cacheWithSpansUnder("yt-1")

        assertTrue(PlaybackErrorClassifiers.hasAnyCachedData("yt-1", isFullyCached = false, null, cache))
    }

    @Test
    fun anotherSongsCacheDoesNotCount() {
        val cache = cacheWithSpansUnder("stream_v2_YT_MUSIC_yt-2")

        assertFalse(PlaybackErrorClassifiers.hasAnyCachedData("yt-1", isFullyCached = false, cache))
    }

    @Test
    fun anEndOfFileInsideCachedDataIsCacheCorruption() {
        val error = sourceError(IOException("load failed", EOFException()))

        assertTrue(PlaybackErrorClassifiers.isCacheCorruptionError(error, isContentCached = true))
        assertFalse(PlaybackErrorClassifiers.isCacheCorruptionError(error, isContentCached = false))
    }

    @Test
    fun theExtractorReadingPastItsBufferIsCacheCorruption() {
        val overrun = ArrayIndexOutOfBoundsException("src.length=65536")
        overrun.stackTrace = arrayOf(StackTraceElement("androidx.media3.extractor.DefaultExtractorInput", "readFromPeekBuffer", null, 0))

        assertTrue(PlaybackErrorClassifiers.isCacheCorruptionError(sourceError(IOException("unexpected", overrun)), isContentCached = true))
    }

    @Test
    fun otherInputErrorsAreNotCacheCorruption() {
        val error = sourceError(IOException("connection reset"))

        assertFalse(PlaybackErrorClassifiers.isCacheCorruptionError(error, isContentCached = true))
    }
}
