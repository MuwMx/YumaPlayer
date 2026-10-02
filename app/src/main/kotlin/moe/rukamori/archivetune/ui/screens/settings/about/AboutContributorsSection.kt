/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.viewmodels.AboutContributorsUiState

@Composable
internal fun ContributorsSection(
    state: AboutContributorsUiState,
    readMoreUrl: String,
    onOpenProfile: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYumaColors.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AboutSectionHeader(title = stringResource(R.string.about_contributors))

        when (state) {
            is AboutContributorsUiState.Success -> {
                ContributorList(
                    contributors = state.contributors,
                    readMoreUrl = readMoreUrl,
                    onOpenProfile = onOpenProfile,
                )
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .yumaGlassCard(
                            shape = RoundedCornerShape(18.dp),
                            backgroundColor = colors.glassBackground,
                            borderColor = colors.glassBorder,
                            strokeWidth = SettingsDimensions.GlassBorderThickness,
                        ),
                ) {
                    val (message, showRetry) = when (state) {
                        AboutContributorsUiState.Loading -> stringResource(R.string.loading) to false
                        AboutContributorsUiState.Empty -> stringResource(R.string.no_results_found) to true
                        is AboutContributorsUiState.Error -> stringResource(state.messageResId) to true
                        is AboutContributorsUiState.Success -> "" to false
                    }

                    ContributorStatusContent(
                        message = message,
                        showRetry = showRetry,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContributorStatusContent(
    message: String,
    showRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!showRetry) {
            LoadingIndicator(modifier = Modifier.size(32.dp))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showRetry) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .yumaClickable(
                        pressedScale = SettingsAnimations.PressScale,
                        onClick = onRetry,
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.retry),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}