/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */


package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.utils.ColorExtractor
import moe.rukamori.archivetune.about.TeamMember
import androidx.core.graphics.toColorInt

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
                    Color(it.toColorInt())
                } catch (_: Exception) {
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

