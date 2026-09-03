package moe.rukamori.archivetune.ui.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat

interface YumaHaptics {
    fun click()
    fun longPress()
    fun toggleOn()
    fun toggleOff()
    fun segmentTick()
    fun gestureStart()
    fun gestureThresholdActivate()
    fun gestureThresholdDeactivate()
    fun gestureEnd()
    fun confirm()
    fun reject()
}

object NoOpYumaHaptics : YumaHaptics {
    override fun click() = Unit
    override fun longPress() = Unit
    override fun toggleOn() = Unit
    override fun toggleOff() = Unit
    override fun segmentTick() = Unit
    override fun gestureStart() = Unit
    override fun gestureThresholdActivate() = Unit
    override fun gestureThresholdDeactivate() = Unit
    override fun gestureEnd() = Unit
    override fun confirm() = Unit
    override fun reject() = Unit
}

class YumaHapticsImpl(
    private val view: View,
    private val enabled: Boolean
) : YumaHaptics {

    private fun perform(constant: Int) {
        if (!enabled) return
        ViewCompat.performHapticFeedback(view, constant)
    }

    override fun click() = perform(HapticFeedbackConstantsCompat.KEYBOARD_TAP)

    override fun longPress() = perform(HapticFeedbackConstantsCompat.LONG_PRESS)

    override fun toggleOn() = perform(HapticFeedbackConstantsCompat.TOGGLE_ON)

    override fun toggleOff() = perform(HapticFeedbackConstantsCompat.TOGGLE_OFF)

    override fun segmentTick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            perform(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        } else {
            perform(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    override fun gestureStart() = perform(HapticFeedbackConstantsCompat.GESTURE_START)

    override fun gestureThresholdActivate() = perform(HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_ACTIVATE)

    override fun gestureThresholdDeactivate() = perform(HapticFeedbackConstantsCompat.GESTURE_THRESHOLD_DEACTIVATE)

    override fun gestureEnd() = perform(HapticFeedbackConstantsCompat.GESTURE_END)

    override fun confirm() = perform(HapticFeedbackConstantsCompat.CONFIRM)

    override fun reject() = perform(HapticFeedbackConstantsCompat.REJECT)
}

val LocalYumaHaptics = staticCompositionLocalOf<YumaHaptics> { NoOpYumaHaptics }

@Composable
fun rememberYumaHaptics(): YumaHaptics = LocalYumaHaptics.current
