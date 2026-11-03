/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.canvas.CanvasSource
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class CanvasCacheSourceIsolationTest {

    private lateinit var tempDir: File
    private lateinit var index: CanvasCacheIndex

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("canvas_cache_test").toFile()
        index = CanvasCacheIndex()
        index.init(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun indexGetRejectsIncompatibleSourceAndExplicitSourceRejectsUntagged() {
        val appleArtwork =
            CanvasArtwork(
                source = CanvasSource.APPLE_MUSIC,
                name = "Track A",
                videoUrl = "https://example.com/apple.mp4",
            )
        val fileA = File(tempDir, "regular_apple.mp4").apply { writeBytes(ByteArray(1024)) }
        val appleEntry =
            CanvasCacheEntry(
                mediaId = "apple_track",
                artwork = appleArtwork,
                regularFileName = fileA.name,
                verticalFileName = null,
                createdAtMs = System.currentTimeMillis(),
                lastAccessedAtMs = System.currentTimeMillis(),
            )
        index.persistEntry(tempDir, appleEntry)

        assertNull(index.get("apple_track", source = CanvasSource.TIDAL))

        val untaggedArtwork =
            CanvasArtwork(
                source = null,
                name = "Legacy Track",
                videoUrl = "https://example.com/legacy.mp4",
            )
        val fileLegacy = File(tempDir, "legacy.mp4").apply { writeBytes(ByteArray(1024)) }
        val legacyEntry =
            CanvasCacheEntry(
                mediaId = "legacy_track",
                artwork = untaggedArtwork,
                regularFileName = fileLegacy.name,
                verticalFileName = null,
                createdAtMs = System.currentTimeMillis(),
                lastAccessedAtMs = System.currentTimeMillis(),
            )
        index.persistEntry(tempDir, legacyEntry)

        assertNull(index.get("legacy_track", source = CanvasSource.TIDAL))
        assertNull(index.get("legacy_track", source = CanvasSource.APPLE_MUSIC))

        assertEquals(CanvasSource.APPLE_MUSIC, index.getEntry("apple_track")?.artwork?.source)
        assertNull(index.getEntry("legacy_track")?.artwork?.source)
    }

    @Test
    fun removeIfDifferentSourceDeletesFilesAndIndexEntry() {
        val appleArtwork =
            CanvasArtwork(
                source = CanvasSource.APPLE_MUSIC,
                name = "Track B",
                videoUrl = "https://example.com/apple_b.mp4",
            )
        val file = File(tempDir, "video_b.mp4").apply { writeBytes(ByteArray(2048)) }
        val entry =
            CanvasCacheEntry(
                mediaId = "track_b",
                artwork = appleArtwork,
                regularFileName = file.name,
                verticalFileName = null,
                createdAtMs = System.currentTimeMillis(),
                lastAccessedAtMs = System.currentTimeMillis(),
            )
        index.persistEntry(tempDir, entry)

        assertTrue(file.exists())
        assertNotNull(index.getEntry("track_b"))

        val removedSame = index.removeIfDifferentSource("track_b", CanvasSource.APPLE_MUSIC)
        assertFalse(removedSame)
        assertTrue(file.exists())
        assertNotNull(index.getEntry("track_b"))

        val removedDifferent = index.removeIfDifferentSource("track_b", CanvasSource.TIDAL)
        assertTrue(removedDifferent)
        assertFalse(file.exists())
        assertNull(index.getEntry("track_b"))
    }

    @Test
    fun getCurrentPlayableWithRequiredSourceRejectsMismatchedSource() {
        val appleArtwork =
            CanvasArtwork(
                source = CanvasSource.APPLE_MUSIC,
                name = "Track C",
                videoUrl = "https://example.com/apple_c.mp4",
            )
        val file = File(tempDir, "video_c.mp4").apply { writeBytes(ByteArray(2048)) }
        val entry =
            CanvasCacheEntry(
                mediaId = "track_c",
                artwork = appleArtwork,
                regularFileName = file.name,
                verticalFileName = null,
                createdAtMs = System.currentTimeMillis(),
                lastAccessedAtMs = System.currentTimeMillis(),
            )
        index.persistEntry(tempDir, entry)

        assertNull(index.getCurrentPlayable("track_c", requiredSource = CanvasSource.TIDAL))
        assertNull(index.getCurrentPlayable("track_c", requiredSource = CanvasSource.BETTER_LYRICS))
    }

    @Test
    fun downloaderDeterministicReplacementAndIdentityFencing() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val downloader = CanvasVideoDownloader(index, ioDispatcher = testDispatcher)
            val mediaId = "track_d"

            downloader.downloadArtworkInBackground(
                directory = tempDir,
                mediaId = mediaId,
                artwork = CanvasArtwork(source = CanvasSource.APPLE_MUSIC),
            )

            val initialJob = downloader.getJob(mediaId)
            assertNotNull(initialJob)
            assertEquals(CanvasSource.APPLE_MUSIC, downloader.getSource(mediaId))

            downloader.downloadArtworkInBackground(
                directory = tempDir,
                mediaId = mediaId,
                artwork = CanvasArtwork(source = CanvasSource.TIDAL),
            )

            val replacedJob = downloader.getJob(mediaId)
            assertNotNull(replacedJob)
            assertEquals(CanvasSource.TIDAL, downloader.getSource(mediaId))
            assertTrue(initialJob!!.isCancelled)
            assertTrue(downloader.isJobCurrent(mediaId, replacedJob!!, CanvasSource.TIDAL))
            assertFalse(downloader.isJobCurrent(mediaId, initialJob, CanvasSource.APPLE_MUSIC))

            testScheduler.advanceUntilIdle()

            assertNull(downloader.getJob(mediaId))
            assertNull(downloader.getSource(mediaId))
        }

    @Test
    fun commitFileAtomicallySuppressesStaleOrCancelledJob() {
        val downloader = CanvasVideoDownloader(index)
        val mediaId = "track_e"
        val fileName = "committed_e.mp4"
        val target = File(tempDir, fileName)

        val partial1 = File(tempDir, "$fileName.part1").apply { writeBytes(ByteArray(512)) }
        val cancelledJob = Job().apply { cancel() }
        val resultCancelled =
            downloader.commitFileAtomically(
                mediaId = mediaId,
                partial = partial1,
                target = target,
                fileName = fileName,
                job = cancelledJob,
                expectedSource = CanvasSource.TIDAL,
            )
        assertNull(resultCancelled)
        assertFalse(target.exists())
        assertFalse(partial1.exists())

        val partial2 = File(tempDir, "$fileName.part2").apply { writeBytes(ByteArray(512)) }
        val unregisteredJob = Job()
        val resultMismatched =
            downloader.commitFileAtomically(
                mediaId = mediaId,
                partial = partial2,
                target = target,
                fileName = fileName,
                job = unregisteredJob,
                expectedSource = CanvasSource.TIDAL,
            )
        assertNull(resultMismatched)
        assertFalse(target.exists())
        assertFalse(partial2.exists())
    }

    @Test
    fun commitEntryAtomicallySuppressesStaleOrCancelledJob() {
        val downloader = CanvasVideoDownloader(index)
        val mediaId = "track_f"
        val artwork = CanvasArtwork(source = CanvasSource.TIDAL, name = "Track F")
        val entry =
            CanvasCacheEntry(
                mediaId = mediaId,
                artwork = artwork,
                regularFileName = null,
                verticalFileName = null,
                createdAtMs = System.currentTimeMillis(),
                lastAccessedAtMs = System.currentTimeMillis(),
            )

        val cancelledJob = Job().apply { cancel() }
        val persistedCancelled =
            downloader.commitEntryAtomically(
                mediaId = mediaId,
                entry = entry,
                job = cancelledJob,
                expectedSource = CanvasSource.TIDAL,
                directory = tempDir,
            )
        assertFalse(persistedCancelled)
        assertNull(index.getEntry(mediaId))

        val unregisteredJob = Job()
        val persistedMismatched =
            downloader.commitEntryAtomically(
                mediaId = mediaId,
                entry = entry,
                job = unregisteredJob,
                expectedSource = CanvasSource.TIDAL,
                directory = tempDir,
            )
        assertFalse(persistedMismatched)
        assertNull(index.getEntry(mediaId))
    }

    @Test
    fun isYouTubeMediaHostMatchesExactAndSubdomainsWhileRejectingSpoofed() {
        val validHosts = listOf(
            "googlevideo.com",
            "rr1---sn-abc.googlevideo.com",
            "youtube.com",
            "music.youtube.com",
            "googleusercontent.com",
            "lh3.googleusercontent.com",
            "youtube-nocookie.com",
            "www.youtube-nocookie.com",
            "ytimg.com",
            "i.ytimg.com",
        )
        for (host in validHosts) {
            assertTrue("Expected valid host for $host", isYouTubeMediaHost(host))
        }

        val invalidHosts = listOf(
            "notyoutube.com",
            "evilgooglevideo.com",
            "fakeytimg.com",
            "notgoogleusercontent.com",
            "notyoutube-nocookie.com",
            "apple.com",
            "cdn.apple-music.com",
            "tidal.com",
            "betterlyrics.org",
            "example.com",
            "",
            "   ",
            null,
        )
        for (host in invalidHosts) {
            assertFalse("Expected invalid host for $host", isYouTubeMediaHost(host))
        }
    }

    @Test
    fun indexPersistedLoadingWithConfiguredLimitsBeforeInit() {
        val testDir = Files.createTempDirectory("canvas_persisted_limit_test").toFile()
        try {
            val fileA = File(testDir, "test_a.mp4").apply {
                RandomAccessFile(this, "rw").use { it.setLength(150L * 1024 * 1024) }
            }
            val fileB = File(testDir, "test_b.mp4").apply {
                RandomAccessFile(this, "rw").use { it.setLength(150L * 1024 * 1024) }
            }
            val entryA =
                CanvasCacheEntry(
                    mediaId = "id_a",
                    artwork = CanvasArtwork(source = CanvasSource.APPLE_MUSIC, name = "Song A"),
                    regularFileName = fileA.name,
                    verticalFileName = null,
                    createdAtMs = 1000L,
                    lastAccessedAtMs = 1000L,
                )
            val entryB =
                CanvasCacheEntry(
                    mediaId = "id_b",
                    artwork = CanvasArtwork(source = CanvasSource.APPLE_MUSIC, name = "Song B"),
                    regularFileName = fileB.name,
                    verticalFileName = null,
                    createdAtMs = 2000L,
                    lastAccessedAtMs = 2000L,
                )

            val json = Json { ignoreUnknownKeys = true }
            fun writeIndexFile(entries: List<CanvasCacheEntry>) {
                val raw = json.encodeToString(ListSerializer(CanvasCacheEntry.serializer()), entries)
                testDir.resolve("canvas_artwork_cache.json").writeText(raw)
            }

            writeIndexFile(listOf(entryA, entryB))
            val largeLimitIndex = CanvasCacheIndex()
            largeLimitIndex.setMaxSize(1024)
            largeLimitIndex.init(testDir)
            assertEquals(2, largeLimitIndex.allKeys().size)
            assertNotNull(largeLimitIndex.getEntry("id_a"))
            assertNotNull(largeLimitIndex.getEntry("id_b"))

            writeIndexFile(listOf(entryA, entryB))
            val unlimitedIndex = CanvasCacheIndex()
            unlimitedIndex.setMaxSize(-1)
            unlimitedIndex.init(testDir)
            assertEquals(2, unlimitedIndex.allKeys().size)
            assertNotNull(unlimitedIndex.getEntry("id_a"))
            assertNotNull(unlimitedIndex.getEntry("id_b"))

            writeIndexFile(listOf(entryA, entryB))
            val disabledIndex = CanvasCacheIndex()
            disabledIndex.setMaxSize(0)
            disabledIndex.init(testDir)
            assertTrue(disabledIndex.isCacheDisabled())
            assertEquals(0, disabledIndex.allKeys().size)
        } finally {
            testDir.deleteRecursively()
        }
    }
}
