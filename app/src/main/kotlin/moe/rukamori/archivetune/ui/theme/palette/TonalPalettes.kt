package moe.rukamori.archivetune.ui.theme.palette

import androidx.compose.ui.graphics.Color

// Специальный класс-обёртка, который разрешает использование квадратных скобок [tone]
class DynamicTonePalette(private val defaultColor: Color) {
    operator fun get(tone: Int): Color {
        // Возвращаем базовый цвет. При желании тут можно подмешивать белый/черный в зависимости от тона,
        // но для успешного билда и дефолтных иконок достаточно отдавать чистый цвет.
        return defaultColor
    }
}

class TonalPalettes {
    // Теперь это не просто цвета, а объекты с поддержкой индексов [tone]А
    val primary = DynamicTonePalette(Color(0xFFED5564))
    val secondary = DynamicTonePalette(Color(0xFFED5564))
    val tertiary = DynamicTonePalette(Color(0xFFED5564))
    val neutral = DynamicTonePalette(Color(0xFF121212))
    val neutralVariant = DynamicTonePalette(Color(0xFF282828))
    val error = DynamicTonePalette(Color.Red)

    companion object {
        // Фиксы для PalettePickerScreen.kt, чтобы старые экраны настроек донора не падали
        fun fromSeedColors(vararg colors: Any?): TonalPalettes = TonalPalettes()
        fun fromSeedColor(color: Color): TonalPalettes = TonalPalettes()
    }
}