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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.viewmodels.AboutContributorUiCollection

@Composable
internal fun ContributorList(
    contributors: AboutContributorUiCollection,
    readMoreUrl: String,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasReadMore = readMoreUrl.isNotBlank()
    val totalCount = contributors.size + if (hasReadMore) 1 else 0

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.5.dp),
    ) {
        repeat(contributors.size) { index ->
            val contributor = contributors[index]
            ContributorListItem(
                index = index,
                count = totalCount,
                login = contributor.login,
                avatarUrl = contributor.avatarUrl,
                profileUrl = contributor.profileUrl,
                onOpenProfile = onOpenProfile,
            )
        }

        if (hasReadMore) {
            ContributorReadMoreListItem(
                index = totalCount - 1,
                count = totalCount,
                readMoreUrl = readMoreUrl,
                onOpenProfile = onOpenProfile,
            )
        }
    }
}

@Composable
private fun ContributorBaseCard(
    index: Int,
    count: Int,
    onClick: (() -> Unit)?,
    showArrow: Boolean,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    title: @Composable () -> Unit,
) {
    val colors = LocalYumaColors.current
    val shape = remember(index, count) { segmentedSettingsItemShape(index, count) }
    val cardMod = if (index == 0) {
        Modifier.yumaGlassCard(shape, colors.glassBackground, colors.glassBorder, SettingsDimensions.GlassBorderThickness)
    } else {
        Modifier.clip(shape).background(colors.glassBackground)
    }
    val clickMod = if (onClick != null) {
        Modifier.yumaClickable(pressedScale = SettingsAnimations.PressScale, onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(clickMod)
            .then(cardMod)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            leading()
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) { title() }
            if (showArrow) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun ContributorListItem(
    index: Int,
    count: Int,
    login: String,
    avatarUrl: String,
    profileUrl: String,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYumaColors.current
    val isClickable = profileUrl.isNotBlank()

    ContributorBaseCard(
        index = index,
        count = count,
        onClick = if (isClickable) { { onOpenProfile(profileUrl) } } else null,
        showArrow = isClickable,
        modifier = modifier,
        leading = {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(colors.glassBackground),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.person),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        title = {
            Text(
                text = login,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
internal fun ContributorReadMoreListItem(
    index: Int,
    count: Int,
    readMoreUrl: String,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ContributorBaseCard(
        index = index,
        count = count,
        onClick = { onOpenProfile(readMoreUrl) },
        showArrow = true,
        modifier = modifier,
        leading = {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.add_circle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.more),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}