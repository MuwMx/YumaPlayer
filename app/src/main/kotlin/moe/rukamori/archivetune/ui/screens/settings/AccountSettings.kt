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

private val CardShape = RoundedCornerShape(28.dp)
private val InnerTileShape = RoundedCornerShape(22.dp)
private val AvatarSize = 88.dp
private val QuickTileIconSize = 48.dp
private val RowIconSize = 42.dp

@Composable
fun AccountSettings(
    navController: NavController,
    updateState: UpdateState,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = appBarScrollBehavior()

    val accountLabel = stringResource(R.string.account)
    val generalLabel = stringResource(R.string.general)
    val integrationLabel = stringResource(R.string.integration)
    val miscLabel = stringResource(R.string.misc)
    val loginLabel = stringResource(R.string.login)
    val notLoggedInLabel = stringResource(R.string.not_logged_in)
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
                AnimatedVisibility(
                    visible = isLoggedIn,
                    enter =
                        fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                            expandVertically(
                                spring(stiffness = Spring.StiffnessLow),
                            ),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    PreferenceGroup(title = generalLabel) {
                        item {
                            SwitchPreference(
                                icon = { Icon(painterResource(R.drawable.add_circle), null) },
                                title = { Text(stringResource(R.string.more_content)) },
                                description = stringResource(R.string.use_login_for_browse_desc),
                                checked = useLoginForBrowse,
                                onCheckedChange = onUseLoginForBrowseChange,
                            )
                        }

                        item {
                            SwitchPreference(
                                icon = { Icon(painterResource(R.drawable.cached), null) },
                                title = { Text(stringResource(R.string.yt_sync)) },
                                checked = ytmSync,
                                onCheckedChange = onYtmSyncChange,
                            )
                        }

                        item {
                            SwitchPreference(
                                icon = { Icon(painterResource(R.drawable.sync), null) },
                                title = { Text(stringResource(R.string.force_sync_on_switch_account)) },
                                description = stringResource(R.string.force_sync_on_switch_account_desc),
                                checked = forceSyncOnAccountSwitch,
                                onCheckedChange = onForceSyncOnAccountSwitchChange,
                            )
                        }
                    }
                }
            }

            item {
                PreferenceGroup(title = integrationLabel) {
                    item {
                        PreferenceEntry(
                            icon = { Icon(painterResource(R.drawable.spotify_icon), null) },
                            title = {
                                Text(
                                    if (spotifyState.isAuthenticated) {
                                        if (spotifyState.accountName.isNotBlank()) {
                                            stringResource(R.string.spotify_connected_as, spotifyState.accountName)
                                        } else {
                                            stringResource(R.string.spotify_account)
                                        }
                                    } else {
                                        stringResource(R.string.spotify_connect)
                                    }
                                )
                            },
                            description = if (spotifyState.isAuthenticated) {
                                if (spotifyState.playlistCount > 0) {
                                    stringResource(R.string.spotify_available_count, spotifyState.playlistCount)
                                } else {
                                    stringResource(R.string.spotify_no_sources)
                                }
                            } else {
                                stringResource(R.string.spotify_not_connected)
                            },
                            onClick = {
                                if (spotifyState.isAuthenticated) {
                                    showSpotifyOptionsDialog = true
                                } else {
                                    showSpotifyLogin = true
                                }
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

                    if (spotifyState.isAuthenticated) {
                        item {
                            SwitchPreference(
                                icon = { Icon(painterResource(R.drawable.sync), null) },
                                title = { Text(stringResource(R.string.spotify_sync_likes)) },
                                description = stringResource(R.string.spotify_sync_likes_desc),
                                checked = spotifySyncLikes,
                                onCheckedChange = onSpotifySyncLikesChange,
                            )
                        }

                        item {
                            SwitchPreference(
                                icon = { Icon(painterResource(R.drawable.visibility_off), null) },
                                title = { Text(stringResource(R.string.hide_ytm_liked_songs)) },
                                checked = hideYtmLikedSongs,
                                onCheckedChange = onHideYtmLikedSongsChange,
                            )
                        }
                    }

                    item {
                        PreferenceEntry(
                            icon = { Icon(painterResource(R.drawable.integration), null) },
                            title = { Text(integrationLabel) },
                            description = stringResource(R.string.account_integrations_summary),
                            onClick = { navController.navigate("settings/integration") },
                            showChevron = true,
                        )
                    }

                    item {
                        PreferenceEntry(
                            icon = { Icon(painterResource(R.drawable.fire), null) },
                            title = { Text(stringResource(R.string.music_together)) },
                            onClick = { navController.navigate("settings/music_together") },
                            showChevron = true,
                        )
                    }
                }
            }

            item {
                PreferenceGroup(title = miscLabel) {
                    item {
                        PreferenceEntry(
                            icon = { Icon(painterResource(R.drawable.visibility_off), null) },
                            title = { Text(stringResource(R.string.hidden_playlists)) },
                            description = stringResource(R.string.hidden_playlists_description),
                            onClick = { navController.navigate("settings/hidden_playlists") },
                            showChevron = true,
                        )
                    }

                    item {
                        PreferenceEntry(
                            icon = { Icon(painterResource(R.drawable.token), null) },
                            title = { Text(tokenActionTitle) },
                            description = tokenDescription,
                            onClick = {
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
                }
            }

            item {
                VersionStamp(modifier = Modifier.padding(horizontal = SettingsDimensions.ScreenHorizontalPadding))
            }
        }
    }
}

    if (showSpotifyLogin) {
        SpotifyLoginSheet(
            onDismiss = { showSpotifyLogin = false },
            onCookiesCaptured = { spDc, spKey ->
                spotifyAccountViewModel.connectWithCookies(spDc = spDc, spKey = spKey)
                showSpotifyLogin = false
            },
        )
    }

    if (showSpotifyOptionsDialog) {
        Dialog(onDismissRequest = { showSpotifyOptionsDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.spotify_account),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )

                    if (!spotifyState.isAuthenticated) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .yumaClickable(onClick = {
                                    showSpotifyOptionsDialog = false
                                    showSpotifyLogin = true
                                }),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.spotify_icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.spotify_connect),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = stringResource(R.string.spotify_not_connected),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .yumaClickable(onClick = {
                                    onShowSpotifyPlaylistsChange(!showSpotifyPlaylists)
                                }),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.spotify_icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.spotify_show_playlist),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = stringResource(R.string.spotify_show_playlist_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    )
                                }
                                Switch(
                                    checked = showSpotifyPlaylists,
                                    onCheckedChange = onShowSpotifyPlaylistsChange,
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f),
                            contentColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .yumaClickable(onClick = {
                                    spotifyAccountViewModel.logout()
                                    showSpotifyOptionsDialog = false
                                }),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.logout),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(R.string.action_logout),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showSpotifyOptionsDialog = false }) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    }
                }
            }
        }
    }

    if (showLoginChoiceDialog) {
        Dialog(onDismissRequest = { showLoginChoiceDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.login),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .yumaClickable(onClick = {
                                showLoginChoiceDialog = false
                                navController.navigate(buildLoginRoute())
                            }),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.login),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.login),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "YouTube Browser",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .yumaClickable(onClick = {
                                showLoginChoiceDialog = false
                                showTokenEditor = true
                            }),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.token),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.advanced_login),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(R.string.token_adv_login_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showLoginChoiceDialog = false }) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    }
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

    if (showUnsavedAccountDialog) {
        Dialog(onDismissRequest = { showUnsavedAccountDialog = false }) {
            Card(
                shape = CardShape,
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.unsaved_account_dialog_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.unsaved_account_dialog_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { showUnsavedAccountDialog = false },
                        ) {
                            Text(text = stringResource(R.string.unsaved_account_dialog_cancel))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = {
                                showUnsavedAccountDialog = false
                                navController.navigate(buildLoginRoute())
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.unsaved_account_dialog_no_thanks),
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = {
                                showUnsavedAccountDialog = false
                                saveCurrentAccount()
                                navController.navigate(buildLoginRoute())
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.unsaved_account_dialog_save_yes),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileIdentityCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape? = null,
    isLoggedIn: Boolean,
    accountName: String,
    accountEmail: String,
    accountHandle: String,
    accountImageUrl: String?,
    savedAccounts: SavedAccountCollection,
    activeInnerTubeCookie: String,
    activeDataSyncId: String,
    accountChannelsState: AccountChannelsState,
    extractedColorHex: String? = null,
    onAvatarPixelsReady: (IntArray) -> Unit = {},
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    onSaveAccount: () -> Unit,
    onSwitchAccount: (SavedAccount) -> Unit,
    onSwitchAccountChannel: (AccountChannelUiModel) -> Unit,
    onRemoveAccount: (SavedAccount) -> Unit,
    onAddAnotherAccount: () -> Unit,
) {
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val menuChevronRotation by animateFloatAsState(
        targetValue = if (accountMenuExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "accountMenuChevron",
    )

    val colors = LocalYumaColors.current
    val context = LocalContext.current
    
    val groupPosition = moe.rukamori.archivetune.ui.component.LocalPreferenceGroupPosition.current
    val resolvedShape = shape ?: when (groupPosition) {
        moe.rukamori.archivetune.ui.component.PreferenceGroupPosition.First -> RoundedCornerShape(
            topStart = SettingsDimensions.SegmentedCornerLarge,
            topEnd = SettingsDimensions.SegmentedCornerLarge,
            bottomStart = SettingsDimensions.SegmentedCornerSmall,
            bottomEnd = SettingsDimensions.SegmentedCornerSmall
        )
        moe.rukamori.archivetune.ui.component.PreferenceGroupPosition.Middle -> RoundedCornerShape(
            SettingsDimensions.SegmentedCornerSmall
        )
        moe.rukamori.archivetune.ui.component.PreferenceGroupPosition.Last -> RoundedCornerShape(
            topStart = SettingsDimensions.SegmentedCornerSmall,
            topEnd = SettingsDimensions.SegmentedCornerSmall,
            bottomStart = SettingsDimensions.SegmentedCornerLarge,
            bottomEnd = SettingsDimensions.SegmentedCornerLarge
        )
        else -> RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)
    }

    val extractedColor = remember(extractedColorHex) {
        extractedColorHex?.let {
            try {
                Color(android.graphics.Color.parseColor(it))
            } catch (e: Exception) {
                null
            }
        }
    }

    val targetPrimary = if (isLoggedIn && extractedColor != null) extractedColor else MaterialTheme.colorScheme.primary
    val targetTertiary = MaterialTheme.colorScheme.tertiary

    val animatedPrimary by animateColorAsState(
        targetValue = targetPrimary,
        animationSpec = tween(durationMillis = 400),
        label = "primaryGlowColor"
    )
    val animatedTertiary by animateColorAsState(
        targetValue = targetTertiary,
        animationSpec = tween(durationMillis = 400),
        label = "tertiaryGlowColor"
    )

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val blendMode = if (isDark) BlendMode.Plus else BlendMode.SrcOver

    val spot1Alpha = if (isDark) 0.22f else 0.18f
    val spot1AlphaMid = if (isDark) 0.08f else 0.05f

    val spot2Alpha = if (isDark) 0.25f else 0.20f
    val spot2AlphaMid = if (isDark) 0.09f else 0.06f

    val transition = rememberInfiniteTransition(label = "profileMeshGlow")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(resolvedShape)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val spot1X = size.width * (0.5f + 0.35f * kotlin.math.cos(time))
                val spot1Y = size.height * (0.5f + 0.30f * kotlin.math.sin(time))

                val spot2X = size.width * (0.5f + 0.40f * kotlin.math.sin(time + 1.8f))
                val spot2Y = size.height * (0.5f + 0.35f * kotlin.math.cos(time + 1.8f))

                val spot1Gradient = Brush.radialGradient(
                    colors = listOf(
                        animatedPrimary.copy(alpha = spot1Alpha),
                        animatedPrimary.copy(alpha = spot1AlphaMid),
                        Color.Transparent
                    ),
                    center = Offset(spot1X, spot1Y),
                    radius = size.width * 0.85f
                )

                val spot2Gradient = Brush.radialGradient(
                    colors = listOf(
                        animatedTertiary.copy(alpha = spot2Alpha),
                        animatedTertiary.copy(alpha = spot2AlphaMid),
                        Color.Transparent
                    ),
                    center = Offset(spot2X, spot2Y),
                    radius = size.width * 0.90f
                )

                onDrawBehind {
                    drawRect(color = colors.glassBackground)
                    drawRect(brush = spot1Gradient, blendMode = blendMode)
                    drawRect(brush = spot2Gradient, blendMode = blendMode)
                }
            }
            .border(1.dp, colors.glassBorder, resolvedShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    animatedPrimary.copy(alpha = 0.20f),
                                    animatedTertiary.copy(alpha = 0.10f),
                                ),
                            )
                        ).border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    animatedPrimary.copy(alpha = 0.70f),
                                    animatedPrimary.copy(alpha = 0.20f),
                                ),
                            ),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoggedIn && !accountImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(accountImageUrl)
                                .size(64, 64)
                                .allowHardware(false)
                                .build(),
                            contentDescription = null,
                            onSuccess = { success ->
                                val bmp = success.result.image.toBitmap()
                                val w = bmp.width
                                val h = bmp.height
                                if (w > 0 && h > 0) {
                                    val pixels = IntArray(w * h)
                                    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
                                    onAvatarPixelsReady(pixels)
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                if (isLoggedIn) R.drawable.account else R.drawable.login,
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = animatedPrimary,
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isLoggedIn,
                    enter = scaleIn(spring(stiffness = Spring.StiffnessHigh)),
                    exit = scaleOut(),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = animatedPrimary,
                        modifier = Modifier.size(18.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = accountName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )

                if (accountHandle.isNotBlank()) {
                    Text(
                        text = accountHandle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                } else if (!isLoggedIn) {
                    Text(
                        text = stringResource(R.string.not_logged_in),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                    )
                }

                Box(modifier = Modifier.padding(top = 6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .height(38.dp)
                                .yumaClickable(pressedScale = 0.95f, onClick = onPrimaryAction),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    painter = painterResource(if (isLoggedIn) R.drawable.account else R.drawable.login),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = if (isLoggedIn) stringResource(R.string.account) else stringResource(R.string.login),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        if (isLoggedIn) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(38.dp)
                                    .yumaClickable(
                                        pressedScale = 0.95f,
                                        onClick = { accountMenuExpanded = !accountMenuExpanded },
                                    ),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.expand_more),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .rotate(menuChevronRotation),
                                    )
                                }
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false },
                    ) {
                        val accountChannels = (accountChannelsState as? AccountChannelsState.Success)?.channels
                        if (accountChannels != null && accountChannels.items.size > 1) {
                            Text(
                                text = stringResource(R.string.youtube_channels),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                            accountChannels.items.forEach { channel ->
                                val isActive = channel.isSelected || channel.dataSyncId == activeDataSyncId
                                DropdownMenuItem(
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text(
                                                text = channel.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val channelSubtitle = channel.channelHandle.ifBlank { channel.byline }
                                            if (channelSubtitle.isNotBlank()) {
                                                Text(
                                                    text = channelSubtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.account),
                                            contentDescription = null,
                                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    onClick = {
                                        if (!isActive) onSwitchAccountChannel(channel)
                                        accountMenuExpanded = false
                                    },
                                )
                            }
                            Spacer(modifier = Modifier.padding(vertical = 4.dp))
                        }

                        if (savedAccounts.accounts.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.saved_accounts),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                            savedAccounts.accounts.forEach { account ->
                                val isActive = account.innerTubeCookie == activeInnerTubeCookie
                                DropdownMenuItem(
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text(
                                                text = account.name.ifBlank { account.email },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (account.email.isNotBlank()) {
                                                Text(
                                                    text = account.email,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.account),
                                            contentDescription = null,
                                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    trailingIcon = {
                                        OutlinedIconButton(
                                            onClick = { onRemoveAccount(account) },
                                            modifier = Modifier.size(32.dp),
                                            border = null,
                                            colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.delete),
                                                contentDescription = stringResource(R.string.remove_account),
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        if (!isActive) onSwitchAccount(account)
                                        accountMenuExpanded = false
                                    },
                                )
                            }
                            Spacer(modifier = Modifier.padding(vertical = 4.dp))
                        }

                        if (isLoggedIn) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.save_current_account),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.bookmark),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    onSaveAccount()
                                    accountMenuExpanded = false
                                },
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.add_another_account),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.add_circle),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    accountMenuExpanded = false
                                    onAddAnotherAccount()
                                },
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .yumaClickable(
                        pressedScale = 0.94f,
                        onClick = onSecondaryAction,
                    )
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
                    .background(colors.glassBackground, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isLoggedIn) stringResource(R.string.action_logout) else stringResource(R.string.advanced_login),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun UpdateBannerStrip(
    modifier: Modifier = Modifier,
    latestVersion: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) SettingsAnimations.PressScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "updateScale",
    )

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        shape = CardShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        onClick = onClick,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BadgedBox(
                badge = { Badge(containerColor = MaterialTheme.colorScheme.error) },
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.10f),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.update),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.new_version_available),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = latestVersion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Medium,
                )
            }

            FilledTonalButton(
                onClick = onClick,
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.14f),
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    text = stringResource(R.string.update_text),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}


