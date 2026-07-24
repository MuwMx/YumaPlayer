/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.utils

import androidx.compose.runtime.mutableStateOf

class ItemWrapper<T>(
    val item: T,
) {
    private val _isSelected = mutableStateOf(false)

    var isSelected: Boolean
        get() = _isSelected.value
        set(value) {
            _isSelected.value = value
        }
}
