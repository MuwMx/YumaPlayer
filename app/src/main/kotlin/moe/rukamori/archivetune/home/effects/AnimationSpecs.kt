package moe.rukamori.archivetune.home.effects

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first

/**
 * Frame-based time accumulator that respects a [speedMultiplier].
 * Returns a [State<Float>] that increases every frame by (deltaMs * speedMultiplier).
 * This allows smooth speed changes without restarting animations.
 */
@Composable
fun rememberAnimatedTime(speedMultiplier: Float): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    // targetSpeed is updated every recomposition via SideEffect (composition thread, safe to read in frame callback)
    val targetSpeed = remember { mutableFloatStateOf(speedMultiplier) }
    SideEffect { targetSpeed.floatValue = speedMultiplier }

    LaunchedEffect(Unit) {
        var lastFrameMs = withInfiniteAnimationFrameMillis { it }
        var currentSpeed = targetSpeed.floatValue
        while (true) {
            if (currentSpeed == 0f && targetSpeed.floatValue == 0f) {
                snapshotFlow { targetSpeed.floatValue }.first { it > 0f }
                lastFrameMs = withInfiniteAnimationFrameMillis { it }
            }
            withInfiniteAnimationFrameMillis { frameMs ->
                val delta = (frameMs - lastFrameMs).coerceIn(0L, 64L).toFloat()
                lastFrameMs = frameMs
                // Smooth lerp: 2.5/sec ramp — ~0.8s to reach target speed.
                // High enough to feel reactive, low enough to avoid jarring jumps.
                currentSpeed += (targetSpeed.floatValue - currentSpeed) * (delta / 1000f) * 2.5f
                if (kotlin.math.abs(currentSpeed) < 0.001f && targetSpeed.floatValue == 0f) {
                    currentSpeed = 0f
                }
                time.floatValue += delta * currentSpeed
            }
        }
    }
    return time
}

/**
 * Parallax sensor state holder
 */
data class ParallaxState(
    val tiltX: State<Float>,
    val tiltY: State<Float>
)

class CalibrationState {
    var baselineX = 0f
    var baselineY = 0f
    var isCalibrated = false
}

/**
 * Reusable parallax effect using device accelerometer
 * Returns ParallaxState with current tilt values as State objects
 *
 * @param enableParallax Whether parallax effect is enabled
 * @param sensitivity Multiplier for tilt sensitivity (default 0.3f)
 */
@Composable
fun rememberParallaxState(
    enableParallax: Boolean,
    sensitivity: Float = 0.3f,
    context: Context
): ParallaxState {
    val smoothTiltX = remember { mutableFloatStateOf(0f) }
    val smoothTiltY = remember { mutableFloatStateOf(0f) }

    val calibration = remember { CalibrationState() }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Reset when parallax is toggled
    LaunchedEffect(enableParallax) {
        if (!enableParallax) {
            smoothTiltX.floatValue = 0f
            smoothTiltY.floatValue = 0f
            calibration.isCalibrated = false
            calibration.baselineX = 0f
            calibration.baselineY = 0f
        } else {
            // Reset calibration when enabling
            calibration.isCalibrated = false
            calibration.baselineX = 0f
            calibration.baselineY = 0f
        }
    }

    DisposableEffect(enableParallax, lifecycleOwner) {
        if (!enableParallax) {
            // Early exit if parallax is disabled
            return@DisposableEffect onDispose { }
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: // No accelerometer available
            return@DisposableEffect onDispose { }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!calibration.isCalibrated) {
                    calibration.baselineX = event.values[0]
                    calibration.baselineY = event.values[1]
                    calibration.isCalibrated = true
                }

                val rawTiltX = (event.values[0] - calibration.baselineX) * sensitivity
                val rawTiltY = -(event.values[1] - calibration.baselineY) * sensitivity

                smoothTiltX.floatValue += (rawTiltX - smoothTiltX.floatValue) * 0.1f
                smoothTiltY.floatValue += (rawTiltY - smoothTiltY.floatValue) * 0.1f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                sensorManager.registerListener(
                    listener,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_GAME
                )
            } else if (event == Lifecycle.Event.ON_STOP) {
                sensorManager.unregisterListener(listener)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            sensorManager.unregisterListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Return State objects directly
    return ParallaxState(
        tiltX = smoothTiltX,
        tiltY = smoothTiltY
    )
}
