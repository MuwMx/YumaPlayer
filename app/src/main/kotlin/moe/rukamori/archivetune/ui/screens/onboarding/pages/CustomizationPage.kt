/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.onboarding.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.App.Companion.forgetAccount
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.onboarding.OnboardingUiState
import moe.rukamori.archivetune.spotify.SpotifyAccountViewModel
import moe.rukamori.archivetune.ui.screens.login.LoginScreen
import moe.rukamori.archivetune.ui.screens.onboarding.components.ExpressivePageHeader
import moe.rukamori.archivetune.ui.screens.onboarding.components.OnboardingContentMaxWidth
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.ui.screens.settings.SpotifyLoginSheet
import moe.rukamori.archivetune.ui.screens.settings.account.component.ExpressiveSegmentedRow
import moe.rukamori.archivetune.ui.screens.settings.account.header.ProfileIdentityCard
import moe.rukamori.archivetune.ui.screens.settings.account.providers.TokenEditorDialog
import moe.rukamori.archivetune.ui.screens.settings.player.FlacTokenInputs
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.SavedAccount
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.encodeSavedAccounts
import moe.rukamori.archivetune.utils.putLegacyPoToken
import java.util.UUID

@Composable
internal fun CustomizationPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
) {
    val page = uiState.pages[pageIndex]
    val context = LocalContext.current

    val accountSettingsViewModel: moe.rukamori.archivetune.ui.screens.settings.account.AccountSettingsViewModel = hiltViewModel()
    val accountUiState by accountSettingsViewModel.uiState.collectAsStateWithLifecycle()

    val spotifyAccountViewModel: SpotifyAccountViewModel = hiltViewModel()
    val spotifyState by spotifyAccountViewModel.uiState.collectAsStateWithLifecycle()

    val homeViewModel: moe.rukamori.archivetune.home.HomeViewModel = hiltViewModel()
    val accountNameFromViewModel by homeViewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsStateWithLifecycle()
    val accountChannelsState by homeViewModel.accountChannelsState.collectAsStateWithLifecycle()

    val (accountNamePref, onAccountNameChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.AccountNameKey, "")
    val (accountEmail, onAccountEmailChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.AccountEmailKey, "")
    val (accountChannelHandle, onAccountChannelHandleChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.InnerTubeCookieKey, "")
    val (visitorData, onVisitorDataChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.VisitorDataKey, "")
    val (dataSyncId, onDataSyncIdChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.DataSyncIdKey, "")
    val (savedAccountsJson, onSavedAccountsJsonChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.SavedAccountsKey, "")

    val savedAccounts = remember(savedAccountsJson) {
        moe.rukamori.archivetune.utils.SavedAccountCollection(moe.rukamori.archivetune.utils.decodeSavedAccounts(savedAccountsJson))
    }

    val isLoggedIn = remember(innerTubeCookie) {
        moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie(innerTubeCookie)
    }

    val displayName = when {
        accountNameFromViewModel.isNotBlank() -> accountNameFromViewModel
        accountNamePref.isNotBlank() -> accountNamePref
        isLoggedIn -> stringResource(R.string.account)
        else -> stringResource(R.string.login)
    }

    val (darkMode, onDarkModeChange) = moe.rukamori.archivetune.utils.rememberEnumPreference<DarkMode>(moe.rukamori.archivetune.constants.DarkModeKey, defaultValue = DarkMode.AUTO)
    val (homeBackgroundStyle, onHomeBackgroundStyleChange) = moe.rukamori.archivetune.utils.rememberEnumPreference<HomeBackgroundStyle>(moe.rukamori.archivetune.constants.HomeBackgroundStyleKey, defaultValue = HomeBackgroundStyle.TONAL)

    val (useSpotifyHome, onUseSpotifyHomeChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.UseSpotifyHomeKey, false)

    val (playbackSource, onPlaybackSourceChange) = moe.rukamori.archivetune.utils.rememberEnumPreference<PlaybackSource>(
        moe.rukamori.archivetune.constants.PlaybackSourceKey,
        defaultValue = PlaybackSource.YT_MUSIC,
    )
    val (enableLossless, onEnableLosslessChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.EnableLosslessKey, defaultValue = false)

    val (qobuzAppId, onQobuzAppIdChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.QobuzAppIdKey, "")
    val (qobuzAppSecret, onQobuzAppSecretChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.QobuzAppSecretKey, "")
    val (qobuzUserAuthToken, onQobuzUserAuthTokenChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.QobuzUserAuthTokenKey, "")

    var showTokenEditor by remember { mutableStateOf(false) }
    var showSpotifyLogin by remember { mutableStateOf(false) }
    var showWebViewLogin by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            showWebViewLogin = false
        }
    }

    val onLegacyPoTokenChange: (String) -> Unit = { value ->
        PreferenceStore.launchEdit(context.dataStore) {
            putLegacyPoToken(value)
        }
    }

    val saveCurrentAccount: () -> Unit = {
        val existing = moe.rukamori.archivetune.utils.decodeSavedAccounts(savedAccountsJson)
        if (isLoggedIn && existing.none { it.innerTubeCookie == innerTubeCookie }) {
            val newAccount =
                SavedAccount(
                    id = UUID.randomUUID().toString(),
                    name = if (accountNameFromViewModel.isNotBlank()) accountNameFromViewModel else accountNamePref,
                    email = accountEmail,
                    channelHandle = accountChannelHandle,
                    innerTubeCookie = innerTubeCookie,
                    visitorData = visitorData,
                    dataSyncId = dataSyncId,
                    ytmSync = true,
                    selectedYtmPlaylists = "",
                )
            onSavedAccountsJsonChange(encodeSavedAccounts(existing + newAccount))
        }
    }

    val lazyListState = rememberLazyListState()

    LaunchedEffect(playbackSource) {
        if (playbackSource == PlaybackSource.FLAC) {
            lazyListState.animateScrollToItem(2)
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = page.id.name, contentType = "header") {
            Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                ExpressivePageHeader(
                    iconResId = page.iconResId,
                    titleResId = page.titleResId,
                    subtitleResId = page.subtitleResId,
                )
            }
        }

        item(contentType = "customizationGroup") {
            moe.rukamori.archivetune.ui.component.PreferenceGroup(
                modifier = Modifier.widthIn(max = OnboardingContentMaxWidth).fillMaxWidth()
            ) {
                item {
                    ProfileIdentityCard(
                        isLoggedIn = isLoggedIn,
                        accountName = displayName,
                        accountEmail = accountEmail,
                        accountHandle = accountChannelHandle,
                        accountImageUrl = accountImageUrl,
                        savedAccounts = savedAccounts,
                        activeInnerTubeCookie = innerTubeCookie,
                        activeDataSyncId = dataSyncId,
                        accountChannelsState = accountChannelsState,
                        extractedColorHex = accountUiState.extractedColorHex,
                        onAvatarPixelsReady = accountSettingsViewModel::processAvatarPixels,
                        onPrimaryAction = {
                            if (!isLoggedIn) {
                                showWebViewLogin = true
                            }
                        },
                        onSecondaryAction = {
                            if (isLoggedIn) {
                                onInnerTubeCookieChange("")
                                forgetAccount(context, clearWebAuthSession = true)
                            } else {
                                showTokenEditor = true
                            }
                        },
                        onSaveAccount = saveCurrentAccount,
                        onSwitchAccount = { account ->
                            homeViewModel.switchToAccount(
                                account = account,
                                forceSyncOnSwitch = false,
                            )
                        },
                        onSwitchAccountChannel = { channel ->
                            homeViewModel.switchToAccountChannel(
                                channel = channel,
                                forceSyncOnSwitch = false,
                            )
                        },
                        onRemoveAccount = { account ->
                            val existing = moe.rukamori.archivetune.utils.decodeSavedAccounts(savedAccountsJson)
                            onSavedAccountsJsonChange(encodeSavedAccounts(existing.filter { it.id != account.id }))
                        },
                        onAddAnotherAccount = {
                            showWebViewLogin = true
                        },
                    )
                }

                item {
                    ExpressiveSegmentedRow(
                        icon = painterResource(if (useSpotifyHome) R.drawable.spotify_icon else R.drawable.yt_music_icon),
                        title = stringResource(R.string.home_screen_provider),
                        subtitle = stringResource(R.string.home_screen_provider_desc),
                        selectedValue = useSpotifyHome,
                        onValueSelected = { isSpotify ->
                            if (isSpotify && !spotifyState.isAuthenticated) {
                                showSpotifyLogin = true
                            } else {
                                onUseSpotifyHomeChange(isSpotify)
                            }
                        },
                    )
                }

                item {
                    moe.rukamori.archivetune.ui.screens.settings.DarkModeSelector(
                        darkMode = darkMode,
                        onDarkModeChange = onDarkModeChange
                    )
                }

                item {
                    moe.rukamori.archivetune.ui.screens.settings.HomeBackgroundSelector(
                        homeBackgroundStyle = homeBackgroundStyle,
                        onHomeBackgroundStyleChange = onHomeBackgroundStyleChange
                    )
                }

                item {
                    moe.rukamori.archivetune.ui.screens.settings.player.PlaybackSourceSelector(
                        playbackSource = playbackSource,
                        onPlaybackSourceChange = onPlaybackSourceChange,
                        onEnableLosslessChange = onEnableLosslessChange
                    )
                }
            }
        }

        if (playbackSource == PlaybackSource.FLAC) {
            item(contentType = "flacTokens") {
                moe.rukamori.archivetune.ui.component.PreferenceGroup(
                    modifier = Modifier.widthIn(max = OnboardingContentMaxWidth).fillMaxWidth()
                ) {
                    FlacTokenInputs(
                        qobuzAppId = qobuzAppId,
                        onQobuzAppIdChange = onQobuzAppIdChange,
                        qobuzAppSecret = qobuzAppSecret,
                        onQobuzAppSecretChange = onQobuzAppSecretChange,
                        qobuzUserAuthToken = qobuzUserAuthToken,
                        onQobuzUserAuthTokenChange = onQobuzUserAuthTokenChange,
                    )
                }
            }
        }
    }

    if (showTokenEditor) {
        TokenEditorDialog(
            innerTubeCookie = innerTubeCookie,
            visitorData = visitorData,
            dataSyncId = dataSyncId,
            accountNamePref = accountNamePref,
            accountEmail = accountEmail,
            accountChannelHandle = accountChannelHandle,
            onInnerTubeCookieChange = onInnerTubeCookieChange,
            onPoTokenChange = onLegacyPoTokenChange,
            onVisitorDataChange = onVisitorDataChange,
            onDataSyncIdChange = onDataSyncIdChange,
            onAccountNameChange = onAccountNameChange,
            onAccountEmailChange = onAccountEmailChange,
            onAccountChannelHandleChange = onAccountChannelHandleChange,
            onDismiss = { showTokenEditor = false },
        )
    }

    if (showSpotifyLogin) {
        SpotifyLoginSheet(
            onDismiss = { showSpotifyLogin = false },
            onCookiesCaptured = { spDc, spKey ->
                spotifyAccountViewModel.connectWithCookies(spDc = spDc, spKey = spKey)
                onUseSpotifyHomeChange(true)
                showSpotifyLogin = false
            },
        )
    }

    if (showWebViewLogin) {
        Dialog(
            onDismissRequest = { showWebViewLogin = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            val loginNavController = remember(context) {
                object : NavHostController(context) {
                    override fun navigateUp(): Boolean {
                        showWebViewLogin = false
                        return true
                    }
                    override fun popBackStack(): Boolean {
                        showWebViewLogin = false
                        return true
                    }
                }.apply {
                    navigatorProvider.addNavigator(androidx.navigation.compose.ComposeNavigator())
                    navigatorProvider.addNavigator(androidx.navigation.compose.DialogNavigator())
                }
            }
            LoginScreen(
                navController = loginNavController,
            )
        }
    }
}
