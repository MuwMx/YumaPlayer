package moe.rukamori.archivetune.ui.player.lyrics_0

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.state.PlayerUiState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

@Composable
fun LyricsBackgroundLayers(
    state: PlayerUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val blurImageRequest = remember(state.coverUrl) {
        ImageRequest.Builder(context)
            .data(state.coverUrl)
            .size(128)
            .build()
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (state.isBlurBackgroundEnabled) {
            if (state.coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = blurImageRequest,
                    contentDescription = "Blurred Background",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(32.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(state.darkMutedColor),
                                Color(0xFF121212)
                            )
                        )
                    )
            )
        }
    }
}
