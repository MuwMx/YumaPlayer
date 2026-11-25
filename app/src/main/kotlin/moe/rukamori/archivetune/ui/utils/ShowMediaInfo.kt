/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.flowOf
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.MediaInfo
import moe.rukamori.archivetune.playback.resolvers.formatForSource
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.main.LocalDatabase
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.utils.makeTimeString
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.utils.reportException

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

private data class MediaInfoExternalLink(
    val label: String,
    val url: String,
)

@Composable
fun ShowMediaInfo(
    videoId: String,
    onDismiss: (() -> Unit)? = null,
) {
    if (videoId.isBlank()) return

    val context = LocalContext.current
    val database = LocalDatabase.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadataFlow = remember(playerConnection) { playerConnection?.mediaMetadata ?: flowOf(null) }
    val currentFormatFlow = remember(playerConnection) { playerConnection?.currentFormat ?: flowOf(null) }
    val selectedSourceFlow = remember(playerConnection) {
        playerConnection?.currentPlaybackSource ?: flowOf(PlaybackSource.YT_MUSIC)
    }
    val currentMediaMetadata by mediaMetadataFlow.collectAsState(initial = null)
    val selectedSource by selectedSourceFlow.collectAsState(initial = PlaybackSource.YT_MUSIC)
    val isCurrentTrack = videoId == currentMediaMetadata?.id
    val song by database.song(videoId).collectAsState(initial = null)
    val currentPlayingFormat by currentFormatFlow.collectAsState(initial = null)
    val fallbackDbFormat by remember(videoId, selectedSource) {
        database.formatForSource(videoId, selectedSource)
    }.collectAsState(initial = null)
    val currentFormat = if (isCurrentTrack) currentPlayingFormat else fallbackDbFormat

    var info by remember(videoId) { mutableStateOf<MediaInfo?>(null) }
    var isLoading by remember(videoId) { mutableStateOf(true) }
    var loadError by remember(videoId) { mutableStateOf(false) }
    var retryKey by remember(videoId) { mutableIntStateOf(0) }
    var selectedTab by rememberSaveable(videoId) { mutableStateOf(MediaInfoTab.Information) }

    val unknownText = stringResource(R.string.unknown)
    val pleaseWaitText = stringResource(R.string.please_wait)
    val errorUnknownText = stringResource(R.string.error_unknown)
    val retryText = stringResource(R.string.retry)
    val copyText = stringResource(R.string.copy)
    val songTitleLabel = stringResource(R.string.song_title)
    val songArtistsLabel = stringResource(R.string.song_artists)
    val mediaIdLabel = stringResource(R.string.media_id)
    val mimeTypeLabel = stringResource(R.string.mime_type)
    val codecsLabel = stringResource(R.string.codecs)
    val bitrateLabel = stringResource(R.string.bitrate)
    val sampleRateLabel = stringResource(R.string.sample_rate)
    val loudnessLabel = stringResource(R.string.loudness)
    val fileSizeLabel = stringResource(R.string.file_size)
    val descriptionLabel = stringResource(R.string.description)
    val detailsLabel = stringResource(R.string.details)
    val numbersLabel = stringResource(R.string.numbers)

    LaunchedEffect(videoId, retryKey) {
        isLoading = true
        loadError = false
        YouTube.getMediaInfo(videoId)
            .onSuccess {
                info = it
                isLoading = false
            }
            .onFailure {
                reportException(it)
                isLoading = false
                loadError = true
            }
    }

    val heroTitle = song?.title ?: currentMediaMetadata?.takeIf { isCurrentTrack }?.title ?: info?.title ?: videoId
    val heroSubtitle =
        song
            ?.artists
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString { it.name }
            ?: currentMediaMetadata?.takeIf { isCurrentTrack }?.artists?.takeIf { it.isNotEmpty() }?.joinToString { it.name }
            ?: info?.author
            ?: unknownText
    val artworkModel = song?.thumbnailUrl ?: currentMediaMetadata?.takeIf { isCurrentTrack }?.thumbnailUrl ?: info?.authorThumbnail
    val albumTitle = song?.album?.title ?: song?.song?.albumName ?: currentMediaMetadata?.takeIf { isCurrentTrack }?.album?.title
    val durationSec = song?.song?.duration?.takeIf { it > 0 } ?: currentMediaMetadata?.takeIf { isCurrentTrack }?.duration?.takeIf { it > 0 }
    val durationText = durationSec?.let { makeTimeString(it * 1000L, context) }
    val uploadDateText = info?.uploadDate?.takeIf { it.isNotBlank() }

    val overviewDetails = buildList {
        add(MediaInfoDetail(label = songTitleLabel, value = heroTitle))
        add(MediaInfoDetail(label = songArtistsLabel, value = heroSubtitle))
        albumTitle?.takeIf { it.isNotBlank() }?.let {
            add(MediaInfoDetail(label = "Album", value = it))
        }
        uploadDateText?.let {
            add(MediaInfoDetail(label = "Released", value = it))
        }
        durationText?.let {
            add(MediaInfoDetail(label = "Length", value = it))
        }
        add(MediaInfoDetail(label = mediaIdLabel, value = videoId))
    }

    val technicalDetails = buildList {
        currentFormat?.itag?.toString()?.let {
            add(MediaInfoDetail(label = stringResource(R.string.media_info_itag), value = it))
        }
        currentFormat?.mimeType?.takeIf { it.isNotBlank() }?.let {
            add(MediaInfoDetail(label = mimeTypeLabel, value = it))
        }
        currentFormat?.codecs?.takeIf { it.isNotBlank() }?.let {
            add(MediaInfoDetail(label = codecsLabel, value = it))
        }
        currentFormat?.bitrate?.takeIf { it > 0 }?.let {
            add(MediaInfoDetail(label = bitrateLabel, value = "${it / 1000} Kbps"))
        }
        currentFormat?.sampleRate?.takeIf { it > 0 }?.let {
            add(MediaInfoDetail(label = sampleRateLabel, value = "$it Hz"))
        }
        currentFormat?.loudnessDb?.let {
            add(MediaInfoDetail(label = loudnessLabel, value = "$it dB"))
        }
        currentFormat?.contentLength?.takeIf { it > 0 }?.let {
            add(MediaInfoDetail(label = fileSizeLabel, value = Formatter.formatShortFileSize(context, it)))
        }
    }

    val quickFacts = buildList {
        val codecLabel = currentFormat?.codecs?.takeIf { it.isNotBlank() }
            ?: currentFormat?.mimeType?.substringBefore(';')?.takeIf { it.isNotBlank() }
        codecLabel?.let { add(MediaInfoQuickFact(iconRes = R.drawable.graphic_eq, text = it)) }
        currentFormat?.bitrate?.takeIf { it > 0 }?.let {
            add(MediaInfoQuickFact(iconRes = R.drawable.waves, text = "${it / 1000} Kbps"))
        }
        currentFormat?.contentLength?.takeIf { it > 0 }?.let {
            add(MediaInfoQuickFact(iconRes = R.drawable.storage, text = Formatter.formatShortFileSize(context, it)))
        }
    }

    val metrics = if (info != null) {
        listOf(
            MediaInfoMetric(R.string.views, info?.viewCount?.let(::numberFormatter) ?: unknownText),
            MediaInfoMetric(R.string.likes, info?.like?.let(::numberFormatter) ?: unknownText),
            MediaInfoMetric(R.string.dislikes, info?.dislike?.let(::numberFormatter) ?: unknownText),
        )
    } else {
        emptyList()
    }

    val encodedQuery = remember(heroTitle, heroSubtitle) {
        val raw = if (heroSubtitle != unknownText) "$heroTitle $heroSubtitle" else heroTitle
        Uri.encode(raw)
    }

    val externalLinks = remember(videoId, encodedQuery) {
        listOf(
            MediaInfoExternalLink("YouTube Music", "https://music.youtube.com/watch?v=$videoId"),
            MediaInfoExternalLink("Spotify", "https://open.spotify.com/search/$encodedQuery"),
            MediaInfoExternalLink("Apple Music", "https://music.apple.com/search?term=$encodedQuery"),
            MediaInfoExternalLink("Tidal", "https://listen.tidal.com/search?q=$encodedQuery"),
        )
    }

    val allInfoText = remember(overviewDetails, technicalDetails) {
        (overviewDetails + technicalDetails).joinToString("\n") { "${it.label}: ${it.value}" }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            if (onDismiss != null) onDismiss() else bottomSheetPageState.dismiss()
        },
        sheetState = sheetState,
        sheetMaxWidth = 600.dp,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 48.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(SettingsDimensions.BottomSheetCornerRadius),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = SettingsDimensions.GlassBorderThickness,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 14.dp, bottom = 10.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
            item(contentType = "Header") {
                MediaInfoCompactHeader(
                    title = heroTitle,
                    subtitle = heroSubtitle,
                    artworkModel = artworkModel,
                    isLoading = isLoading,
                    copyContentDescription = copyText,
                    onCopyAll = { copyToClipboard(context, allInfoText) },
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
                            MediaInfoTonalChip(
                                text = fact.text,
                                iconRes = fact.iconRes,
                                onClick = { copyToClipboard(context, fact.text) },
                            )
                        }
                    }
                }
            }

            item(contentType = "Tabs") {
                MediaInfoTabSelector(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
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
                                MediaInfoDetailList(
                                    items = overviewDetails,
                                    copyContentDescription = copyText,
                                    onCopy = { copyToClipboard(context, it) },
                                )

                                when {
                                    isLoading && info == null -> {
                                        MediaInfoPendingCard(
                                            title = descriptionLabel,
                                            message = pleaseWaitText,
                                        )
                                    }

                                    loadError && info == null -> {
                                        MediaInfoErrorCard(
                                            title = descriptionLabel,
                                            message = errorUnknownText,
                                            retryText = retryText,
                                            onRetry = { retryKey++ },
                                        )
                                    }

                                    else -> {
                                        MediaInfoNarrativeCard(
                                            title = descriptionLabel,
                                            body = info?.description?.takeIf { it.isNotBlank() } ?: unknownText,
                                            copyContentDescription = copyText,
                                            onCopy = {
                                                info
                                                    ?.description
                                                    ?.takeIf { value -> value.isNotBlank() }
                                                    ?.let { copyToClipboard(context, it) }
                                            },
                                        )
                                    }
                                }
                            }

                            MediaInfoTab.Details -> {
                                if (technicalDetails.isEmpty()) {
                                    if (isLoading) {
                                        MediaInfoPendingCard(
                                            title = detailsLabel,
                                            message = pleaseWaitText,
                                        )
                                    } else {
                                        MediaInfoErrorCard(
                                            title = detailsLabel,
                                            message = unknownText,
                                        )
                                    }
                                } else {
                                    MediaInfoDetailList(
                                        items = technicalDetails,
                                        copyContentDescription = copyText,
                                        onCopy = { copyToClipboard(context, it) },
                                    )
                                }
                            }

                            MediaInfoTab.Numbers -> {
                                if (metrics.isEmpty()) {
                                    if (isLoading) {
                                        MediaInfoPendingCard(
                                            title = numbersLabel,
                                            message = pleaseWaitText,
                                        )
                                    } else {
                                        MediaInfoErrorCard(
                                            title = numbersLabel,
                                            message = errorUnknownText,
                                            retryText = retryText,
                                            onRetry = { retryKey++ },
                                        )
                                    }
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
        }
    }
}

