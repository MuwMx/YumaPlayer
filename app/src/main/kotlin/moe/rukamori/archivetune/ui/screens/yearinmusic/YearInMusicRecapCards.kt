/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.yearinmusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.screens.RecapBlack
import moe.rukamori.archivetune.ui.screens.RecapBlue
import moe.rukamori.archivetune.ui.screens.RecapCream
import moe.rukamori.archivetune.ui.screens.RecapGreen
import moe.rukamori.archivetune.ui.screens.RecapInk
import moe.rukamori.archivetune.ui.screens.RecapLime
import moe.rukamori.archivetune.ui.screens.RecapPink
import moe.rukamori.archivetune.ui.screens.RecapRed
import moe.rukamori.archivetune.ui.screens.RecapYellow
import moe.rukamori.archivetune.ui.screens.YearInMusicRecapCard
import moe.rukamori.archivetune.ui.screens.rememberShareSafeImageRequest
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
internal fun EmptyRecapCard(
    card: YearInMusicRecapCard.Empty,
    applySafeContentInsets: Boolean,
) {
    RecapCardContent(
        badge = stringResource(R.string.year_in_music_recap),
        footer = joinByBullet(stringResource(R.string.app_name), card.year.toString()),
        verticalArrangement = Arrangement.Center,
        applySafeContentInsets = applySafeContentInsets,
    ) {
        IconBadge(
            icon = R.drawable.stats,
            background = RecapRed,
            tint = RecapCream,
            modifier = Modifier.size(78.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.year_in_music_empty_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = RecapCream,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.year_in_music_empty_subtitle, card.year),
            style = MaterialTheme.typography.bodyLarge,
            color = RecapCream.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun IntroRecapCard(
    card: YearInMusicRecapCard.Intro,
    applySafeContentInsets: Boolean,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                Color(0xFF56C8F2),
                                Color(0xFF77D77C),
                                RecapPink,
                                Color(0xFFB7B5FF),
                            ),
                    ),
                ).then(if (applySafeContentInsets) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                .padding(24.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(176.dp)
                    .graphicsLayer {
                        rotationZ = -18f
                        translationX = 44f
                        translationY = 30f
                    }.clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFFE569), RecapPink, Color(0xFFFF8A42)),
                        ),
                    ),
        )

        Row(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(top = 72.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(11) { index ->
                Box(
                    modifier =
                        Modifier
                            .width(5.dp)
                            .height((92 - index * 3).dp)
                            .background(if (index % 2 == 0) RecapLime else RecapBlue.copy(alpha = 0.76f)),
                )
            }
        }

        ArchiveTuneBrand(
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.TopStart),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 84.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = card.year.toString(),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 76.sp),
                fontWeight = FontWeight.Black,
                color = Color.White,
                lineHeight = 70.sp,
            )
            Text(
                text = stringResource(R.string.year_in_music_recap_word),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                fontWeight = FontWeight.Black,
                color = Color.White,
                lineHeight = 66.sp,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.year_in_music_intro_hero),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = Color.White,
                lineHeight = 30.sp,
            )
            Text(
                text = stringResource(R.string.year_in_music_intro_details),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.82f),
                lineHeight = 19.sp,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(RecapInk)
                        .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.year_in_music_swipe_begin),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
internal fun TotalsRecapCard(
    card: YearInMusicRecapCard.Totals,
    applySafeContentInsets: Boolean,
) {
    RecapCardContent(
        badge = stringResource(R.string.total_listening_time),
        footer = stringResource(R.string.year_in_music_totals_title),
        verticalArrangement = Arrangement.SpaceBetween,
        applySafeContentInsets = applySafeContentInsets,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.year_in_music_totals_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = RecapCream,
                lineHeight = 36.sp,
            )
            Text(
                text = makeTimeString(card.totalListeningTime),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 60.sp),
                fontWeight = FontWeight.Black,
                color = RecapYellow,
                lineHeight = 56.sp,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RecapStatRow(
                icon = R.drawable.play,
                label = stringResource(R.string.year_in_music_plays_label),
                value = card.totalSongsPlayed.toString(),
                color = RecapCream,
            )
            card.topSong?.let {
                RecapStatRow(
                    icon = R.drawable.music_note,
                    label = stringResource(R.string.year_in_music_top_track),
                    value = it.title,
                    color = RecapRed,
                )
            }
            card.topArtist?.let {
                RecapStatRow(
                    icon = R.drawable.artist,
                    label = stringResource(R.string.top_artists),
                    value = it.artist.name,
                    color = RecapGreen,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TopSongRecapCard(
    card: YearInMusicRecapCard.TopSong,
    applySafeContentInsets: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val imageModel = rememberShareSafeImageRequest(card.song.thumbnailUrl)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .blur(18.dp),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    RecapBlack.copy(alpha = 0.28f),
                                    RecapBlack.copy(alpha = 0.74f),
                                    RecapBlack.copy(alpha = 0.96f),
                                ),
                        ),
                    ),
        )
        RecapCardContent(
            badge = "#1 ${stringResource(R.string.year_in_music_top_track)}",
            footer = stringResource(R.string.year_in_music_top_pick),
            verticalArrangement = Arrangement.SpaceBetween,
            applySafeContentInsets = applySafeContentInsets,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .border(
                                width = 2.dp,
                                color = RecapCream.copy(alpha = 0.82f),
                                shape = RoundedCornerShape(26.dp),
                            ),
                )
                Text(
                    text = card.song.title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = RecapCream,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 36.sp,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RecapChip(
                    icon = R.drawable.play,
                    text =
                        pluralStringResource(
                            R.plurals.n_time,
                            card.song.songCountListened,
                            card.song.songCountListened,
                        ),
                    color = RecapRed,
                )
                RecapChip(
                    icon = R.drawable.timer,
                    text = makeTimeString(card.song.timeListened),
                    color = RecapYellow,
                )
            }
        }
    }
}
