/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.viewmodels.AboutContributorUiCollection
import moe.rukamori.archivetune.viewmodels.AboutContributorsUiState

@Composable
internal fun ContributorsSection(
    state: AboutContributorsUiState,
    readMoreUrl: String,
    onOpenProfile: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AboutSectionHeader(title = stringResource(R.string.about_contributors))

        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors =
                androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            when (state) {
                AboutContributorsUiState.Loading -> {
                    ContributorStatusContent(
                        message = stringResource(R.string.loading),
                        showRetry = false,
                        onRetry = onRetry,
                    )
                }

                AboutContributorsUiState.Empty -> {
                    ContributorStatusContent(
                        message = stringResource(R.string.no_results_found),
                        showRetry = true,
                        onRetry = onRetry,
                    )
                }

                is AboutContributorsUiState.Error -> {
                    ContributorStatusContent(
                        message = stringResource(state.messageResId),
                        showRetry = true,
                        onRetry = onRetry,
                    )
                }

                is AboutContributorsUiState.Success -> {
                    ContributorList(
                        contributors = state.contributors,
                        readMoreUrl = readMoreUrl,
                        onOpenProfile = onOpenProfile,
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
        modifier =
            modifier
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
            TextButton(onClick = onRetry) {
                Text(text = stringResource(R.string.retry))
            }
        }
    }
}
