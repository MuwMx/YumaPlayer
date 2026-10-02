/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.yearinmusic

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.screens.RecapInk
import moe.rukamori.archivetune.ui.screens.RecapLime
import moe.rukamori.archivetune.ui.screens.rememberShareSafeImageRequest

@Composable
internal fun PassportStamp(
    label: String,
    imageData: Any?,
    count: Int,
) {
    val imageModel = rememberShareSafeImageRequest(imageData)

    Column(
        modifier = Modifier.width(92.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = RecapInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(width = 82.dp, height = 62.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.5f))
                    .border(
                        width = 2.dp,
                        brush =
                            Brush.horizontalGradient(
                                colors = listOf(Color.White, RecapLime, Color.White),
                            ),
                        shape = RoundedCornerShape(10.dp),
                    ),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = RecapInk,
        )
    }
}
@Composable
internal fun SummaryGuideLine(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(64.dp)
                    .height(2.dp)
                    .background(RecapInk.copy(alpha = 0.42f)),
        )
        Box(
            modifier =
                Modifier
                    .width(64.dp)
                    .height(2.dp)
                    .background(RecapInk.copy(alpha = 0.42f)),
        )
    }
}
@Composable
internal fun ArchiveTuneBrand(
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.app_icon_small),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = contentColor,
        )
    }
}
