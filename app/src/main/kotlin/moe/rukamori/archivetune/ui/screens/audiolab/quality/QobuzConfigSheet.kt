/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab.quality

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.screens.settings.player.FlacTokenInputs
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@Composable
internal fun QobuzConfigSheet(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    appId: String,
    onAppIdChange: (String) -> Unit,
    appSecret: String,
    onAppSecretChange: (String) -> Unit,
    userAuthToken: String,
    onUserAuthTokenChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    QualityConfigSheetShell(
        title = stringResource(R.string.qobuz_config_title),
        subtitle = stringResource(R.string.qobuz_config_subtitle),
        onDismiss = onDismiss,
    ) {
        PreferenceGroup {
            item {
                SwitchPreference(
                    title = { Text(stringResource(R.string.qobuz_enable)) },
                    icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            FlacTokenInputs(
                qobuzAppId = appId,
                onQobuzAppIdChange = onAppIdChange,
                qobuzAppSecret = appSecret,
                onQobuzAppSecretChange = onAppSecretChange,
                qobuzUserAuthToken = userAuthToken,
                onQobuzUserAuthTokenChange = onUserAuthTokenChange,
            )
        }
        Spacer(Modifier.height(SettingsDimensions.SegmentGap))
    }
}
