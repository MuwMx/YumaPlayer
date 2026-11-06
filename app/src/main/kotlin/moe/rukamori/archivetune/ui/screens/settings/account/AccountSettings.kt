/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.spotify.SpotifyAccountViewModel
import moe.rukamori.archivetune.ui.screens.login.buildLoginRoute
import moe.rukamori.archivetune.ui.screens.settings.account.AccountSettingsContent
import moe.rukamori.archivetune.ui.screens.settings.account.AccountSettingsViewModel
import moe.rukamori.archivetune.ui.screens.settings.account.actions.createAccountActionHandlers
import moe.rukamori.archivetune.ui.screens.settings.account.header.ProfileAccountMenu
import moe.rukamori.archivetune.ui.screens.settings.account.modals.rememberAccountModalsCoordinator
import moe.rukamori.archivetune.ui.screens.settings.account.providers.AccountDialogsHost
import moe.rukamori.archivetune.ui.screens.settings.account.session.rememberAccountSessionBinding
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.Updater
import moe.rukamori.archivetune.home.HomeViewModel

@Composable
fun AccountSettings(
    navController: NavController,
    updateState: UpdateState,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = appBarScrollBehavior()

    val accountLabel = stringResource(R.string.account)
    val loginLabel = stringResource(R.string.login)
    val tokenDescription = stringResource(R.string.token_adv_login_description)

    val session = rememberAccountSessionBinding(context)

    val viewModel: HomeViewModel = hiltViewModel()
    val accountNameFromViewModel by viewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by viewModel.accountImageUrl.collectAsStateWithLifecycle()
    val accountChannelsState by viewModel.accountChannelsState.collectAsStateWithLifecycle()

    val accountSettingsViewModel: AccountSettingsViewModel = hiltViewModel()
    val accountUiState by accountSettingsViewModel.uiState.collectAsStateWithLifecycle()

    val spotifyAccountViewModel: SpotifyAccountViewModel = hiltViewModel()
    val spotifyState by spotifyAccountViewModel.uiState.collectAsStateWithLifecycle()

    val actions =
        createAccountActionHandlers(
            viewModel = viewModel,
            session = session,
            accountNameFromViewModel = accountNameFromViewModel,
            context = context,
        )

    val modals =
        rememberAccountModalsCoordinator(
            session = session,
            spotifyAccountViewModel = spotifyAccountViewModel,
            spotifyState = spotifyState,
            navController = navController,
            onSaveCurrentAccount = actions.saveCurrentAccount,
        )

    val displayName =
        when {
            accountNameFromViewModel.isNotBlank() -> accountNameFromViewModel
            session.accountNamePref.isNotBlank() -> session.accountNamePref
            session.isLoggedIn -> accountLabel
            else -> loginLabel
        }

    val hasUpdate = updateState is UpdateState.SoftUpdate || updateState is UpdateState.CriticalUpdate
    val latestVersionName = when (updateState) {
        is UpdateState.SoftUpdate -> updateState.versionName
        is UpdateState.CriticalUpdate -> updateState.versionName
        else -> BuildConfig.VERSION_NAME
    }

    val uiState =
        AccountSettingsUiState(
            displayName = displayName,
            accountEmail = session.accountEmail,
            accountHandle = session.accountChannelHandle,
            accountImageUrl = accountImageUrl,
            isLoggedIn = session.isLoggedIn,
            savedAccounts = session.savedAccounts,
            activeInnerTubeCookie = session.innerTubeCookie,
            activeDataSyncId = session.dataSyncId,
            accountChannelsState = accountChannelsState,
            extractedColorHex = accountUiState.extractedColorHex,
            useLoginForBrowse = session.useLoginForBrowse,
            ytmSync = session.ytmSync,
            forceSyncOnAccountSwitch = session.forceSyncOnAccountSwitch,
            spotifyState = spotifyState,
            useSpotifyHome = session.useSpotifyHome,
            showSpotifyPlaylists = session.showSpotifyPlaylists,
            spotifySyncLikes = session.spotifySyncLikes,
            hideYtmLikedSongs = session.hideYtmLikedSongs,
            hasUpdate = hasUpdate,
            latestVersionName = latestVersionName,
            showToken = modals.showToken,
            tokenActionTitle = modals.tokenActionTitle,
            tokenDescription = tokenDescription,
            tokenPreview = modals.tokenPreview,
        )

    val uiActions =
        AccountSettingsUiActions(
            onNavigateUp = navController::navigateUp,
            onNavigateHome = navController::backToMain,
            onOpenTokenEditor = modals.openTokenEditor,
            onOpenAccountMenu = modals.openAccountMenu,
            onOpenUpdateDownload = { uriHandler.openUri(Updater.getLatestDownloadUrl()) },
            onAvatarPixelsReady = accountSettingsViewModel::processAvatarPixels,
            onPrimaryAction = {
                if (session.isLoggedIn) navController.navigate("account") else modals.openLoginChoice()
            },
            onSecondaryAction = {
                if (session.isLoggedIn) {
                    modals.hideToken()
                    actions.logoutCurrentAccount()
                } else {
                    modals.openTokenEditor()
                }
            },
            onSaveAccount = actions.saveCurrentAccount,
            onSwitchAccount = actions.switchToAccount,
            onSwitchAccountChannel = actions.switchToAccountChannel,
            onRemoveAccount = actions.removeAccount,
            onAddAnotherAccount = {
                val isSaved = session.savedAccounts.accounts.any { it.innerTubeCookie == session.innerTubeCookie }
                if (session.isLoggedIn && !isSaved) modals.openUnsavedAccountDialog() else navController.navigate(buildLoginRoute())
            },
            onUseLoginForBrowseChange = session.onUseLoginForBrowseChange,
            onYtmSyncChange = session.onYtmSyncChange,
            onForceSyncOnAccountSwitchChange = session.onForceSyncOnAccountSwitchChange,
            onSpotifyClick = {
                if (spotifyState.isAuthenticated) modals.openSpotifyOptions() else modals.openSpotifyLogin()
            },
            onUseSpotifyHomeChange = { isSpotify ->
                if (isSpotify && !spotifyState.isAuthenticated) modals.openSpotifyLogin() else session.onUseSpotifyHomeChange(isSpotify)
            },
            onSpotifySyncLikesChange = session.onSpotifySyncLikesChange,
            onHideYtmLikedSongsChange = session.onHideYtmLikedSongsChange,
            onNavigateIntegration = { navController.navigate("settings/integration") },
            onNavigateMusicTogether = { navController.navigate("settings/music_together") },
            onNavigateHiddenPlaylists = { navController.navigate("settings/hidden_playlists") },
            onTokenRowClick = modals.openTokenEditor,
            onRevealToken = modals.revealToken,
        )

    AccountSettingsContent(
        state = uiState,
        actions = uiActions,
        scrollBehavior = scrollBehavior,
    )

    AccountDialogsHost(
        state = modals.dialogsState,
        actions = modals.dialogsActions,
    )
    if (modals.showAccountMenu) {
        ProfileAccountMenu(
            expanded = true,
            onDismissRequest = modals.dismissAccountMenu,
            accountChannelsState = uiState.accountChannelsState,
            activeDataSyncId = uiState.activeDataSyncId,
            onSwitchAccountChannel = { channel ->
                modals.dismissAccountMenu()
                uiActions.onSwitchAccountChannel(channel)
            },
            savedAccounts = uiState.savedAccounts,
            activeInnerTubeCookie = uiState.activeInnerTubeCookie,
            onRemoveAccount = uiActions.onRemoveAccount,
            onSwitchAccount = { account ->
                modals.dismissAccountMenu()
                uiActions.onSwitchAccount(account)
            },
            isLoggedIn = uiState.isLoggedIn,
            onSaveAccount = {
                uiActions.onSaveAccount()
            },
            onAddAnotherAccount = {
                modals.dismissAccountMenu()
                uiActions.onAddAnotherAccount()
            },
            onLogout = {
                modals.dismissAccountMenu()
                uiActions.onSecondaryAction()
            },
            onBrowserLogin = {
                modals.dismissAccountMenu()
                modals.dialogsActions.onBrowserLogin()
            },
            onAdvancedLogin = {
                modals.dismissAccountMenu()
                modals.dialogsActions.onAdvancedLogin()
            },
        )
    }
}

@ThemePreviews
@Composable
private fun AccountSettingsPreview() {
    TestThemeWrapper {
        AccountSettings(
            navController = rememberNavController(),
            updateState = UpdateState.NoUpdate,
        )
    }
}
