package moe.rukamori.archivetune.ui.component.splash

import androidx.compose.ui.graphics.Color

object Fu {
    data class ColorScheme(val r: Int, val g: Int, val b: Int, val coreHex: Long) {
        val color: Color = Color(r, g, b)
        val coreColor: Color = Color(coreHex)
    }

    val ok: ColorScheme = ColorScheme(255, 236, 203, 0xFFFFFAF0)
    val fail: ColorScheme = ColorScheme(255, 120, 110, 0xFFFFDEDB)
}
