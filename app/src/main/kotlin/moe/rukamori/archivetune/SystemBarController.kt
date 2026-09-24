package moe.rukamori.archivetune

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class SystemBarController(
    private val activity: Activity,
) {
    var immersiveStatusBarsHidden: Boolean = false
        private set

    fun setSystemBarAppearance(isDark: Boolean) {
        setSystemBarAppearance(activity.window, isDark)
    }

    fun setStatusBarsHidden(hidden: Boolean) {
        immersiveStatusBarsHidden = hidden
        setStatusBarsHidden(activity.window, hidden)
    }
}

@SuppressLint("ObsoleteSdkInt")
fun setSystemBarAppearance(window: Window, isDark: Boolean) {
    WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
        isAppearanceLightStatusBars = !isDark
        isAppearanceLightNavigationBars = !isDark
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        window.statusBarColor =
            (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        window.navigationBarColor =
            (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
    }
}

fun setStatusBarsHidden(window: Window, hidden: Boolean) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes =
            window.attributes.apply {
                layoutInDisplayCutoutMode =
                    if (hidden) {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    } else {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
            }
    }

    if (hidden) {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        controller.show(WindowInsetsCompat.Type.statusBars())
    }
}
