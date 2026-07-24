/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import moe.rukamori.archivetune.R

/**
 * Returns a `Material3SettingsItem` that can be placed inside a `Material3SettingsGroup`.
 * The caller should supply composables or values for the dynamic content.
 */
@Composable
fun DebugPanelItem(
    title: @Composable () -> Unit,
    description: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
): Material3SettingsItem =
    Material3SettingsItem(
        icon = painterResource(R.drawable.ic_about),
        title = title,
        description = description,
        trailingContent = trailingContent,
        isHighlighted = true,
    )
