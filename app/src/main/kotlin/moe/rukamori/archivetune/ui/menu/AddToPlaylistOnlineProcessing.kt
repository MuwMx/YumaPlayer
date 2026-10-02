/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.utils.SyncUtils
import timber.log.Timber
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

data class ProcessingSummary(
    val total: Int,
    val success: Int,
    val failed: Int,
    val failedItems: List<String>,
)

internal fun CoroutineScope.launchOnlineImport(
    songsSnapshot: List<Song>,
    targetPlaylist: Playlist?,
    addToLiked: Boolean,
    database: MusicDatabase,
    syncUtils: SyncUtils,
    onProgressStart: (Boolean) -> Unit,
    onPercentageChange: (Int) -> Unit,
    onStatusChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onResult: (ProcessingSummary) -> Unit,
) {
    launch(Dispatchers.IO) {
        val snapshotSongs = songsSnapshot
        val total = snapshotSongs.size
        if (total == 0) {
            withContext(Dispatchers.Main) {
                onProgressStart(false)
                onPercentageChange(0)
                onDismiss()
            }
            return@launch
        }

        try {
            withContext(Dispatchers.Main) {
                onProgressStart(true)
                onPercentageChange(0)
                onStatusChange("Preparing import...")
                onDismiss()
            }

            val processed = AtomicInteger(0)
            val successCount = AtomicInteger(0)
            val failCount = AtomicInteger(0)
            val failedSongs = mutableListOf<String>()

            val semaphore = Semaphore(5)

            val tasks =
                snapshotSongs.map { song ->
                    async {
                        semaphore.withPermit {
                            val allArtists =
                                song.artists
                                    .joinToString(" ") { artist ->
                                        try {
                                            URLDecoder.decode(artist.name, StandardCharsets.UTF_8.toString())
                                        } catch (e: Exception) {
                                            artist.name
                                        }
                                    }.trim()

                            val query =
                                if (allArtists.isEmpty()) {
                                    song.title
                                } else {
                                    "${song.title} - $allArtists"
                                }

                            var success = false
                            try {
                                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                                result
                                    .onSuccess { search ->
                                        val firstSong = search.items.distinctBy { it.id }.firstOrNull() as? SongItem
                                        if (firstSong != null) {
                                            val media = firstSong.toMediaMetadata()
                                            val ids = listOf(firstSong.id)
                                            try {
                                                database.insert(media)
                                                if (targetPlaylist != null) {
                                                    database.addSongToPlaylist(targetPlaylist, ids)
                                                }
                                                if (addToLiked) {
                                                    val entity = media.toSongEntity()
                                                    val updatedEntity = entity.localToggleLike(LikeSource.YTM)
                                                    database.query {
                                                        update(updatedEntity)
                                                    }
                                                    syncUtils.likeSong(updatedEntity, LikeSource.YTM)
                                                }
                                                success = true
                                            } catch (e: Exception) {
                                                Timber.e(e, "Error inserting/adding song")
                                            }
                                        }
                                    }.onFailure {
                                        Timber.w(it, "Search failed for $query")
                                    }
                            } catch (e: Exception) {
                                Timber.e(e, "Error processing song $query")
                            }

                            if (success) {
                                successCount.incrementAndGet()
                            } else {
                                failCount.incrementAndGet()
                                synchronized(failedSongs) {
                                    failedSongs.add(song.title)
                                }
                            }

                            val currentProcessed = processed.incrementAndGet()
                            val percent =
                                ((currentProcessed.toDouble() / total.toDouble()) * 100)
                                    .toInt()
                                    .coerceIn(0, 100)

                            withContext(Dispatchers.Main) {
                                onPercentageChange(percent)
                                onStatusChange("Importing: $currentProcessed/$total\nFailed: ${failCount.get()}")
                            }
                        }
                    }
                }

            runCatching { tasks.awaitAll() }.onFailure {
                Timber.e(it, "Import failed")
            }

            withContext(Dispatchers.Main) {
                onPercentageChange(100)
                onResult(
                    ProcessingSummary(
                        total = total,
                        success = successCount.get(),
                        failed = failCount.get(),
                        failedItems = failedSongs,
                    ),
                )
            }
        } finally {
            withContext(Dispatchers.Main) {
                onProgressStart(false)
            }
        }
    }
}

@Composable
internal fun AddToPlaylistOnlineResultDialog(
    summary: ProcessingSummary,
    onDismissResult: () -> Unit,
) {
    DefaultDialog(
        title = { Text("Import Complete") },
        onDismiss = onDismissResult,
        buttons = {
            TextButton(onClick = onDismissResult, shapes = ButtonDefaults.shapes()) {
                Text("OK")
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Total Processed: ${summary.total}")
            Text("Successfully Imported: ${summary.success}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            if (summary.failed > 0) {
                Text("Failed: ${summary.failed}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Failed Items:", style = MaterialTheme.typography.labelLarge)
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                ) {
                    items(summary.failedItems) { title ->
                        Text(
                            text = "• $title",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
