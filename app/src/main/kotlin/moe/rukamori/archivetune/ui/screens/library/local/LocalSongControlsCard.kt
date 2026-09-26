/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library.local

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.SortHeader
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

@Composable
internal fun LocalSongControlsCard(
    sortType: LocalSongSortType,
    sortDescending: Boolean,
    visibleSongCount: Int,
    shuffleEnabled: Boolean,
    onSortTypeChange: (LocalSongSortType) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    onShuffleClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding, vertical = 8.dp),
    ) {
        SortHeader(
            sortType = sortType,
            sortDescending = sortDescending,
            onSortTypeChange = onSortTypeChange,
            onSortDescendingChange = onSortDescendingChange,
            sortTypeText = { selectedSort ->
                when (selectedSort) {
                    LocalSongSortType.MODIFIED -> R.string.sort_by_last_updated
                    LocalSongSortType.NAME -> R.string.sort_by_name
                    LocalSongSortType.ARTIST -> R.string.sort_by_artist
                    LocalSongSortType.ALBUM -> R.string.sort_by_album
                }
            },
        )

        Spacer(modifier = Modifier.width(8.dp))

        val shuffleLabel = stringResource(R.string.shuffle)
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .yumaClickable(
                        enabled = shuffleEnabled,
                        pressedScale = SettingsAnimations.PressScale,
                        onClick = onShuffleClick,
                    )
                    .yumaGlassCard(
                        shape = CircleShape,
                        backgroundColor = LocalYumaColors.current.glassBackground,
                    )
                    .clip(CircleShape)
                    .semantics(mergeDescendants = true) {
                        contentDescription = shuffleLabel
                        role = Role.Button
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.shuffle),
                contentDescription = null,
                tint =
                    if (shuffleEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = pluralStringResource(R.plurals.n_song, visibleSongCount, visibleSongCount),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}
