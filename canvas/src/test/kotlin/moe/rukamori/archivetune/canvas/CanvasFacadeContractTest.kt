/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.canvas

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.canvas.ArchiveTuneCanvas.acceptSongCandidate
import moe.rukamori.archivetune.canvas.ArchiveTuneCanvas.hasUsableCanvasAnimation
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasFacadeContractTest {

    @Test
    fun spotifySourceReturnsNullWithoutNetwork() {
        runBlocking {
            val result =
                ArchiveTuneCanvas.getBySongArtist(
                    song = "Test Song",
                    artist = "Test Artist",
                    source = CanvasSource.SPOTIFY,
                )
            assertNull(result)
        }
    }

    @Test
    fun unconfiguredTidalPathReturnsNullWithoutNetwork() {
        runBlocking {
            try {
                TidalCanvasProvider.initialize(null)
                assertFalse(TidalCanvasProvider.isHealthy())
                assertNull(TidalCanvasProvider.getBySongArtist("Unconfigured Track", "Unconfigured Artist"))
            } finally {
                TidalCanvasProvider.initialize(null)
            }
        }
    }

    @Test
    fun tidalVerticalSelectionThroughFacadeSkipsNetwork() {
        runBlocking {
            val result =
                ArchiveTuneCanvas.getBySongArtist(
                    song = "Test Track",
                    artist = "Test Artist",
                    source = CanvasSource.TIDAL,
                    requireVertical = true,
                )
            assertNull(result)
        }
    }

    @Test(expected = CancellationException::class)
    fun getBySongArtistPropagatesExternalCancellation() {
        runBlocking {
            val cancelledScope = CoroutineScope(Dispatchers.Unconfined + Job().apply { cancel() })
            cancelledScope.async {
                ArchiveTuneCanvas.getBySongArtist(
                    song = "Cancelled Test",
                    artist = "Cancelled Artist",
                    source = CanvasSource.ALL,
                )
            }.await()
        }
    }

    @Test
    fun executeProviderAttemptReturnsNullOnTimeoutWithoutThrowing() {
        runBlocking {
            val result =
                ArchiveTuneCanvas.executeProviderAttempt(
                    provider = CanvasSource.BETTER_LYRICS,
                    requireVertical = false,
                    timeoutMs = 20L,
                ) {
                    delay(100L)
                    CanvasArtwork(name = "Delayed", videoUrl = "https://example.com/video.mp4")
                }
            assertNull(result)
        }
    }

    @Test(expected = CancellationException::class)
    fun executeProviderAttemptPropagatesParentCancellation() {
        runBlocking {
            val cancelledScope = CoroutineScope(Dispatchers.Unconfined + Job().apply { cancel() })
            cancelledScope.async {
                ArchiveTuneCanvas.executeProviderAttempt(
                    provider = CanvasSource.BETTER_LYRICS,
                    requireVertical = false,
                ) {
                    CanvasArtwork(name = "Cancelled")
                }
            }.await()
        }
    }

    @Test
    fun hasUsableCanvasAnimationRejectsBlankOrMetadataOnlyArtwork() {
        val metadataOnly = CanvasArtwork(name = "Only Meta", artist = "Artist")
        assertFalse(metadataOnly.hasUsableCanvasAnimation(requireVertical = false))
        assertFalse(metadataOnly.hasUsableCanvasAnimation(requireVertical = true))

        val blankUrl = CanvasArtwork(name = "Blank", videoUrl = "   ", animated = "")
        assertFalse(blankUrl.hasUsableCanvasAnimation(requireVertical = false))
        assertFalse(blankUrl.hasUsableCanvasAnimation(requireVertical = true))
    }

    @Test
    fun hasUsableCanvasAnimationValidatesSquareAndVerticalUrls() {
        val squareArtwork =
            CanvasArtwork(
                name = "Square Track",
                videoUrl = "https://resources.tidal.com/videos/123/1280x1280.mp4",
            )
        assertTrue(squareArtwork.hasUsableCanvasAnimation(requireVertical = false))
        assertFalse(squareArtwork.hasUsableCanvasAnimation(requireVertical = true))

        val verticalArtwork =
            CanvasArtwork(
                name = "Vertical Track",
                videoUrl = "https://resources.tidal.com/videos/123/1280x1280.mp4",
                videoUrlVertical = "https://example.com/vertical.mp4",
            )
        assertTrue(verticalArtwork.hasUsableCanvasAnimation(requireVertical = false))
        assertTrue(verticalArtwork.hasUsableCanvasAnimation(requireVertical = true))

        val animatedVerticalArtwork =
            CanvasArtwork(
                name = "Animated Vertical",
                animatedVertical = "https://example.com/animated-vertical.m3u8",
            )
        assertFalse(animatedVerticalArtwork.hasUsableCanvasAnimation(requireVertical = false))
        assertTrue(animatedVerticalArtwork.hasUsableCanvasAnimation(requireVertical = true))
    }

    @Test
    fun acceptSongCandidateRejectsTitleMismatchForBetterLyricsAndAppleMusic() {
        val candidate =
            CanvasArtwork(
                name = "Blinding Lights",
                artist = "The Weeknd",
                videoUrl = "https://example.com/video.mp4",
            )
        assertNull(
            acceptSongCandidate(
                candidate = candidate,
                provider = CanvasSource.BETTER_LYRICS,
                song = "Save Your Tears",
                artist = "The Weeknd",
                requireVertical = false,
            ),
        )
        assertNull(
            acceptSongCandidate(
                candidate = candidate,
                provider = CanvasSource.APPLE_MUSIC,
                song = "Save Your Tears",
                artist = "The Weeknd",
                requireVertical = false,
            ),
        )

        val unrelatedCandidate =
            CanvasArtwork(
                name = "Desperado",
                artist = "Eagles",
                videoUrl = "https://example.com/video.mp4",
            )
        assertNull(
            acceptSongCandidate(
                candidate = unrelatedCandidate,
                provider = CanvasSource.BETTER_LYRICS,
                song = "Hotel California",
                artist = "Eagles",
                requireVertical = false,
            ),
        )
    }

    @Test
    fun acceptSongCandidateRejectsArtistMismatchForBetterLyricsAndAppleMusic() {
        val candidate =
            CanvasArtwork(
                name = "Save Your Tears",
                artist = "Taylor Swift",
                videoUrl = "https://example.com/video.mp4",
            )
        assertNull(
            acceptSongCandidate(
                candidate = candidate,
                provider = CanvasSource.BETTER_LYRICS,
                song = "Save Your Tears",
                artist = "The Weeknd",
                requireVertical = false,
            ),
        )
        assertNull(
            acceptSongCandidate(
                candidate = candidate,
                provider = CanvasSource.APPLE_MUSIC,
                song = "Save Your Tears",
                artist = "The Weeknd",
                requireVertical = false,
            ),
        )
    }

    @Test
    fun acceptSongCandidateForcesProviderAttributionOverUpstreamSource() {
        val upstreamNullSource =
            CanvasArtwork(
                name = "Save Your Tears",
                artist = "The Weeknd",
                videoUrl = "https://example.com/video.mp4",
                source = null,
            )
        val attributedBetterLyrics =
            acceptSongCandidate(
                candidate = upstreamNullSource,
                provider = CanvasSource.BETTER_LYRICS,
                song = "Save Your Tears",
                artist = "The Weeknd",
                requireVertical = false,
            )
        assertNotNull(attributedBetterLyrics)
        assertEquals(CanvasSource.BETTER_LYRICS, attributedBetterLyrics?.source)

        val upstreamForeignSource =
            CanvasArtwork(
                name = "Save Your Tears",
                artist = "The Weeknd",
                videoUrl = "https://example.com/video.mp4",
                source = CanvasSource.TIDAL,
            )
        val attributedAppleMusic =
            acceptSongCandidate(
                candidate = upstreamForeignSource,
                provider = CanvasSource.APPLE_MUSIC,
                song = "Save Your Tears",
                artist = "The Weeknd",
                requireVertical = false,
            )
        assertNotNull(attributedAppleMusic)
        assertEquals(CanvasSource.APPLE_MUSIC, attributedAppleMusic?.source)
    }

    @Test
    fun acceptSongCandidateAcceptsLegitimateLiveAndRemasterVariants() {
        val liveParenthesis =
            CanvasArtwork(
                name = "Hotel California (Live at The Forum, 1976)",
                artist = "Eagles",
                videoUrl = "https://example.com/video.mp4",
            )
        val acceptedLive =
            acceptSongCandidate(
                candidate = liveParenthesis,
                provider = CanvasSource.BETTER_LYRICS,
                song = "Hotel California",
                artist = "Eagles",
                requireVertical = false,
            )
        assertNotNull(acceptedLive)
        assertEquals(CanvasSource.BETTER_LYRICS, acceptedLive?.source)

        val remasterDash =
            CanvasArtwork(
                name = "In the End - Remastered 2020",
                artist = "Linkin Park",
                videoUrl = "https://example.com/video.mp4",
            )
        val acceptedRemaster =
            acceptSongCandidate(
                candidate = remasterDash,
                provider = CanvasSource.APPLE_MUSIC,
                song = "In the End",
                artist = "Linkin Park",
                requireVertical = false,
            )
        assertNotNull(acceptedRemaster)
        assertEquals(CanvasSource.APPLE_MUSIC, acceptedRemaster?.source)

        val bracketVariant =
            CanvasArtwork(
                name = "Numb [Live]",
                artist = "Linkin Park",
                videoUrl = "https://example.com/video.mp4",
            )
        val acceptedBracket =
            acceptSongCandidate(
                candidate = bracketVariant,
                provider = CanvasSource.BETTER_LYRICS,
                song = "Numb",
                artist = "Linkin Park",
                requireVertical = false,
            )
        assertNotNull(acceptedBracket)
        assertEquals(CanvasSource.BETTER_LYRICS, acceptedBracket?.source)
    }

    @Test
    fun acceptSongCandidateAcceptsPriorBareAndParenthesizedVideoQualifiers() {
        val bareVideoCandidate =
            CanvasArtwork(
                name = "Save Your Tears Official Music Video",
                artist = "The Weeknd",
                videoUrl = "https://example.com/video.mp4",
            )
        val acceptedBare =
            acceptSongCandidate(
                candidate = bareVideoCandidate,
                provider = CanvasSource.BETTER_LYRICS,
                song = "Save Your Tears",
                artist = "The Weeknd",
                requireVertical = false,
            )
        assertNotNull(acceptedBare)
        assertEquals(CanvasSource.BETTER_LYRICS, acceptedBare?.source)

        val parenthesizedVideoCandidate =
            CanvasArtwork(
                name = "Save Your Tears (Official Music Video)",
                artist = "The Weeknd",
                videoUrl = "https://example.com/video.mp4",
            )
        val acceptedParenthesized =
            acceptSongCandidate(
                candidate = parenthesizedVideoCandidate,
                provider = CanvasSource.APPLE_MUSIC,
                song = "Save Your Tears",
                artist = "The Weeknd",
                requireVertical = false,
            )
        assertNotNull(acceptedParenthesized)
        assertEquals(CanvasSource.APPLE_MUSIC, acceptedParenthesized?.source)
    }

    @Test
    fun acceptSongCandidateNormalizesRequestedTitleLiveAndRemasterVariants() {
        val cleanCandidate =
            CanvasArtwork(
                name = "Hotel California",
                artist = "Eagles",
                videoUrl = "https://example.com/video.mp4",
            )
        val acceptedLiveRequest =
            acceptSongCandidate(
                candidate = cleanCandidate,
                provider = CanvasSource.BETTER_LYRICS,
                song = "Hotel California (Live)",
                artist = "Eagles",
                requireVertical = false,
            )
        assertNotNull(acceptedLiveRequest)
        assertEquals(CanvasSource.BETTER_LYRICS, acceptedLiveRequest?.source)

        val acceptedRemasterRequest =
            acceptSongCandidate(
                candidate = cleanCandidate,
                provider = CanvasSource.APPLE_MUSIC,
                song = "Hotel California - Remastered",
                artist = "Eagles",
                requireVertical = false,
            )
        assertNotNull(acceptedRemasterRequest)
        assertEquals(CanvasSource.APPLE_MUSIC, acceptedRemasterRequest?.source)
    }

    @Test
    fun acceptSongCandidateRejectsCandidateWithoutUsableAnimation() {
        val noAnimation =
            CanvasArtwork(
                name = "Save Your Tears",
                artist = "The Weeknd",
            )
        assertNull(
            acceptSongCandidate(
                candidate = noAnimation,
                provider = CanvasSource.BETTER_LYRICS,
                song = "Save Your Tears",
                artist = "The Weeknd",
                requireVertical = false,
            ),
        )
    }
}
