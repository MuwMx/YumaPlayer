package moe.rukamori.archivetune.ui.player.player_0

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun PlayerCoverCard(
    coverDrawable: Drawable? = null,
    coverUrl: String? = null,
    modifier: Modifier = Modifier,
    placeholderResId: Int,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                spotColor = Color.Black,
                ambientColor = Color.Black.copy(alpha = 0.6f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x0FFFFFFF))
            .border(BorderStroke(1.dp, Color(0x1AFFFFFF)), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = coverDrawable ?: coverUrl,
            animationSpec = tween(durationMillis = 300),
            label = "CoverArtCrossfade"
        ) { currentTarget ->
            val largeBitmap = (currentTarget as? BitmapDrawable)?.bitmap
            if (largeBitmap != null) {
                Image(
                    bitmap = largeBitmap.asImageBitmap(),
                    contentDescription = "Album Art Large",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (currentTarget is String && currentTarget.isNotEmpty()) {
                coil3.compose.AsyncImage(
                    model = currentTarget,
                    contentDescription = "Album Art Large",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = placeholderResId),
                    contentDescription = "Mascot Placeholder Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
