package moe.rukamori.archivetune.ui.state

import android.graphics.Color

sealed interface UpdateState {
    data object NoUpdate : UpdateState
    data class SoftUpdate(
        val versionName: String,
        val updateUrl: String,
        val changelog: String,
        val accentColor: Color? = null,
        val isOverlayDismissed: Boolean = false

    ) : UpdateState
    data class CriticalUpdate(
        val versionName: String,
        val updateUrl: String,
        val changelog: String,
        val accentColor: Color? = null,
        val isOverlayDismissed: Boolean = false
    ) : UpdateState
}