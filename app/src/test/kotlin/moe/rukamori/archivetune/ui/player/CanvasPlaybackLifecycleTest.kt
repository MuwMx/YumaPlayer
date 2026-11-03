package moe.rukamori.archivetune.ui.player

import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
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
