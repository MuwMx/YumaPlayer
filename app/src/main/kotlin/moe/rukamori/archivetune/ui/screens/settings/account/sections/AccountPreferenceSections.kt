/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.account.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.spotify.SpotifyAccountUiState
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SpoilerVeil
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.screens.settings.account.component.ExpressiveSegmentedRow
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@Composable
internal fun AccountGeneralSection(
    isLoggedIn: Boolean,
    useLoginForBrowse: Boolean,
    onUseLoginForBrowseChange: (Boolean) -> Unit,
    ytmSync: Boolean,
    onYtmSyncChange: (Boolean) -> Unit,
    forceSyncOnAccountSwitch: Boolean,
    onForceSyncOnAccountSwitchChange: (Boolean) -> Unit,
) {
    AnimatedVisibility(
        visible = isLoggedIn,
        enter =
            fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                expandVertically(
                    spring(stiffness = Spring.StiffnessLow),
                ),
        exit = fadeOut() + shrinkVertically(),
    ) {
        PreferenceGroup(title = stringResource(R.string.general)) {
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

@Composable
internal fun AccountIntegrationSection(
    spotifyState: SpotifyAccountUiState,
    useSpotifyHome: Boolean,
    spotifySyncLikes: Boolean,
    hideYtmLikedSongs: Boolean,
    onSpotifyEntryClick: () -> Unit,
    onUseSpotifyHomeChange: (Boolean) -> Unit,
    onSpotifySyncLikesChange: (Boolean) -> Unit,
    onHideYtmLikedSongsChange: (Boolean) -> Unit,
    onNavigateIntegration: () -> Unit,
    onNavigateMusicTogether: () -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.integration)) {
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
                onClick = onSpotifyEntryClick,
            )
        }

        item {
            ExpressiveSegmentedRow(
                icon = painterResource(if (useSpotifyHome) R.drawable.spotify_icon else R.drawable.yt_music_icon),
                title = stringResource(R.string.home_screen_provider),
                subtitle = stringResource(R.string.home_screen_provider_desc),
                selectedValue = useSpotifyHome,
                onValueSelected = onUseSpotifyHomeChange,
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
                title = { Text(stringResource(R.string.integration)) },
                description = stringResource(R.string.account_integrations_summary),
                onClick = onNavigateIntegration,
                showChevron = true,
            )
        }

        item {
            PreferenceEntry(
                icon = { Icon(painterResource(R.drawable.fire), null) },
                title = { Text(stringResource(R.string.music_together)) },
                onClick = onNavigateMusicTogether,
                showChevron = true,
            )
        }
    }
}

@Composable
internal fun AccountMiscSection(
    tokenActionTitle: String,
    tokenDescription: String,
    onNavigateHiddenPlaylists: () -> Unit,
    onTokenEntryClick: () -> Unit,
    onRevealToken: () -> Unit = {},
    tokenPreview: String? = null,
    showToken: Boolean = false,
) {
    PreferenceGroup(title = stringResource(R.string.misc)) {
        item {
            PreferenceEntry(
                icon = { Icon(painterResource(R.drawable.visibility_off), null) },
                title = { Text(stringResource(R.string.hidden_playlists)) },
                description = stringResource(R.string.hidden_playlists_description),
                onClick = onNavigateHiddenPlaylists,
                showChevron = true,
            )
        }

        item {
            PreferenceEntry(
                icon = { Icon(painterResource(R.drawable.token), null) },
                title = { Text(tokenActionTitle) },
                description = if (tokenPreview != null) null else tokenDescription,
                content = if (tokenPreview != null) {
                    {
                        Spacer(Modifier.height(SettingsDimensions.SegmentedRowSpacing))
                        SpoilerVeil(
                            revealed = showToken,
                            onRevealChange = onRevealToken,
                            hiddenDescription = stringResource(R.string.spoiler_hidden),
                            revealedDescription = stringResource(R.string.spoiler_revealed),
                            modifier = Modifier.wrapContentWidth(),
                        ) {
                            Text(
                                text = tokenPreview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                } else null,
                onClick = onTokenEntryClick,
            )
        }
    }
}
