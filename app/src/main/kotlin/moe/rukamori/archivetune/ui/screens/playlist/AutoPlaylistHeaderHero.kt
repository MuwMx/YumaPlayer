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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
            ) {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.n_song,
                            songs.size,
                            songs.size,
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
            ) {
                Text(
                    text = makeTimeString(likeLength * 1000L),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleButton(
                checked = false,
                onCheckedChange = {
                    onProgressNavigate()
                },
                modifier = Modifier.size(48.dp),
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        checkedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            ) {
                HeaderDownloadProgressIndicator(
                    progress = globalProgress ?: 0f,
                )
            }

            ToggleButton(
                checked = downloadState == HeaderDownloadState.Completed,
                onCheckedChange = {
                    when (downloadState) {
                        HeaderDownloadState.Completed -> onRemoveConfirm()
                        else -> onDownloadToggle()
                    }
                },
                modifier = Modifier.size(48.dp),
                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        checkedContentColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                when (val state = downloadState) {
                    HeaderDownloadState.Completed -> {
                        Icon(
                            painter = painterResource(R.drawable.offline),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
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
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            ToggleButton(
                checked = false,
                onCheckedChange = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = playlist,
                            items = songs.map { it.toMediaItem() },
                        ),
                    )
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp),
                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = stringResource(R.string.play),
                    modifier = Modifier.size(24.dp),
                )
            }

            ToggleButton(
                checked = false,
                onCheckedChange = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = playlist,
                            items = songs.shuffled().map { it.toMediaItem() },
                        ),
                    )
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp),
                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = stringResource(R.string.shuffle),
                    modifier = Modifier.size(24.dp),
                )
            }

            ToggleButton(
                checked = false,
                onCheckedChange = {
                    playerConnection.addToQueue(
                        items = songs.map { it.toMediaItem() },
                    )
                },
                modifier = Modifier.size(48.dp),
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        checkedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
