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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.utils.ColorExtractor
import moe.rukamori.archivetune.viewmodels.AboutLinkCollection
import moe.rukamori.archivetune.viewmodels.TeamMember
import moe.rukamori.archivetune.viewmodels.TeamMemberCollection

@Composable
internal fun LeadDeveloperSection(
    member: TeamMember,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var extractedColorHex by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AboutSectionHeader(title = stringResource(R.string.about_lead_developer))

        val colors = LocalYumaColors.current
        val cardShape = MaterialTheme.shapes.extraLarge

        val extractedColor = remember(extractedColorHex) {
            extractedColorHex?.let {
                try {
                    Color(android.graphics.Color.parseColor(it))
                } catch (e: Exception) {
                    null
                }
            }
        }

        val gradientStart = extractedColor?.copy(alpha = 0.35f)
            ?: MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            gradientStart,
                            colors.glassBackground,
                        )
                    )
                )
                .border(1.dp, colors.glassBorder, cardShape)
        ) {
            TeamMemberListItem(
                member = member,
                onOpenUri = onOpenUri,
                containerColor = Color.Transparent,
                extractedColor = extractedColor,
                onAvatarPixelsReady = { pixels ->
                    scope.launch(Dispatchers.IO) {
                        val hex = ColorExtractor.extractVibrantHex(pixels)
                        withContext(Dispatchers.Main) {
                            extractedColorHex = hex
                        }
                    }
                },
                avatarSize = 72.dp,
                minHeight = 104.dp,
            )
        }
    }
}

@Composable
internal fun TeamMemberSection(
    title: String,
    members: TeamMemberCollection,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (members.isEmpty) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AboutSectionHeader(title = title)

        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors =
                androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column {
                repeat(members.size) { index ->
                    TeamMemberListItem(
                        member = members[index],
                        onOpenUri = onOpenUri,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    )

                    if (index < members.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 88.dp),
                            thickness = SettingsDimensions.DividerThickness,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}
