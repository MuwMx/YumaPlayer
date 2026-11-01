/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.account.actions

import android.content.Context
import moe.rukamori.archivetune.App.Companion.forgetAccount
import moe.rukamori.archivetune.home.state.AccountChannelUiModel
import moe.rukamori.archivetune.ui.screens.settings.account.session.AccountSessionData
import moe.rukamori.archivetune.utils.SavedAccount
import moe.rukamori.archivetune.utils.decodeSavedAccounts
import moe.rukamori.archivetune.utils.encodeSavedAccounts
import moe.rukamori.archivetune.home.HomeViewModel
import java.util.UUID

internal class AccountActionHandlers(
    val saveCurrentAccount: () -> Unit,
    val switchToAccount: (SavedAccount) -> Unit,
    val switchToAccountChannel: (AccountChannelUiModel) -> Unit,
    val removeAccount: (SavedAccount) -> Unit,
    val logoutCurrentAccount: () -> Unit,
)

internal fun createAccountActionHandlers(
    viewModel: HomeViewModel,
    session: AccountSessionData,
    accountNameFromViewModel: String,
    context: Context,
): AccountActionHandlers {
    val saveCurrentAccount: () -> Unit = {
        val existing = decodeSavedAccounts(session.savedAccountsJson)
        if (session.isLoggedIn && existing.none { it.innerTubeCookie == session.innerTubeCookie }) {
            val newAccount =
                SavedAccount(
                    id = UUID.randomUUID().toString(),
                    name = if (accountNameFromViewModel.isNotBlank()) accountNameFromViewModel else session.accountNamePref,
                    email = session.accountEmail,
                    channelHandle = session.accountChannelHandle,
                    innerTubeCookie = session.innerTubeCookie,
                    visitorData = session.visitorData,
                    dataSyncId = session.dataSyncId,
                    ytmSync = session.ytmSync,
                    selectedYtmPlaylists = session.selectedYtmPlaylists,
                )
            session.onSavedAccountsJsonChange(encodeSavedAccounts(existing + newAccount))
        }
    }

    val switchToAccount: (SavedAccount) -> Unit = { account ->
        viewModel.switchToAccount(account = account, forceSyncOnSwitch = session.forceSyncOnAccountSwitch)
    }

    val switchToAccountChannel: (AccountChannelUiModel) -> Unit = { channel ->
        viewModel.switchToAccountChannel(channel = channel, forceSyncOnSwitch = session.forceSyncOnAccountSwitch)
    }

    val removeAccount: (SavedAccount) -> Unit = { account ->
        val existing = decodeSavedAccounts(session.savedAccountsJson)
        session.onSavedAccountsJsonChange(encodeSavedAccounts(existing.filter { it.id != account.id }))
    }

    val logoutCurrentAccount: () -> Unit = {
        session.onInnerTubeCookieChange("")
        forgetAccount(context, clearWebAuthSession = true)
    }

    return AccountActionHandlers(
        saveCurrentAccount = saveCurrentAccount,
        switchToAccount = switchToAccount,
        switchToAccountChannel = switchToAccountChannel,
        removeAccount = removeAccount,
        logoutCurrentAccount = logoutCurrentAccount,
    )
}
