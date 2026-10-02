/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.viewmodels.AboutLinkCollection
import moe.rukamori.archivetune.viewmodels.TeamMember

@Composable
internal fun AboutSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
        modifier =
            modifier.padding(
                horizontal = SettingsDimensions.SectionHeaderHorizontalPadding,
                vertical = SettingsDimensions.SectionHeaderBottomPadding,
            ),
    )
}

@Composable
internal fun TeamMemberListItem(
    member: TeamMember,
    onOpenUri: (String) -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    extractedColor: Color? = null,
    onAvatarPixelsReady: ((IntArray) -> Unit)? = null,
    avatarSize: Dp = 56.dp,
    minHeight: Dp = 88.dp,
) {
    val profileUrl = member.profileUrl
    val itemClickModifier =
        remember(profileUrl, onOpenUri) {
            if (profileUrl.isNullOrBlank()) {
                Modifier
            } else {
                Modifier.clickable { onOpenUri(profileUrl) }
            }
        }
    val context = LocalContext.current

    ListItem(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .then(itemClickModifier),
        colors = ListItemDefaults.colors(containerColor = containerColor),
        leadingContent = {
            Box(
                modifier =
                    Modifier
                        .size(avatarSize)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        (extractedColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.20f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                                    ),
                            ),
                        ).border(
                            width = 1.5.dp,
                            brush =
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            (extractedColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.70f),
                                            (extractedColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.20f),
                                        ),
                                ),
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                val avatarImageRequest =
                    remember(member.avatarUrl, context) {
                        ImageRequest.Builder(context)
                            .data(member.avatarUrl)
                            .diskCacheKey(member.avatarUrl)
                            .memoryCacheKey(member.avatarUrl)
                            .crossfade(true)
                            .allowHardware(false)
                            .build()
                    }

                AsyncImage(
                    model = avatarImageRequest,
                    placeholder = painterResource(R.drawable.person),
                    error = painterResource(R.drawable.person),
                    fallback = painterResource(R.drawable.person),
                    contentDescription = null,
                    onSuccess = { success ->
                        if (onAvatarPixelsReady != null) {
                            val bmp = success.result.image.toBitmap()
                            val w = bmp.width
                            val h = bmp.height
                            if (w > 0 && h > 0) {
                                val pixels = IntArray(w * h)
                                bmp.getPixels(pixels, 0, w, 0, 0, w, h)
                                onAvatarPixelsReady(pixels)
                            }
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
        },
        headlineContent = {
            Text(
                text = member.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(member.positionResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            MemberLinkActions(
                links = member.links,
                onOpenUri = onOpenUri,
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemberLinkActions(
    links: AboutLinkCollection,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.widthIn(max = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(links.size) { index ->
            val link = links[index]
            val onClick =
                remember(link.url, onOpenUri) {
                    { onOpenUri(link.url) }
                }

            FilledTonalIconButton(
                onClick = onClick,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(link.iconResId),
                    contentDescription = stringResource(link.labelResId),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
