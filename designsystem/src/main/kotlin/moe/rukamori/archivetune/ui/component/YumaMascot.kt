package moe.rukamori.archivetune.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import moe.rukamori.archivetune.designsystem.R

enum class YumaMood(@DrawableRes val drawableRes: Int) {
    Chill(R.drawable.yuma_chill),
    Alert(R.drawable.yuma_alert),
    Stress(R.drawable.yuma_stress),
    Panic(R.drawable.yuma_panic),
    Shrug(R.drawable.yuma_shrug)
}

@Composable
fun YumaMascot(
    mood: YumaMood,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    Image(
        painter = painterResource(mood.drawableRes),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
