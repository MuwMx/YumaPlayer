package moe.rukamori.archivetune

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.compositionLocalOf

val LocalPlayerAwareWindowInsets =
    compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
