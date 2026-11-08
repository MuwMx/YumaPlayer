/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.graphics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import moe.rukamori.archivetune.constants.BlurNavBarKey
import moe.rukamori.archivetune.constants.BlurRadiusKey
import moe.rukamori.archivetune.constants.ForceHighRefreshRateKey
import moe.rukamori.archivetune.constants.GraphicsPresetAppliedKey
import moe.rukamori.archivetune.constants.HomeBackgroundParallaxEnabledKey
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.constants.HomeBackgroundStyleKey

enum class GraphicsPreset(
    val blurNavBar: Boolean,
    val blurRadius: Float,
    val homeBackgroundStyle: HomeBackgroundStyle,
    val parallaxEnabled: Boolean,
    val forceHighRefreshRate: Boolean,
) {
    LOW(
        blurNavBar = false,
        blurRadius = 0f,
        homeBackgroundStyle = HomeBackgroundStyle.TONAL,
        parallaxEnabled = false,
        forceHighRefreshRate = false,
    ),
    MEDIUM(
        blurNavBar = true,
        blurRadius = 16f,
        homeBackgroundStyle = HomeBackgroundStyle.MESH,
        parallaxEnabled = false,
        forceHighRefreshRate = false,
    ),
    HIGH(
        blurNavBar = true,
        blurRadius = 24f,
        homeBackgroundStyle = HomeBackgroundStyle.CIRCLES,
        parallaxEnabled = true,
        forceHighRefreshRate = true,
    ),
}

@Singleton
class DeviceGraphicsClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    fun resolveDeviceTier(): GraphicsPreset {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return GraphicsPreset.LOW
        }

        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return GraphicsPreset.LOW

        if (activityManager.isLowRamDevice) {
            return GraphicsPreset.LOW
        }

        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalMemInGb = memoryInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)

        return when {
            totalMemInGb < 4.5 -> GraphicsPreset.LOW
            totalMemInGb >= 7.5 -> GraphicsPreset.HIGH
            else -> GraphicsPreset.MEDIUM
        }
    }

    suspend fun applyDefaultPresetIfFirstRun() {
        dataStore.edit { preferences ->
            if (preferences[GraphicsPresetAppliedKey] == true) return@edit

            val preset = resolveDeviceTier()
            preferences[GraphicsPresetAppliedKey] = true
            preferences[BlurNavBarKey] = preset.blurNavBar
            preferences[BlurRadiusKey] = preset.blurRadius
            preferences[HomeBackgroundStyleKey] = preset.homeBackgroundStyle.name
            preferences[HomeBackgroundParallaxEnabledKey] = preset.parallaxEnabled
            preferences[ForceHighRefreshRateKey] = preset.forceHighRefreshRate
        }
    }
}
