/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.runtime.Immutable
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
import moe.rukamori.archivetune.constants.UseLoginForBrowse
import moe.rukamori.archivetune.constants.UseSpotifyHomeKey
import moe.rukamori.archivetune.constants.VisitorDataKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.spotify.SpotifyAccountUiState
import moe.rukamori.archivetune.utils.SavedAccount
import moe.rukamori.archivetune.utils.SavedAccountCollection
import moe.rukamori.archivetune.viewmodels.AccountChannelUiModel
import moe.rukamori.archivetune.viewmodels.AccountChannelsState

internal object AccountContract {
    val NameKey = AccountNameKey
    val EmailKey = AccountEmailKey
    val ChannelHandleKey = AccountChannelHandleKey
    val CookieKey = InnerTubeCookieKey
    val VisitorData = VisitorDataKey
    val SyncIdKey = DataSyncIdKey
    val LoginForBrowse = UseLoginForBrowse
    val YtmSync = YtmSyncKey
    val ForceSyncOnSwitch = ForceSyncOnAccountSwitchKey
    val SelectedYtmPlaylists = SelectedYtmPlaylistsKey
    val SavedAccounts = SavedAccountsKey
    val ShowSpotifyPlaylists = ShowSpotifyPlaylistsKey
    val SpotifyHome = UseSpotifyHomeKey
    val SpotifySyncLikes = SpotifySyncLikesKey
    val HideYtmLikedSongs = HideYtmLikedSongsKey
}

@Immutable
data class TokenEditorDialogState(
    val innerTubeCookie: String = "",
    val visitorData: String = "",
    val dataSyncId: String = "",
    val accountNamePref: String = "",
    val accountEmail: String = "",
    val accountChannelHandle: String = "",
)

@Immutable
data class TokenEditorDialogActions(
    val onInnerTubeCookieChange: (String) -> Unit = {},
    val onPoTokenChange: (String) -> Unit = {},
    val onVisitorDataChange: (String) -> Unit = {},
    val onDataSyncIdChange: (String) -> Unit = {},
    val onAccountNameChange: (String) -> Unit = {},
    val onAccountEmailChange: (String) -> Unit = {},
    val onAccountChannelHandleChange: (String) -> Unit = {},
    val onDismiss: () -> Unit = {},
)

@Immutable
data class AccountSettingsUiState(
    val displayName: String = "",
    val accountEmail: String = "",
    val accountHandle: String = "",
    val accountImageUrl: String? = null,
    val isLoggedIn: Boolean = false,
    val savedAccounts: SavedAccountCollection = SavedAccountCollection(emptyList()),
    val activeInnerTubeCookie: String = "",
    val activeDataSyncId: String = "",
    val accountChannelsState: AccountChannelsState = AccountChannelsState.Loading,
    val extractedColorHex: String? = null,
    val useLoginForBrowse: Boolean = true,
    val ytmSync: Boolean = true,
    val forceSyncOnAccountSwitch: Boolean = false,
    val spotifyState: SpotifyAccountUiState = SpotifyAccountUiState(),
    val useSpotifyHome: Boolean = false,
    val showSpotifyPlaylists: Boolean = true,
    val spotifySyncLikes: Boolean = false,
    val hideYtmLikedSongs: Boolean = false,
    val hasUpdate: Boolean = false,
    val latestVersionName: String = "",
    val showToken: Boolean = false,
    val tokenActionTitle: String = "",
)

@Immutable
data class AccountSettingsUiActions(
    val onNavigateUp: () -> Unit = {},
    val onNavigateHome: () -> Unit = {},
    val onOpenTokenEditor: () -> Unit = {},
    val onOpenUpdateDownload: () -> Unit = {},
    val onAvatarPixelsReady: (IntArray) -> Unit = {},
    val onPrimaryAction: () -> Unit = {},
    val onSecondaryAction: () -> Unit = {},
    val onSaveAccount: () -> Unit = {},
    val onSwitchAccount: (SavedAccount) -> Unit = {},
    val onSwitchAccountChannel: (AccountChannelUiModel) -> Unit = {},
    val onRemoveAccount: (SavedAccount) -> Unit = {},
    val onAddAnotherAccount: () -> Unit = {},
    val onUseLoginForBrowseChange: (Boolean) -> Unit = {},
    val onYtmSyncChange: (Boolean) -> Unit = {},
    val onForceSyncOnAccountSwitchChange: (Boolean) -> Unit = {},
    val onSpotifyClick: () -> Unit = {},
    val onUseSpotifyHomeChange: (Boolean) -> Unit = {},
    val onSpotifySyncLikesChange: (Boolean) -> Unit = {},
    val onHideYtmLikedSongsChange: (Boolean) -> Unit = {},
    val onNavigateIntegration: () -> Unit = {},
    val onNavigateMusicTogether: () -> Unit = {},
    val onNavigateHiddenPlaylists: () -> Unit = {},
    val onTokenRowClick: () -> Unit = {},
)

fun hasVisibleSecureDetails(
    innerTubeCookie: String,
    visitorData: String,
    dataSyncId: String,
    poToken: String,
): Boolean = innerTubeCookie.isNotBlank() || visitorData.isNotBlank() || dataSyncId.isNotBlank() || poToken.isNotBlank()

fun previewSecureValue(value: String): String {
    val normalized = value.replace("\n", " ").replace("\r", " ").trim()
    if (normalized.length <= 76) {
        return normalized
    }
    return normalized.take(52) + "\u2025" + normalized.takeLast(18)
}
