/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.ui.component.DefaultDialog

@Composable
internal fun AddToPlaylistDuplicateDialog(
    isVisible: Boolean,
    duplicateSongsMap: Map<String, List<String>>,
    playlistsWithDuplicates: List<Playlist>,
    songIds: List<String>?,
    coroutineScope: CoroutineScope,
    onAddSafely: suspend (Playlist, List<String>) -> Int,
    onAddComplete: ((songCount: Int, playlistNames: List<String>) -> Unit)?,
    onDismiss: () -> Unit,
    onDismissAll: () -> Unit,
) {
    if (!isVisible) return

    val totalDuplicates =
        duplicateSongsMap.values
            .flatten()
            .distinct()
            .size

    DefaultDialog(
        title = { Text(stringResource(R.string.duplicates)) },
        buttons = {
            TextButton(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        var totalAdded = 0
                        val names = mutableListOf<String>()
                        playlistsWithDuplicates.forEach { playlist ->
                            val duplicatesForThisPlaylist = duplicateSongsMap[playlist.id] ?: emptyList()
                            val songsToAdd = songIds!!.filter { it !in duplicatesForThisPlaylist }
                            val addedCount = onAddSafely(playlist, songsToAdd)
                            if (addedCount > 0) {
                                totalAdded += addedCount
                                names += playlist.playlist.name
                            }
                        }
                        if (totalAdded > 0) {
                            withContext(Dispatchers.Main) {
                                onAddComplete?.invoke(totalAdded, names)
                            }
                        }
                    }
                    onDismiss()
                    onDismissAll()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.skip_duplicates))
            }

            TextButton(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        var totalAdded = 0
                        val names = mutableListOf<String>()
                        playlistsWithDuplicates.forEach { playlist ->
                            val addedCount = onAddSafely(playlist, songIds!!)
                            if (addedCount > 0) {
                                totalAdded += addedCount
                                names += playlist.playlist.name
                            }
                        }
                        if (totalAdded > 0) {
                            withContext(Dispatchers.Main) {
                                onAddComplete?.invoke(totalAdded, names)
                            }
                        }
                    }
                    onDismiss()
                    onDismissAll()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.add_anyway))
            }

            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        onDismiss = onDismiss,
    ) {
        Text(
            text =
                if (totalDuplicates == 1) {
                    stringResource(R.string.duplicates_description_single)
                } else {
                    stringResource(R.string.duplicates_description_multiple, totalDuplicates)
                },
            textAlign = TextAlign.Start,
            modifier = Modifier.align(Alignment.Start),
        )
    }
}
