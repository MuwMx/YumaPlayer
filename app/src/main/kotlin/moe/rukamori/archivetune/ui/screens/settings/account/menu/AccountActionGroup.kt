/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.account.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.screens.settings.components.segmentedSettingsItemShape
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.YumaSegmentPosition
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.theme.yumaSegmentPosition

@Composable
internal fun AccountActionGroup(
    isLoggedIn: Boolean,
    isCurrentSaved: Boolean,
    onSaveAccount: () -> Unit,
    onAddAnotherAccount: () -> Unit,
    onAdvancedLogin: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
    onBrowserLogin: () -> Unit = {},
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
    ) {
        if (isLoggedIn) {
            val actionsCount = (if (!isCurrentSaved) 1 else 0) + 3
            var actionIndex = 0

            if (!isCurrentSaved) {
                val currIndex = actionIndex++
                AccountActionItem(
                    title = stringResource(R.string.save_current_account),
                    icon = painterResource(R.drawable.download),
                    tint = MaterialTheme.colorScheme.primary,
                    shape = segmentedSettingsItemShape(currIndex, actionsCount),
                    position = yumaSegmentPosition(currIndex, actionsCount),
                    onClick = onSaveAccount,
                )
            }

            val addIndex = actionIndex++
            AccountActionItem(
                title = stringResource(R.string.add_another_account),
                icon = painterResource(R.drawable.add),
                tint = MaterialTheme.colorScheme.primary,
                shape = segmentedSettingsItemShape(addIndex, actionsCount),
                position = yumaSegmentPosition(addIndex, actionsCount),
                onClick = {
                    onDismissRequest()
                    onAddAnotherAccount()
                },
            )

            val tokenIndex = actionIndex++
            AccountActionItem(
                title = stringResource(R.string.token_settings),
                subtitle = stringResource(R.string.token_adv_login_description),
                icon = painterResource(R.drawable.token),
                tint = MaterialTheme.colorScheme.secondary,
                shape = segmentedSettingsItemShape(tokenIndex, actionsCount),
                position = yumaSegmentPosition(tokenIndex, actionsCount),
                onClick = {
                    onDismissRequest()
                    onAdvancedLogin()
                },
            )

            val logoutIndex = actionIndex
            AccountActionItem(
                title = stringResource(R.string.action_logout),
                icon = painterResource(R.drawable.logout),
                tint = MaterialTheme.colorScheme.error,
                textColor = MaterialTheme.colorScheme.error,
                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                shape = segmentedSettingsItemShape(logoutIndex, actionsCount),
                position = yumaSegmentPosition(logoutIndex, actionsCount),
                onClick = {
                    onDismissRequest()
                    onLogout()
                },
            )
        } else {
            AccountActionItem(
                title = stringResource(R.string.login),
                subtitle = stringResource(R.string.login_browser),
                icon = painterResource(R.drawable.login),
                tint = MaterialTheme.colorScheme.primary,
                shape = segmentedSettingsItemShape(0, 2),
                position = yumaSegmentPosition(0, 2),
                onClick = {
                    onDismissRequest()
                    onBrowserLogin()
                },
            )

            AccountActionItem(
                title = stringResource(R.string.advanced_login),
                subtitle = stringResource(R.string.token_adv_login_description),
                icon = painterResource(R.drawable.token),
                tint = MaterialTheme.colorScheme.secondary,
                shape = segmentedSettingsItemShape(1, 2),
                position = yumaSegmentPosition(1, 2),
                onClick = {
                    onDismissRequest()
                    onAdvancedLogin()
                },
            )
        }
    }
}

@Composable
private fun AccountActionItem(
    title: String,
    icon: Painter,
    shape: Shape,
    position: YumaSegmentPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = Color.White,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .yumaClickable(pressedScale = SettingsAnimations.PressScale, onClick = onClick)
            .yumaGlassCard(
                shape = shape,
                position = position,
                backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                borderColor = borderColor,
                strokeWidth = SettingsDimensions.GlassBorderThickness,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
