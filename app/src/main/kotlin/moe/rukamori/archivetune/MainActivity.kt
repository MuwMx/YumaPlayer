/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import moe.rukamori.archivetune.ui.haptics.LocalYumaHaptics
import moe.rukamori.archivetune.ui.haptics.YumaHapticsImpl
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.AppFontPreference
import moe.rukamori.archivetune.constants.AppLanguageKey
import moe.rukamori.archivetune.constants.CustomFontUriKey
import moe.rukamori.archivetune.constants.CustomThemeColorKey
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.constants.DefaultOpenTabKey
import moe.rukamori.archivetune.constants.DisableAnimationsKey
import moe.rukamori.archivetune.constants.DisableScreenshotKey
import moe.rukamori.archivetune.constants.DynamicThemeKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.FloatingToolbarHeight
import moe.rukamori.archivetune.constants.FloatingToolbarHorizontalPadding
import moe.rukamori.archivetune.constants.FontPreferenceKey
import moe.rukamori.archivetune.constants.HomeBackgroundBrightnessKey
import moe.rukamori.archivetune.constants.HomeBackgroundParallaxEnabledKey
import moe.rukamori.archivetune.constants.HomeBackgroundParallaxStrengthKey
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.constants.HomeBackgroundStyleKey
import moe.rukamori.archivetune.home.effects.HomeBackgroundSettings
import moe.rukamori.archivetune.home.effects.LocalHomeBackgroundStyle
import moe.rukamori.archivetune.home.effects.ScreenBackground
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.constants.OnboardingCompletedKey
import moe.rukamori.archivetune.constants.PauseSearchHistoryKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.constants.SYSTEM_DEFAULT
import moe.rukamori.archivetune.constants.SearchSource
import moe.rukamori.archivetune.constants.SearchSourceKey
import moe.rukamori.archivetune.constants.SplashOverlayEnabledKey
import moe.rukamori.archivetune.constants.StopMusicOnTaskClearKey
import moe.rukamori.archivetune.constants.UpdateChannelKey
import moe.rukamori.archivetune.constants.UseSystemFontKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.musicrecognition.ACTION_MUSIC_RECOGNITION
import moe.rukamori.archivetune.onboarding.OnboardingViewModel
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.PlayerConnectionHolder
import dev.chrisbanes.haze.HazeState
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.splash.SplashConfig
import moe.rukamori.archivetune.ui.component.splash.SplashOverlay
import moe.rukamori.archivetune.ui.component.splash.SplashSlots
import moe.rukamori.archivetune.ui.component.splash.SplashVectorLoader
import moe.rukamori.archivetune.ui.component.TopSearch
import moe.rukamori.archivetune.ui.component.TvNavigationRail
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerTheme
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.screens.onboarding.OnboardingRoute
import moe.rukamori.archivetune.ui.screens.search.LocalSearchScreen
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultArgument
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultRoutePrefix
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchScreen
import moe.rukamori.archivetune.ui.screens.search.decodeOnlineSearchQuery
import moe.rukamori.archivetune.ui.screens.search.onlineSearchResultRoute
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.ui.screens.settings.NavigationTab
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme
import moe.rukamori.archivetune.ui.theme.ColorSaver
import moe.rukamori.archivetune.ui.theme.DefaultThemeColor
import moe.rukamori.archivetune.ui.theme.YdsInsets
import moe.rukamori.archivetune.ui.theme.extractSeedColor
import moe.rukamori.archivetune.ui.utils.LocalGlobalVisibility
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.resetHeightOffset
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.isLowRamDevice
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.utils.setAppLocale
import moe.rukamori.archivetune.viewmodels.HomeViewModel
import moe.rukamori.archivetune.viewmodels.NetworkBannerViewModel
import moe.rukamori.archivetune.viewmodels.NewsViewModel
import moe.rukamori.archivetune.viewmodels.OnlineSearchSort
import moe.rukamori.archivetune.viewmodels.OnlineSearchViewModel
import moe.rukamori.archivetune.viewmodels.UpdateViewModel
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import moe.rukamori.archivetune.ui.state.UpdateState

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var playerConnectionHolder: PlayerConnectionHolder

    private val musicServiceBinding = MusicServiceBinding(this)
    private val intentRouter = MainIntentRouter(this, musicServiceBinding)

    init {
        musicServiceBinding.intentRouter = intentRouter
    }

    private lateinit var navController: NavHostController

    private var pendingIntent: Intent?
        get() = intentRouter.pendingIntent
        set(value) {
            intentRouter.pendingIntent = value
        }

    private var pendingBackupRestoreUri: Uri?
        get() = intentRouter.pendingBackupRestoreUri
        set(value) {
            intentRouter.pendingBackupRestoreUri = value
        }

    private var aodModeLaunchRequestCount: Int
        get() = intentRouter.aodModeLaunchRequestCount
        set(value) {
            intentRouter.aodModeLaunchRequestCount = value
        }

    private val playerConnection: PlayerConnection?
        get() = musicServiceBinding.playerConnection

    private val systemBarController = SystemBarController(this)
    private var isOnboardingCompleted by mutableStateOf<Boolean?>(null)
    private var isReady by mutableStateOf(false)
    internal val playerViewModel: PlayerViewModel by viewModels()
    private val onboardingViewModel: OnboardingViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        musicServiceBinding.onStart()
    }

    override fun onStop() {
        musicServiceBinding.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        musicServiceBinding.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && systemBarController.immersiveStatusBarsHidden) {
            systemBarController.setStatusBarsHidden(true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::navController.isInitialized) {
            handleIntent(intent, navController)
        } else {
            pendingIntent = intent
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            isOnboardingCompleted == null || !isReady
        }
        lifecycleScope.launch {
            dataStore.data
                .map { it[OnboardingCompletedKey] ?: false }
                .distinctUntilChanged()
                .collectLatest { completed ->
                    isOnboardingCompleted = completed
                }
        }
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val initialLocale =
                PreferenceStore
                    .get(AppLanguageKey)
                    ?.takeUnless { it == SYSTEM_DEFAULT }
                    ?.let { Locale.forLanguageTag(it) }
                    ?: Locale.getDefault()
            setAppLocale(this, initialLocale)

            lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    dataStore.data.first()[AppLanguageKey]
                }.onSuccess { lang ->
                    val targetLocale =
                        lang
                            ?.takeUnless { it == SYSTEM_DEFAULT }
                            ?.let { Locale.forLanguageTag(it) }
                            ?: Locale.getDefault()
                    if (targetLocale != initialLocale) {
                        withContext(Dispatchers.Main) {
                            setAppLocale(this@MainActivity, targetLocale)
                            recreate()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    withContext(Dispatchers.Main) {
                        if (it) {
                            window.setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE,
                            )
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                }
        }

        lifecycleScope.launch(Dispatchers.Default) {
            val path = SplashVectorLoader.loadPath(this@MainActivity, R.drawable.about_splash)
            withContext(Dispatchers.Main) {
                SplashSlots.customVectorPath = path
                SplashSlots.vectorVersion++
            }
        }

        setContent {
            val navController = rememberNavController()
            DisposableEffect(navController) {
                this@MainActivity.navController = navController
                onDispose {}
            }

            var playerExpansionFractionProvider by remember { mutableStateOf<() -> Float>({ 0f }) }
            val isHomeScreenVisible by remember {
                derivedStateOf { playerExpansionFractionProvider() < 0.99f }
            }

            val updateChannel by rememberEnumPreference(UpdateChannelKey, defaultValue = defaultUpdateChannel)

            val context = LocalContext.current
            val uriHandler = LocalUriHandler.current

            // Use remembered instances so the same state object is used everywhere
            // (previously retrieving the composition local directly created different
            // instances in different composition scopes which caused the update
            // bottom sheet to not appear and overlay interactions to be blocked).
            val bottomSheetPageState =
                remember {
                    moe.rukamori.archivetune.ui.component
                        .BottomSheetPageState()
                }
            val menuState =
                remember {
                    moe.rukamori.archivetune.ui.component
                        .MenuState()
                }

            val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
            val customThemeColorValue by rememberPreference(CustomThemeColorKey, defaultValue = "default")
            val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
            val defaultDisableAnimations = remember(this@MainActivity) { applicationContext.isLowRamDevice() }
            val disableAnimations by rememberPreference(
                DisableAnimationsKey,
                defaultValue = defaultDisableAnimations,
            )
            val splashEnabled by rememberPreference(SplashOverlayEnabledKey, defaultValue = true)
            LaunchedEffect(Unit) {
                dataStore.data.first()
                snapshotFlow { splashEnabled }.first()
                isReady = true
            }
            var coldSplash by remember(isReady) { mutableStateOf(splashEnabled) }
            var contentVisible by remember(isReady) { mutableStateOf(!coldSplash || disableAnimations) }
            var splashDone by remember(isReady) { mutableStateOf(!splashEnabled || disableAnimations) }
            LaunchedEffect(contentVisible) {
                if (contentVisible && (!splashEnabled || disableAnimations)) {
                    splashDone = true
                }
            }
            val contentAlpha by animateFloatAsState(
                targetValue = if (contentVisible) 1f else 0f,
                animationSpec = tween(durationMillis = if (disableAnimations) 0 else SplashConfig.Reveal.DURATION_MS, easing = EaseOut),
                label = "splashContentAlpha",
            )
            val homeBackgroundStyle by rememberEnumPreference(HomeBackgroundStyleKey, HomeBackgroundStyle.TONAL)
            val homeBackgroundParallaxEnabled by rememberPreference(HomeBackgroundParallaxEnabledKey, defaultValue = true)
            val homeBackgroundParallaxStrength by rememberPreference(HomeBackgroundParallaxStrengthKey, defaultValue = 0.6f)
            val homeBackgroundBrightness by rememberPreference(HomeBackgroundBrightnessKey, defaultValue = 1f)
            val fontPreference by rememberEnumPreference(FontPreferenceKey, defaultValue = AppFontPreference.DEFAULT)
            val customFontUri by rememberPreference(CustomFontUriKey, defaultValue = "")
            val legacyUseSystemFont by rememberPreference(UseSystemFontKey, defaultValue = false)
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val useDarkTheme =
                remember(darkTheme, isSystemInDarkTheme) {
                    if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
                }
            val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
            val pureBlack = pureBlackEnabled && useDarkTheme

                val customThemeSeedPalette =
                remember(customThemeColorValue) {
                    if (customThemeColorValue.startsWith("seedPalette:")) {
                        moe.rukamori.archivetune.ui.theme.ThemeSeedPaletteCodec
                            .decodeFromPreference(customThemeColorValue)
                    } else {
                        null
                    }
                }

            val customThemeColor =
                remember(customThemeColorValue, customThemeSeedPalette) {
                    if (customThemeColorValue.startsWith("#")) {
                        try {
                            val colorString = customThemeColorValue.removePrefix("#")
                            Color(android.graphics.Color.parseColor("#$colorString"))
                        } catch (e: Exception) {
                            DefaultThemeColor
                        }
                    } else {
                        customThemeSeedPalette?.primary ?: DefaultThemeColor
                    }
                }

            var themeColor by rememberSaveable(stateSaver = ColorSaver) {
                mutableStateOf(DefaultThemeColor)
            }

            LaunchedEffect(legacyUseSystemFont) {
                if (!legacyUseSystemFont) return@LaunchedEffect
                val preferences = dataStore.data.first()
                if (preferences[FontPreferenceKey] == null) {
                    dataStore.edit { it[FontPreferenceKey] = AppFontPreference.SYSTEM.name }
                }
            }

            LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme, customThemeColor) {
                val playerConnection = playerConnection
                if (!enableDynamicTheme || playerConnection == null) {
                    themeColor = if (!enableDynamicTheme) customThemeColor else DefaultThemeColor
                    return@LaunchedEffect
                }
                playerConnection.service.currentMediaMetadata.collectLatest { song ->
                    if (song != null) {
                        withContext(Dispatchers.Default) {
                            try {
                                val result =
                                    imageLoader.execute(
                                        ImageRequest
                                            .Builder(applicationContext)
                                            .data(song.thumbnailUrl)
                                            .allowHardware(false)
                                            .build(),
                                    )
                                // СТАЛО (бахаем твой движок):
                                val bitmap = result.image?.toBitmap()
                                if (bitmap != null) {
                                    // Вызываем твой продвинутый метод квантизации пикселей
                                    val accurateSeed = extractSeedColor(bitmap)
                                    withContext(Dispatchers.Main) {
                                        themeColor = accurateSeed
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        themeColor = DefaultThemeColor
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    themeColor = DefaultThemeColor
                                }
                            }
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            themeColor = DefaultThemeColor
                        } else {
                            themeColor = customThemeColor
                        }
                    }
                }
            }

            ArchiveTuneTheme(
                darkTheme = useDarkTheme,
                pureBlack = pureBlack,
                themeColor = themeColor,
                seedPalette = if (!enableDynamicTheme) customThemeSeedPalette else null,
                disableAnimations = disableAnimations,
                fontPreference = fontPreference,
                customFontUri = customFontUri,
            ) {
                if (isOnboardingCompleted == null || !isReady) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator()
                    }
                    return@ArchiveTuneTheme
                }

                if (isOnboardingCompleted == false || intent.getBooleanExtra("force_onboarding", false)) {
                    OnboardingRoute(viewModel = onboardingViewModel)
                    return@ArchiveTuneTheme
                }

                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface,
                            ),
                ) {
                    val focusManager = LocalFocusManager.current
                    val density = LocalDensity.current
                    val windowsInsets = WindowInsets.systemBars
                    val topInset = with(density) { windowsInsets.getTop(density).toDp() }
                    val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                    val bottomInsetDp = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

                    val isTvDevice = remember { applicationContext.isTvDevice() }
                    val useRail =
                        isTvDevice ||
                            currentWindowAdaptiveInfo()
                                .windowSizeClass
                                .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

                    val updateViewModel: UpdateViewModel = hiltViewModel()
                    LaunchedEffect(updateChannel) {
                        updateViewModel.forceCheck(updateChannel)
                    }
                    val updateState by updateViewModel.updateState.collectAsStateWithLifecycle()
                    // ========================================================
                    // ВОТ СЮДА, ВНУТРЬ КОРРЕКТНОГО КАНВАСА, МЫ И КЛАДЕМ ЛОВИТЕЛЬ:
                    // ========================================================
                    val coroutineScope = rememberCoroutineScope()
                    val homeViewModel: HomeViewModel = hiltViewModel()
                    val networkBannerViewModel: NetworkBannerViewModel = hiltViewModel()
                    val newsViewModel: NewsViewModel = hiltViewModel()
                    val networkBannerState by networkBannerViewModel.bannerState.collectAsStateWithLifecycle()
                    val hasUnreadNews by newsViewModel.hasUnreadNews.collectAsStateWithLifecycle()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val onlineSearchViewModel: OnlineSearchViewModel? =
                        if (currentRoute?.startsWith(OnlineSearchResultRoutePrefix) == true && navBackStackEntry != null) {
                            hiltViewModel(navBackStackEntry!!)
                        } else {
                            null
                        }
                    val onlineSearchSort =
                        if (onlineSearchViewModel != null) {
                            onlineSearchViewModel.sort.collectAsStateWithLifecycle().value
                        } else {
                            OnlineSearchSort.DEFAULT
                        }
                    val isYearInMusicScreen = currentRoute?.startsWith("year_in_music") == true

                    val navigationItems =
                        remember(isTvDevice) {
                            if (isTvDevice) Screens.TvMainScreens else Screens.MainScreens
                        }
                    val defaultOpenTab by rememberEnumPreference(DefaultOpenTabKey, NavigationTab.HOME)
                    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
                    val tabOpenedFromShortcut =
                        remember {
                            when (intent?.action) {
                                ACTION_LIBRARY -> NavigationTab.LIBRARY
                                ACTION_SEARCH -> NavigationTab.SEARCH
                                else -> null
                            }
                        }
                    val launchMusicRecognitionFromShortcut =
                        remember {
                            intent?.action == ACTION_MUSIC_RECOGNITION
                        }

                    val topLevelScreens =
                        remember(navigationItems) {
                            navigationItems.map(Screens::route) + "settings"
                        }

                    val (query, onQueryChange) =
                        rememberSaveable(stateSaver = TextFieldValue.Saver) {
                            mutableStateOf(TextFieldValue())
                        }

                    var active by rememberSaveable {
                        mutableStateOf(false)
                    }

                    val onActiveChange: (Boolean) -> Unit = { newActive ->
                        active = newActive
                        if (!newActive) {
                            focusManager.clearFocus()
                            if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                onQueryChange(TextFieldValue())
                            }
                        }
                    }

                    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)

                    val searchBarFocusRequester = remember { FocusRequester() }
                    val tvRailFocusRequester = remember { FocusRequester() }
                    val contentAreaFocusRequester = remember { FocusRequester() }

                    val openSearch: () -> Unit = {
                        onActiveChange(true)
                        searchBarFocusRequester.requestFocus()
                    }

                    // Измененный onSearch для основного TopSearch
                    val onSearch: (String) -> Unit = { query ->
                        if (query.isNotEmpty()) {
                            onActiveChange(false)
                            navController.navigate(onlineSearchResultRoute(query))
                            playerViewModel.addSearchHistory(query) // Обращение ко ViewModel без прямого внедрения БД в UI
                        }
                    }

                    var openSearchImmediately: Boolean by remember {
                        mutableStateOf(intent?.action == ACTION_SEARCH)
                    }

                    val shouldShowSearchBar =
                        remember(active, navBackStackEntry) {
                            active ||
                                navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == true
                        }

                    val shouldShowNavigationBar =
                        remember(navBackStackEntry, active) {
                            navBackStackEntry?.destination?.route == null ||
                                navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } &&
                                !active
                        }

                    val shouldShowHomeShuffleButton =
                        currentRoute == Screens.Home.route &&
                            (homeViewModel.allLocalItems.value.isNotEmpty() || homeViewModel.allYtItems.value.isNotEmpty())

                    val floatingToolbarBottomPadding = YdsInsets.floatingToolbarBottomPadding()
                    val navVisibleHeight = FloatingToolbarHeight

                    val playerBottomSheetState =
                        rememberPlayerBottomSheetState(
                            maxHeight = maxHeight,
                            bottomInset = bottomInset,
                            shouldShowNav = shouldShowNavigationBar,
                            useRail = useRail,
                        )

                    val isMiniPlayerVisible by remember(playerViewModel) {
                        playerViewModel.uiState
                            .map { it.trackUrl.isNotEmpty() }
                            .distinctUntilChanged()
                    }.collectAsStateWithLifecycle(initialValue = false)

                    val playerAwareWindowInsets =
                        rememberPlayerAwareWindowInsets(
                            useRail = useRail,
                            bottomInset = bottomInset,
                            shouldShowNavigationBar = shouldShowNavigationBar,
                            isMiniPlayerVisible = isMiniPlayerVisible,
                            floatingToolbarBottomPadding = floatingToolbarBottomPadding,
                            windowsInsets = windowsInsets,
                        )

                    val homeScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == false &&
                                    navBackStackEntry?.destination?.route != Screens.Library.route &&
                                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                            },
                        )
                    val searchScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == false &&
                                    navBackStackEntry?.destination?.route != Screens.Library.route &&
                                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                            },
                        )
                    val topAppBarScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == false &&
                                    navBackStackEntry?.destination?.route != Screens.Library.route &&
                                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                            },
                        )

                    val handlePrimaryNavigationClick: (Screens, Boolean) -> Unit = { screen, isSelected ->
                        if (isSelected) {
                            if (screen == Screens.Search) {
                                openSearch()
                                coroutineScope.launch { searchScrollBehavior.state.resetHeightOffset() }
                            } else {
                                navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                when (screen) {
                                    Screens.Home -> {
                                        coroutineScope.launch { homeScrollBehavior.state.resetHeightOffset() }
                                    }

                                    else -> {}
                                }
                            }
                        } else {
                            if (navController.currentDestination?.route != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    }

                    LaunchedEffect(currentRoute) {
                        when (currentRoute) {
                            Screens.Home.route -> {
                                homeScrollBehavior.state.resetHeightOffset()
                            }

                            Screens.Search.route -> {
                                searchScrollBehavior.state.resetHeightOffset()
                            }

                            else -> {}
                        }
                    }

                    var previousRoute by rememberSaveable { mutableStateOf<String?>(null) }

                    LaunchedEffect(navBackStackEntry) {
                        val currentRoute = navBackStackEntry?.destination?.route

                        val isEnteringSubScreen =
                            currentRoute != null &&
                                currentRoute !in topLevelScreens &&
                                currentRoute.startsWith(OnlineSearchResultRoutePrefix) != true
                        if (isEnteringSubScreen) {
                            topAppBarScrollBehavior.state.heightOffset = 0f
                            topAppBarScrollBehavior.state.contentOffset = 0f
                        }

                        previousRoute = currentRoute

                        if ((
                                currentRoute?.startsWith("artist/") == true ||
                                    currentRoute?.startsWith("album/") == true
                            ) &&
                            playerBottomSheetState.isExpanded
                        ) {
                            playerBottomSheetState.collapseSoft()
                        }

                        if (navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == true) {
                            val searchQuery =
                                decodeOnlineSearchQuery(
                                    navBackStackEntry
                                        ?.arguments
                                        ?.getString(OnlineSearchResultArgument)
                                        .orEmpty(),
                                )
                            onQueryChange(
                                TextFieldValue(
                                    searchQuery,
                                    TextRange(searchQuery.length),
                                ),
                            )
                        } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                            navBackStackEntry?.destination?.route in topLevelScreens
                        ) {
                            onQueryChange(TextFieldValue())
                        }
                    }
                    LaunchedEffect(active) {
                        if (active) {
                            when (currentRoute) {
                                Screens.Home.route -> {
                                    homeScrollBehavior.state.resetHeightOffset()
                                }

                                Screens.Search.route -> {
                                    searchScrollBehavior.state.resetHeightOffset()
                                }

                                else -> {}
                            }
                            searchBarFocusRequester.requestFocus()
                        }
                    }

                    LaunchedEffect(isTvDevice, useRail, active, currentRoute, shouldShowNavigationBar) {
                        if (
                            isTvDevice &&
                            useRail &&
                            shouldShowNavigationBar &&
                            !active &&
                            currentRoute in topLevelScreens
                        ) {
                            delay(100)
                            tvRailFocusRequester.requestFocus()
                        }
                    }

                    var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(navBackStackEntry) {
                        shouldShowTopBar =
                            !active && navBackStackEntry?.destination?.route in topLevelScreens &&
                            navBackStackEntry?.destination?.route != "settings"
                    }

                    LaunchedEffect(Unit) {
                        if (pendingIntent != null) {
                            handleIntent(pendingIntent, navController)
                            pendingIntent = null
                        } else {
                            handleIntent(intent, navController)
                        }
                    }

                    val currentTitleRes =
                        remember(navBackStackEntry) {
                            when (navBackStackEntry?.destination?.route) {
                                Screens.Home.route -> R.string.home
                                Screens.Search.route -> R.string.search
                                Screens.Library.route -> R.string.filter_library
                                else -> null
                            }
                        }
                    val haptic = LocalHapticFeedback.current
                    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
                    val hapticView = LocalView.current
                    val yumaHaptics = remember(enableHapticFeedback, hapticView) { YumaHapticsImpl(hapticView, enableHapticFeedback) }
                    val customHaptic =
                        remember(haptic, enableHapticFeedback) {
                            object : HapticFeedback {
                                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                                    if (enableHapticFeedback) {
                                        haptic.performHapticFeedback(hapticFeedbackType)
                                    }
                                }
                            }
                        }

                    CompositionLocalProvider(
                        LocalYumaHaptics provides yumaHaptics,
                        LocalHapticFeedback provides customHaptic,
                        LocalAnimationsDisabled provides disableAnimations,
                        LocalHomeBackgroundStyle provides
                            HomeBackgroundSettings(
                                style = homeBackgroundStyle,
                                parallaxEnabled = homeBackgroundParallaxEnabled,
                                parallaxSensitivity = homeBackgroundParallaxStrength,
                                brightness = homeBackgroundBrightness,
                            ),
                        LocalDatabase provides database,
                        LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
                        LocalPlayerConnection provides playerConnection,
                        LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                        LocalDownloadUtil provides downloadUtil,
                        LocalShimmerTheme provides ShimmerTheme,
                        LocalSyncUtils provides syncUtils,
                        moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState provides bottomSheetPageState,
                        moe.rukamori.archivetune.ui.component.LocalMenuState provides menuState,
                        LocalGlobalVisibility provides isHomeScreenVisible,
                    ) {
                        ScreenBackground(
                            isVisible = LocalGlobalVisibility.current,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (splashEnabled) {
                            SplashOverlay(
                                isDark = useDarkTheme,
                                onBurstStart = {
                                    contentVisible = true
                                    coldSplash = false
                                },
                                onDismiss = {
                                    splashDone = true
                                },
                            )
                        }
                        Row(
                            modifier =
                                Modifier
                                    .graphicsLayer {
                                        alpha = contentAlpha
                                        translationY = (1f - contentAlpha) * SplashConfig.Reveal.RISE_DP.dp.toPx()
                                        compositingStrategy = CompositingStrategy.ModulateAlpha
                                    }
                                    .pointerInput(contentVisible) {
                                        if (!contentVisible) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    awaitPointerEvent().changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                    },
                        ) {
                            AnimatedVisibility(
                                visible = useRail && shouldShowNavigationBar,
                                enter = fadeIn(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 150)),
                                exit = fadeOut(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 100)),
                            ) {
                                if (isTvDevice) {
                                    TvNavigationRail(
                                        items = navigationItems,
                                        selectedItemRoute =
                                            if (active) {
                                                Screens.Search.route
                                            } else {
                                                currentRoute
                                            },
                                        modifier = Modifier,
                                        firstItemFocusRequester = tvRailFocusRequester,
                                        contentFocusRequester =
                                            if (active ||
                                                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == true
                                            ) {
                                                searchBarFocusRequester
                                            } else {
                                                contentAreaFocusRequester
                                            },
                                        onItemClick = { screen ->
                                            val wasPlayerActive = playerBottomSheetState.isExpanded
                                            if (wasPlayerActive) {
                                                playerBottomSheetState.collapse(if (disableAnimations) snap() else spring())
                                            }
                                            val isSelected =
                                                navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true
                                            if (wasPlayerActive && isSelected) {
                                                return@TvNavigationRail
                                            }
                                            handlePrimaryNavigationClick(screen, isSelected)
                                        },
                                    )
                                } else {
                                    NavigationRail(
                                        containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        header = { Spacer(Modifier.height(24.dp)) },
                                    ) {
                                        navigationItems.fastForEach { screen ->
                                            val isSelected =
                                                navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true

                                            NavigationRailItem(
                                                selected = isSelected,
                                                icon = {
                                                    Icon(
                                                        painter =
                                                            painterResource(
                                                                id = if (isSelected) screen.iconIdActive else screen.iconIdInactive,
                                                            ),
                                                        contentDescription = null,
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = stringResource(screen.titleId),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                },
                                                onClick = {
                                                    val wasPlayerActive = playerBottomSheetState.isExpanded

                                                    if (wasPlayerActive) {
                                                        playerBottomSheetState.collapse(if (disableAnimations) snap() else spring())
                                                    }

                                                    if (wasPlayerActive && isSelected) return@NavigationRailItem
                                                    handlePrimaryNavigationClick(screen, isSelected)
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            val hazeState = remember { HazeState() }

                            Scaffold(
                                topBar = {
                                    if (shouldShowTopBar) {
                                        val shouldUseFloatingTopBar =
                                            remember(navBackStackEntry) {
                                                navBackStackEntry?.destination?.route == Screens.Home.route ||
                                                    navBackStackEntry?.destination?.route == Screens.Search.route ||
                                                    navBackStackEntry?.destination?.route == Screens.Library.route
                                            }
                                        val shouldShowBlurBackground =
                                            remember(navBackStackEntry) {
                                                shouldUseFloatingTopBar
                                            }

                                        val surfaceColor = MaterialTheme.colorScheme.surface
                                        val currentScrollBehavior =
                                            when (navBackStackEntry?.destination?.route) {
                                                Screens.Home.route -> homeScrollBehavior

                                                Screens.Search.route -> searchScrollBehavior

                                                // Library hits else but is offset 0 (self-contained);
                                                // sub-screens use the shared shell behavior.
                                                else -> topAppBarScrollBehavior
                                            }
                                        val isLibraryRoute = navBackStackEntry?.destination?.route == Screens.Library.route

                                        // Rigid slide (Step 3): the header translates as a block via
                                        // Modifier.offset while the M3 TopAppBar itself gets
                                        // scrollBehavior = null (below), so it never collapses or
                                        // double-renders. The floating behavior only listens to
                                        // scroll (non-consuming) and updates heightOffset.
                                        //
                                        // heightOffsetLimit must be set from the measured header
                                        // height. The header Box is a single shared shell composable
                                        // whose size is identical for Home/Search, so onSizeChanged
                                        // fires only once (size doesn't change on tab switch).
                                        // Therefore: measure once here, then apply the limit to the
                                        // CURRENT route's state via LaunchedEffect so every route gets
                                        // its limit on entry (not just the first-measured one).
                                        var headerHeightPx by remember { mutableStateOf(0) }
                                        LaunchedEffect(currentScrollBehavior, headerHeightPx) {
                                            if (headerHeightPx > 0 && !isLibraryRoute) {
                                                val limit = -headerHeightPx.toFloat()
                                                val state = currentScrollBehavior.state
                                                if (state.heightOffsetLimit != limit) {
                                                    state.heightOffsetLimit = limit
                                                    state.heightOffset = state.heightOffset.coerceIn(limit, 0f)
                                                }
                                            }
                                        }

                                        Box(
                                            modifier =
                                                Modifier
                                                    .onSizeChanged { size ->
                                                        if (size.height > 0) headerHeightPx = size.height
                                                    }.offset {
                                                        IntOffset(
                                                            x = 0,
                                                            y =
                                                                if (isLibraryRoute) {
                                                                    0
                                                                } else {
                                                                    currentScrollBehavior.state.heightOffset
                                                                        .roundToInt()
                                                                },
                                                        )
                                                    },
                                        ) {
                                            // Gradient shadow background. It lives inside the
                                            // translating Box (which moves by the full heightOffset,
                                            // so the TopAppBar fully hides), but a counter-offset
                                            // clamps the gradient's net translation to [-appBarHeight, 0]
                                            // so it parks with its top band over the status bar instead
                                            // of sliding fully off — a legibility scrim once the header
                                            // is hidden.
                                            if (shouldShowBlurBackground) {
                                                val appBarHeightPx = with(LocalDensity.current) { AppBarHeight.toPx() }
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .offset {
                                                                if (isLibraryRoute) {
                                                                    // Library owns its scroll; the shell gradient
                                                                    // stays static (mirrors the header Box above and
                                                                    // matches upstream, which ships a static Library
                                                                    // gradient). Keeping it rendered avoids the
                                                                    // Library→Home predictive-back scrim pop.
                                                                    IntOffset(x = 0, y = 0)
                                                                } else {
                                                                    val raw = currentScrollBehavior.state.heightOffset
                                                                    val clamped = raw.coerceAtLeast(-appBarHeightPx)
                                                                    IntOffset(x = 0, y = (clamped - raw).roundToInt())
                                                                }
                                                            }.fillMaxWidth()
                                                            .height(
                                                                AppBarHeight +
                                                                    with(LocalDensity.current) {
                                                                        WindowInsets.systemBars.getTop(LocalDensity.current).toDp()
                                                                    },
                                                            ).background(
                                                                Brush.verticalGradient(
                                                                    colors =
                                                                        listOf(
                                                                            surfaceColor.copy(alpha = 0.95f),
                                                                            surfaceColor.copy(alpha = 0.85f),
                                                                            surfaceColor.copy(alpha = 0.6f),
                                                                            Color.Transparent,
                                                                        ),
                                                                ),
                                                            ),
                                                )
                                            }

                                            TopAppBar(
                                                windowInsets =
                                                    WindowInsets.safeDrawing.only(
                                                        (
                                                            if (useRail) {
                                                                WindowInsetsSides.Right
                                                            } else {
                                                                WindowInsetsSides.Horizontal
                                                            }
                                                        ) + WindowInsetsSides.Top,
                                                    ),
                                                title = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        // app icon
                                                        Icon(
                                                            painter = painterResource(R.drawable.about_appbar),
                                                            contentDescription = null,
                                                            modifier =
                                                                Modifier
                                                                    .size(35.dp)
                                                                    .padding(end = 3.dp),
                                                        )
                                                        Text(
                                                            text = stringResource(R.string.app_name),
                                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                },
                                                actions = {
                                                    IconButton(
                                                        onClick = { navController.navigate("history") },
                                                        onLongClick = {},
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.history),
                                                            contentDescription = stringResource(R.string.history),
                                                        )
                                                    }
                                                    TooltipBox(
                                                        positionProvider =
                                                            if (hasUnreadNews) {
                                                                TooltipDefaults.rememberRichTooltipPositionProvider()
                                                            } else {
                                                                TooltipDefaults.rememberPlainTooltipPositionProvider()
                                                            },
                                                        tooltip = {
                                                            if (hasUnreadNews) {
                                                                RichTooltip(
                                                                    title = { Text(stringResource(R.string.news_tooltip_title)) },
                                                                ) {
                                                                    Text(stringResource(R.string.news_tooltip_body))
                                                                }
                                                            } else {
                                                                PlainTooltip {
                                                                    Text(stringResource(R.string.news))
                                                                }
                                                            }
                                                        },
                                                        state = rememberTooltipState(),
                                                    ) {
                                                        IconButton(
                                                            onClick = { navController.navigate("news") },
                                                            onLongClick = {},
                                                        ) {
                                                            BadgedBox(badge = {
                                                                if (hasUnreadNews) {
                                                                    Badge()
                                                                }
                                                            }) {
                                                                Icon(
                                                                    painter = painterResource(R.drawable.newspaper),
                                                                    contentDescription = stringResource(R.string.news),
                                                                )
                                                            }
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = { navController.navigate("new_release") },
                                                        onLongClick = {},
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.new_release),
                                                            contentDescription = stringResource(R.string.new_release_albums),
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { navController.navigate("settings") },
                                                        onLongClick = {},
                                                    ) {
                                                        BadgedBox(badge = {
                                                            if (splashDone && (updateState is UpdateState.SoftUpdate || updateState is UpdateState.CriticalUpdate)) {
                                                                Badge()
                                                            }
                                                        }) {
                                                            Icon(
                                                                painter = painterResource(R.drawable.ic_settings),
                                                                contentDescription = stringResource(R.string.settings),
                                                                modifier = Modifier.size(24.dp),
                                                            )
                                                        }
                                                    }
                                                },
                                                scrollBehavior =
                                                    if (navBackStackEntry?.destination?.route == Screens.Library.route ||
                                                        shouldUseFloatingTopBar
                                                    ) {
                                                        // Library is fixed, and floating routes
                                                        // (Home/Search) now slide rigidly via the
                                                        // outer Box.offset — passing a behavior here
                                                        // would make M3 collapse/co-render and
                                                        // double-move the header. Only non-floating
                                                        // sub-screens use M3's collapse behavior.
                                                        null
                                                    } else {
                                                        topAppBarScrollBehavior
                                                    },
                                                colors =
                                                    TopAppBarDefaults.topAppBarColors(
                                                        containerColor =
                                                            if (shouldUseFloatingTopBar) {
                                                                Color.Transparent
                                                            } else if (pureBlack) {
                                                                Color.Black
                                                            } else {
                                                                MaterialTheme.colorScheme.surface
                                                            },
                                                        scrolledContainerColor =
                                                            if (shouldUseFloatingTopBar) {
                                                                Color.Transparent
                                                            } else if (pureBlack) {
                                                                Color.Black
                                                            } else {
                                                                MaterialTheme.colorScheme.surface
                                                            },
                                                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                                                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    ),
                                            )
                                        }
                                    }
                                    AnimatedVisibility(
                                        visible =
                                            active ||
                                                navBackStackEntry?.destination?.route?.startsWith(OnlineSearchResultRoutePrefix) == true,
                                        enter = fadeIn(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 300)),
                                        exit = fadeOut(animationSpec = tween(durationMillis = if (disableAnimations) 0 else 200)),
                                    ) {
                                        TopSearch(
                                            query = query,
                                            onQueryChange = onQueryChange,
                                            onSearch = onSearch,
                                            active = active,
                                            onActiveChange = onActiveChange,
                                            placeholder = {
                                                Text(
                                                    text =
                                                        stringResource(
                                                            when (searchSource) {
                                                                SearchSource.LOCAL -> R.string.search_library
                                                                SearchSource.ONLINE -> R.string.search_yt_music
                                                            },
                                                        ),
                                                )
                                            },
                                            leadingIcon = {
                                                IconButton(
                                                    onClick = {
                                                        when {
                                                            active -> {
                                                                onActiveChange(false)
                                                            }

                                                            !navigationItems.fastAny {
                                                                it.route == navBackStackEntry?.destination?.route
                                                            } -> {
                                                                navController.navigateUp()
                                                            }

                                                            else -> {
                                                                onActiveChange(true)
                                                            }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        when {
                                                            active -> {}

                                                            !navigationItems.fastAny {
                                                                it.route == navBackStackEntry?.destination?.route
                                                            } -> {
                                                                navController.backToMain()
                                                            }

                                                            else -> {}
                                                        }
                                                    },
                                                ) {
                                                    Icon(
                                                        painterResource(
                                                            if (active ||
                                                                !navigationItems.fastAny {
                                                                    it.route == navBackStackEntry?.destination?.route
                                                                }
                                                            ) {
                                                                R.drawable.arrow_back
                                                            } else {
                                                                R.drawable.ic_search
                                                            },
                                                        ),
                                                        contentDescription = null,
                                                    )
                                                }
                                            },
                                            trailingIcon = {
                                                Row {
                                                    if (active) {
                                                        if (query.text.isNotEmpty()) {
                                                            IconButton(
                                                                onClick = {
                                                                    onQueryChange(
                                                                        TextFieldValue(
                                                                            "",
                                                                        ),
                                                                    )
                                                                },
                                                            ) {
                                                                Icon(
                                                                    painter = painterResource(R.drawable.close),
                                                                    contentDescription = null,
                                                                )
                                                            }
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                searchSource =
                                                                    if (searchSource ==
                                                                        SearchSource.ONLINE
                                                                    ) {
                                                                        SearchSource.LOCAL
                                                                    } else {
                                                                        SearchSource.ONLINE
                                                                    }
                                                            },
                                                        ) {
                                                            Icon(
                                                                painter =
                                                                    painterResource(
                                                                        when (searchSource) {
                                                                            SearchSource.LOCAL -> R.drawable.library_music
                                                                            SearchSource.ONLINE -> R.drawable.language
                                                                        },
                                                                    ),
                                                                contentDescription = null,
                                                            )
                                                        }
                                                    } else if (onlineSearchViewModel != null) {
                                                        OnlineSearchSortMenu(
                                                            selectedSort = onlineSearchSort,
                                                            onSortSelected = onlineSearchViewModel::updateSort,
                                                        )
                                                    }
                                                }
                                            },
                                            modifier =
                                                Modifier
                                                    .focusRequester(searchBarFocusRequester)
                                                    .let { with(this@BoxWithConstraints) { it.align(Alignment.TopCenter) } },
                                            focusRequester = searchBarFocusRequester,
                                            leftFocusRequester = tvRailFocusRequester,
                                            colors =
                                                if (pureBlack && active) {
                                                    SearchBarDefaults.colors(
                                                        containerColor = Color.Black,
                                                        dividerColor = Color.DarkGray,
                                                        inputFieldColors =
                                                            TextFieldDefaults.colors(
                                                                focusedTextColor = Color.White,
                                                                unfocusedTextColor = Color.Gray,
                                                                focusedContainerColor = Color.Transparent,
                                                                unfocusedContainerColor = Color.Transparent,
                                                                cursorColor = Color.White,
                                                                focusedIndicatorColor = Color.Transparent,
                                                                unfocusedIndicatorColor = Color.Transparent,
                                                            ),
                                                    )
                                                } else {
                                                    SearchBarDefaults.colors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                                    )
                                                },
                                        ) {
                                            Crossfade(
                                                targetState = searchSource,
                                                animationSpec = tween(durationMillis = if (disableAnimations) 0 else 300),
                                                label = "",
                                                modifier =
                                                    Modifier
                                                        .fillMaxSize()
                                                        .padding(
                                                            bottom = if (isMiniPlayerVisible) MiniPlayerHeight else 0.dp,
                                                        ).navigationBarsPadding(),
                                            ) { searchSource ->
                                                when (searchSource) {
                                                    SearchSource.LOCAL -> {
                                                        LocalSearchScreen(
                                                            query = query.text,
                                                            navController = navController,
                                                            onDismiss = { onActiveChange(false) },
                                                            pureBlack = pureBlack,
                                                        )
                                                    }

                                                    SearchSource.ONLINE -> {
                                                        OnlineSearchScreen(
                                                            query = query.text,
                                                            onQueryChange = onQueryChange,
                                                            navController = navController,
                                                            onSearch = { query ->
                                                                navController.navigate(onlineSearchResultRoute(query))
                                            },
                                                            onDismiss = { onActiveChange(false) },
                                                            pureBlack = pureBlack,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                bottomBar = {
                                    PlayerOverlayHost(
                                        navController = navController,
                                        maxHeight = this@BoxWithConstraints.maxHeight,
                                        bottomInset = bottomInset,
                                        shouldShowNav = shouldShowNavigationBar,
                                        isYearInMusic = isYearInMusicScreen,
                                        useRail = useRail,
                                        hazeState = hazeState,
                                        pureBlack = pureBlack,
                                        playerViewModel = playerViewModel,
                                        homeViewModel = homeViewModel,
                                        playerConnection = playerConnection,
                                        database = database,
                                        systemBarController = systemBarController,
                                        window = window,
                                        sheetState = playerBottomSheetState,
                                        aodModeLaunchRequestCount = aodModeLaunchRequestCount,
                                        onResetAodLaunchRequestCount = { aodModeLaunchRequestCount = 0 },
                                        useDarkTheme = useDarkTheme,
                                        shouldShowHomeShuffleButton = shouldShowHomeShuffleButton,
                                        navigationItems = navigationItems,
                                        navBackStackEntry = navBackStackEntry,
                                        handlePrimaryNavigationClick = handlePrimaryNavigationClick,
                                        onSearchItemDoubleClick = {
                                            searchSource = SearchSource.ONLINE
                                            openSearch()
                                        },
                                        disableAnimations = disableAnimations,
                                        navVisibleHeight = navVisibleHeight,
                                        floatingToolbarBottomPadding = floatingToolbarBottomPadding,
                                        onExpansionFraction = { provider ->
                                            playerExpansionFractionProvider = provider
                                        },
                                    )
                                },
                                containerColor = Color.Transparent,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                NavigationHost(
                                    navController = navController,
                                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                                    hazeState = hazeState,
                                    updateState = updateState,
                                    modifier = Modifier.fillMaxSize(),
                                    homeScrollConnection = homeScrollBehavior.nestedScrollConnection,
                                    searchScrollConnection = searchScrollBehavior.nestedScrollConnection,
                                    onClearUpdateBadge = { updateViewModel.dismissUpdate() },
                                    disableAnimations = disableAnimations,
                                    isTvDevice = isTvDevice,
                                    contentAreaFocusRequester = contentAreaFocusRequester,
                                    launchMusicRecognitionFromShortcut = launchMusicRecognitionFromShortcut,
                                    tabOpenedFromShortcut = tabOpenedFromShortcut,
                                    defaultOpenTab = defaultOpenTab,
                                    navigationItems = navigationItems,
                                    updateChannel = updateChannel,
                                )
                            }
                        }

                        GlobalDialogsHost(
                            navController = navController,
                            playerConnection = playerConnection,
                            bottomSheetPageState = bottomSheetPageState,
                            menuState = menuState,
                            networkBannerState = networkBannerState,
                            pendingBackupRestoreUri = pendingBackupRestoreUri,
                            onDismissBackupRestore = { pendingBackupRestoreUri = null },
                            splashDone = splashDone,
                            shouldShowTopBar = shouldShowTopBar,
                            topInset = topInset,
                            updateChannel = updateChannel,
                            coroutineScope = coroutineScope,
                        )
                    }

                    LaunchedEffect(shouldShowSearchBar, openSearchImmediately) {
                        if (shouldShowSearchBar && openSearchImmediately) {
                            onActiveChange(true)
                            try {
                                delay(100)
                                searchBarFocusRequester.requestFocus()
                            } catch (_: Exception) {
                            }
                            openSearchImmediately = false
                        }
                    }

                    val openSearchFromRoute =
                        navBackStackEntry
                            ?.savedStateHandle
                            ?.getStateFlow("openSearch", false)
                            ?.collectAsStateWithLifecycle()

                    LaunchedEffect(openSearchFromRoute?.value) {
                        if (openSearchFromRoute?.value == true) {
                            navBackStackEntry?.savedStateHandle?.set("openSearch", false)
                            openSearch()
                        }
                    }
                }
            }
        }
    }
    private fun handleIntent(
        intent: Intent?,
        navController: NavHostController,
    ) {
        intentRouter.handleIntent(intent, navController)
    }

}


