/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.spotify.SpotifyAccountUiState
import moe.rukamori.archivetune.ui.component.InfoLabel
import moe.rukamori.archivetune.ui.component.LocalPreferenceGroupPosition
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.PreferenceGroupPosition
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.component.rememberPreferenceIconShape
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.utils.SavedAccount
import moe.rukamori.archivetune.utils.SavedAccountCollection
import moe.rukamori.archivetune.viewmodels.AccountChannelUiModel
import moe.rukamori.archivetune.viewmodels.AccountChannelsState

private val CardShape = RoundedCornerShape(28.dp)
private val RowIconSize = 42.dp

@Composable
fun ProfileIdentityCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    isLoggedIn: Boolean,
    accountName: String,
    accountEmail: String,
    accountHandle: String,
    accountImageUrl: String?,
    savedAccounts: SavedAccountCollection,
    activeInnerTubeCookie: String,
    activeDataSyncId: String,
    accountChannelsState: AccountChannelsState,
    extractedColorHex: String? = null,
    onAvatarPixelsReady: (IntArray) -> Unit = {},
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    onSaveAccount: () -> Unit,
    onSwitchAccount: (SavedAccount) -> Unit,
    onSwitchAccountChannel: (AccountChannelUiModel) -> Unit,
    onRemoveAccount: (SavedAccount) -> Unit,
    onAddAnotherAccount: () -> Unit,
) {
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val menuChevronRotation by animateFloatAsState(
        targetValue = if (accountMenuExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "accountMenuChevron",
    )

    val colors = LocalYumaColors.current
    val context = LocalContext.current

    val groupPosition = LocalPreferenceGroupPosition.current
    val resolvedShape = shape ?: when (groupPosition) {
        PreferenceGroupPosition.First -> RoundedCornerShape(
            topStart = SettingsDimensions.SegmentedCornerLarge,
            topEnd = SettingsDimensions.SegmentedCornerLarge,
            bottomStart = SettingsDimensions.SegmentedCornerSmall,
            bottomEnd = SettingsDimensions.SegmentedCornerSmall
        )
        PreferenceGroupPosition.Middle -> RoundedCornerShape(
            SettingsDimensions.SegmentedCornerSmall
        )
        PreferenceGroupPosition.Last -> RoundedCornerShape(
            topStart = SettingsDimensions.SegmentedCornerSmall,
            topEnd = SettingsDimensions.SegmentedCornerSmall,
            bottomStart = SettingsDimensions.SegmentedCornerLarge,
            bottomEnd = SettingsDimensions.SegmentedCornerLarge
        )
        else -> RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)
    }

    val extractedColor = remember(extractedColorHex) {
        extractedColorHex?.let {
            try {
                Color(android.graphics.Color.parseColor(it))
            } catch (e: Exception) {
                null
            }
        }
    }

    val targetPrimary = if (isLoggedIn && extractedColor != null) extractedColor else MaterialTheme.colorScheme.primary
    val targetTertiary = MaterialTheme.colorScheme.tertiary

    val animatedPrimary by animateColorAsState(
        targetValue = targetPrimary,
        animationSpec = tween(durationMillis = 400),
        label = "primaryGlowColor"
    )
    val animatedTertiary by animateColorAsState(
        targetValue = targetTertiary,
        animationSpec = tween(durationMillis = 400),
        label = "tertiaryGlowColor"
    )

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val blendMode = if (isDark) BlendMode.Plus else BlendMode.SrcOver

    val spot1Alpha = if (isDark) 0.22f else 0.18f
    val spot1AlphaMid = if (isDark) 0.08f else 0.05f

    val spot2Alpha = if (isDark) 0.25f else 0.20f
    val spot2AlphaMid = if (isDark) 0.09f else 0.06f

    val transition = rememberInfiniteTransition(label = "profileMeshGlow")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(resolvedShape)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val spot1X = size.width * (0.5f + 0.35f * kotlin.math.cos(time))
                val spot1Y = size.height * (0.5f + 0.30f * kotlin.math.sin(time))

                val spot2X = size.width * (0.5f + 0.40f * kotlin.math.sin(time + 1.8f))
                val spot2Y = size.height * (0.5f + 0.35f * kotlin.math.cos(time + 1.8f))

                val spot1Gradient = Brush.radialGradient(
                    colors = listOf(
                        animatedPrimary.copy(alpha = spot1Alpha),
                        animatedPrimary.copy(alpha = spot1AlphaMid),
                        Color.Transparent
                    ),
                    center = Offset(spot1X, spot1Y),
                    radius = size.width * 0.85f
                )

                val spot2Gradient = Brush.radialGradient(
                    colors = listOf(
                        animatedTertiary.copy(alpha = spot2Alpha),
                        animatedTertiary.copy(alpha = spot2AlphaMid),
                        Color.Transparent
                    ),
                    center = Offset(spot2X, spot2Y),
                    radius = size.width * 0.90f
                )

                onDrawBehind {
                    drawRect(color = colors.glassBackground)
                    drawRect(brush = spot1Gradient, blendMode = blendMode)
                    drawRect(brush = spot2Gradient, blendMode = blendMode)
                }
            }
            .border(1.dp, colors.glassBorder, resolvedShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    animatedPrimary.copy(alpha = 0.20f),
                                    animatedTertiary.copy(alpha = 0.10f),
                                ),
                            )
                        ).border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    animatedPrimary.copy(alpha = 0.70f),
                                    animatedPrimary.copy(alpha = 0.20f),
                                ),
                            ),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoggedIn && !accountImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(accountImageUrl)
                                .size(64, 64)
                                .allowHardware(false)
                                .build(),
                            contentDescription = null,
                            onSuccess = { success ->
                                val bmp = success.result.image.toBitmap()
                                val w = bmp.width
                                val h = bmp.height
                                if (w > 0 && h > 0) {
                                    val pixels = IntArray(w * h)
                                    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
                                    onAvatarPixelsReady(pixels)
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                if (isLoggedIn) R.drawable.account else R.drawable.login,
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = animatedPrimary,
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isLoggedIn,
                    enter = scaleIn(spring(stiffness = Spring.StiffnessHigh)),
                    exit = scaleOut(),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = animatedPrimary,
                        modifier = Modifier.size(18.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = accountName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )

                if (accountHandle.isNotBlank()) {
                    Text(
                        text = accountHandle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                } else if (!isLoggedIn) {
                    Text(
                        text = stringResource(R.string.not_logged_in),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                    )
                }

                Box(modifier = Modifier.padding(top = 6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .height(38.dp)
                                .yumaClickable(pressedScale = 0.95f, onClick = onPrimaryAction),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    painter = painterResource(if (isLoggedIn) R.drawable.account else R.drawable.login),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = if (isLoggedIn) stringResource(R.string.account) else stringResource(R.string.login),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        if (isLoggedIn) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(38.dp)
                                    .yumaClickable(
                                        pressedScale = 0.95f,
                                        onClick = { accountMenuExpanded = !accountMenuExpanded },
                                    ),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.expand_more),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .rotate(menuChevronRotation),
                                    )
                                }
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false },
                    ) {
                        val accountChannels = (accountChannelsState as? AccountChannelsState.Success)?.channels
                        if (accountChannels != null && accountChannels.items.size > 1) {
                            Text(
                                text = stringResource(R.string.youtube_channels),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                            accountChannels.items.forEach { channel ->
                                val isActive = channel.isSelected || channel.dataSyncId == activeDataSyncId
                                DropdownMenuItem(
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text(
                                                text = channel.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val channelSubtitle = channel.channelHandle.ifBlank { channel.byline }
                                            if (channelSubtitle.isNotBlank()) {
                                                Text(
                                                    text = channelSubtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.account),
                                            contentDescription = null,
                                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    onClick = {
                                        if (!isActive) onSwitchAccountChannel(channel)
                                        accountMenuExpanded = false
                                    },
                                )
                            }
                            Spacer(modifier = Modifier.padding(vertical = 4.dp))
                        }

                        if (savedAccounts.accounts.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.saved_accounts),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                            savedAccounts.accounts.forEach { account ->
                                val isActive = account.innerTubeCookie == activeInnerTubeCookie
                                DropdownMenuItem(
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text(
                                                text = account.name.ifBlank { account.email },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (account.email.isNotBlank()) {
                                                Text(
                                                    text = account.email,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.account),
                                            contentDescription = null,
                                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    trailingIcon = {
                                        OutlinedIconButton(
                                            onClick = { onRemoveAccount(account) },
                                            modifier = Modifier.size(32.dp),
                                            border = null,
                                            colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.delete),
                                                contentDescription = stringResource(R.string.remove_account),
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        if (!isActive) onSwitchAccount(account)
                                        accountMenuExpanded = false
                                    },
                                )
                            }
                            Spacer(modifier = Modifier.padding(vertical = 4.dp))
                        }

                        if (isLoggedIn) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.save_current_account),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.bookmark),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    onSaveAccount()
                                    accountMenuExpanded = false
                                },
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.add_another_account),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.add_circle),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    accountMenuExpanded = false
                                    onAddAnotherAccount()
                                },
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .yumaClickable(
                        pressedScale = 0.94f,
                        onClick = onSecondaryAction,
                    )
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
                    .background(colors.glassBackground, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isLoggedIn) stringResource(R.string.action_logout) else stringResource(R.string.advanced_login),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun UpdateBannerStrip(
    modifier: Modifier = Modifier,
    latestVersion: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) SettingsAnimations.PressScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "updateScale",
    )

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        shape = CardShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        onClick = onClick,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BadgedBox(
                badge = { Badge(containerColor = MaterialTheme.colorScheme.error) },
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.10f),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.update),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.new_version_available),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = latestVersion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Medium,
                )
            }

            FilledTonalButton(
                onClick = onClick,
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.14f),
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    text = stringResource(R.string.update_text),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
internal fun AccountGeneralSection(
    isLoggedIn: Boolean,
    useLoginForBrowse: Boolean,
    onUseLoginForBrowseChange: (Boolean) -> Unit,
    ytmSync: Boolean,
    onYtmSyncChange: (Boolean) -> Unit,
    forceSyncOnAccountSwitch: Boolean,
    onForceSyncOnAccountSwitchChange: (Boolean) -> Unit,
) {
    AnimatedVisibility(
        visible = isLoggedIn,
        enter =
            fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                expandVertically(
                    spring(stiffness = Spring.StiffnessLow),
                ),
        exit = fadeOut() + shrinkVertically(),
    ) {
        PreferenceGroup(title = stringResource(R.string.general)) {
            item {
                SwitchPreference(
                    icon = { Icon(painterResource(R.drawable.add_circle), null) },
                    title = { Text(stringResource(R.string.more_content)) },
                    description = stringResource(R.string.use_login_for_browse_desc),
                    checked = useLoginForBrowse,
                    onCheckedChange = onUseLoginForBrowseChange,
                )
            }

            item {
                SwitchPreference(
                    icon = { Icon(painterResource(R.drawable.cached), null) },
                    title = { Text(stringResource(R.string.yt_sync)) },
                    checked = ytmSync,
                    onCheckedChange = onYtmSyncChange,
                )
            }

            item {
                SwitchPreference(
                    icon = { Icon(painterResource(R.drawable.sync), null) },
                    title = { Text(stringResource(R.string.force_sync_on_switch_account)) },
                    description = stringResource(R.string.force_sync_on_switch_account_desc),
                    checked = forceSyncOnAccountSwitch,
                    onCheckedChange = onForceSyncOnAccountSwitchChange,
                )
            }
        }
    }
}

