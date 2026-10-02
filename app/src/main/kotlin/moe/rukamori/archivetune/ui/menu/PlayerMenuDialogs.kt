/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.ListDialog

internal data class PlayerMenuSplitArtist(
    val name: String,
    val originalArtist: MediaMetadata.Artist?,
)

@Composable
internal fun rememberPlayerMenuSplitArtists(
    artists: List<MediaMetadata.Artist>,
    artistSeparators: String,
): List<PlayerMenuSplitArtist> =
    remember(artists, artistSeparators) {
        if (artistSeparators.isEmpty()) {
            artists.map { PlayerMenuSplitArtist(it.name, it) }
        } else {
            val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
            artists.flatMap { artist ->
                val parts =
                    artist.name
                        .split(separatorRegex)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                if (parts.size > 1) {
                    parts.mapIndexed { index, name ->
                        PlayerMenuSplitArtist(name, if (index == 0) artist else null)
                    }
                } else {
                    listOf(PlayerMenuSplitArtist(artist.name, artist))
                }
            }
        }
    }

@Composable
internal fun PlayerMenuAddToPlaylistDialog(
    isVisible: Boolean,
    mediaMetadata: MediaMetadata,
    database: MusicDatabase,
    context: Context,
    onDismiss: () -> Unit,
) {
    AddToPlaylistDialog(
        isVisible = isVisible,
        onGetSong = {
            database.withTransaction {
                insert(mediaMetadata)
            }
            listOf(mediaMetadata.id)
        },
        onDismiss = onDismiss,
        onAddComplete = { _, playlistNames ->
            val message =
                when {
                    playlistNames.size == 1 -> context.getString(R.string.added_to_playlist, playlistNames.first())
                    else -> context.getString(R.string.added_to_n_playlists, playlistNames.size)
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
    )
}

@Composable
internal fun PlayerMenuSelectArtistDialog(
    isVisible: Boolean,
    splitArtists: List<PlayerMenuSplitArtist>,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    onDismiss: () -> Unit,
    onDismissMenu: () -> Unit,
) {
    if (!isVisible) return
    val artistChoices = remember(splitArtists) { splitArtists.distinctBy { it.name } }
    ListDialog(
        onDismiss = onDismiss,
    ) {
        items(
            items = artistChoices,
            key = { it.name },
            contentType = { "artist_pick" },
        ) { splitArtist ->
            ListItem(
                headlineContent = {
                    Text(
                        text = splitArtist.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    val thumbUrl = splitArtist.originalArtist?.thumbnailUrl
                    if (thumbUrl.isNullOrBlank()) {
                        Box(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.music_note),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        AsyncImage(
                            model = thumbUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                        )
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            splitArtist.originalArtist?.let { artist ->
                                navController.navigate("artist/${artist.id}")
                                onDismiss()
                                playerBottomSheetState.collapseSoft()
                                onDismissMenu()
                            }
                        },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

@Composable
internal fun PlayerMenuEqualizerDialog(
    isVisible: Boolean,
    playerConnection: PlayerConnection,
    context: Context,
    activityResultLauncher: ActivityResultLauncher<Intent>,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return
    EqualizerDialog(
        onDismiss = onDismiss,
        openSystemEqualizer = {
            try {
                val intent =
                    Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                        putExtra(
                            AudioEffect.EXTRA_AUDIO_SESSION,
                            playerConnection.localPlayer.audioSessionId,
                        )
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                    }
                if (intent.resolveActivity(context.packageManager) != null) {
                    activityResultLauncher.launch(intent)
                } else {
                    Toast.makeText(context, context.getString(R.string.system_equalizer_not_found), Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.system_equalizer_not_found), Toast.LENGTH_SHORT).show()
            }
        },
    )
}
