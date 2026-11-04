package moe.rukamori.archivetune.ui.player

import androidx.media3.common.Player
import java.lang.reflect.Proxy
import moe.rukamori.archivetune.ui.player.canvas.playback.CanvasPlaybackController
import moe.rukamori.archivetune.ui.player.canvas.playback.setCanvasPlayback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasPlaybackLifecycleTest {
    @Test
    fun pauseAndResumeDoNotPrepareOrSeek() {
        val calls = mutableListOf<String>()
        val player = recordingPlayer(calls)

        player.setCanvasPlayback(isPlaying = true, isStarted = true)
        player.setCanvasPlayback(isPlaying = false, isStarted = true)
        player.setCanvasPlayback(isPlaying = true, isStarted = true)

        assertEquals(listOf("play", "pause", "play"), calls)
    }

    @Test
    fun backgroundPausesDespiteActiveAudioAndResumesWithoutReloading() {
        val calls = mutableListOf<String>()
        val player = recordingPlayer(calls)

        player.setCanvasPlayback(isPlaying = true, isStarted = false)
        player.setCanvasPlayback(isPlaying = true, isStarted = true)

        assertEquals(listOf("pause", "play"), calls)
    }

    @Test
    fun inactiveAudioNeverStartsCanvas() {
        val calls = mutableListOf<String>()
        val player = recordingPlayer(calls)

        player.setCanvasPlayback(isPlaying = false, isStarted = false)
        player.setCanvasPlayback(isPlaying = false, isStarted = true)

        assertEquals(listOf("pause", "pause"), calls)
    }

    @Test
    fun controllerSyncsPlaybackToPlayerState() {
        val calls = mutableListOf<String>()
        val player = recordingPlayer(calls)
        val controller = CanvasPlaybackController(player = player)

        controller.syncPlayback(isPlaying = true, isStarted = true)
        controller.syncPlayback(isPlaying = false, isStarted = true)
        controller.syncPlayback(isPlaying = true, isStarted = false)

        assertEquals(listOf("play", "pause", "pause"), calls)
    }

    @Test
    fun controllerSwitchesToFallbackOnErrorAndStopsWhenExhausted() {
        val calls = mutableListOf<String>()
        val player = recordingPlayer(calls)
        val controller =
            CanvasPlaybackController(
                player = player,
                initialUrl = "https://example.com/primary.m3u8",
            )

        val readyEvents = mutableListOf<Boolean>()
        controller.onReadyChange = { readyEvents += it }

        controller.handlePlayerError(
            error = null,
            primary = "https://example.com/primary.m3u8",
            fallback = "https://example.com/fallback.mp4",
        )

        assertEquals("https://example.com/fallback.mp4", controller.currentUrl)
        assertFalse(controller.hasFailed)
        assertFalse(controller.isVideoReady)
        assertEquals(listOf(false), readyEvents)
        assertTrue(calls.isEmpty())

        controller.handlePlayerError(
            error = null,
            primary = "https://example.com/primary.m3u8",
            fallback = "https://example.com/fallback.mp4",
        )

        assertTrue(controller.hasFailed)
        assertFalse(controller.isVideoReady)
        assertEquals(listOf("stop"), calls)
    }

    @Test
    fun controllerFirstFrameSetsVideoReadyAndStartsPlaybackIfPlaying() {
        val calls = mutableListOf<String>()
        val player = recordingPlayer(calls)
        val controller = CanvasPlaybackController(player = player)

        assertFalse(controller.isVideoReady)
        controller.handleFirstFrameRendered(isPlaying = true, isStarted = true)

        assertTrue(controller.isVideoReady)
        assertEquals(listOf("play"), calls)
    }

    @Test
    fun controllerReleaseDelegatesToPlayer() {
        val calls = mutableListOf<String>()
        val player = recordingPlayer(calls)
        val controller = CanvasPlaybackController(player = player)

        controller.release()
        assertEquals(listOf("release"), calls)
    }

    private fun recordingPlayer(calls: MutableList<String>): Player =
        Player::class.java.cast(
            Proxy.newProxyInstance(
                Player::class.java.classLoader,
                arrayOf(Player::class.java),
            ) { _, method, _ ->
                calls += method.name
                null
            },
        )
}