@Composable
internal fun AccountIntegrationSection(
    spotifyState: SpotifyAccountUiState,
    useSpotifyHome: Boolean,
    spotifySyncLikes: Boolean,
    hideYtmLikedSongs: Boolean,
    onSpotifyEntryClick: () -> Unit,
    onUseSpotifyHomeChange: (Boolean) -> Unit,
    onSpotifySyncLikesChange: (Boolean) -> Unit,
    onHideYtmLikedSongsChange: (Boolean) -> Unit,
    onNavigateIntegration: () -> Unit,
    onNavigateMusicTogether: () -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.integration)) {
        item {
            PreferenceEntry(
                icon = { Icon(painterResource(R.drawable.spotify_icon), null) },
                title = {
                    Text(
                        if (spotifyState.isAuthenticated) {
                            if (spotifyState.accountName.isNotBlank()) {
                                stringResource(R.string.spotify_connected_as, spotifyState.accountName)
                            } else {
                                stringResource(R.string.spotify_account)
                            }
                        } else {
                            stringResource(R.string.spotify_connect)
                        }
                    )
                },
                description = if (spotifyState.isAuthenticated) {
                    if (spotifyState.playlistCount > 0) {
                        stringResource(R.string.spotify_available_count, spotifyState.playlistCount)
                    } else {
                        stringResource(R.string.spotify_no_sources)
                    }
                } else {
                    stringResource(R.string.spotify_not_connected)
                },
                onClick = onSpotifyEntryClick,
            )
        }

        item {
            ExpressiveSegmentedRow(
                icon = painterResource(if (useSpotifyHome) R.drawable.spotify_icon else R.drawable.yt_music_icon),
                title = stringResource(R.string.home_screen_provider),
                subtitle = stringResource(R.string.home_screen_provider_desc),
                selectedValue = useSpotifyHome,
                onValueSelected = onUseSpotifyHomeChange,
            )
        }

        if (spotifyState.isAuthenticated) {
            item {
                SwitchPreference(
                    icon = { Icon(painterResource(R.drawable.sync), null) },
                    title = { Text(stringResource(R.string.spotify_sync_likes)) },
                    description = stringResource(R.string.spotify_sync_likes_desc),
                    checked = spotifySyncLikes,
                    onCheckedChange = onSpotifySyncLikesChange,
                )
            }

            item {
                SwitchPreference(
                    icon = { Icon(painterResource(R.drawable.visibility_off), null) },
                    title = { Text(stringResource(R.string.hide_ytm_liked_songs)) },
                    checked = hideYtmLikedSongs,
                    onCheckedChange = onHideYtmLikedSongsChange,
                )
            }
        }

        item {
            PreferenceEntry(
                icon = { Icon(painterResource(R.drawable.integration), null) },
                title = { Text(stringResource(R.string.integration)) },
                description = stringResource(R.string.account_integrations_summary),
                onClick = onNavigateIntegration,
                showChevron = true,
            )
        }

        item {
            PreferenceEntry(
                icon = { Icon(painterResource(R.drawable.fire), null) },
                title = { Text(stringResource(R.string.music_together)) },
                onClick = onNavigateMusicTogether,
                showChevron = true,
            )
        }
    }
}

