/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Intent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ArtistSongSortType
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.component.NewMenuItem
import moe.rukamori.archivetune.utils.SpeedDialPin
import moe.rukamori.archivetune.utils.serializeSpeedDialPins
import moe.rukamori.archivetune.utils.toggleSpeedDialPin

@Composable
internal fun ArtistMenuActionGrid(
    artist: Artist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    MenuSurfaceSection {
        NewActionGrid(
            actions =
                buildList {
                    if (artist.songCount > 0) {
                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.play),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.play),
                                onClick = {
                                    coroutineScope.launch {
                                        val songs =
                                            withContext(Dispatchers.IO) {
                                                database
                                                    .artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true)
                                                    .first()
                                                    .map { it.toMediaItem() }
                                            }
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = artist.artist.name,
                                                items = songs,
                                            ),
                                        )
                                    }
                                    onDismiss()
                                },
                            ),
                        )

                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.shuffle),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.shuffle),
                                onClick = {
                                    coroutineScope.launch {
                                        val songs =
                                            withContext(Dispatchers.IO) {
                                                database
                                                    .artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true)
                                                    .first()
                                                    .map { it.toMediaItem() }
                                                    .shuffled()
                                            }
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = artist.artist.name,
                                                items = songs,
                                            ),
                                        )
                                    }
                                    onDismiss()
                                },
                            ),
                        )
                    }

                    if (artist.artist.isYouTubeArtist) {
                        add(
                            NewAction(
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_share),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                text = stringResource(R.string.share),
                                onClick = {
                                    onDismiss()
                                    val intent =
                                        Intent().apply {
                                            action = Intent.ACTION_SEND
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "https://music.youtube.com/channel/${artist.id}",
                                            )
                                        }
                                    context.startActivity(Intent.createChooser(intent, null))
                                },
                            ),
                        )
                    }
                },
        )
    }
}

@Composable
internal fun ArtistMenuOptions(
    artist: Artist,
    isInSpeedDial: Boolean,
    speedDialPins: List<SpeedDialPin>,
    artistPin: SpeedDialPin,
    onSpeedDialSongIdsChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val database = LocalDatabase.current
    val actionCount = 2
    MenuSurfaceSection {
        NewMenuItem(
            headlineContent = {
                Text(
                    text =
                        if (artist.artist.bookmarkedAt !=
                            null
                        ) {
                            stringResource(R.string.subscribed)
                        } else {
                            stringResource(R.string.subscribe)
                        },
                )
            },
            leadingContent = {
                Icon(
                    painter =
                        painterResource(
                            if (artist.artist.bookmarkedAt !=
                                null
                            ) {
                                R.drawable.subscribed
                            } else {
                                R.drawable.subscribe
                            },
                        ),
                    contentDescription = null,
                )
            },
            onClick = {
                database.transaction {
                    update(artist.artist.toggleLike())
                }
            },
            index = 0,
            count = actionCount,
        )

        NewMenuItem(
            headlineContent = {
                Text(
                    text =
                        stringResource(
                            if (isInSpeedDial) {
                                R.string.remove_from_speed_dial
                            } else {
                                R.string.pin_to_speed_dial
                            },
                        ),
                )
            },
            leadingContent = {
                Icon(
                    painter = painterResource(if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark),
                    contentDescription = null,
                )
            },
            onClick = {
                val updatedPins = toggleSpeedDialPin(speedDialPins, artistPin)
                onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
                onDismiss()
            },
            index = 1,
            count = actionCount,
        )
    }
}
