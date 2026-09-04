package moe.rukamori.archivetune.flaccore.streaming

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.flaccore.FlacLogger
import moe.rukamori.archivetune.flaccore.model.TrackQuery

/**
 * Walks the streaming-source roster in priority order and returns the first match.
 *
 * Ported from Stash core/media/streaming/StreamSourceRegistry.kt
 */
class FlacStreamRegistry(
    private val qbdlx: suspend (TrackQuery, Int) -> FlacStreamUrl?,
) {
    private val logger = FlacLogger(TAG)
    var timeoutMs = 35_000L

    suspend fun resolve(query: TrackQuery, requestedQuality: Int): FlacStreamUrl? {
        val resolvers = listOf(
            "qbdlx" to qbdlx,
        )

        logger.i("chain for '${query.title}': [${resolvers.joinToString(",") { it.first }}]")

        for ((name, fn) in resolvers) {
            val result = runCatching {
                withTimeout(timeoutMs) {
                    fn(query, requestedQuality)
                }
            }.onFailure { e ->
                if (e is TimeoutCancellationException) {
                    logger.w("$name timed out after ${timeoutMs}ms")
                    return@onFailure
                }
                if (e is CancellationException) throw e
                logger.w("$name threw on resolve for '${query.title}': ${e.message}")
            }.getOrNull()

            if (result != null) {
                logger.i("$name served '${query.title}'")
                return result
            }
        }
        return null
    }

    private companion object {
        private const val TAG = "FlacStreamRegistry"
    }
}
