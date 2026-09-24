/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import dagger.hilt.android.AndroidEntryPoint
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.onboarding.OnboardingViewModel
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.PlayerConnectionHolder
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.utils.SyncUtils
import javax.inject.Inject

@Suppress("DEPRECATION")
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
        set(value) { intentRouter.pendingIntent = value }

    private var pendingBackupRestoreUri: Uri?
        get() = intentRouter.pendingBackupRestoreUri
        set(value) { intentRouter.pendingBackupRestoreUri = value }

    private var aodModeLaunchRequestCount: Int
        get() = intentRouter.aodModeLaunchRequestCount
        set(value) { intentRouter.aodModeLaunchRequestCount = value }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setupMainActivity(
            activity = this,
            splashScreen = splashScreen,
            isReady = { isReady },
            isOnboardingCompleted = { isOnboardingCompleted },
            onOnboardingCompleted = { isOnboardingCompleted = it },
        )
        setContent {
            YumaApp(
                activity = this,
                database = database,
                downloadUtil = downloadUtil,
                syncUtils = syncUtils,
                playerConnection = playerConnection,
                systemBarController = systemBarController,
                playerViewModel = playerViewModel,
                onboardingViewModel = onboardingViewModel,
                pendingIntent = pendingIntent,
                onClearPendingIntent = { pendingIntent = null },
                pendingBackupRestoreUri = pendingBackupRestoreUri,
                onClearPendingBackupRestoreUri = { pendingBackupRestoreUri = null },
                aodModeLaunchRequestCount = aodModeLaunchRequestCount,
                onResetAodLaunchRequestCount = { aodModeLaunchRequestCount = 0 },
                onNavControllerCreated = { navController = it },
                onHandleIntent = { intent, controller -> handleIntent(intent, controller) },
                isOnboardingCompleted = isOnboardingCompleted,
                isReady = isReady,
                onReadyChange = { isReady = it },
            )
        }
    }

    private fun handleIntent(intent: Intent?, navController: NavHostController) {
        intentRouter.handleIntent(intent, navController)
    }
}
