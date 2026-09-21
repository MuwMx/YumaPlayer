/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface

import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.App.Companion.forgetAccount
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AccountChannelHandleKey
import moe.rukamori.archivetune.constants.AccountEmailKey
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.DataSyncIdKey
import moe.rukamori.archivetune.constants.ForceSyncOnAccountSwitchKey
import moe.rukamori.archivetune.constants.HideYtmLikedSongsKey
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.SavedAccountsKey
import moe.rukamori.archivetune.constants.SelectedYtmPlaylistsKey
import moe.rukamori.archivetune.constants.ShowSpotifyPlaylistsKey
import moe.rukamori.archivetune.constants.SpotifySyncLikesKey
import moe.rukamori.archivetune.constants.UseSpotifyHomeKey
import moe.rukamori.archivetune.spotify.SpotifyAccountUiState
import moe.rukamori.archivetune.spotify.SpotifyAccountViewModel
import moe.rukamori.archivetune.constants.UseLoginForBrowse
import moe.rukamori.archivetune.constants.VisitorDataKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.InfoLabel
import moe.rukamori.archivetune.ui.component.LocalPreferenceGroupPosition
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.PreferenceGroupPosition
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.component.rememberPreferenceIconShape
import moe.rukamori.archivetune.ui.screens.buildLoginRoute
import moe.rukamori.archivetune.ui.screens.settings.account.AccountSettingsViewModel
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.SavedAccount
import moe.rukamori.archivetune.utils.SavedAccountCollection
import moe.rukamori.archivetune.utils.Updater
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.decodeSavedAccounts
import moe.rukamori.archivetune.utils.encodeSavedAccounts
import moe.rukamori.archivetune.utils.putLegacyPoToken
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.AccountChannelUiModel
import moe.rukamori.archivetune.viewmodels.AccountChannelsState
import moe.rukamori.archivetune.viewmodels.HomeViewModel
import java.util.UUID
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

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

    val (accountNamePref, onAccountNameChange) = rememberPreference(AccountNameKey, "")
    val (accountEmail, onAccountEmailChange) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, onAccountChannelHandleChange) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (visitorData, onVisitorDataChange) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId, onDataSyncIdChange) = rememberPreference(DataSyncIdKey, "")
    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)
    val (forceSyncOnAccountSwitch, onForceSyncOnAccountSwitchChange) =
        rememberPreference(ForceSyncOnAccountSwitchKey, false)
    val (selectedYtmPlaylists, _) = rememberPreference(SelectedYtmPlaylistsKey, "")
    val (savedAccountsJson, onSavedAccountsJsonChange) = rememberPreference(SavedAccountsKey, "")
    val savedAccounts =
        remember(savedAccountsJson) {
            SavedAccountCollection(decodeSavedAccounts(savedAccountsJson))
        }

    val onLegacyPoTokenChange: (String) -> Unit = { value ->
        PreferenceStore.launchEdit(context.dataStore) {
            putLegacyPoToken(value)
        }
    }

    val isLoggedIn =
        remember(innerTubeCookie) {
            hasYouTubeLoginCookie(innerTubeCookie)
        }

    LaunchedEffect(useLoginForBrowse) {
        YouTube.useLoginForBrowse = useLoginForBrowse
    }

    val viewModel: HomeViewModel = hiltViewModel()
    val accountNameFromViewModel by viewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by viewModel.accountImageUrl.collectAsStateWithLifecycle()
    val accountChannelsState by viewModel.accountChannelsState.collectAsStateWithLifecycle()

    val displayName =
        when {
            accountNameFromViewModel.isNotBlank() -> accountNameFromViewModel
            accountNamePref.isNotBlank() -> accountNamePref
            isLoggedIn -> accountLabel
            else -> loginLabel
        }

    val spotifyAccountViewModel: SpotifyAccountViewModel = hiltViewModel()
    val spotifyState by spotifyAccountViewModel.uiState.collectAsStateWithLifecycle()
    val (showSpotifyPlaylists, onShowSpotifyPlaylistsChange) = rememberPreference(ShowSpotifyPlaylistsKey, true)
    val (useSpotifyHome, onUseSpotifyHomeChange) = rememberPreference(UseSpotifyHomeKey, false)
    val (spotifySyncLikes, onSpotifySyncLikesChange) = rememberPreference(SpotifySyncLikesKey, false)
    val (hideYtmLikedSongs, onHideYtmLikedSongsChange) = rememberPreference(HideYtmLikedSongsKey, false)
    var showSpotifyOptionsDialog by remember { mutableStateOf(false) }
    var showSpotifyLogin by remember { mutableStateOf(false) }

    var showToken by remember { mutableStateOf(false) }
    var showTokenEditor by remember { mutableStateOf(false) }
    var showUnsavedAccountDialog by remember { mutableStateOf(false) }
    var showLoginChoiceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            showToken = false
        }
    }

    LaunchedEffect(showToken) {
        if (showToken) {
            delay(5000)
            showToken = false
        }
    }

    val hasUpdate = updateState is UpdateState.SoftUpdate || updateState is UpdateState.CriticalUpdate
    val latestVersionName = when (updateState) {
        is UpdateState.SoftUpdate -> updateState.versionName
        is UpdateState.CriticalUpdate -> updateState.versionName
        else -> BuildConfig.VERSION_NAME
    }

    val tokenActionTitle =
        when {
            !isLoggedIn -> stringResource(R.string.advanced_login)
            showToken -> stringResource(R.string.token_shown)
            else -> stringResource(R.string.token_hidden)
        }

    val saveCurrentAccount: () -> Unit = {
        val existing = decodeSavedAccounts(savedAccountsJson)
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
                    ytmSync = ytmSync,
                    selectedYtmPlaylists = selectedYtmPlaylists,
                )
            onSavedAccountsJsonChange(encodeSavedAccounts(existing + newAccount))
        }
    }

    val switchToAccount: (SavedAccount) -> Unit = { account ->
        viewModel.switchToAccount(
            account = account,
            forceSyncOnSwitch = forceSyncOnAccountSwitch,
        )
    }

    val switchToAccountChannel: (AccountChannelUiModel) -> Unit = { channel ->
        viewModel.switchToAccountChannel(
            channel = channel,
            forceSyncOnSwitch = forceSyncOnAccountSwitch,
        )
    }

    val removeAccount: (SavedAccount) -> Unit = { account ->
        val existing = decodeSavedAccounts(savedAccountsJson)
        onSavedAccountsJsonChange(encodeSavedAccounts(existing.filter { it.id != account.id }))
    }

    SettingsScreenBackground {
        Scaffold(
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Column {
                            Text(
                                text = accountLabel,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = navController::navigateUp,
                            onLongClick = navController::backToMain,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        OutlinedIconButton(
                            onClick = { showTokenEditor = true },
                            colors =
                                IconButtonDefaults.outlinedIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                            border = null,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.token),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        if (hasUpdate) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = MaterialTheme.colorScheme.error)
                                },
                            ) {
                                OutlinedIconButton(
                                    onClick = { uriHandler.openUri(Updater.getLatestDownloadUrl()) },
                                    colors =
                                        IconButtonDefaults.outlinedIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        ),
                                    border = null,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.update),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    },
                    windowInsets = TopAppBarDefaults.windowInsets,
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + 4.dp,
                    bottom = SettingsDimensions.ScreenBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
        ) {
            item {
                val accountSettingsViewModel: AccountSettingsViewModel = hiltViewModel()
                val accountUiState by accountSettingsViewModel.uiState.collectAsStateWithLifecycle()

                ProfileIdentityCard(
                    modifier = Modifier.padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
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
                        if (isLoggedIn) {
                            navController.navigate("account")
                        } else {
                            showLoginChoiceDialog = true
                        }
                    },
                    onSecondaryAction = {
                        if (isLoggedIn) {
                            showToken = false
                            onInnerTubeCookieChange("")
                            forgetAccount(context, clearWebAuthSession = true)
                        } else {
                            showTokenEditor = true
                        }
                    },
                    onSaveAccount = saveCurrentAccount,
                    onSwitchAccount = switchToAccount,
                    onSwitchAccountChannel = switchToAccountChannel,
                    onRemoveAccount = removeAccount,
                    onAddAnotherAccount = {
                        val isSaved = savedAccounts.accounts.any { it.innerTubeCookie == innerTubeCookie }
                        if (isLoggedIn && !isSaved) {
                            showUnsavedAccountDialog = true
                        } else {
                            navController.navigate(buildLoginRoute())
                        }
                    },
                )
            }

            if (hasUpdate) {
                item {
                    UpdateBannerStrip(
                        modifier = Modifier.padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                        latestVersion = latestVersionName,
                        onClick = { uriHandler.openUri(Updater.getLatestDownloadUrl()) },
                    )
                }
            }

            item {
                AccountGeneralSection(
                    isLoggedIn = isLoggedIn,
                    useLoginForBrowse = useLoginForBrowse,
                    onUseLoginForBrowseChange = onUseLoginForBrowseChange,
                    ytmSync = ytmSync,
                    onYtmSyncChange = onYtmSyncChange,
                    forceSyncOnAccountSwitch = forceSyncOnAccountSwitch,
                    onForceSyncOnAccountSwitchChange = onForceSyncOnAccountSwitchChange,
                )
            }

            item {
                AccountIntegrationSection(
                    spotifyState = spotifyState,
                    useSpotifyHome = useSpotifyHome,
                    spotifySyncLikes = spotifySyncLikes,
                    hideYtmLikedSongs = hideYtmLikedSongs,
                    onSpotifyEntryClick = {
                        if (spotifyState.isAuthenticated) {
                            showSpotifyOptionsDialog = true
                        } else {
                            showSpotifyLogin = true
                        }
                    },
                    onUseSpotifyHomeChange = { isSpotify ->
                        if (isSpotify && !spotifyState.isAuthenticated) {
                            showSpotifyLogin = true
                        } else {
                            onUseSpotifyHomeChange(isSpotify)
                        }
                    },
                    onSpotifySyncLikesChange = onSpotifySyncLikesChange,
                    onHideYtmLikedSongsChange = onHideYtmLikedSongsChange,
                    onNavigateIntegration = { navController.navigate("settings/integration") },
                    onNavigateMusicTogether = { navController.navigate("settings/music_together") },
                )
            }

            item {
                AccountMiscSection(
                    tokenActionTitle = tokenActionTitle,
                    tokenDescription = tokenDescription,
                    tokenPreview = if (isLoggedIn && innerTubeCookie.isNotBlank()) previewSecureValue(innerTubeCookie) else null,
                    showToken = showToken,
                    onNavigateHiddenPlaylists = { navController.navigate("settings/hidden_playlists") },
                    onTokenEntryClick = {
                        if (!isLoggedIn) {
                            showTokenEditor = true
                        } else if (!showToken) {
                            showToken = true
                        } else {
                            showTokenEditor = true
                        }
                    },
                )
            }

            item {
                VersionStamp(modifier = Modifier.padding(horizontal = SettingsDimensions.ScreenHorizontalPadding))
            }
        }
    }
}

    AccountSpotifyLoginSheet(
        show = showSpotifyLogin,
        onDismiss = { showSpotifyLogin = false },
        onCookiesCaptured = { spDc, spKey ->
            spotifyAccountViewModel.connectWithCookies(spDc = spDc, spKey = spKey)
            showSpotifyLogin = false
        },
    )

    if (showSpotifyOptionsDialog) {
        SpotifyOptionsDialog(
            spotifyState = spotifyState,
            showSpotifyPlaylists = showSpotifyPlaylists,
            onShowSpotifyPlaylistsChange = onShowSpotifyPlaylistsChange,
            onConnectSpotify = {
                showSpotifyOptionsDialog = false
                showSpotifyLogin = true
            },
            onLogout = {
                spotifyAccountViewModel.logout()
                showSpotifyOptionsDialog = false
            },
            onDismissRequest = { showSpotifyOptionsDialog = false },
        )
    }

    if (showLoginChoiceDialog) {
        LoginChoiceDialog(
            onDismissRequest = { showLoginChoiceDialog = false },
            onBrowserLogin = {
                showLoginChoiceDialog = false
                navController.navigate(buildLoginRoute())
            },
            onAdvancedLogin = {
                showLoginChoiceDialog = false
                showTokenEditor = true
            },
        )
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

    if (showUnsavedAccountDialog) {
        UnsavedAccountDialog(
            onDismissRequest = { showUnsavedAccountDialog = false },
            onCancel = { showUnsavedAccountDialog = false },
            onNoThanks = {
                showUnsavedAccountDialog = false
                navController.navigate(buildLoginRoute())
            },
            onSaveYes = {
                showUnsavedAccountDialog = false
                saveCurrentAccount()
                navController.navigate(buildLoginRoute())
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