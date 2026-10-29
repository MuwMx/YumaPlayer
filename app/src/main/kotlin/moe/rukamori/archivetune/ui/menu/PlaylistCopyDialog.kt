/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.models.PlaylistSource
import moe.rukamori.archivetune.models.playlistSource
import moe.rukamori.archivetune.playlistcopy.CopyFailure
import moe.rukamori.archivetune.playlistcopy.CopyFailureReason
import moe.rukamori.archivetune.playlistcopy.PlaylistCopyDestination
import moe.rukamori.archivetune.playlistcopy.PlaylistCopyRequest
import moe.rukamori.archivetune.playlistcopy.PlaylistCopySource
import moe.rukamori.archivetune.playlistcopy.PlaylistCopyStage
import moe.rukamori.archivetune.playlistcopy.PlaylistCopyState
import moe.rukamori.archivetune.playlistcopy.PlaylistCopyViewModel
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.PlaylistListItem
import moe.rukamori.archivetune.utils.rememberPreference

private const val MAX_LISTED_FAILURES = 5

/** Lets the user pick where to copy [source] to, runs the copy and shows how it went. */
@Composable
fun PlaylistCopyDialog(
    source: PlaylistCopySource,
    onDismiss: () -> Unit,
    viewModel: PlaylistCopyViewModel = hiltViewModel(),
) {
    val manager = viewModel.manager
    val state by manager.state.collectAsStateWithLifecycle()
    val database = LocalDatabase.current
    val playlists by database.playlistsByCreateDateAsc().collectAsStateWithLifecycle(initialValue = emptyList())
    val (cookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(cookie) { hasYouTubeLoginCookie(cookie) }
    val targets = remember(playlists, isLoggedIn, source) { copyTargets(playlists, isLoggedIn, source) }
    val editableCopy =
        (source as? PlaylistCopySource.Spotify)?.let { spotify ->
            playlists.firstOrNull { it.playlist.spotifySourceId == spotify.playlistId }
        }


    // The manager is shared, so a result left by an earlier dialog must not show up in this one.
    LaunchedEffect(Unit) {
        if (manager.state.value is PlaylistCopyState.Success || manager.state.value is PlaylistCopyState.Failure) {
            manager.dismiss()
        }
    }

    val close = {
        manager.dismiss()
        onDismiss()
    }

    DefaultDialog(
        onDismiss = close,
        title = { Text(stringResource(R.string.copy_to_playlist)) },
        contentScrollable = true,
        buttons = {
            when (val current = state) {
                is PlaylistCopyState.Success -> {
                    if (current.result.failed.any { it.reason != CopyFailureReason.LOCAL_FILE }) {
                        TextButton(onClick = manager::retry) { Text(stringResource(R.string.copy_retry_missing)) }
                    }
                    TextButton(onClick = close) { Text(stringResource(R.string.close_dialog)) }
                }
                is PlaylistCopyState.Failure -> {
                    TextButton(onClick = manager::retry) { Text(stringResource(R.string.retry)) }
                    TextButton(onClick = close) { Text(stringResource(R.string.close_dialog)) }
                }
                is PlaylistCopyState.Running, PlaylistCopyState.Idle ->
                    TextButton(onClick = close) { Text(stringResource(R.string.cancel_button)) }
            }
        },
    ) {
        when (val current = state) {
            PlaylistCopyState.Idle -> {
                Text(stringResource(R.string.copy_choose_target), modifier = Modifier.fillMaxWidth())
                if (source is PlaylistCopySource.Spotify) {
                    NewPlaylistRow(
                        label =
                            stringResource(
                                if (editableCopy == null) R.string.make_editable_copy else R.string.update_editable_copy,
                            ),
                        supporting = stringResource(R.string.editable_copy_description),
                    ) { manager.startEditableCopy(source) }
                }
                NewPlaylistRow(stringResource(R.string.copy_new_personal)) {
                    manager.start(PlaylistCopyRequest(source, PlaylistCopyDestination.NewPersonal(source.name)))
                }
                if (isLoggedIn) {
                    NewPlaylistRow(stringResource(R.string.copy_new_youtube)) {
                        manager.start(PlaylistCopyRequest(source, PlaylistCopyDestination.NewYouTube(source.name)))
                    }
                }
                targets.forEach { target ->
                    PlaylistListItem(
                        playlist = target,
                        badges = {
                            Text(
                                text = target.playlist.sourceLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    manager.start(PlaylistCopyRequest(source, PlaylistCopyDestination.Existing(target.id)))
                                },
                    )
                }
            }
            is PlaylistCopyState.Running -> {
                CircularWavyProgressIndicator()
                val progress = current.progress
                Text(
                    text =
                        when (progress.stage) {
                            PlaylistCopyStage.READING -> stringResource(R.string.copy_reading)
                            PlaylistCopyStage.MATCHING -> stringResource(R.string.copy_matching, progress.completed, progress.total)
                            PlaylistCopyStage.WRITING -> stringResource(R.string.copy_writing)
                        },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            is PlaylistCopyState.Success -> {
                Text(pluralStringResource(R.plurals.copy_complete, current.result.added, current.result.added))
                FailureList(current.result.failed)
            }
            is PlaylistCopyState.Failure -> {
                Text(stringResource(R.string.copy_failed))
                FailureList(current.failed)
            }
        }
    }
}

/** The copy source for a library playlist, by where its songs come from. */
internal fun PlaylistEntity.toCopySource(): PlaylistCopySource =
    when (playlistSource()) {
        PlaylistSource.SPOTIFY -> PlaylistCopySource.Spotify(spotifyId.orEmpty(), name, thumbnailUrl)
        PlaylistSource.YOUTUBE -> PlaylistCopySource.YouTube(browseId.orEmpty(), name, thumbnailUrl)
        PlaylistSource.PERSONAL -> PlaylistCopySource.Personal(id, name, thumbnailUrl)
    }

private val PlaylistCopySource.name: String
    get() =
        when (this) {
            is PlaylistCopySource.Spotify -> name
            is PlaylistCopySource.YouTube -> name
            is PlaylistCopySource.Personal -> name
        }

/**
 * Playlists a copy of [source] can be added to: Personal playlists, and YouTube playlists while
 * signed in, but not the playlist that is being copied.
 */
internal fun copyTargets(
    playlists: List<Playlist>,
    isLoggedIn: Boolean,
    source: PlaylistCopySource,
): List<Playlist> =
    playlistsForAddToPlaylist(playlists).filter { playlist ->
        val isSource =
            when (source) {
                is PlaylistCopySource.Personal -> playlist.id == source.playlistId
                is PlaylistCopySource.YouTube -> playlist.playlist.browseId == source.browseId
                is PlaylistCopySource.Spotify -> false
            }
        !isSource &&
            when (playlist.playlist.playlistSource()) {
                PlaylistSource.PERSONAL -> true
                PlaylistSource.YOUTUBE -> isLoggedIn && playlist.playlist.isEditable
                PlaylistSource.SPOTIFY -> false
            }
    }

@Composable
private fun PlaylistEntity.sourceLabel(): String =
    stringResource(
        when (playlistSource()) {
            PlaylistSource.SPOTIFY -> R.string.spotify_integration
            PlaylistSource.YOUTUBE -> R.string.youtube_synced
            PlaylistSource.PERSONAL -> R.string.personal_label
        },
    )

/** A "new playlist" choice, drawn like the create row of the Add to playlist sheet. */
@Composable
private fun NewPlaylistRow(
    label: String,
    supporting: String? = null,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label, fontWeight = FontWeight.Medium) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(ListThumbnailSize)
                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                        .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    )
}

@Composable
private fun FailureList(failed: List<CopyFailure>) {
    if (failed.isEmpty()) return
    Text(
        text = pluralStringResource(R.plurals.copy_missing, failed.size, failed.size),
        modifier = Modifier.padding(top = 8.dp),
    )
    failed.take(MAX_LISTED_FAILURES).forEach { failure ->
        val reason =
            stringResource(
                when (failure.reason) {
                    CopyFailureReason.NO_MATCH -> R.string.copy_reason_no_match
                    CopyFailureReason.SEARCH_FAILED -> R.string.copy_reason_search_failed
                    CopyFailureReason.LOCAL_FILE -> R.string.copy_reason_local_file
                },
            )
        Text(
            text = stringResource(R.string.copy_failure_item, failure.title, failure.artist, reason),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
        )
    }
}
