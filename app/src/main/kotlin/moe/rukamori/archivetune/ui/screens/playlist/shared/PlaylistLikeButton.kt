/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.shared

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.utils.LikeSourceResolver
import moe.rukamori.archivetune.utils.SyncUtils

/**
 * The trailing row HEART (left of the 3-dot menu) on unified playlist rows
 * (user spec 2026-11-15). Hidden on the Liked Songs auto playlist, whose
 * rows are liked by definition — its rows keep the legacy passive badge.
 *
 * The row's subtitle BADGE heart is suppressed where this button shows so
 * a song carries exactly one heart.
 */
@Composable
fun PlaylistLikeButton(
    isLiked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onToggle,
        onLongClick = {},
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
            tint = if (isLiked) MaterialTheme.colorScheme.error else LocalContentColor.current,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * The standard like toggle behind the button: SongMenuHeader semantics —
 * toggle the LIVE song row when one exists (a stale seed would reset
 * flags), upsert so a never-played track keeps its like, then push the
 * source-appropriate sync.
 */
fun togglePlaylistSongLike(
    seed: SongEntity,
    source: LikeSource,
    database: MusicDatabase,
    syncUtils: SyncUtils,
    coroutineScope: CoroutineScope,
) {
    coroutineScope.launch(Dispatchers.IO) {
        val live = database.getSongByIdBlocking(seed.id)?.song ?: seed
        val updated = live.localToggleLike(source)
        database.upsert(updated)
        val spotifyId = if (LikeSourceResolver.isSpotifyId(updated.id, updated.isLocal)) updated.id else null
        syncUtils.likeSong(updated, source, spotifyId)
    }
}

/**
 * The Spotify-placeholder variant. A like can live on EITHER row shape:
 * the placeholder row ("spotify:<raw>", a menu like on a never-played
 * track) or the resolved YouTube row (SpotifyLikedSync writes
 * likedSpotify there with a spotify_match entry). The toggle must flip the
 * row the state actually lives on — toggling a fresh placeholder seed when
 * the like sits on the resolved row would push a phantom unlike to
 * Spotify and leave the resolved row untouched.
 */
fun toggleSpotifyTrackLike(
    placeholder: MediaMetadata,
    database: MusicDatabase,
    syncUtils: SyncUtils,
    coroutineScope: CoroutineScope,
) {
    coroutineScope.launch(Dispatchers.IO) {
        val rawId = placeholder.id.removePrefix("spotify:track:").removePrefix("spotify:")
        val live =
            database.getSongByIdBlocking(placeholder.id)?.song
                ?: database.getSpotifyMatch(rawId)?.let { match ->
                    database.getSongByIdBlocking(match.youtubeId)?.song
                }
        val updated = (live ?: placeholder.toSongEntity()).localToggleLike(LikeSource.SPOTIFY)
        database.upsert(updated)
        syncUtils.likeSong(updated, LikeSource.SPOTIFY, rawId)
    }
}