@Composable
internal fun AccountMiscSection(
    tokenActionTitle: String,
    tokenDescription: String,
    onNavigateHiddenPlaylists: () -> Unit,
    onTokenEntryClick: () -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.misc)) {
        item {
            PreferenceEntry(
                icon = { Icon(painterResource(R.drawable.visibility_off), null) },
                title = { Text(stringResource(R.string.hidden_playlists)) },
                description = stringResource(R.string.hidden_playlists_description),
                onClick = onNavigateHiddenPlaylists,
                showChevron = true,
            )
        }

        item {
            PreferenceEntry(
                icon = { Icon(painterResource(R.drawable.token), null) },
                title = { Text(tokenActionTitle) },
                description = tokenDescription,
                onClick = onTokenEntryClick,
            )
        }
    }
}

@Composable
fun ExpressiveSegmentedRow(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    selectedValue: Boolean,
    onValueSelected: (Boolean) -> Unit,
) {
    val colors = LocalYumaColors.current
    val groupPosition = LocalPreferenceGroupPosition.current

    val shape = when (groupPosition) {
        null,
        PreferenceGroupPosition.Single -> RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)
        PreferenceGroupPosition.First -> RoundedCornerShape(
            topStart = SettingsDimensions.SegmentedCornerLarge,
            topEnd = SettingsDimensions.SegmentedCornerLarge,
            bottomEnd = SettingsDimensions.SegmentedCornerSmall,
            bottomStart = SettingsDimensions.SegmentedCornerSmall,
        )
        PreferenceGroupPosition.Middle -> RoundedCornerShape(SettingsDimensions.SegmentedCornerSmall)
        PreferenceGroupPosition.Last -> RoundedCornerShape(
            topStart = SettingsDimensions.SegmentedCornerSmall,
            topEnd = SettingsDimensions.SegmentedCornerSmall,
            bottomEnd = SettingsDimensions.SegmentedCornerLarge,
            bottomStart = SettingsDimensions.SegmentedCornerLarge,
        )
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .yumaGlassCard(
                    shape = shape,
                    backgroundColor = colors.glassBackground,
                    borderColor = colors.glassBorder,
                )
                .clip(shape)
                .padding(
                    horizontal = SettingsDimensions.RowHorizontalPadding,
                    vertical = SettingsDimensions.RowVerticalPadding,
                ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemPaddingVertical)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.RowIconSpacing),
            ) {
                ExpressiveRowIcon(
                    icon = icon,
                    title = title,
                    tint = MaterialTheme.colorScheme.primary,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                    }
                }
            }

            val indicatorOffset by animateFloatAsState(
                targetValue = if (selectedValue) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "SourceIndicatorOffset"
            )

            val barShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(barShape)
                    .background(colors.glassBackground)
                    .border(1.dp, colors.glassBorder, barShape),
                contentAlignment = Alignment.CenterStart
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    val tabWidth = maxWidth / 2

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(width = tabWidth, height = 36.dp)
                            .offset(x = tabWidth * indicatorOffset)
                    ) {}

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onValueSelected(false) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.home_provider_youtube_music),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!selectedValue) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onValueSelected(true) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.home_provider_spotify),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedValue) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveRowIcon(
    icon: Painter,
    title: String,
    tint: Color,
    emphasized: Boolean = false,
) {
    val iconShape = rememberPreferenceIconShape(title)
    val bgColor = if (emphasized) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        tint.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
    }
    val iconTint = if (emphasized) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        tint
    }

    Surface(
        modifier = Modifier.size(RowIconSize),
        shape = iconShape,
        color = bgColor,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(SettingsDimensions.SegmentedIconSize),
                tint = iconTint,
            )
        }
    }
}

