@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.HeaderDownloadProgressIndicator
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
fun AutoPlaylistHeaderHero(
    playlist: String,
    songs: List<Song>,
    downloadState: HeaderDownloadState,
    globalProgress: Float?,
    onDownloadToggle: () -> Unit,
    onRemoveConfirm: () -> Unit,
    onProgressNavigate: () -> Unit,
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
    systemBarsTopPadding: Dp = WindowInsets.systemBars.asPaddingValues().calculateTopPadding(),
    likeLength: Int = songs.fastSumBy { it.song.duration },
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = systemBarsTopPadding + 48.dp)
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(240.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    ),
        ) {
            AsyncImage(
                model = songs.firstOrNull()?.song?.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = playlist,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetadataChip(
                icon = R.drawable.music_note,
                text =
                    pluralStringResource(
                        R.plurals.n_song,
                        songs.size,
                        songs.size,
                    ),
                modifier = Modifier.weight(1f, fill = false),
            )

            MetadataChip(
                icon = R.drawable.timer,
                text = makeTimeString(likeLength * 1000L),
                modifier = Modifier.weight(1f, fill = false),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        val playLabel = stringResource(R.string.play)
        val shuffleLabel = stringResource(R.string.shuffle)
        val downloadLabel = stringResource(R.string.download)
        val queueLabel = stringResource(R.string.add_to_queue)

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .yumaClickable(
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = {
                                if (globalProgress != null && globalProgress > 0f && downloadState is HeaderDownloadState.Partial) {
                                    onProgressNavigate()
                                } else {
                                    when (downloadState) {
                                        HeaderDownloadState.Completed -> onRemoveConfirm()
                                        else -> onDownloadToggle()
                                    }
                                }
                            },
                        )
                        .yumaGlassCard(
                            shape = CircleShape,
                            backgroundColor = LocalYumaColors.current.glassBackground,
                        )
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) {
                            contentDescription = downloadLabel
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                if (globalProgress != null && globalProgress > 0f && downloadState is HeaderDownloadState.Partial) {
                    HeaderDownloadProgressIndicator(
                        progress = globalProgress,
                    )
                } else {
                    when (val state = downloadState) {
                        HeaderDownloadState.Completed -> {
                            Icon(
                                painter = painterResource(R.drawable.offline),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                            )
                        }

                        is HeaderDownloadState.Partial -> {
                            CircularProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                trackColor = MaterialTheme.colorScheme.outlineVariant,
                                strokeWidth = 2.dp,
                            )
                        }

                        else -> {
                            Icon(
                                painter = painterResource(R.drawable.download),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                            )
                        }
                    }
                }
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .yumaClickable(
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = playlist,
                                        items = songs.map { it.toMediaItem() },
                                    ),
                                )
                            },
                        )
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        )
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = playLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .yumaClickable(
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = playlist,
                                        items = songs.shuffled().map { it.toMediaItem() },
                                    ),
                                )
                            },
                        )
                        .yumaGlassCard(
                            shape = CircleShape,
                            backgroundColor = LocalYumaColors.current.glassBackground,
                        )
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) {
                            contentDescription = shuffleLabel
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                )
            }

            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .yumaClickable(
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = {
                                playerConnection.addToQueue(
                                    items = songs.map { it.toMediaItem() },
                                )
                            },
                        )
                        .yumaGlassCard(
                            shape = CircleShape,
                            backgroundColor = LocalYumaColors.current.glassBackground,
                        )
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) {
                            contentDescription = queueLabel
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
