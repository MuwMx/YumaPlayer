/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.YumaSegmentPosition
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.viewmodels.LibraryTopMixEmptyReason
import moe.rukamori.archivetune.viewmodels.LibraryTopMixUiModel
import moe.rukamori.archivetune.viewmodels.LibraryTopMixesUiState

@Composable
internal fun TopMixesForYouSection(
    state: LibraryTopMixesUiState,
    onRefresh: () -> Unit,
    onConfigureAi: () -> Unit,
    onPlayMix: (LibraryTopMixUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        LibraryTopMixesUiState.Loading -> {
            TopMixesMessageSection(
                message = stringResource(R.string.library_top_mixes_loading),
                isRefreshing = true,
                onRefresh = onRefresh,
                showRefresh = false,
                modifier = modifier,
            )
        }

        is LibraryTopMixesUiState.Empty -> {
            TopMixesEmptySection(
                reason = state.reason,
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                onConfigureAi = onConfigureAi,
                modifier = modifier,
            )
        }

        is LibraryTopMixesUiState.Error -> {
            TopMixesMessageSection(
                message = state.message,
                isRefreshing = false,
                onRefresh = onRefresh,
                showRefresh = true,
                modifier = modifier,
            )
        }

        is LibraryTopMixesUiState.Success -> {
            if (state.mixes.isEmpty()) {
                TopMixesMessageSection(
                    message = stringResource(R.string.library_top_mixes_no_recent_history),
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    showRefresh = true,
                    modifier = modifier,
                )
            } else {
                Column(modifier = modifier.fillMaxWidth()) {
                    TopMixesHeader(
                        isRefreshing = state.isRefreshing,
                        onRefresh = onRefresh,
                        showRefresh = true,
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = SettingsDimensions.ScreenHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.ScreenHorizontalPadding),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(
                            items = state.mixes,
                            key = { mix -> mix.id },
                            contentType = { "library_top_mix" },
                        ) { mix ->
                            LibraryTopMixCard(
                                mix = mix,
                                onPlay = { onPlayMix(mix) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopMixesMessageSection(
    message: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    showRefresh: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TopMixesHeader(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            showRefresh = showRefresh,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TopMixesEmptySection(
    reason: LibraryTopMixEmptyReason,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onConfigureAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TopMixesHeader(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            showRefresh = reason != LibraryTopMixEmptyReason.AI_NOT_CONFIGURED,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                    .yumaGlassCard(
                        shape = RoundedCornerShape(SettingsDimensions.LibrarySheetRadius),
                        position = YumaSegmentPosition.Single,
                    )
                    .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing),
            ) {
                Text(
                    text =
                        stringResource(
                            when (reason) {
                                LibraryTopMixEmptyReason.AI_NOT_CONFIGURED -> R.string.library_top_mixes_ai_not_configured_title
                                LibraryTopMixEmptyReason.NO_RECENT_HISTORY -> R.string.library_top_mixes_no_recent_history_title
                            },
                        ),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        stringResource(
                            when (reason) {
                                LibraryTopMixEmptyReason.AI_NOT_CONFIGURED -> R.string.library_top_mixes_ai_not_configured_desc
                                LibraryTopMixEmptyReason.NO_RECENT_HISTORY -> R.string.library_top_mixes_no_recent_history
                            },
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (reason == LibraryTopMixEmptyReason.AI_NOT_CONFIGURED) {
                    Button(onClick = onConfigureAi) {
                        Text(text = stringResource(R.string.library_top_mixes_configure_ai))
                    }
                } else {
                    FilledTonalButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                    ) {
                        Text(text = stringResource(R.string.library_top_mixes_refresh))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopMixesHeader(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    showRefresh: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = SettingsDimensions.ScreenHorizontalPadding, top = 8.dp, end = SettingsDimensions.ScreenHorizontalPadding, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.top_mixes),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (showRefresh) {
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.sync),
                        contentDescription = stringResource(R.string.library_top_mixes_refresh),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTopMixCard(
    mix: LibraryTopMixUiModel,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .width(180.dp)
                .height(130.dp)
                .yumaClickable(
                    pressedScale = SettingsAnimations.PressScale,
                    onClick = onPlay,
                )
                .yumaGlassCard(
                    shape = RoundedCornerShape(SettingsDimensions.LibrarySheetRadius),
                    position = YumaSegmentPosition.Single,
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(SettingsDimensions.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = mix.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = mix.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    mix.tracks.take(3).forEach { track ->
                        val artworkUrl = track.thumbnailUrl
                        if (artworkUrl == null) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                            )
                        } else {
                            AsyncImage(
                                model = artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onPlay,
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.play),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
