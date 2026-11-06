/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Adapted from Mochy Music (GPL-3.0)
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.carousel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HeroRollCard(
    state: HeroRollState,
    pageOffset: () -> Float,
    wipeCurve: () -> Float,
    isResultPlaying: Boolean,
    edgeBlur: Boolean,
    blurFactor: Float = 1f,
    isCarouselActive: Boolean,
    animationsDisabled: Boolean,
    onRoll: () -> Unit,
    onPlayResult: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rolling = state.phase == HeroRollPhase.Rolling
    val result = state.result
    val candidates = state.candidates
    val count = candidates.size

    HeroPageWipe(
        pageOffset = pageOffset,
        curve = wipeCurve,
        blur = edgeBlur,
        blurFactor = blurFactor,
        modifier = modifier,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .heroPageMediaDrift(pageOffset)
        ) {
            val unit = maxHeight.value / HeroCarouselTokens.GachaDesignHeight
            val focus = (unit * HeroCarouselTokens.GachaFocusSize).dp
            val coverShape = RoundedCornerShape((unit * HeroCarouselTokens.GachaCornerRadius).dp)
            val focusPx = with(LocalDensity.current) { focus.toPx() }

            HeroRollWashBackground(
                state = state,
                unit = unit,
                focusPx = focusPx,
                pageOffset = pageOffset,
                isCarouselActive = isCarouselActive,
                animationsDisabled = animationsDisabled,
                modifier = Modifier.fillMaxSize(),
            )

            val blankCover = remember { ColorPainter(Color.White.copy(alpha = 0.2f)) }
            candidates.forEachIndexed { index, song ->
                key(song.id) {
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = blankCover,
                        error = blankCover,
                        fallback = blankCover,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(
                                x = (unit * HeroCarouselTokens.GachaOverhang).dp,
                                y = (unit * HeroCarouselTokens.GachaCenterY).dp - focus / 2
                            )
                            .size(focus)
                            .graphicsLayer {
                                val d = reelDistance(index, state.reel.value, count)
                                val near = (1f - abs(d)).coerceIn(0f, 1f)
                                val landed = if (near > 0.5f) state.landPop.value else 1f
                                val scale = (HeroCarouselTokens.GachaItemSize + (HeroCarouselTokens.GachaFocusSize - HeroCarouselTokens.GachaItemSize) * near) / HeroCarouselTokens.GachaFocusSize * landed
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(1f, 0.5f)
                                val slots = d * HeroCarouselTokens.GachaPitch + HeroCarouselTokens.GachaFocusGap * d.coerceIn(-1f, 1f)
                                translationY = slots * unit * density
                                rotationX = (-d * 18f).coerceIn(-50f, 50f)
                                cameraDistance = 14f * density
                                alpha = (2.7f - abs(d)).coerceIn(0f, 1f)
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                shape = coverShape
                                clip = true
                            }
                    )
                }
            }

            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = HeroCarouselTokens.GachaPointerColor,
                modifier = Modifier
                    .offset(
                        x = (unit * (HeroCarouselTokens.GachaPointerX - HeroCarouselTokens.GachaPointerWidth / 2f)).dp,
                        y = (unit * (HeroCarouselTokens.GachaPointerY - HeroCarouselTokens.GachaPointerHeight / 2f)).dp,
                    )
                    .size(
                        width = (unit * HeroCarouselTokens.GachaPointerWidth).dp,
                        height = (unit * HeroCarouselTokens.GachaPointerHeight).dp
                    )
                    .graphicsLayer {
                        val kick = state.pointerKick.value
                        translationX = -kick * 7.dp.toPx()
                        rotationZ = -kick * 9f
                        val win = state.burst.value
                        val pop = if (win > 0f) 1f + 0.18f * sin(win * PI.toFloat()) else 1f
                        scaleX = pop
                        scaleY = pop
                    }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(
                        x = (unit * HeroCarouselTokens.GachaOverhang).dp,
                        y = (unit * HeroCarouselTokens.GachaCenterY).dp - focus / 2
                    )
                    .size(focus)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        enabled = !rolling,
                        onClick = onRoll,
                    )
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .heroPageTextDrift(pageOffset)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
            ) {
                Text(
                    text = when {
                        result == null || rolling -> stringResource(R.string.quick_picks_hero_roll_title)
                        isResultPlaying -> stringResource(R.string.now_playing)
                        else -> stringResource(R.string.quick_picks_hero_roll_result)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = HeroCarouselTokens.ArtOverlayTextSecondary,
                )

                Text(
                    text = if (result != null && !rolling) {
                        result.title
                    } else {
                        stringResource(R.string.quick_picks_hero_roll_title)
                    },
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    fontWeight = FontWeight.Bold,
                    color = HeroCarouselTokens.ArtOverlayTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                )

                Text(
                    text = if (result != null && !rolling) {
                        result.artist
                    } else {
                        stringResource(R.string.quick_picks)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = HeroCarouselTokens.ArtOverlayTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .heightIn(min = HeroCarouselTokens.MinTouchTarget)
                            .clip(CircleShape)
                            .background(HeroCarouselTokens.ArtOverlayButtonSurface)
                            .clickable(
                                enabled = !rolling,
                                onClick = {
                                    if (result != null && !isResultPlaying) {
                                        onPlayResult(result.id)
                                    } else {
                                        onRoll()
                                    }
                                }
                            )
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        if (rolling || result == null || isResultPlaying) {
                            HeroRollDie(spinning = rolling && !animationsDisabled, animationsDisabled = animationsDisabled)
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = null,
                                tint = HeroCarouselTokens.ArtOverlayButtonContent,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Text(
                            text = when {
                                rolling -> stringResource(R.string.quick_picks_hero_rolling)
                                result != null && !isResultPlaying -> stringResource(R.string.play)
                                result != null && isResultPlaying -> stringResource(R.string.quick_picks_hero_roll_again)
                                else -> stringResource(R.string.quick_picks_hero_roll_action)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = HeroCarouselTokens.ArtOverlayButtonContent,
                        )
                    }

                    if (result != null && !rolling && !isResultPlaying) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .heightIn(min = HeroCarouselTokens.MinTouchTarget)
                                .clip(CircleShape)
                                .background(HeroCarouselTokens.ArtOverlayButtonSecondarySurface)
                                .clickable(onClick = onRoll)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            HeroRollDie(spinning = false, animationsDisabled = animationsDisabled)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.quick_picks_hero_roll_again),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = HeroCarouselTokens.ArtOverlayTextPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun reelDistance(index: Int, reel: Float, count: Int): Float {
    if (count == 0) return 0f
    var d = (reel - index) % count
    if (d < -count / 2f) d += count
    if (d >= count / 2f) d -= count
    return d
}
