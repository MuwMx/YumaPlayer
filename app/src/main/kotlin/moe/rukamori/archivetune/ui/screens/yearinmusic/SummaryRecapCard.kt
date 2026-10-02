/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.yearinmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.screens.RecapInk
import moe.rukamori.archivetune.ui.screens.RecapLime
import moe.rukamori.archivetune.ui.screens.YearInMusicRecapCard
import moe.rukamori.archivetune.ui.screens.rememberShareSafeImageRequest

@Composable
internal fun SummaryRecapCard(
    card: YearInMusicRecapCard.Summary,
    applySafeContentInsets: Boolean,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color(0xFF7BA9FF),
                                Color(0xFFD7E7FF),
                                Color(0xFFE9B4FF),
                                Color(0xFFFF6F8F),
                            ),
                    ),
                ).then(if (applySafeContentInsets) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        SummaryGuideLine(modifier = Modifier.align(Alignment.TopCenter))
        SummaryGuideLine(modifier = Modifier.align(Alignment.Center))

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${card.year} ${stringResource(R.string.year_in_music_recap_word)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = RecapInk,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SummaryRankColumn(
                    title = stringResource(R.string.top_artists),
                    imageData = card.topArtists.firstOrNull()?.thumbnailUrl,
                    names = card.topArtists.map { it.artist.name },
                    circularImage = true,
                    modifier = Modifier.weight(1f),
                )
                SummaryRankColumn(
                    title = stringResource(R.string.top_songs),
                    imageData = card.topSongs.firstOrNull()?.thumbnailUrl,
                    names = card.topSongs.map { it.title },
                    circularImage = false,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = stringResource(R.string.year_in_music_musical_passport).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = RecapInk,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PassportStamp(
                    label = stringResource(R.string.top_artists),
                    imageData = card.topArtists.firstOrNull()?.thumbnailUrl,
                    count = card.topArtists.size,
                )
                PassportStamp(
                    label = stringResource(R.string.top_songs),
                    imageData = card.topSongs.firstOrNull()?.thumbnailUrl,
                    count = card.topSongs.size,
                )
                PassportStamp(
                    label = stringResource(R.string.albums),
                    imageData = card.topAlbums.firstOrNull()?.thumbnailUrl,
                    count = card.topAlbums.size,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.year_in_music_minutes_label).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = RecapInk,
                )
                Text(
                    text = formatListeningMinutes(card.totalListeningTime),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp),
                    fontWeight = FontWeight.Black,
                    color = RecapInk,
                    lineHeight = 40.sp,
                )
                Text(
                    text = stringResource(R.string.year_in_music_minutes_unit).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = RecapInk,
                )
            }

            ArchiveTuneBrand(
                contentColor = RecapInk,
                modifier = Modifier.align(Alignment.Start),
            )
        }
    }
}
@Composable
internal fun SummaryRankColumn(
    title: String,
    imageData: Any?,
    names: List<String>,
    circularImage: Boolean,
    modifier: Modifier = Modifier,
) {
    val imageModel = rememberShareSafeImageRequest(imageData)
    val imageShape = if (circularImage) CircleShape else RoundedCornerShape(14.dp)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(contentAlignment = Alignment.BottomStart) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(118.dp)
                        .clip(imageShape)
                        .background(Color.White.copy(alpha = 0.42f))
                        .border(
                            width = 2.dp,
                            color = Color.White.copy(alpha = 0.72f),
                            shape = imageShape,
                        ),
            )
            Text(
                text = title,
                modifier =
                    Modifier
                        .graphicsLayer { rotationZ = -4f }
                        .clip(RoundedCornerShape(7.dp))
                        .background(RecapLime)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = RecapInk,
                lineHeight = 18.sp,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            names.take(5).forEachIndexed { index, name ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = RecapInk,
                    )
                    Text(
                        text = name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = RecapInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