@Composable
private fun MediaInfoCompactHeader(
    title: String,
    subtitle: String,
    artworkModel: String?,
    isLoading: Boolean,
    copyContentDescription: String,
    onCopyAll: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(52.dp),
        ) {
            if (artworkModel != null) {
                AsyncImage(
                    model = artworkModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.music_note),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = LocalArchiveTuneFontFamily.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontFamily = LocalArchiveTuneFontFamily.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isLoading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        IconButton(onClick = onCopyAll) {
            Icon(
                painter = painterResource(R.drawable.copy),
                contentDescription = copyContentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MediaInfoTonalChip(
    text: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = LocalArchiveTuneFontFamily.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MediaInfoTabSelector(
    selectedTab: MediaInfoTab,
    onTabSelected: (MediaInfoTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = MediaInfoTab.entries
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    val indicatorOffset by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "MediaInfoTabIndicator",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
        ) {
            val tabWidth = maxWidth / tabs.size

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(width = tabWidth, height = 36.dp)
                    .offset(x = tabWidth * indicatorOffset),
            ) {}

            Row(modifier = Modifier.fillMaxWidth()) {
                tabs.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val tabInteraction = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = tabInteraction,
                                indication = null,
                            ) { onTabSelected(tab) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(tab.labelRes),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontFamily = LocalArchiveTuneFontFamily.current,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaInfoDetailList(
    items: List<MediaInfoDetail>,
    copyContentDescription: String,
    onCopy: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCopy(item.value) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = item.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontFamily = LocalArchiveTuneFontFamily.current,
                        )
                        Text(
                            text = item.value,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = LocalArchiveTuneFontFamily.current,
                            maxLines = if (item.multiline) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.copy),
                        contentDescription = copyContentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }

                if (index != items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
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
    copyContentDescription: String,
    onCopy: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = LocalArchiveTuneFontFamily.current,
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.copy),
                        contentDescription = copyContentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontFamily = LocalArchiveTuneFontFamily.current,
                lineHeight = 19.sp,
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
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                        ) {
                            Text(
                                text = stringResource(metric.labelRes),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontFamily = LocalArchiveTuneFontFamily.current,
                            )
                            Text(
                                text = metric.value,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = LocalArchiveTuneFontFamily.current,
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            LoadingIndicator(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = LocalArchiveTuneFontFamily.current,
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontFamily = LocalArchiveTuneFontFamily.current,
            )
        }
    }
}

@Composable
private fun MediaInfoErrorCard(
    title: String,
    message: String,
    retryText: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = LocalArchiveTuneFontFamily.current,
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontFamily = LocalArchiveTuneFontFamily.current,
            )
            if (retryText != null && onRetry != null) {
                TextButton(onClick = onRetry) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = retryText,
                        fontFamily = LocalArchiveTuneFontFamily.current,
                    )
                }
            }
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
