/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.defaultUpdateChannel
import moe.rukamori.archivetune.ui.screens.settings.AboutScreen
import moe.rukamori.archivetune.ui.screens.settings.AccountSettings
import moe.rukamori.archivetune.ui.screens.settings.AiIntegrationSettings
import moe.rukamori.archivetune.ui.screens.settings.AppearanceSettings
import moe.rukamori.archivetune.ui.screens.settings.BackupAndRestore
import moe.rukamori.archivetune.ui.screens.settings.ChangelogScreen
import moe.rukamori.archivetune.ui.screens.settings.ContentSettings
import moe.rukamori.archivetune.ui.screens.settings.DebugSettings
import moe.rukamori.archivetune.ui.screens.settings.DiscordSettings
import moe.rukamori.archivetune.ui.screens.settings.HiddenPlaylistsScreen
import moe.rukamori.archivetune.ui.screens.settings.IconScreen
import moe.rukamori.archivetune.ui.screens.settings.IntegrationScreen
import moe.rukamori.archivetune.ui.screens.settings.InternetSettings
import moe.rukamori.archivetune.ui.screens.settings.LastFMSettings
import moe.rukamori.archivetune.ui.screens.settings.LyricsSettings
import moe.rukamori.archivetune.ui.screens.settings.MusicTogetherScreen
import moe.rukamori.archivetune.ui.screens.settings.PalettePickerScreen
import moe.rukamori.archivetune.ui.screens.settings.PlayerSettings
import moe.rukamori.archivetune.ui.screens.settings.PoTokenScreen
import moe.rukamori.archivetune.ui.screens.settings.PrivacySettings
import moe.rukamori.archivetune.ui.screens.settings.StorageSettings
import moe.rukamori.archivetune.ui.screens.settings.ThemeCreatorScreen
import moe.rukamori.archivetune.ui.state.UpdateState

internal fun NavGraphBuilder.settingsNavigationRoutes(
    navController: NavHostController,
    updateState: UpdateState,
) {
    composable("settings/account") {
        AccountSettings(navController, updateState)
    }
    composable("settings/hidden_playlists") {
        HiddenPlaylistsScreen(navController)
    }
    composable("settings/appearance") {
        AppearanceSettings(navController)
    }
    composable("settings/appearance/icon") {
        IconScreen(navController)
    }
    composable("settings/appearance/palette_picker") {
        PalettePickerScreen(navController)
    }
    composable("settings/appearance/theme_creator") {
        ThemeCreatorScreen(navController)
    }
    composable("settings/content") {
        ContentSettings(navController)
    }
    composable("settings/player") {
        PlayerSettings(navController)
    }
    composable("settings/lyrics") {
        LyricsSettings(navController)
    }
    composable("settings/internet") {
        InternetSettings(navController)
    }
    composable("settings/storage") {
        StorageSettings(navController)
    }
    composable("settings/privacy") {
        PrivacySettings(navController)
    }
    composable("settings/backup_restore") {
        BackupAndRestore(navController)
    }
    composable("settings/discord") {
        DiscordSettings(navController)
    }
    composable("settings/integration") {
        IntegrationScreen(navController)
    }
    composable("settings/ai_integration") {
        AiIntegrationSettings(navController)
    }
    composable("settings/music_together") {
        MusicTogetherScreen(navController)
    }
    composable("settings/lastfm") {
        LastFMSettings(navController)
    }
    composable("settings/discord/experimental") {
        moe.rukamori.archivetune.ui.screens.settings
            .DiscordExperimental(navController)
    }
    composable("settings/misc") {
        DebugSettings(navController)
    }
    composable(
        route = "settings/changelog?channel={channel}",
        arguments =
            listOf(
                navArgument("channel") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        val channelName = backStackEntry.arguments?.getString("channel")
        val channel =
            channelName?.let {
                runCatching { UpdateChannel.valueOf(it) }.getOrNull()
            } ?: defaultUpdateChannel
        ChangelogScreen(navController, channel = channel)
    }
    composable("settings/about") {
        AboutScreen(navController)
    }
    composable("settings/po_token") {
        PoTokenScreen(navController)
    }
    composable(
        route = "$LOGIN_ROUTE?$LOGIN_URL_ARGUMENT={$LOGIN_URL_ARGUMENT}",
        arguments =
            listOf(
                navArgument(LOGIN_URL_ARGUMENT) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        LoginScreen(
            navController,
            startUrl = backStackEntry.arguments?.getString(LOGIN_URL_ARGUMENT)?.let(Uri::decode),
        )
    }
}
