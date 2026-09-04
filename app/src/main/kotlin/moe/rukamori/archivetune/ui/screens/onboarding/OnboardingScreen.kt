/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import moe.rukamori.archivetune.App.Companion.forgetAccount
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.onboarding.OnboardingCommunityActionUiModel
import moe.rukamori.archivetune.onboarding.OnboardingEvent
import moe.rukamori.archivetune.onboarding.OnboardingPageId
import moe.rukamori.archivetune.onboarding.OnboardingPermissionAction
import moe.rukamori.archivetune.onboarding.OnboardingPermissionStatus
import moe.rukamori.archivetune.onboarding.OnboardingPermissionUiModel
import moe.rukamori.archivetune.onboarding.OnboardingScreenState
import moe.rukamori.archivetune.onboarding.OnboardingUiState
import moe.rukamori.archivetune.spotify.SpotifyAccountViewModel
import moe.rukamori.archivetune.ui.screens.LoginScreen
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.onboarding.OnboardingViewModel
import moe.rukamori.archivetune.ui.screens.settings.FlacTokenInputs
import moe.rukamori.archivetune.ui.screens.settings.SpotifyLoginSheet
import moe.rukamori.archivetune.ui.screens.settings.TokenEditorDialog
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.YumaSegmentPosition
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.theme.yumaSegmentPosition
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.SavedAccount
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.encodeSavedAccounts
import moe.rukamori.archivetune.utils.putLegacyPoToken
import java.util.UUID

private val OnboardingContentMaxWidth = 540.dp
private val OnboardingPagePadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)

@Composable
private fun rememberExpressiveShapes(): List<Shape> {
    val s0 = MaterialShapes.Cookie4Sided.toShape()
    val s1 = MaterialShapes.Clover4Leaf.toShape()
    val s2 = MaterialShapes.Ghostish.toShape()
    val s3 = MaterialShapes.Sunny.toShape()
    return remember(s0, s1, s2, s3) { listOf(s0, s1, s2, s3) }
}

private fun segmentedOnboardingItemShape(
    index: Int,
    count: Int,
): Shape {
    val large = SettingsDimensions.SegmentedCornerLarge
    val small = SettingsDimensions.SegmentedCornerSmall
    return when {
        count <= 1 -> {
            RoundedCornerShape(large)
        }

        index == 0 -> {
            RoundedCornerShape(
                topStart = large,
                topEnd = large,
                bottomEnd = small,
                bottomStart = small,
            )
        }

        index == count - 1 -> {
            RoundedCornerShape(
                topStart = small,
                topEnd = small,
                bottomEnd = large,
                bottomStart = large,
            )
        }

        else -> {
            RoundedCornerShape(small)
        }
    }
}

