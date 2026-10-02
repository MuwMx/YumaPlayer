/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.viewmodels.AboutScreenState

internal object AboutContract {
    const val SOLANA_ADDRESS = "DT3ckdbNuiQMR1mrCpBXCMrhLB19GckVv3YfxsLLiF8z"
    const val SOLANA_LABEL = "Solana (SOL)"
    const val GPL_LICENSE_NOTICE = "Based on ArchiveTune by Rukamori.\nSource code available under GPL-3.0."
}

typealias AboutUiState = AboutScreenState

@Immutable
data class AboutUiActions(
    val onNavigateUp: () -> Unit = {},
    val onNavigateHome: () -> Unit = {},
    val onOpenUri: (String) -> Unit = {},
    val onShowOverflowMenu: () -> Unit = {},
    val onDismissOverflowMenu: () -> Unit = {},
    val onOpenTranslationContributors: () -> Unit = {},
    val onOpenDependencyLicenses: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
    val onRetryTranslationContributors: () -> Unit = {},
    val onRetryDependencyLicenses: () -> Unit = {},
)

typealias AboutActions = AboutUiActions