@Composable
fun ExpressiveSegmentedRow(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    selectedValue: Boolean,
    onValueSelected: (Boolean) -> Unit,
) {
    val colors = LocalYumaColors.current
    val groupPosition = LocalPreferenceGroupPosition.current

    val shape = when (groupPosition) {
        null,
        PreferenceGroupPosition.Single -> RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)
        PreferenceGroupPosition.First -> RoundedCornerShape(
            topStart = SettingsDimensions.SegmentedCornerLarge,
            topEnd = SettingsDimensions.SegmentedCornerLarge,
            bottomEnd = SettingsDimensions.SegmentedCornerSmall,
            bottomStart = SettingsDimensions.SegmentedCornerSmall,
        )
        PreferenceGroupPosition.Middle -> RoundedCornerShape(SettingsDimensions.SegmentedCornerSmall)
        PreferenceGroupPosition.Last -> RoundedCornerShape(
            topStart = SettingsDimensions.SegmentedCornerSmall,
            topEnd = SettingsDimensions.SegmentedCornerSmall,
            bottomEnd = SettingsDimensions.SegmentedCornerLarge,
            bottomStart = SettingsDimensions.SegmentedCornerLarge,
        )
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .yumaGlassCard(
                    shape = shape,
                    backgroundColor = colors.glassBackground,
                    borderColor = colors.glassBorder,
                )
                .clip(shape)
                .padding(
                    horizontal = SettingsDimensions.RowHorizontalPadding,
                    vertical = SettingsDimensions.RowVerticalPadding,
                ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemPaddingVertical)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.RowIconSpacing),
            ) {
                ExpressiveRowIcon(
                    icon = icon,
                    title = title,
                    tint = MaterialTheme.colorScheme.primary,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                    }
                }
            }
            
            val indicatorOffset by animateFloatAsState(
                targetValue = if (selectedValue) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "SourceIndicatorOffset"
            )

            val barShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(barShape)
                    .background(colors.glassBackground)
                    .border(1.dp, colors.glassBorder, barShape),
                contentAlignment = Alignment.CenterStart
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    val tabWidth = maxWidth / 2

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(width = tabWidth, height = 36.dp)
                            .offset(x = tabWidth * indicatorOffset)
                    ) {}

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onValueSelected(false) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.home_provider_youtube_music),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!selectedValue) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onValueSelected(true) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.home_provider_spotify),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedValue) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveRowIcon(
    icon: Painter,
    title: String,
    tint: Color,
    emphasized: Boolean = false,
) {
    val iconShape = rememberPreferenceIconShape(title)
    val bgColor = if (emphasized) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        tint.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
    }
    val iconTint = if (emphasized) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        tint
    }

    Surface(
        modifier = Modifier.size(RowIconSize),
        shape = iconShape,
        color = bgColor,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(SettingsDimensions.SegmentedIconSize),
                tint = iconTint,
            )
        }
    }
}


