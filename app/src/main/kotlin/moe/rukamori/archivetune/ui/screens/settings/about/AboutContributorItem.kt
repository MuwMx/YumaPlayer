/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

@Composable
internal fun ContributorList(
    contributors: AboutContributorUiCollection,
    readMoreUrl: String,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(contributors.size) { index ->
            val contributor = contributors[index]

            ContributorListItem(
                login = contributor.login,
                avatarUrl = contributor.avatarUrl,
                profileUrl = contributor.profileUrl,
                onOpenProfile = onOpenProfile,
            )

            if (index < contributors.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    thickness = SettingsDimensions.DividerThickness,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 72.dp),
            thickness = SettingsDimensions.DividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        ContributorReadMoreListItem(
            readMoreUrl = readMoreUrl,
            onOpenProfile = onOpenProfile,
        )
    }
}

@Composable
internal fun ContributorListItem(
    login: String,
    avatarUrl: String,
    profileUrl: String,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemClickModifier =
        remember(profileUrl, onOpenProfile) {
            if (profileUrl.isBlank()) {
                Modifier
            } else {
                Modifier.clickable { onOpenProfile(profileUrl) }
            }
        }
    val context = LocalContext.current
    val imageRequest =
        remember(avatarUrl, context) {
            ImageRequest.Builder(context)
                .data(avatarUrl)
                .diskCacheKey(avatarUrl)
                .memoryCacheKey(avatarUrl)
                .crossfade(true)
                .build()
        }

    ListItem(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .then(itemClickModifier),
        colors =
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        leadingContent = {
            AsyncImage(
                model = imageRequest,
                placeholder = painterResource(R.drawable.person),
                error = painterResource(R.drawable.person),
                fallback = painterResource(R.drawable.person),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        },
        headlineContent = {
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
    readMoreUrl: String,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onClick =
        remember(readMoreUrl, onOpenProfile) {
            { onOpenProfile(readMoreUrl) }
        }

    ListItem(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(onClick = onClick),
        colors =
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.add_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        },
        headlineContent = {
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
