/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.YearInMusicUiState

@Composable
internal fun rememberYearInMusicCards(content: YearInMusicUiState.Content): List<YearInMusicRecapCard> {
    val introLabel = stringResource(R.string.year_in_music_recap)
    val totalsLabel = stringResource(R.string.total_listening_time)
    val topTrackLabel = stringResource(R.string.year_in_music_top_track)
    val artistsLabel = stringResource(R.string.year_in_music_ranked_artists)
    val albumsLabel = stringResource(R.string.year_in_music_ranked_albums)
    val summaryLabel = stringResource(R.string.share_summary)
    val emptyLabel = stringResource(R.string.no_listening_data)

    return remember(
        content.selectedYear,
        content.totalListeningTime,
        content.totalSongsPlayed,
        content.topSongsStats,
        content.topSongs,
        content.topArtists,
        content.topAlbums,
        introLabel,
        totalsLabel,
        topTrackLabel,
        artistsLabel,
        albumsLabel,
        summaryLabel,
        emptyLabel,
    ) {
        if (!content.hasData) {
            listOf(YearInMusicRecapCard.Empty(content.selectedYear, emptyLabel))
        } else {
            buildList {
                add(
                    YearInMusicRecapCard.Intro(
                        year = content.selectedYear,
                        totalListeningTime = content.totalListeningTime,
                        totalSongsPlayed = content.totalSongsPlayed,
                        label = introLabel,
                    ),
                )
                add(
                    YearInMusicRecapCard.Totals(
                        totalListeningTime = content.totalListeningTime,
                        totalSongsPlayed = content.totalSongsPlayed,
                        topSong = content.topSongsStats.firstOrNull(),
                        topArtist = content.topArtists.firstOrNull(),
                        label = totalsLabel,
                    ),
                )
                content.topSongsStats.firstOrNull()?.let { topSong ->
                    add(
                        YearInMusicRecapCard.TopSong(
                            song = topSong,
                            originalSong = content.topSongs.firstOrNull { it.id == topSong.id } ?: content.topSongs.firstOrNull(),
                            label = topTrackLabel,
                        ),
                    )
                }
                if (content.topArtists.isNotEmpty()) {
                    add(
                        YearInMusicRecapCard.RankedArtists(
                            artists = content.topArtists,
                            label = artistsLabel,
                        ),
                    )
                }
                if (content.topAlbums.isNotEmpty()) {
                    add(
                        YearInMusicRecapCard.RankedAlbums(
                            albums = content.topAlbums,
                            label = albumsLabel,
                        ),
                    )
                }
                add(
                    YearInMusicRecapCard.Summary(
                        year = content.selectedYear,
                        totalListeningTime = content.totalListeningTime,
                        totalSongsPlayed = content.totalSongsPlayed,
                        topSongs = content.topSongsStats,
                        topArtists = content.topArtists,
                        topAlbums = content.topAlbums,
                        label = summaryLabel,
                    ),
                )
            }
        }
    }
}
@Composable
internal fun RecapShareButton(
    isGenerating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = RecapRed,
        contentColor = RecapCream,
        shape = MaterialTheme.shapes.large,
        icon = {
            AnimatedContent(
                targetState = isGenerating,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "shareProgress",
            ) { generating ->
                if (generating) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = RecapCream,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = null,
                    )
                }
            }
        },
        text = {
            Text(
                text = stringResource(R.string.year_in_music_current_card),
                fontWeight = FontWeight.Black,
            )
        },
    )
}
@Composable
internal fun rememberShareSafeImageRequest(data: Any?): Any? {
    val context = LocalContext.current
    return remember(data, context) {
        data?.let {
            ImageRequest
                .Builder(context)
                .data(it)
                .allowHardware(false)
                .build()
        }
    }
}
internal suspend fun awaitNextPreDraw(view: View) {
    suspendCancellableCoroutine { cont ->
        val vto = view.viewTreeObserver
        val listener =
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (vto.isAlive) vto.removeOnPreDrawListener(this)
                    cont.resume(Unit)
                    return true
                }
            }
        vto.addOnPreDrawListener(listener)
        cont.invokeOnCancellation {
            if (vto.isAlive) vto.removeOnPreDrawListener(listener)
        }
        view.invalidate()
    }
}
