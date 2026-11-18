package moe.rukamori.archivetune.app.init

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Binds heavy background work to the splash: anything that boots WebViews or hits the network
 * waits here so it never competes with the opening animation for the main thread.
 */
object SplashGate {
    private val splashDone = MutableStateFlow(false)

    fun onSplashDone() {
        splashDone.value = true
    }

    fun skip() {
        onSplashDone()
    }

    suspend fun awaitSplashDone(timeoutMs: Long = 3000L) {
        if (splashDone.value) return
        withTimeoutOrNull(timeoutMs) {
            splashDone.first { it }
        }
    }
}