@Composable
private fun VersionStamp(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
        )
        Text(
            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
        )
    }
}

@Composable
fun TokenEditorDialog(
    innerTubeCookie: String,
    visitorData: String,
    dataSyncId: String,
    accountNamePref: String,
    accountEmail: String,
    accountChannelHandle: String,
    onInnerTubeCookieChange: (String) -> Unit,
    onPoTokenChange: (String) -> Unit,
    onVisitorDataChange: (String) -> Unit,
    onDataSyncIdChange: (String) -> Unit,
    onAccountNameChange: (String) -> Unit,
    onAccountEmailChange: (String) -> Unit,
    onAccountChannelHandleChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val text =
        """
        ***INNERTUBE COOKIE*** =$innerTubeCookie
        ***VISITOR DATA*** =$visitorData
        ***DATASYNC ID*** =$dataSyncId
        ***PO TOKEN*** =${YouTube.poToken.orEmpty()}
        ***ACCOUNT NAME*** =$accountNamePref
        ***ACCOUNT EMAIL*** =$accountEmail
        ***ACCOUNT CHANNEL HANDLE*** =$accountChannelHandle
        """.trimIndent()

    TextFieldDialog(
        initialTextFieldValue = TextFieldValue(text),
        onDone = { data ->
            data.split("\n").forEach {
                when {
                    it.startsWith("***INNERTUBE COOKIE*** =") -> onInnerTubeCookieChange(it.substringAfter("="))
                    it.startsWith("***VISITOR DATA*** =") -> onVisitorDataChange(it.substringAfter("="))
                    it.startsWith("***DATASYNC ID*** =") -> onDataSyncIdChange(it.substringAfter("="))
                    it.startsWith("***PO TOKEN*** =") -> onPoTokenChange(it.substringAfter("="))
                    it.startsWith("***ACCOUNT NAME*** =") -> onAccountNameChange(it.substringAfter("="))
                    it.startsWith("***ACCOUNT EMAIL*** =") -> onAccountEmailChange(it.substringAfter("="))
                    it.startsWith("***ACCOUNT CHANNEL HANDLE*** =") -> onAccountChannelHandleChange(it.substringAfter("="))
                }
            }
        },
        onDismiss = onDismiss,
        singleLine = false,
        maxLines = 20,
        isInputValid = {
            hasYouTubeLoginCookie(it)
        },
        extraContent = {
            InfoLabel(text = stringResource(R.string.token_adv_login_description))
        },
    )
}

private fun hasVisibleSecureDetails(
    innerTubeCookie: String,
    visitorData: String,
    dataSyncId: String,
    poToken: String,
): Boolean = innerTubeCookie.isNotBlank() || visitorData.isNotBlank() || dataSyncId.isNotBlank() || poToken.isNotBlank()

private fun previewSecureValue(value: String): String {
    val normalized = value.replace("\n", " ").replace("\r", " ").trim()
    if (normalized.length <= 76) {
        return normalized
    }
    return normalized.take(52) + "\u2025" + normalized.takeLast(18)
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