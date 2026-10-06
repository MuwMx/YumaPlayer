/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import moe.rukamori.archivetune.lyrics.LyricsRomanizationPreferences
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder

@Composable
internal fun LyricsDisplayView(
    lyrics: String?,
    isSynced: Boolean,
    syncedLyrics: SyncedLyrics,
    plainLyrics: PlainLyrics,
    listState: LazyListState,
    selectedLineKeySet: Set<String>,
    textColor: Color,
    normalTextStyle: TextStyle,
    accompanimentTextStyle: TextStyle,
    phoneticTextStyle: TextStyle,
    lyricsLineBlur: Boolean,
    isReadyToParse: Boolean,
    showTranslations: Boolean,
    romanizationPreferences: LyricsRomanizationPreferences,
    lyricsSessionKey: Pair<String, String?>,
    syncedLyricsRenderVersion: Int,
    lyricsTextSize: Float,
    lyricsLineSpacing: Float,
    timingState: LyricsTimingState,
    onPlainLineClicked: (String) -> Unit,
    onPlainLinePressed: (String) -> Unit,
    onKaraokeLineClicked: (ISyncedLine) -> Unit,
    onKaraokeLinePressed: (ISyncedLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize().padding(bottom = 12.dp),
    ) {
        when {
            lyrics == LYRICS_NOT_FOUND -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            lyrics == null -> {
                ShimmerHost { repeat(6) { TextPlaceholder() } }
            }

            isSynced && syncedLyrics.lines.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            !isSynced && plainLyrics.items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            !isSynced -> {
                PlainLyricsView(
                    lines = plainLyrics,
                    listState = listState,
                    selectedLineKeys = selectedLineKeySet,
                    textColor = textColor,
                    textStyle = normalTextStyle,
                    onLineClicked = onPlainLineClicked,
                    onLinePressed = onPlainLinePressed,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                )
            }

            else -> {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize().nestedScroll(timingState.nestedScrollConnection),
                ) {
                    val lyricsViewportOffset = remember(maxHeight) { maxHeight * 0.32f }

                    key(lyricsSessionKey, syncedLyricsRenderVersion, lyricsTextSize, lyricsLineSpacing) {
                        KaraokeLyricsView(
                            listState = listState,
                            lyrics = syncedLyrics,
                            currentPosition = timingState.playbackSyncPosition,
                            onLineClicked = onKaraokeLineClicked,
                            onLinePressed = onKaraokeLinePressed,
                            textColor = textColor,
                            normalLineTextStyle = normalTextStyle,
                            accompanimentLineTextStyle = accompanimentTextStyle,
                            phoneticTextStyle = phoneticTextStyle,
                            blendMode = BlendMode.SrcOver,
                            useBlurEffect = lyricsLineBlur && isReadyToParse,
                            showTranslation = showTranslations,
                            showPhonetic = romanizationPreferences.isEnabled,
                            offset = lyricsViewportOffset,
                            keepAliveZone = 72.dp,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
