/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.appicon

import javax.inject.Inject

class LoadAppIconsUseCase
    @Inject
    constructor(
        private val repository: AppIconRepository,
    ) {
        suspend operator fun invoke(): AppIconCatalog = repository.loadCatalog()
    }

class SelectAppIconUseCase
    @Inject
    constructor(
        private val repository: AppIconRepository,
    ) {
        suspend operator fun invoke(iconId: String): AppIconCatalog = repository.selectIcon(iconId)
    }
