/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.playback.engine.getOrCreateStreamPrefetcher
import moe.rukamori.archivetune.playback.service.resolvePlaybackDataSpec
import moe.rukamori.archivetune.spotify.MatchPriority
import moe.rukamori.archivetune.spotify.SpotifyPlaceholders
import moe.rukamori.archivetune.utils.isLocalMediaId
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

internal class SpotifyPlaceholderLookahead(
    private val service: MusicService,
) {
    private val warming = ConcurrentHashMap.newKeySet<String>()
    private val warmedAt = ConcurrentHashMap<String, Long>()
    private val permits = Semaphore(PARALLELISM)
    private var backgroundJob: Job? = null

    fun onQueuePosition(currentIndex: Int) {
        runCatching { prepareAround(currentIndex) }
            .onFailure { Timber.tag(TAG).w(it, "Lookahead skipped") }
    }

    private fun prepareAround(currentIndex: Int) {
        val player = service.player
        val timeline = player.currentTimeline
        val count = player.mediaItemCount
        if (timeline.isEmpty || timeline.windowCount != count || currentIndex !in 0 until count) return
        val shuffle = player.shuffleModeEnabled

        fun walk(
            steps: Int,
            forward: Boolean,
        ): List<Int> {
            val indices = ArrayList<Int>(steps)
            var index = currentIndex
            for (step in 0 until steps) {
                index =
                    if (forward) {
                        timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
                    } else {
                        timeline.getPreviousWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
                    }
                if (index == C.INDEX_UNSET || index !in 0 until count) break
                indices += index
            }
            return indices
        }

        val ahead = walk(AHEAD, forward = true)
        val behind = walk(BEHIND, forward = false)
        val nextIndex = ahead.firstOrNull()

        val near = buildList {
            nextIndex?.let(::add)
            for (i in 0 until maxOf(ahead.size, behind.size)) {
                behind.getOrNull(i)?.let(::add)
                ahead.getOrNull(i + 1)?.let(::add)
            }
        }
        val further =
            (walk(BACKGROUND_AHEAD, forward = true).drop(AHEAD) + walk(BACKGROUND_BEHIND, forward = false).drop(BEHIND))
                .map { player.getMediaItemAt(it).mediaId }
                .filter { SpotifyPlaceholders.isPlaceholder(it) && !SpotifyPlaceholders.isKnownUnmatched(it) }
                .distinct()

        if (further.isEmpty() && near.none { SpotifyPlaceholders.isPlaceholder(player.getMediaItemAt(it).mediaId) }) {
            backgroundJob?.cancel()
            return
        }
        near.forEach { index ->
            prepare(player.getMediaItemAt(index), isNext = index == nextIndex)
        }
        matchInBackground(further)
    }

    private fun prepare(
        item: MediaItem,
        isNext: Boolean,
    ) {
        val mediaId = item.mediaId
        if (mediaId.isBlank() || mediaId.isLocalMediaId() || mediaId.startsWith("/")) return
        val isPlaceholder = SpotifyPlaceholders.isPlaceholder(mediaId)
        if (!isPlaceholder && isNext) return
        if (isFresh(mediaId) && !isNext) return
        if (!warming.add(mediaId)) return

        service.ioScope.launch {
            try {
                permits.withPermit {
                    val youtubeId =
                        if (isPlaceholder) {
                            SpotifyPlaceholders.resolve(mediaId, service.database, MatchPriority.UPCOMING)?.id
                                ?: return@withPermit
                        } else {
                            mediaId
                        }
                    warmStreamUrl(youtubeId)
                    val now = System.currentTimeMillis()
                    warmedAt.values.removeIf { now - it >= WARM_TTL_MS }
                    warmedAt[mediaId] = now
                    if (isNext) {
                        withContext(Dispatchers.Main) {
                            if (service.player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }
                                    ?.let { service.player.getMediaItemAt(it).mediaId } == mediaId
                            ) {
                                service.getOrCreateStreamPrefetcher().prefetchMedia(youtubeId, item)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Could not prepare %s", mediaId)
            } finally {
                warming.remove(mediaId)
            }
        }
    }

    private fun warmStreamUrl(youtubeId: String) {
        val startedAt = System.currentTimeMillis()
        service.resolvePlaybackDataSpec(
            dataSpec = DataSpec.Builder().setUri(youtubeId).setKey(youtubeId).build(),
            allowCacheShortCircuit = true,
        )
        Timber.tag(TAG).d("Warmed %s in %dms", youtubeId, System.currentTimeMillis() - startedAt)
    }

    private fun isFresh(mediaId: String): Boolean {
        val at = warmedAt[mediaId] ?: return false
        return System.currentTimeMillis() - at < WARM_TTL_MS
    }

    private fun matchInBackground(placeholderIds: List<String>) {
        backgroundJob?.cancel()
        if (placeholderIds.isEmpty()) return
        backgroundJob =
            service.ioScope.launch {
                for (id in placeholderIds) {
                    if (!isActive) break
                    try {
                        SpotifyPlaceholders.resolve(id, service.database, MatchPriority.BACKGROUND)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Could not match %s", id)
                    }
                }
            }
    }

    private companion object {
        const val TAG = "PlaceholderLookahead"
        const val AHEAD = 4
        const val BEHIND = 2
        const val BACKGROUND_AHEAD = 25
        const val BACKGROUND_BEHIND = 5
        const val PARALLELISM = 2

        const val WARM_TTL_MS = 30 * 60 * 1000L
    }
}
