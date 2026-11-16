/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.player.ambient

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

@RequiresApi(33)
internal fun createAgslShader(source: String): Any = RuntimeShader(source)

@RequiresApi(33)
internal fun Any.setAgslUniform(name: String, value: Float) {
    (this as RuntimeShader).setFloatUniform(name, value)
}

@RequiresApi(33)
internal fun Any.setAgslUniform(name: String, first: Float, second: Float) {
    (this as RuntimeShader).setFloatUniform(name, first, second)
}

@RequiresApi(33)
internal fun Any.setAgslUniform(name: String, first: Float, second: Float, third: Float) {
    (this as RuntimeShader).setFloatUniform(name, first, second, third)
}

@RequiresApi(33)
internal fun Any.setAgslColorUniform(name: String, color: Int) {
    (this as RuntimeShader).setColorUniform(name, color)
}

@RequiresApi(33)
internal fun Any.setAgslInputShader(name: String, input: android.graphics.Shader) {
    (this as RuntimeShader).setInputShader(name, input)
}

@RequiresApi(33)
internal fun Any.asAgslPaintShader(): android.graphics.Shader = this as RuntimeShader

@RequiresApi(33)
internal fun agslRenderEffect(shader: Any, inputName: String): RenderEffect =
    RenderEffect.createRuntimeShaderEffect(shader as RuntimeShader, inputName)
