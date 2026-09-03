/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.MediaInfo
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState

import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily

private enum class MediaInfoTab(
    @StringRes val labelRes: Int,
) {
    Information(R.string.information),
    Details(R.string.details),
    Numbers(R.string.numbers),
}

private data class MediaInfoQuickFact(
    @DrawableRes val iconRes: Int,
    val text: String,
)

private data class MediaInfoDetail(
    val label: String,
    val value: String,
    val multiline: Boolean = false,
)

private data class MediaInfoMetric(
    @StringRes val labelRes: Int,
    val value: String,
)

@Composable
fun ShowMediaInfo(videoId: String) {
    if (videoId.isBlank()) return

    val context = LocalContext.current
    val database = LocalDatabase.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current
    val song by database.song(videoId).collectAsState(initial = null)
    val currentFormat by database.format(videoId).collectAsState(initial = null)
    var info by remember(videoId) { mutableStateOf<MediaInfo?>(null) }
    var selectedTab by rememberSaveable(videoId) { mutableStateOf(MediaInfoTab.Information) }

    val unknownText = stringResource(R.string.unknown)
    val pleaseWaitText = stringResource(R.string.please_wait)
    val copyText = stringResource(R.string.copy)
    val shareText = stringResource(R.string.share)
    val closeText = stringResource(R.string.close)
    val songTitleLabel = stringResource(R.string.song_title)
    val songArtistsLabel = stringResource(R.string.song_artists)
    val mediaIdLabel = stringResource(R.string.media_id)
    val mimeTypeLabel = stringResource(R.string.mime_type)
    val codecsLabel = stringResource(R.string.codecs)
    val bitrateLabel = stringResource(R.string.bitrate)
    val sampleRateLabel = stringResource(R.string.sample_rate)
    val loudnessLabel = stringResource(R.string.loudness)
    val volumeLabel = stringResource(R.string.volume)
    val fileSizeLabel = stringResource(R.string.file_size)
    val descriptionLabel = stringResource(R.string.description)
    val detailsLabel = stringResource(R.string.details)
    val numbersLabel = stringResource(R.string.numbers)
    val informationLabel = stringResource(R.string.information)

    val mediaUrl = remember(videoId) { "https://music.youtube.com/watch?v=$videoId" }

    LaunchedEffect(videoId) {
        info = YouTube.getMediaInfo(videoId).getOrNull()
    }

    val heroTitle = song?.title ?: info?.title ?: videoId
    val heroSubtitle =
        song
            ?.artists
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString { it.name }
            ?: info?.author
            ?: unknownText
    val artworkModel = song?.thumbnailUrl ?: info?.authorThumbnail
    val playbackVolume = playerConnection?.let { "${(it.player.volume * 100).toInt()}%" }

    val overviewDetails =
        buildList {
            add(MediaInfoDetail(label = songTitleLabel, value = song?.title ?: info?.title ?: unknownText))
            add(
                MediaInfoDetail(
                    label = songArtistsLabel,
                    value =
                        song
                            ?.artists
                            ?.takeIf { it.isNotEmpty() }
                            ?.joinToString { it.name }
                            ?: info?.author
                            ?: unknownText,
                ),
            )
            add(MediaInfoDetail(label = mediaIdLabel, value = videoId))
        }

    val technicalDetails =
        buildList {
            currentFormat?.itag?.toString()?.let { add(MediaInfoDetail(label = "Itag", value = it)) }
            currentFormat
                ?.mimeType
                ?.takeIf { it.isNotBlank() }
                ?.let { add(MediaInfoDetail(label = mimeTypeLabel, value = it)) }
            currentFormat
                ?.codecs
                ?.takeIf { it.isNotBlank() }
                ?.let { add(MediaInfoDetail(label = codecsLabel, value = it)) }
            currentFormat
                ?.bitrate
                ?.takeIf { it > 0 }
                ?.let { add(MediaInfoDetail(label = bitrateLabel, value = "${it / 1000} Kbps")) }
            currentFormat
                ?.sampleRate
                ?.takeIf { it > 0 }
                ?.let { add(MediaInfoDetail(label = sampleRateLabel, value = "$it Hz")) }
            currentFormat?.loudnessDb?.let { add(MediaInfoDetail(label = loudnessLabel, value = "$it dB")) }
            playbackVolume?.let { add(MediaInfoDetail(label = volumeLabel, value = it)) }
            currentFormat
                ?.contentLength
                ?.takeIf { it > 0 }
                ?.let {
                    add(
                        MediaInfoDetail(
                            label = fileSizeLabel,
                            value = Formatter.formatShortFileSize(context, it),
                        ),
                    )
                }
        }

    val quickFacts =
        buildList {
            currentFormat
                ?.mimeType
                ?.substringBefore(';')
                ?.takeIf { it.isNotBlank() }
                ?.let { add(MediaInfoQuickFact(iconRes = R.drawable.graphic_eq, text = it)) }
            currentFormat
                ?.bitrate
                ?.takeIf { it > 0 }
                ?.let { add(MediaInfoQuickFact(iconRes = R.drawable.waves, text = "${it / 1000} Kbps")) }
            currentFormat
                ?.contentLength
                ?.takeIf { it > 0 }
                ?.let {
                    add(
                        MediaInfoQuickFact(
                            iconRes = R.drawable.storage,
                            text = Formatter.formatShortFileSize(context, it),
                        ),
                    )
                }
            info
                ?.subscribers
                ?.takeIf { it.isNotBlank() }
                ?.let { add(MediaInfoQuickFact(iconRes = R.drawable.person, text = it)) }
        }

    val metrics =
        if (info != null) {
            listOf(
                MediaInfoMetric(R.string.subscribers, info?.subscribers ?: unknownText),
                MediaInfoMetric(R.string.views, info?.viewCount?.let(::numberFormatter) ?: unknownText),
                MediaInfoMetric(R.string.likes, info?.like?.let(::numberFormatter) ?: unknownText),
                MediaInfoMetric(R.string.dislikes, info?.dislike?.let(::numberFormatter) ?: unknownText),
            )
        } else {
            emptyList()
        }

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(contentType = "Hero") {
            MediaInfoHeroCard(
                title = heroTitle,
                subtitle = heroSubtitle,
                artworkModel = artworkModel,
                sectionLabel = informationLabel,
                isLoading = info == null,
                loadingText = pleaseWaitText,
            )
        }

        item(contentType = "Actions") {
            MediaInfoActionButton(
                text = copyText,
                iconRes = R.drawable.copy,
                onClick = { copyToClipboard(context, videoId) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (quickFacts.isNotEmpty()) {
            item(contentType = "QuickFacts") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    quickFacts.forEach { fact ->
                        AssistChip(
                            onClick = { copyToClipboard(context, fact.text) },
                            label = {
                                Text(
                                    text = fact.text,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontFamily = LocalArchiveTuneFontFamily.current,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(fact.iconRes),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                                labelColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }

        item(contentType = "Tabs") {
            MediaInfoTabSelector(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }

        item(contentType = "SelectedContent") {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "mediaInfoTab",
            ) { tab ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                ) {
                    when (tab) {
                        MediaInfoTab.Information -> {
                            MediaInfoDetailCard(
                                items = overviewDetails,
                                copyContentDescription = copyText,
                                onCopy = { copyToClipboard(context, it) },
                            )

                            if (info == null) {
                                MediaInfoPendingCard(
                                    title = descriptionLabel,
                                    message = pleaseWaitText,
                                )
                            } else {
                                MediaInfoNarrativeCard(
                                    title = descriptionLabel,
                                    body = info?.description?.takeIf { it.isNotBlank() } ?: unknownText,
                                    copyText = copyText,
                                    onCopy = {
                                        info
                                            ?.description
                                            ?.takeIf { value -> value.isNotBlank() }
                                            ?.let { copyToClipboard(context, it) }
                                    },
                                )
                            }
                        }

                        MediaInfoTab.Details -> {
                            if (technicalDetails.isEmpty()) {
                                MediaInfoPendingCard(
                                    title = detailsLabel,
                                    message = pleaseWaitText,
                                )
                            } else {
                                MediaInfoDetailCard(
                                    items = technicalDetails,
                                    copyContentDescription = copyText,
                                    onCopy = { copyToClipboard(context, it) },
                                )
                            }
                        }

                        MediaInfoTab.Numbers -> {
                            if (metrics.isEmpty()) {
                                MediaInfoPendingCard(
                                    title = numbersLabel,
                                    message = pleaseWaitText,
                                )
                            } else {
                                MediaInfoMetricsGrid(metrics = metrics)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Фирменная стильная кнопка действия YumaPlayer с правильной иконкой 18dp
 */
@Composable
private fun MediaInfoActionButton(
    text: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "ActionButtonBounce"
    )

    Box(
        modifier = modifier
            .height(42.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = text,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp) // 👈 Фиксированный аккуратный размер иконки
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = LocalArchiveTuneFontFamily.current
            )
        }
    }
}

/**
 * Переключатель вкладок в дизайне YumaPlayer
 */
@Composable
private fun MediaInfoTabSelector(
    selectedTab: MediaInfoTab,
    onTabSelected: (MediaInfoTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MediaInfoTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            val tabInteraction = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    )
                    .clickable(
                        interactionSource = tabInteraction,
                        indication = null
                    ) { onTabSelected(tab) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(tab.labelRes),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = LocalArchiveTuneFontFamily.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MediaInfoHeroCard(
    title: String,
    subtitle: String,
    artworkModel: String?,
    sectionLabel: String,
    isLoading: Boolean,
    loadingText: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(80.dp),
            ) {
                if (artworkModel != null) {
                    AsyncImage(
                        model = artworkModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.music_note),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = sectionLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontFamily = LocalArchiveTuneFontFamily.current
                )
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = LocalArchiveTuneFontFamily.current,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontFamily = LocalArchiveTuneFontFamily.current,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = loadingText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontFamily = LocalArchiveTuneFontFamily.current
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaInfoDetailCard(
    items: List<MediaInfoDetail>,
    copyContentDescription: String,
    onCopy: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                ListItem(
                    overlineContent = {
                        Text(
                            text = item.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontFamily = LocalArchiveTuneFontFamily.current
                        )
                    },
                    trailingContent = {
                        Icon(
                            painter = painterResource(R.drawable.copy),
                            contentDescription = copyContentDescription,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp) // 👈 Фиксированный аккуратный размер иконки
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onCopy(item.value) },
                ) {
                    Text(
                        text = item.value,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = LocalArchiveTuneFontFamily.current,
                        maxLines = if (item.multiline) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (index != items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaInfoNarrativeCard(
    title: String,
    body: String,
    copyText: String,
    onCopy: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .padding(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = LocalArchiveTuneFontFamily.current
                )
                MediaInfoActionButton(
                    text = copyText,
                    iconRes = R.drawable.copy,
                    onClick = onCopy
                )
            }

            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontFamily = LocalArchiveTuneFontFamily.current,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun MediaInfoMetricsGrid(metrics: List<MediaInfoMetric>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        metrics.chunked(2).forEach { rowMetrics ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowMetrics.forEach { metric ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                            .padding(14.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(metric.labelRes),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontFamily = LocalArchiveTuneFontFamily.current
                            )
                            Text(
                                text = metric.value,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = LocalArchiveTuneFontFamily.current
                            )
                        }
                    }
                }

                if (rowMetrics.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MediaInfoPendingCard(
    title: String,
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .padding(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LoadingIndicator(modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary)
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LocalArchiveTuneFontFamily.current
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontFamily = LocalArchiveTuneFontFamily.current
            )
        }
    }
}

private fun copyToClipboard(
    context: Context,
    value: String,
) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText("text", value))
    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}

private fun shareMediaLink(
    context: Context,
    mediaUrl: String,
) {
    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, mediaUrl)
        }
    context.startActivity(Intent.createChooser(shareIntent, null))
}