@Composable
internal fun VersionStamp(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
        )
        Text(
            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
        )
    }
}

@Composable
fun TokenEditorDialog(
    innerTubeCookie: String,
    visitorData: String,
    dataSyncId: String,
    accountNamePref: String,
    accountEmail: String,
    accountChannelHandle: String,
    onInnerTubeCookieChange: (String) -> Unit,
    onPoTokenChange: (String) -> Unit,
    onVisitorDataChange: (String) -> Unit,
    onDataSyncIdChange: (String) -> Unit,
    onAccountNameChange: (String) -> Unit,
    onAccountEmailChange: (String) -> Unit,
    onAccountChannelHandleChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val text =
        """
        ***INNERTUBE COOKIE*** =$innerTubeCookie
        ***VISITOR DATA*** =$visitorData
        ***DATASYNC ID*** =$dataSyncId
        ***PO TOKEN*** =${YouTube.poToken.orEmpty()}
        ***ACCOUNT NAME*** =$accountNamePref
        ***ACCOUNT EMAIL*** =$accountEmail
        ***ACCOUNT CHANNEL HANDLE*** =$accountChannelHandle
        """.trimIndent()

    TextFieldDialog(
        initialTextFieldValue = TextFieldValue(text),
        onDone = { data ->
            data.split("\n").forEach {
                when {
                    it.startsWith("***INNERTUBE COOKIE*** =") -> onInnerTubeCookieChange(it.substringAfter("="))
                    it.startsWith("***VISITOR DATA*** =") -> onVisitorDataChange(it.substringAfter("="))
                    it.startsWith("***DATASYNC ID*** =") -> onDataSyncIdChange(it.substringAfter("="))
                    it.startsWith("***PO TOKEN*** =") -> onPoTokenChange(it.substringAfter("="))
                    it.startsWith("***ACCOUNT NAME*** =") -> onAccountNameChange(it.substringAfter("="))
                    it.startsWith("***ACCOUNT EMAIL*** =") -> onAccountEmailChange(it.substringAfter("="))
                    it.startsWith("***ACCOUNT CHANNEL HANDLE*** =") -> onAccountChannelHandleChange(it.substringAfter("="))
                }
            }
        },
        onDismiss = onDismiss,
        singleLine = false,
        maxLines = 20,
        isInputValid = {
            hasYouTubeLoginCookie(it)
        },
        extraContent = {
            InfoLabel(text = stringResource(R.string.token_adv_login_description))
        },
    )
}

