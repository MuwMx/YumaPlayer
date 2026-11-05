/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlistcopy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistCopyManager
    @Inject
    constructor(
        private val copier: PlaylistCopier,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val lock = Any()

        private var job: Job? = null
        private var generation = 0
        private var lastRequest: PlaylistCopyRequest? = null

        private val _state = MutableStateFlow<PlaylistCopyState>(PlaylistCopyState.Idle)
        val state: StateFlow<PlaylistCopyState> = _state.asStateFlow()

        fun start(request: PlaylistCopyRequest) {
            synchronized(lock) {
                job?.cancel()
                val current = ++generation
                lastRequest = request
                _state.value = PlaylistCopyState.Running(PlaylistCopyProgress(PlaylistCopyStage.READING))
                job =
                    scope.launch {
                        val outcome = copier.copy(request) { progress -> publish(current, PlaylistCopyState.Running(progress)) }
                        synchronized(lock) {
                            if (current != generation) return@launch
                            if (outcome is PlaylistCopyState.Success) {
                                lastRequest = request.copy(destination = PlaylistCopyDestination.Existing(outcome.result.playlistId))
                            }
                            _state.value = outcome
                        }
                    }
            }
        }

        fun retry() {
            val request = synchronized(lock) { lastRequest } ?: return
            start(request)
        }

        fun dismiss() {
            synchronized(lock) {
                job?.cancel()
                job = null
                generation++
                _state.value = PlaylistCopyState.Idle
            }
        }

        private fun publish(
            current: Int,
            state: PlaylistCopyState,
        ) {
            synchronized(lock) {
                if (current == generation) _state.value = state
            }
        }
    }
