/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.account.modals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.spotify.SpotifyAccountUiState
import moe.rukamori.archivetune.spotify.SpotifyAccountViewModel
import moe.rukamori.archivetune.ui.screens.login.buildLoginRoute
import moe.rukamori.archivetune.ui.screens.settings.AccountDialogsActions
import moe.rukamori.archivetune.ui.screens.settings.AccountDialogsState
import moe.rukamori.archivetune.ui.screens.settings.TokenEditorDialogActions
import moe.rukamori.archivetune.ui.screens.settings.TokenEditorDialogState
import moe.rukamori.archivetune.ui.screens.settings.account.session.AccountSessionData
import moe.rukamori.archivetune.ui.screens.settings.previewSecureValue
import kotlin.time.Duration.Companion.milliseconds

internal class AccountModalsWiring(
    val showAccountMenu: Boolean,
    val openAccountMenu: () -> Unit,
    val dismissAccountMenu: () -> Unit,
    val showToken: Boolean,
    val tokenActionTitle: String,
    val tokenPreview: String?,
    val revealToken: () -> Unit,
    val hideToken: () -> Unit,
    val openTokenEditor: () -> Unit,
    val openSpotifyLogin: () -> Unit,
    val openSpotifyOptions: () -> Unit,
    val openLoginChoice: () -> Unit,
    val openUnsavedAccountDialog: () -> Unit,
    val dialogsState: AccountDialogsState,
    val dialogsActions: AccountDialogsActions,
)

@Composable
internal fun rememberAccountModalsCoordinator(
    session: AccountSessionData,
    spotifyAccountViewModel: SpotifyAccountViewModel,
    spotifyState: SpotifyAccountUiState,
    navController: NavController,
    onSaveCurrentAccount: () -> Unit,
): AccountModalsWiring {
    var showAccountMenu by remember { mutableStateOf(false) }
    var showSpotifyOptionsDialog by remember { mutableStateOf(false) }
    var showSpotifyLogin by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    var showTokenEditor by remember { mutableStateOf(false) }
    var showUnsavedAccountDialog by remember { mutableStateOf(false) }
    var showLoginChoiceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(session.isLoggedIn) {
        if (!session.isLoggedIn) {
            showToken = false
        }
    }

    LaunchedEffect(showToken) {
        if (showToken) {
            delay(5000.milliseconds)
            showToken = false
        }
    }

    val tokenActionTitle =
        when {
            !session.isLoggedIn -> stringResource(R.string.advanced_login)
            showToken -> stringResource(R.string.token_shown)
            else -> stringResource(R.string.token_hidden)
        }

    val tokenPreview =
        if (session.isLoggedIn && session.innerTubeCookie.isNotBlank()) {
            previewSecureValue(session.innerTubeCookie)
        } else {
            null
        }

    val dialogsState =
        AccountDialogsState(
            showSpotifyLogin = showSpotifyLogin,
            showSpotifyOptionsDialog = showSpotifyOptionsDialog,
            showLoginChoiceDialog = showLoginChoiceDialog,
            showTokenEditor = showTokenEditor,
            showUnsavedAccountDialog = showUnsavedAccountDialog,
            spotifyState = spotifyState,
            showSpotifyPlaylists = session.showSpotifyPlaylists,
            tokenEditorState =
                TokenEditorDialogState(
                    innerTubeCookie = session.innerTubeCookie,
                    visitorData = session.visitorData,
                    dataSyncId = session.dataSyncId,
                    accountNamePref = session.accountNamePref,
                    accountEmail = session.accountEmail,
                    accountChannelHandle = session.accountChannelHandle,
                ),
        )

    val dialogsActions =
        AccountDialogsActions(
            onDismissSpotifyLogin = { showSpotifyLogin = false },
            onCookiesCaptured = { spDc, spKey ->
                spotifyAccountViewModel.connectWithCookies(spDc = spDc, spKey = spKey)
                showSpotifyLogin = false
            },
            onShowSpotifyPlaylistsChange = session.onShowSpotifyPlaylistsChange,
            onConnectSpotify = {
                showSpotifyOptionsDialog = false
                showSpotifyLogin = true
            },
            onSpotifyLogout = {
                spotifyAccountViewModel.logout()
                showSpotifyOptionsDialog = false
            },
            onDismissSpotifyOptions = { showSpotifyOptionsDialog = false },
            onDismissLoginChoice = { showLoginChoiceDialog = false },
            onBrowserLogin = {
                showLoginChoiceDialog = false
                navController.navigate(buildLoginRoute())
            },
            onAdvancedLogin = {
                showLoginChoiceDialog = false
                showTokenEditor = true
            },
            tokenEditorActions =
                TokenEditorDialogActions(
                    onInnerTubeCookieChange = session.onInnerTubeCookieChange,
                    onPoTokenChange = session.onLegacyPoTokenChange,
                    onVisitorDataChange = session.onVisitorDataChange,
                    onDataSyncIdChange = session.onDataSyncIdChange,
                    onAccountNameChange = session.onAccountNameChange,
                    onAccountEmailChange = session.onAccountEmailChange,
                    onAccountChannelHandleChange = session.onAccountChannelHandleChange,
                    onDismiss = { showTokenEditor = false },
                ),
            onDismissUnsavedAccount = { showUnsavedAccountDialog = false },
            onCancelUnsavedAccount = { showUnsavedAccountDialog = false },
            onNoThanksUnsavedAccount = {
                showUnsavedAccountDialog = false
                navController.navigate(buildLoginRoute())
            },
            onSaveYesUnsavedAccount = {
                showUnsavedAccountDialog = false
                onSaveCurrentAccount()
                navController.navigate(buildLoginRoute())
            },
        )

    return AccountModalsWiring(
        showAccountMenu = showAccountMenu,
        openAccountMenu = { showAccountMenu = true },
        dismissAccountMenu = { showAccountMenu = false },
        showToken = showToken,
        tokenActionTitle = tokenActionTitle,
        tokenPreview = tokenPreview,
        revealToken = { showToken = true },
        hideToken = { showToken = false },
        openTokenEditor = { showTokenEditor = true },
        openSpotifyLogin = { showSpotifyLogin = true },
        openSpotifyOptions = { showSpotifyOptionsDialog = true },
        openLoginChoice = { showLoginChoiceDialog = true },
        openUnsavedAccountDialog = { showUnsavedAccountDialog = true },
        dialogsState = dialogsState,
        dialogsActions = dialogsActions,
    )
}
