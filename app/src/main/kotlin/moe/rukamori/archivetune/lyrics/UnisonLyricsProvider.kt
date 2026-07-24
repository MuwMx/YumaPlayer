/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import android.util.Log
import moe.rukamori.archivetune.constants.EnableUnisonLyricsKey
import moe.rukamori.archivetune.unison.Unison
import moe.rukamori.archivetune.utils.GlobalLog
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get

object UnisonLyricsProvider : LyricsProvider {
    init {
        Unison.logger = { message ->
            GlobalLog.append(Log.INFO, "Unison", message)
        }
    }

    override val name = "Unison"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableUnisonLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> =
        Unison.getLyrics(
            videoId = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = duration,
        )

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        Unison.getAllLyrics(
            videoId = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = duration,
            callback = callback,
        )
    }
}
