/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R

@Composable
fun buildSettingsGroups(
    navController: NavController,
    isAndroid12OrLater: Boolean,
    hasUpdate: Boolean,
    context: Context,
): List<SettingsGroup> =
    buildList {
        // Группа 1: Учётная запись и интеграции (2 элемента)
        add(
            SettingsGroup(
                title = stringResource(R.string.settings_section_account),
                items =
                    listOf(
                        SettingsItem(
                            key = "account",
                            icon = painterResource(R.drawable.account),
                            title = stringResource(R.string.account),
                            subtitle = stringResource(R.string.settings_account_subtitle),
                            accentColor = MaterialTheme.colorScheme.primary,
                            onClick = { navController.navigate("settings/account") },
                        ),
                        SettingsItem(
                            key = "ai_integration",
                            icon = painterResource(R.drawable.ai),
                            title = stringResource(R.string.ai_integration),
                            subtitle = stringResource(R.string.ai_integration_desc),
                            accentColor = MaterialTheme.colorScheme.secondary,
                            onClick = { navController.navigate("settings/ai_integration") },
                        ),
                    ),
            ),
        )

        // Группа 2: Интерфейс и внешний вид (3 элемента)
        add(
            SettingsGroup(
                title = stringResource(R.string.settings_section_appearance),
                items =
                    listOf(
                        SettingsItem(
                            key = "appearance",
                            icon = painterResource(R.drawable.ic_palette),
                            title = stringResource(R.string.appearance),
                            subtitle = stringResource(R.string.settings_appearance_subtitle),
                            accentColor = MaterialTheme.colorScheme.secondary,
                            onClick = { navController.navigate("settings/appearance") },
                        ),
                        SettingsItem(
                            key = "lyrics",
                            icon = painterResource(R.drawable.lyrics),
                            title = stringResource(R.string.lyrics),
                            subtitle = stringResource(R.string.settings_lyrics_subtitle),
                            accentColor = MaterialTheme.colorScheme.primary,
                            onClick = { navController.navigate("settings/lyrics") },
                        ),
                        SettingsItem(
                            key = "stats",
                            icon = painterResource(R.drawable.stats),
                            title = stringResource(R.string.settings_stats_title),
                            subtitle = stringResource(R.string.settings_stats_subtitle),
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            onClick = { navController.navigate("stats") },
                        ),
                    ),
            ),
        )

        // Группа 3: Воспроизведение и медиатека (4 элемента)
        add(
            SettingsGroup(
                title = stringResource(R.string.settings_section_player_content),
                items =
                    listOf(
                        SettingsItem(
                            key = "player",
                            icon = painterResource(R.drawable.play),
                            title = stringResource(R.string.player_and_audio),
                            subtitle = stringResource(R.string.settings_player_subtitle),
                            accentColor = MaterialTheme.colorScheme.primary,
                            onClick = { navController.navigate("settings/player") },
                        ),
                        SettingsItem(
                            key = "content",
                            icon = painterResource(R.drawable.language),
                            title = stringResource(R.string.content),
                            subtitle = stringResource(R.string.settings_content_subtitle),
                            accentColor = MaterialTheme.colorScheme.secondary,
                            onClick = { navController.navigate("settings/content") },
                        ),
                        SettingsItem(
                            key = "storage",
                            icon = painterResource(R.drawable.storage),
                            title = stringResource(R.string.storage),
                            subtitle = stringResource(R.string.settings_storage_subtitle),
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            onClick = { navController.navigate("settings/storage") },
                        ),
                        SettingsItem(
                            key = "backup_restore",
                            icon = painterResource(R.drawable.backup),
                            title = stringResource(R.string.backup_restore),
                            subtitle = stringResource(R.string.settings_backup_restore_subtitle),
                            accentColor = MaterialTheme.colorScheme.primary,
                            onClick = { navController.navigate("settings/backup_restore") },
                        ),
                    ),
            ),
        )

        // Группа 4: Сеть и сервисы (2 элемента)
        add(
            SettingsGroup(
                title = stringResource(R.string.settings_section_network),
                items =
                    listOf(
                        SettingsItem(
                            key = "internet",
                            icon = painterResource(R.drawable.wifi_proxy),
                            title = stringResource(R.string.internet),
                            subtitle = stringResource(R.string.settings_internet_subtitle),
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            onClick = { navController.navigate("settings/internet") },
                        ),
                        SettingsItem(
                            key = "po_token",
                            icon = painterResource(R.drawable.token),
                            title = stringResource(R.string.po_token_generation),
                            subtitle = stringResource(R.string.settings_po_token_subtitle),
                            accentColor = MaterialTheme.colorScheme.secondary,
                            onClick = { navController.navigate("settings/po_token") },
                        ),
                    ),
            ),
        )

        // Группа 5: О системе и приложении (2-3 элемента)
        add(
            SettingsGroup(
                title = stringResource(R.string.settings_section_about),
                items =
                    buildList {
                        add(
                            SettingsItem(
                                key = "behavior",
                                icon = painterResource(R.drawable.swipe),
                                title = stringResource(R.string.settings_behavior_title),
                                subtitle = stringResource(R.string.settings_behavior_subtitle),
                                accentColor = MaterialTheme.colorScheme.primary,
                                onClick = { navController.navigate("settings/privacy") },
                            ),
                        )
                        if (isAndroid12OrLater) {
                            add(
                                SettingsItem(
                                    key = "default_links",
                                    icon = painterResource(R.drawable.link),
                                    title = stringResource(R.string.default_links),
                                    subtitle = stringResource(R.string.open_supported_links),
                                    accentColor = MaterialTheme.colorScheme.secondary,
                                    onClick = {
                                        try {
                                            val intent =
                                                Intent(
                                                    Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                                    Uri.parse("package:${context.packageName}"),
                                                )
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.error_unknown),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                ),
                            )
                        }
                        if (BuildConfig.UPDATER_AVAILABLE) {
                            add(
                                SettingsItem(
                                    key = "updates",
                                    icon = painterResource(R.drawable.ic_refresh),
                                    title = stringResource(R.string.updates),
                                    subtitle =
                                        if (hasUpdate) {
                                            stringResource(R.string.new_version_available)
                                        } else {
                                            stringResource(R.string.settings_updates_subtitle)
                                        },
                                    showUpdateIndicator = hasUpdate,
                                    badge = if (hasUpdate) "v${BuildConfig.VERSION_NAME}" else BuildConfig.VERSION_NAME,
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    onClick = { navController.navigate("settings/update") },
                                ),
                            )
                        }
                        add(
                            SettingsItem(
                                key = "about",
                                icon = painterResource(R.drawable.ic_about),
                                title = stringResource(R.string.about),
                                subtitle = "v${BuildConfig.VERSION_NAME}",
                                showUpdateIndicator = false,
                                accentColor = MaterialTheme.colorScheme.primary,
                                onClick = { navController.navigate("settings/about") },
                            ),
                        )
                    },
            ),
        )
    }
