package moe.rukamori.archivetune

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AppLanguageKey
import moe.rukamori.archivetune.constants.DisableScreenshotKey
import moe.rukamori.archivetune.constants.OnboardingCompletedKey
import moe.rukamori.archivetune.constants.SYSTEM_DEFAULT
import moe.rukamori.archivetune.ui.component.splash.SplashSlots
import moe.rukamori.archivetune.ui.component.splash.SplashVectorLoader
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.setAppLocale
import java.util.Locale

fun setupMainActivity(
    activity: ComponentActivity,
    splashScreen: SplashScreen,
    isReady: () -> Boolean,
    isOnboardingCompleted: () -> Boolean?,
    onOnboardingCompleted: (Boolean) -> Unit,
) {
    splashScreen.setKeepOnScreenCondition {
        isOnboardingCompleted() == null || !isReady()
    }
    activity.lifecycleScope.launch {
        activity.dataStore.data
            .map { it[OnboardingCompletedKey] ?: false }
            .distinctUntilChanged()
            .collectLatest { completed ->
                onOnboardingCompleted(completed)
            }
    }
    activity.window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
    WindowCompat.setDecorFitsSystemWindows(activity.window, false)

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        val initialLocale =
            PreferenceStore
                .get(AppLanguageKey)
                ?.takeUnless { it == SYSTEM_DEFAULT }
                ?.let { Locale.forLanguageTag(it) }
                ?: Locale.getDefault()
        setAppLocale(activity, initialLocale)

        activity.lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                activity.dataStore.data.first()[AppLanguageKey]
            }.onSuccess { lang ->
                val targetLocale =
                    lang
                        ?.takeUnless { it == SYSTEM_DEFAULT }
                        ?.let { Locale.forLanguageTag(it) }
                        ?: Locale.getDefault()
                if (targetLocale != initialLocale) {
                    withContext(Dispatchers.Main) {
                        setAppLocale(activity, targetLocale)
                        activity.recreate()
                    }
                }
            }
        }
    }

    activity.lifecycleScope.launch(Dispatchers.IO) {
        activity.dataStore.data
            .map { it[DisableScreenshotKey] ?: false }
            .distinctUntilChanged()
            .collectLatest {
                withContext(Dispatchers.Main) {
                    if (it) {
                        activity.window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
    }

    activity.lifecycleScope.launch(Dispatchers.Default) {
        val path = SplashVectorLoader.loadPath(activity, R.drawable.about_splash)
        withContext(Dispatchers.Main) {
            SplashSlots.customVectorPath = path
            SplashSlots.vectorVersion++
        }
    }
}
