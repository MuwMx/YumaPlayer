/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.artist

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveArtistBlockedUseCase
    @Inject
    constructor(
        private val repository: ArtistBlockRepository,
    ) {
        operator fun invoke(artistId: String): Flow<Boolean?> = repository.observeBlocked(artistId)
    }

class SetArtistBlockedUseCase
    @Inject
    constructor(
        private val repository: ArtistBlockRepository,
    ) {
        suspend operator fun invoke(request: ArtistBlockRequest) {
            require(request.id.isNotBlank())
            require(request.name.isNotBlank())
            repository.setBlocked(request)
        }
    }