@Composable
fun TokenEditorDialog(
    state: TokenEditorDialogState,
    actions: TokenEditorDialogActions,
) {
    TokenEditorDialog(
        innerTubeCookie = state.innerTubeCookie,
        visitorData = state.visitorData,
        dataSyncId = state.dataSyncId,
        accountNamePref = state.accountNamePref,
        accountEmail = state.accountEmail,
        accountChannelHandle = state.accountChannelHandle,
        onInnerTubeCookieChange = actions.onInnerTubeCookieChange,
        onPoTokenChange = actions.onPoTokenChange,
        onVisitorDataChange = actions.onVisitorDataChange,
        onDataSyncIdChange = actions.onDataSyncIdChange,
        onAccountNameChange = actions.onAccountNameChange,
        onAccountEmailChange = actions.onAccountEmailChange,
        onAccountChannelHandleChange = actions.onAccountChannelHandleChange,
        onDismiss = actions.onDismiss,
    )
}

@Composable
fun AccountSpotifyLoginSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onCookiesCaptured: (spDc: String, spKey: String) -> Unit,
) {
    if (show) {
        SpotifyLoginSheet(
            onDismiss = onDismiss,
            onCookiesCaptured = onCookiesCaptured,
        )
    }
}

@Composable
internal fun SpotifyOptionsDialog(
    spotifyState: SpotifyAccountUiState,
    showSpotifyPlaylists: Boolean,
    onShowSpotifyPlaylistsChange: (Boolean) -> Unit,
    onConnectSpotify: () -> Unit,
    onLogout: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.spotify_account),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                if (!spotifyState.isAuthenticated) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .yumaClickable(onClick = onConnectSpotify),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.spotify_icon),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.spotify_connect),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(R.string.spotify_not_connected),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .yumaClickable(onClick = {
                                onShowSpotifyPlaylistsChange(!showSpotifyPlaylists)
                            }),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.spotify_icon),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.spotify_show_playlist),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(R.string.spotify_show_playlist_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                )
                            }
                            Switch(
                                checked = showSpotifyPlaylists,
                                onCheckedChange = onShowSpotifyPlaylistsChange,
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f),
                        contentColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .yumaClickable(onClick = onLogout),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.logout),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.action_logout),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

@Composable
internal fun LoginChoiceDialog(
    onDismissRequest: () -> Unit,
    onBrowserLogin: () -> Unit,
    onAdvancedLogin: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.login),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .yumaClickable(onClick = onBrowserLogin),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.login),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.login),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "YouTube Browser",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .yumaClickable(onClick = onAdvancedLogin),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.token),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.advanced_login),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.token_adv_login_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

@Composable
internal fun UnsavedAccountDialog(
    onDismissRequest: () -> Unit,
    onCancel: () -> Unit,
    onNoThanks: () -> Unit,
    onSaveYes: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = CardShape,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.unsaved_account_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.unsaved_account_dialog_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onCancel,
                    ) {
                        Text(text = stringResource(R.string.unsaved_account_dialog_cancel))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = onNoThanks,
                    ) {
                        Text(
                            text = stringResource(R.string.unsaved_account_dialog_no_thanks),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = onSaveYes,
                    ) {
                        Text(
                            text = stringResource(R.string.unsaved_account_dialog_save_yes),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
