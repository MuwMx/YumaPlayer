/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import android.widget.Toast
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import kotlin.math.roundToInt

@Composable
fun SwipeToSongBox(
    modifier: Modifier = Modifier,
    mediaItem: MediaItem,
    contentBackgroundColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val ctx = LocalContext.current
    val player = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val offset = remember { mutableStateOf(0f) }
    val threshold = 300f
    val resolvedContentBackgroundColor = contentBackgroundColor ?: MaterialTheme.colorScheme.surface

    val dragState =
        rememberDraggableState { delta ->
            offset.value = (offset.value + delta).coerceIn(-threshold, threshold)
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = dragState,
                    onDragStopped = {
                        when {
                            offset.value >= threshold -> {
                                player?.playNext(listOf(mediaItem))
                                Toast.makeText(ctx, R.string.play_next, Toast.LENGTH_SHORT).show()
                                reset(offset, scope)
                            }

                            offset.value <= -threshold -> {
                                player?.addToQueue(listOf(mediaItem))
                                Toast.makeText(ctx, R.string.add_to_queue, Toast.LENGTH_SHORT).show()
                                reset(offset, scope)
                            }

                            else -> {
                                reset(offset, scope)
                            }
                        }
                    },
                ),
    ) {
        if (offset.value != 0f) {
            val (iconRes, bg, tint, align) =
                if (offset.value > 0) {
                    Quadruple(
                        R.drawable.playlist_play,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.onSecondary,
                        Alignment.CenterStart,
                    )
                } else {
                    Quadruple(
                        R.drawable.queue_music,
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.onPrimary,
                        Alignment.CenterEnd,
                    )
                }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.Center)
                        .background(bg),
                contentAlignment = align,
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .padding(horizontal = 24.dp)
                            .size(30.dp)
                            .alpha(0.9f),
                    tint = tint,
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .offset { IntOffset(offset.value.roundToInt(), 0) }
                    .fillMaxWidth()
                    .background(resolvedContentBackgroundColor),
            content = content,
        )
    }
}

private fun reset(
    offset: MutableState<Float>,
    scope: CoroutineScope,
) {
    scope.launch {
        animate(
            initialValue = offset.value,
            targetValue = 0f,
            animationSpec = tween(durationMillis = 300),
        ) { value, _ -> offset.value = value }
    }
}

data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

@Composable
fun ItemFavoriteBadge(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.favorite),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier =
            modifier
                .size(18.dp)
                .padding(end = 2.dp),
    )
}

@Composable
fun ItemLibraryBadge(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.library_add_check),
        contentDescription = null,
        modifier =
            modifier
                .size(18.dp)
                .padding(end = 2.dp),
    )
}

@Composable
fun ItemDownloadBadge(
    state: Int?,
    percent: Float = -1f,
    modifier: Modifier = Modifier,
) {
    when (state) {
        STATE_COMPLETED -> {
            Icon(
                painter = painterResource(R.drawable.offline),
                contentDescription = null,
                modifier =
                    modifier
                        .size(18.dp)
                        .padding(end = 2.dp),
            )
        }

        STATE_QUEUED, STATE_DOWNLOADING -> {
            if (percent > 0f) {
                Box(contentAlignment = Alignment.Center, modifier = modifier) {
                    CircularProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = "${percent.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                CircularWavyProgressIndicator(
                    modifier =
                        modifier
                            .size(16.dp)
                            .padding(end = 2.dp),
                )
            }
        }

        else -> { /* no icon */ }
    }
}

@Composable
fun ItemExplicitBadge(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.explicit),
        contentDescription = null,
        modifier =
            modifier
                .size(18.dp)
                .padding(end = 2.dp),
    )
}