@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onPermissionResult()
        }
    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.onPermissionResult()
        }

    LaunchedEffect(context, viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OnboardingEvent.RequestPermission -> {
                    permissionLauncher.launch(event.permission)
                }

                OnboardingEvent.OpenInstallPackagesSettings -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        val intent =
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                "package:${context.packageName}".toUri(),
                            )
                        settingsLauncher.launch(intent)
                    }
                }

                is OnboardingEvent.OpenUri -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                    context.startActivity(intent)
                }
            }
        }
    }

    OnboardingScreen(
        state = state,
        onNext = viewModel::onNext,
        onBack = viewModel::onBack,
        onComplete = viewModel::complete,
        onPermissionAction = viewModel::onPermissionAction,
        onCommunityAction = viewModel::onCommunityAction,
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingScreenState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (state) {
            OnboardingScreenState.Loading -> {
                LoadingContent(contentPadding = padding)
            }

            OnboardingScreenState.Empty -> {
                MessageContent(
                    title = stringResource(R.string.onboarding_empty_title),
                    subtitle = stringResource(R.string.onboarding_empty_subtitle),
                    actionLabel = stringResource(R.string.onboarding_finish),
                    onAction = onComplete,
                    contentPadding = padding,
                )
            }

            is OnboardingScreenState.Error -> {
                MessageContent(
                    title = stringResource(state.messageResId),
                    subtitle = stringResource(R.string.onboarding_empty_subtitle),
                    actionLabel = stringResource(R.string.onboarding_finish),
                    onAction = onComplete,
                    contentPadding = padding,
                )
            }

            is OnboardingScreenState.Success -> {
                OnboardingSuccessContent(
                    uiState = state.uiState,
                    onNext = onNext,
                    onBack = onBack,
                    onPermissionAction = onPermissionAction,
                    onCommunityAction = onCommunityAction,
                    contentPadding = padding,
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(contentPadding: PaddingValues) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}

@Composable
private fun MessageContent(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    contentPadding: PaddingValues,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = OnboardingContentMaxWidth)
                    .yumaGlassCard(shape = RoundedCornerShape(28.dp))
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = LocalArchiveTuneFontFamily.current,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = LocalArchiveTuneFontFamily.current,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onAction,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = actionLabel,
                    fontFamily = LocalArchiveTuneFontFamily.current,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun OnboardingSuccessContent(
    uiState: OnboardingUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
    contentPadding: PaddingValues,
) {
    val pagerState =
        rememberPagerState(
            initialPage = uiState.currentPage,
            pageCount = { uiState.pages.size },
        )

    LaunchedEffect(uiState.currentPage, uiState.pages.size) {
        val targetPage = uiState.currentPage.coerceIn(0, uiState.pages.lastIndex)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            beyondViewportPageCount = uiState.pages.size,
            key = { pageIndex -> uiState.pages[pageIndex].id },
            modifier = Modifier.weight(1f),
        ) { pageIndex ->
            when (uiState.pages[pageIndex].id) {
                OnboardingPageId.WELCOME -> {
                    WelcomePage(
                        uiState = uiState,
                        pageIndex = pageIndex,
                    )
                }

                OnboardingPageId.PERMISSIONS -> {
                    PermissionsPage(
                        uiState = uiState,
                        pageIndex = pageIndex,
                        onPermissionAction = onPermissionAction,
                    )
                }

                OnboardingPageId.CUSTOMIZATION -> {
                    CustomizationPage(
                        uiState = uiState,
                        pageIndex = pageIndex,
                    )
                }

                OnboardingPageId.COMMUNITY -> {
                    CommunityPage(
                        uiState = uiState,
                        pageIndex = pageIndex,
                        onCommunityAction = onCommunityAction,
                    )
                }
            }
        }

        GlassBottomNavigation(
            currentPage = pagerState.currentPage,
            pageCount = uiState.pages.size,
            onBack = onBack,
            onNext = onNext,
        )
    }
}

@Composable
private fun GlassBottomNavigation(
    currentPage: Int,
    pageCount: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val showBack = currentPage > 0
    val isLastPage = currentPage >= pageCount - 1
    val nextLabel =
        if (currentPage == 0) {
            stringResource(R.string.onboarding_lets_go)
        } else if (isLastPage) {
            stringResource(R.string.onboarding_finish)
        } else {
            stringResource(R.string.next)
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            repeat(pageCount) { index ->
                val selected = index == currentPage
                val dotWidth by animateDpAsState(
                    targetValue = if (selected) 26.dp else 10.dp,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                    label = "dotWidth",
                )
                Surface(
                    shape = CircleShape,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        },
                    modifier =
                        Modifier
                            .height(10.dp)
                            .width(dotWidth),
                ) {}

            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = showBack,
                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
            ) {
                val backShape = RoundedCornerShape(16.dp)
                val colors = LocalYumaColors.current
                Box(
                    modifier =
                        Modifier
                            .yumaClickable(onClick = onBack)
                            .yumaGlassCard(
                                shape = backShape,
                                backgroundColor = colors.glassBackground,
                                borderColor = colors.glassBorder,
                                strokeWidth = SettingsDimensions.GlassBorderThickness,
                            )
                            .clip(backShape),
                ) {
                    Text(
                        text = stringResource(R.string.back_button_desc),
                        fontFamily = LocalArchiveTuneFontFamily.current,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }
            }

            Surface(
                modifier =
                    Modifier
                        .weight(1f)
                        .yumaClickable(onClick = onNext),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = nextLabel,
                        fontFamily = LocalArchiveTuneFontFamily.current,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 15.sp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomePage(
    uiState: OnboardingUiState,
    pageIndex: Int,
) {
    val page = uiState.pages[pageIndex]
    val avatarShape = MaterialShapes.Cookie4Sided.toShape()

    val infiniteTransition = rememberInfiniteTransition(label = "avatarEffects")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val glassBg = LocalYumaColors.current.glassBackground

    val glowBrush = remember(primaryColor) {
        Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.45f),
                primaryColor.copy(alpha = 0.15f),
                Color.Transparent,
            ),
        )
    }
    val borderBrush = remember(primaryColor, tertiaryColor) {
        Brush.sweepGradient(
            colors = listOf(
                primaryColor,
                tertiaryColor,
                primaryColor,
            )
        )
    }
    val borderStroke = remember(borderBrush) {
        BorderStroke(width = 4.dp, brush = borderBrush)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = OnboardingPagePadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        item(key = "welcome_content", contentType = "welcome") {
            Column(
                modifier = Modifier
                    .widthIn(max = OnboardingContentMaxWidth)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(260.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .graphicsLayer { rotationZ = rotationAngle },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = glowBrush,
                                    shape = avatarShape,
                                )
                        )

                        Surface(
                            modifier = Modifier.size(210.dp),
                            shape = avatarShape,
                            color = glassBg,
                            border = borderStroke,
                        ) {}
                    }

                    Icon(
                        painter = painterResource(id = R.drawable.about_splash),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(150.dp),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(page.titleResId),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = LocalArchiveTuneFontFamily.current,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(page.subtitleResId),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontFamily = LocalArchiveTuneFontFamily.current,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )

                Spacer(modifier = Modifier.height(20.dp))

                OnboardingMetadataPills(uiState = uiState)
            }
        }
    }
}

