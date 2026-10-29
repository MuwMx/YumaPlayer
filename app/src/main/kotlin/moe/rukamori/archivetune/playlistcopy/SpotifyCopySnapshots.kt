/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlistcopy

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Spotify songs each editable copy has already been given, by Spotify playlist id. A song in
 * here that is missing from the copy was removed by the user, so syncing does not add it again.
 */
@Singleton
class SpotifyCopySnapshots
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val dir: File
            get() = File(context.filesDir, DIR_NAME)

        /** The songs given to the copy of [spotifyId], or null when none were recorded yet. */
        fun read(spotifyId: String): Set<String>? {
            val file = fileFor(spotifyId)
            if (!file.exists()) return null
            return try {
                file.readLines().filterTo(HashSet()) { it.isNotBlank() }
            } catch (e: IOException) {
                Timber.tag(TAG).w(e, "Could not read the copy snapshot of %s", spotifyId)
                null
            }
        }

        fun write(
            spotifyId: String,
            songIds: Collection<String>,
        ) {
            try {
                dir.mkdirs()
                val file = fileFor(spotifyId)
                val temp = File(dir, "${file.name}.tmp")
                temp.writeText(songIds.joinToString("\n"))
                if (!temp.renameTo(file)) {
                    file.delete()
                    temp.renameTo(file)
                }
            } catch (e: IOException) {
                Timber.tag(TAG).w(e, "Could not write the copy snapshot of %s", spotifyId)
            }
        }

        private fun fileFor(spotifyId: String) = File(dir, spotifyId.filter { it.isLetterOrDigit() } + ".txt")

        private companion object {
            const val TAG = "SpotifyCopySnapshots"
            const val DIR_NAME = "spotify_copy_tracks"
        }
    }
