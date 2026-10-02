/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.GitCommit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun CommitHistorySection(
    commits: List<GitCommit>,
    isLoading: Boolean,
    isExpanded: Boolean,
    rotationAngle: Float,
    modifier: Modifier = Modifier,
    onToggleExpanded: () -> Unit,
    onCommitClick: (GitCommit) -> Unit,
) {
    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SegmentedListItem(
            onClick = onToggleExpanded,
            shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.extraLarge),
            modifier = Modifier.fillMaxWidth(),
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            leadingContent = {
                FeatureIcon(iconRes = R.drawable.history, containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            },
            trailingContent = {
                Icon(painter = painterResource(R.drawable.expand_more), contentDescription = null, modifier = Modifier.rotate(rotationAngle))
            },
            supportingContent = {
                Text(
                    text = when {
                        isLoading -> stringResource(R.string.updates_loading_commits)
                        commits.isEmpty() -> stringResource(R.string.updates_no_commits)
                        else -> stringResource(R.string.updates_recent_commits_count, commits.size)
                    },
                )
            },
            content = {
                Text(text = stringResource(R.string.recent_commits), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            },
        )

        AnimatedVisibility(visible = isExpanded) {
            when {
                isLoading -> {
                    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                LoadingIndicator(modifier = Modifier.size(32.dp))
                                Text(text = stringResource(R.string.updates_loading_commits), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                commits.isEmpty() -> {
                    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Text(
                            text = stringResource(R.string.updates_no_commits),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                        )
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        commits.forEachIndexed { index, commit ->
                            key(commit.sha) {
                                CommitItem(commit = commit, index = index, count = commits.size, onClick = { onCommitClick(commit) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FeatureIcon(
    @DrawableRes iconRes: Int,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(shape = CircleShape, color = containerColor) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = contentColor, modifier = Modifier.padding(12.dp).size(22.dp))
    }
}

@Composable
internal fun CommitItem(
    commit: GitCommit,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        leadingContent = { CommitAvatar(avatarUrl = commit.authorAvatarUrl) },
        trailingContent = {
            Icon(painter = painterResource(R.drawable.ic_arrow_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = commit.sha, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = if (commit.date.isNotEmpty()) "${commit.author} - ${formatCommitDate(commit.date)}" else commit.author,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        content = {
            Text(text = commit.message, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
    )
}

@Composable
internal fun CommitAvatar(avatarUrl: String?) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(model = avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painter = painterResource(R.drawable.ic_github), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
    }
}

internal fun formatCommitDate(isoDate: String): String =
    try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(isoDate)
        val outputFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        outputFormat.format(date!!)
    } catch (_: Exception) {
        isoDate.take(10)
    }