@Composable
private fun OnboardingMetadataPills(uiState: OnboardingUiState) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PassivePill(text = stringResource(uiState.variantLabelResId))
        PassivePill(
            text =
                stringResource(
                    R.string.onboarding_version_label,
                    uiState.versionName,
                ),
        )
    }
}

@Composable
private fun PassivePill(text: String) {
    val pillShape = RoundedCornerShape(12.dp)
    val colors = LocalYumaColors.current
    Box(
        modifier =
            Modifier
                .yumaGlassCard(
                    shape = pillShape,
                    backgroundColor = colors.glassBackground,
                    borderColor = colors.glassBorder,
                    strokeWidth = SettingsDimensions.GlassBorderThickness,
                )
                .clip(pillShape),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            fontFamily = LocalArchiveTuneFontFamily.current,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PermissionsPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
) {
    val page = uiState.pages[pageIndex]
    val expressiveShapes = rememberExpressiveShapes()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = OnboardingPagePadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = page.id.name, contentType = "header") {
            ExpressivePageHeader(
                iconResId = page.iconResId,
                titleResId = page.titleResId,
                subtitleResId = page.subtitleResId,
            )
        }
        item(key = "permissions_group", contentType = "permissions_group") {
            val count = uiState.permissions.size
            Column(
                modifier =
                    Modifier
                        .widthIn(max = OnboardingContentMaxWidth)
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
            ) {
                uiState.permissions.forEachIndexed { index, item ->
                    val shape = remember(index, count) { segmentedOnboardingItemShape(index, count) }
                    val position = remember(index, count) { yumaSegmentPosition(index, count) }
                    GlassPermissionRow(
                        permission = item,
                        iconShape = expressiveShapes[index % expressiveShapes.size],
                        shape = shape,
                        position = position,
                        onPermissionAction = onPermissionAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassPermissionRow(
    permission: OnboardingPermissionUiModel,
    iconShape: Shape,
    shape: Shape,
    position: YumaSegmentPosition,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = permission.action
    val onClick =
        remember(action, onPermissionAction) {
            {
                if (action != null) {
                    onPermissionAction(action)
                }
            }
        }
    val colors = LocalYumaColors.current

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .yumaClickable(
                    enabled = action != null,
                    onClick = onClick,
                )
                .yumaGlassCard(
                    shape = shape,
                    backgroundColor = colors.glassBackground,
                    borderColor = colors.glassBorder,
                    strokeWidth = SettingsDimensions.GlassBorderThickness,
                    position = position,
                )
                .clip(shape)
                .padding(
                    horizontal = SettingsDimensions.SegmentedItemPaddingHorizontal,
                    vertical = SettingsDimensions.SegmentedItemPaddingVertical,
                ),
        horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.RowIconSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PermissionIcon(permission = permission, iconShape = iconShape)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(permission.titleResId),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = LocalArchiveTuneFontFamily.current,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedRowSpacing))
            Text(
                text = stringResource(permission.descriptionResId),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = LocalArchiveTuneFontFamily.current,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PermissionStatusAction(
            permission = permission,
            onPermissionAction = onPermissionAction,
        )
    }
}

@Composable
private fun PermissionIcon(
    permission: OnboardingPermissionUiModel,
    iconShape: Shape,
) {
    val containerColor =
        when (permission.status) {
            OnboardingPermissionStatus.ALLOWED -> MaterialTheme.colorScheme.primary
            OnboardingPermissionStatus.NEEDS_ACTION -> MaterialTheme.colorScheme.tertiary
            OnboardingPermissionStatus.ALLOWED_BY_INSTALL -> MaterialTheme.colorScheme.secondary
            OnboardingPermissionStatus.UNAVAILABLE -> LocalYumaColors.current.glassBackground
        }

    val iconTint =
        when (permission.status) {
            OnboardingPermissionStatus.ALLOWED -> MaterialTheme.colorScheme.onPrimary
            OnboardingPermissionStatus.NEEDS_ACTION -> MaterialTheme.colorScheme.onTertiary
            OnboardingPermissionStatus.ALLOWED_BY_INSTALL -> MaterialTheme.colorScheme.onSecondary
            OnboardingPermissionStatus.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        shape = iconShape,
        color = containerColor,
        modifier = Modifier.size(SettingsDimensions.SegmentedIconBoxSize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(permission.iconResId),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun PermissionStatusAction(
    permission: OnboardingPermissionUiModel,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
) {
    val action = permission.action
    val colors = LocalYumaColors.current

    if (action != null) {
        Surface(
            modifier =
                Modifier
                    .yumaClickable { onPermissionAction(action) }
                    .clip(RoundedCornerShape(12.dp)),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = stringResource(R.string.allow),
                fontFamily = LocalArchiveTuneFontFamily.current,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    } else {
        val badgeShape = RoundedCornerShape(12.dp)
        Box(
            modifier =
                Modifier
                    .yumaGlassCard(
                        shape = badgeShape,
                        backgroundColor = colors.glassBackground,
                        borderColor = colors.glassBorder,
                        strokeWidth = SettingsDimensions.GlassBorderThickness,
                    )
                    .clip(badgeShape),
        ) {
            Text(
                text = stringResource(permission.status.labelResId()),
                fontFamily = LocalArchiveTuneFontFamily.current,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun CommunityPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
) {
    val page = uiState.pages[pageIndex]
    val expressiveShapes = rememberExpressiveShapes()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = OnboardingPagePadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = page.id.name, contentType = "header") {
            ExpressivePageHeader(
                iconResId = page.iconResId,
                titleResId = page.titleResId,
                subtitleResId = page.subtitleResId,
            )
        }
        item(key = "community_group", contentType = "community_group") {
            val count = uiState.communityActions.size
            Column(
                modifier =
                    Modifier
                        .widthIn(max = OnboardingContentMaxWidth)
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
            ) {
                uiState.communityActions.forEachIndexed { index, item ->
                    val shape = remember(index, count) { segmentedOnboardingItemShape(index, count) }
                    val position = remember(index, count) { yumaSegmentPosition(index, count) }
                    GlassCommunityRow(
                        action = item,
                        iconShape = expressiveShapes[index % expressiveShapes.size],
                        shape = shape,
                        position = position,
                        onCommunityAction = onCommunityAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassCommunityRow(
    action: OnboardingCommunityActionUiModel,
    iconShape: Shape,
    shape: Shape,
    position: YumaSegmentPosition,
    onCommunityAction: (OnboardingCommunityActionUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYumaColors.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .yumaClickable { onCommunityAction(action) }
                .yumaGlassCard(
                    shape = shape,
                    backgroundColor = colors.glassBackground,
                    borderColor = colors.glassBorder,
                    strokeWidth = SettingsDimensions.GlassBorderThickness,
                    position = position,
                )
                .clip(shape)
                .padding(
                    horizontal = SettingsDimensions.SegmentedItemPaddingHorizontal,
                    vertical = SettingsDimensions.SegmentedItemPaddingVertical,
                ),
        horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.RowIconSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(SettingsDimensions.SegmentedIconBoxSize),
            shape = iconShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(action.iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(action.titleResId),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = LocalArchiveTuneFontFamily.current,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedRowSpacing))
            Text(
                text = stringResource(action.descriptionResId),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = LocalArchiveTuneFontFamily.current,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExpressivePageHeader(
    iconResId: Int,
    titleResId: Int,
    subtitleResId: Int,
) {
    val headerShape = MaterialShapes.Cookie9Sided.toShape()

    Column(
        modifier =
            Modifier
                .widthIn(max = OnboardingContentMaxWidth)
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = headerShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Text(
            text = stringResource(titleResId),
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = LocalArchiveTuneFontFamily.current,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(subtitleResId),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = LocalArchiveTuneFontFamily.current,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun OnboardingPermissionStatus.labelResId(): Int =
    when (this) {
        OnboardingPermissionStatus.ALLOWED -> R.string.permission_status_allowed
        OnboardingPermissionStatus.NEEDS_ACTION -> R.string.allow
        OnboardingPermissionStatus.ALLOWED_BY_INSTALL -> R.string.onboarding_permission_allowed_by_install
        OnboardingPermissionStatus.UNAVAILABLE -> R.string.onboarding_permission_unavailable
    }

@Composable
private fun CustomizationPage(
    uiState: OnboardingUiState,
    pageIndex: Int,
) {
    val page = uiState.pages[pageIndex]
    val context = LocalContext.current

    val accountSettingsViewModel: moe.rukamori.archivetune.ui.screens.settings.account.AccountSettingsViewModel = hiltViewModel()
    val accountUiState by accountSettingsViewModel.uiState.collectAsStateWithLifecycle()

    val spotifyAccountViewModel: SpotifyAccountViewModel = hiltViewModel()
    val spotifyState by spotifyAccountViewModel.uiState.collectAsStateWithLifecycle()

    val homeViewModel: moe.rukamori.archivetune.viewmodels.HomeViewModel = hiltViewModel()
    val accountNameFromViewModel by homeViewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsStateWithLifecycle()
    val accountChannelsState by homeViewModel.accountChannelsState.collectAsStateWithLifecycle()

    val (accountNamePref, onAccountNameChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.AccountNameKey, "")
    val (accountEmail, onAccountEmailChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.AccountEmailKey, "")
    val (accountChannelHandle, onAccountChannelHandleChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.InnerTubeCookieKey, "")
    val (visitorData, onVisitorDataChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.VisitorDataKey, "")
    val (dataSyncId, onDataSyncIdChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.DataSyncIdKey, "")
    val (savedAccountsJson, onSavedAccountsJsonChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.SavedAccountsKey, "")
    
    val savedAccounts = remember(savedAccountsJson) {
        moe.rukamori.archivetune.utils.SavedAccountCollection(moe.rukamori.archivetune.utils.decodeSavedAccounts(savedAccountsJson))
    }
    
    val isLoggedIn = remember(innerTubeCookie) {
        moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie(innerTubeCookie)
    }

    val displayName = when {
        accountNameFromViewModel.isNotBlank() -> accountNameFromViewModel
        accountNamePref.isNotBlank() -> accountNamePref
        isLoggedIn -> stringResource(R.string.account)
        else -> stringResource(R.string.login)
    }

    val (darkMode, onDarkModeChange) = moe.rukamori.archivetune.utils.rememberEnumPreference<DarkMode>(moe.rukamori.archivetune.constants.DarkModeKey, defaultValue = DarkMode.AUTO)
    val (homeBackgroundStyle, onHomeBackgroundStyleChange) = moe.rukamori.archivetune.utils.rememberEnumPreference<HomeBackgroundStyle>(moe.rukamori.archivetune.constants.HomeBackgroundStyleKey, defaultValue = HomeBackgroundStyle.TONAL)
    
    val (useSpotifyHome, onUseSpotifyHomeChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.UseSpotifyHomeKey, false)

    val (playbackSource, onPlaybackSourceChange) = moe.rukamori.archivetune.utils.rememberEnumPreference<PlaybackSource>(
        moe.rukamori.archivetune.constants.PlaybackSourceKey,
        defaultValue = PlaybackSource.YT_MUSIC,
    )
    val (enableLossless, onEnableLosslessChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.EnableLosslessKey, defaultValue = false)

    val (qobuzAppId, onQobuzAppIdChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.QobuzAppIdKey, "")
    val (qobuzAppSecret, onQobuzAppSecretChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.QobuzAppSecretKey, "")
    val (qobuzUserAuthToken, onQobuzUserAuthTokenChange) = moe.rukamori.archivetune.utils.rememberPreference(moe.rukamori.archivetune.constants.QobuzUserAuthTokenKey, "")

    var showTokenEditor by remember { mutableStateOf(false) }
    var showSpotifyLogin by remember { mutableStateOf(false) }
    var showWebViewLogin by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            showWebViewLogin = false
        }
    }

    val onLegacyPoTokenChange: (String) -> Unit = { value ->
        PreferenceStore.launchEdit(context.dataStore) {
            putLegacyPoToken(value)
        }
    }

    val saveCurrentAccount: () -> Unit = {
        val existing = moe.rukamori.archivetune.utils.decodeSavedAccounts(savedAccountsJson)
        if (isLoggedIn && existing.none { it.innerTubeCookie == innerTubeCookie }) {
            val newAccount =
                SavedAccount(
                    id = UUID.randomUUID().toString(),
                    name = if (accountNameFromViewModel.isNotBlank()) accountNameFromViewModel else accountNamePref,
                    email = accountEmail,
                    channelHandle = accountChannelHandle,
                    innerTubeCookie = innerTubeCookie,
                    visitorData = visitorData,
                    dataSyncId = dataSyncId,
                    ytmSync = true,
                    selectedYtmPlaylists = "",
                )
            onSavedAccountsJsonChange(encodeSavedAccounts(existing + newAccount))
        }
    }

    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(playbackSource) {
        if (playbackSource == PlaybackSource.FLAC) {
            lazyListState.animateScrollToItem(2)
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = page.id.name, contentType = "header") {
            Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                ExpressivePageHeader(
                    iconResId = page.iconResId,
                    titleResId = page.titleResId,
                    subtitleResId = page.subtitleResId,
                )
            }
        }

        item(contentType = "customizationGroup") {
            moe.rukamori.archivetune.ui.component.PreferenceGroup(
                modifier = Modifier.widthIn(max = OnboardingContentMaxWidth).fillMaxWidth()
            ) {
                item {
                    moe.rukamori.archivetune.ui.screens.settings.ProfileIdentityCard(
                        isLoggedIn = isLoggedIn,
                        accountName = displayName,
                        accountEmail = accountEmail,
                        accountHandle = accountChannelHandle,
                        accountImageUrl = accountImageUrl,
                        savedAccounts = savedAccounts,
                        activeInnerTubeCookie = innerTubeCookie,
                        activeDataSyncId = dataSyncId,
                        accountChannelsState = accountChannelsState,
                        extractedColorHex = accountUiState.extractedColorHex,
                        onAvatarPixelsReady = accountSettingsViewModel::processAvatarPixels,
                        onPrimaryAction = {
                            if (!isLoggedIn) {
                                showWebViewLogin = true
                            }
                        },
                        onSecondaryAction = {
                            if (isLoggedIn) {
                                onInnerTubeCookieChange("")
                                forgetAccount(context, clearWebAuthSession = true)
                            } else {
                                showTokenEditor = true
                            }
                        },
                        onSaveAccount = saveCurrentAccount,
                        onSwitchAccount = { account ->
                            homeViewModel.switchToAccount(
                                account = account,
                                forceSyncOnSwitch = false,
                            )
                        },
                        onSwitchAccountChannel = { channel ->
                            homeViewModel.switchToAccountChannel(
                                channel = channel,
                                forceSyncOnSwitch = false,
                            )
                        },
                        onRemoveAccount = { account ->
                            val existing = moe.rukamori.archivetune.utils.decodeSavedAccounts(savedAccountsJson)
                            onSavedAccountsJsonChange(encodeSavedAccounts(existing.filter { it.id != account.id }))
                        },
                        onAddAnotherAccount = {
                            showWebViewLogin = true
                        },
                    )
                }

                item {
                    moe.rukamori.archivetune.ui.screens.settings.ExpressiveSegmentedRow(
                        icon = painterResource(if (useSpotifyHome) R.drawable.spotify_icon else R.drawable.yt_music_icon),
                        title = stringResource(R.string.home_screen_provider),
                        subtitle = stringResource(R.string.home_screen_provider_desc),
                        selectedValue = useSpotifyHome,
                        onValueSelected = { isSpotify ->
                            if (isSpotify && !spotifyState.isAuthenticated) {
                                showSpotifyLogin = true
                            } else {
                                onUseSpotifyHomeChange(isSpotify)
                            }
                        },
                    )
                }

                item {
                    moe.rukamori.archivetune.ui.screens.settings.DarkModeSelector(
                        darkMode = darkMode,
                        onDarkModeChange = onDarkModeChange
                    )
                }

                item {
                    moe.rukamori.archivetune.ui.screens.settings.HomeBackgroundSelector(
                        homeBackgroundStyle = homeBackgroundStyle,
                        onHomeBackgroundStyleChange = onHomeBackgroundStyleChange
                    )
                }

                item {
                    moe.rukamori.archivetune.ui.screens.settings.PlaybackSourceSelector(
                        playbackSource = playbackSource,
                        onPlaybackSourceChange = onPlaybackSourceChange,
                        onEnableLosslessChange = onEnableLosslessChange
                    )
                }
            }
        }

        if (playbackSource == PlaybackSource.FLAC) {
            item(contentType = "flacTokens") {
                moe.rukamori.archivetune.ui.component.PreferenceGroup(
                    modifier = Modifier.widthIn(max = OnboardingContentMaxWidth).fillMaxWidth()
                ) {
                    FlacTokenInputs(
                        qobuzAppId = qobuzAppId,
                        onQobuzAppIdChange = onQobuzAppIdChange,
                        qobuzAppSecret = qobuzAppSecret,
                        onQobuzAppSecretChange = onQobuzAppSecretChange,
                        qobuzUserAuthToken = qobuzUserAuthToken,
                        onQobuzUserAuthTokenChange = onQobuzUserAuthTokenChange,
                    )
                }
            }
        }
    }

    if (showTokenEditor) {
        TokenEditorDialog(
            innerTubeCookie = innerTubeCookie,
            visitorData = visitorData,
            dataSyncId = dataSyncId,
            accountNamePref = accountNamePref,
            accountEmail = accountEmail,
            accountChannelHandle = accountChannelHandle,
            onInnerTubeCookieChange = onInnerTubeCookieChange,
            onPoTokenChange = onLegacyPoTokenChange,
            onVisitorDataChange = onVisitorDataChange,
            onDataSyncIdChange = onDataSyncIdChange,
            onAccountNameChange = onAccountNameChange,
            onAccountEmailChange = onAccountEmailChange,
            onAccountChannelHandleChange = onAccountChannelHandleChange,
            onDismiss = { showTokenEditor = false },
        )
    }

    if (showSpotifyLogin) {
        SpotifyLoginSheet(
            onDismiss = { showSpotifyLogin = false },
            onCookiesCaptured = { spDc, spKey ->
                spotifyAccountViewModel.connectWithCookies(spDc = spDc, spKey = spKey)
                onUseSpotifyHomeChange(true)
                showSpotifyLogin = false
            },
        )
    }

    if (showWebViewLogin) {
        Dialog(
            onDismissRequest = { showWebViewLogin = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            val loginNavController = remember(context) {
                object : NavHostController(context) {
                    override fun navigateUp(): Boolean {
                        showWebViewLogin = false
                        return true
                    }
                    override fun popBackStack(): Boolean {
                        showWebViewLogin = false
                        return true
                    }
                }.apply {
                    navigatorProvider.addNavigator(androidx.navigation.compose.ComposeNavigator())
                    navigatorProvider.addNavigator(androidx.navigation.compose.DialogNavigator())
                }
            }
            LoginScreen(
                navController = loginNavController,
            )
        }
    }
}
