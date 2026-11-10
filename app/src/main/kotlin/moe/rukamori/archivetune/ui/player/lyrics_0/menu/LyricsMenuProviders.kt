package moe.rukamori.archivetune.ui.player.lyrics_0.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PreferredLyricsProvider
import moe.rukamori.archivetune.constants.PreferredLyricsProviderKey
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.sett.SettingsMenuRow
import moe.rukamori.archivetune.ui.screens.settings.displayName
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
internal fun LyricsMenuProviders(
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    onBack: () -> Unit
) {
    val font = LocalArchiveTuneFontFamily.current
    val (currentProviderStr, setProviderStr) = rememberPreference(
        PreferredLyricsProviderKey,
        PreferredLyricsProvider.BETTER_LYRICS.name
    )

    val currentProvider = PreferredLyricsProvider.entries.find { it.name == currentProviderStr }
        ?: PreferredLyricsProvider.BETTER_LYRICS

    val availableProviders = PreferredLyricsProvider.entries

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.lyrics_provider),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = font,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )

            TextButton(onClick = onBack) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = font,
                )
            }
        }

        availableProviders.forEachIndexed { index, provider ->
            val isSelected = currentProvider == provider

            SettingsMenuRow(
                title = provider.displayName(),
                subtitle = if (isSelected) {
                    stringResource(R.string.lyrics_provider_active)
                } else {
                    stringResource(R.string.lyrics_provider_select)
                },
                iconResId = if (isSelected) R.drawable.check else R.drawable.queue_music,
                onClick = {
                    setProviderStr(provider.name)
                    onAction(PlayerAction.SearchLyrics)
                    onBack()
                },
                index = index,
                count = availableProviders.size,
            )

            if (index < availableProviders.size - 1) {
                Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))
            }
        }
    }
}
