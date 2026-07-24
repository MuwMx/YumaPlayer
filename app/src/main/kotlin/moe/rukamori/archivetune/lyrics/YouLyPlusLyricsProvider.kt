/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import android.util.Log
import moe.rukamori.archivetune.constants.EnableYouLyPlusLyricsKey
import moe.rukamori.archivetune.utils.GlobalLog
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.youlyplus.YouLyPlus

object YouLyPlusLyricsProvider : LyricsProvider {
    init {
        YouLyPlus.logger = { message ->
            GlobalLog.append(Log.INFO, "YouLyPlus", message)
        }
    }

    override val name = "YouLyPlus"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableYouLyPlusLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> =
        YouLyPlus.getLyrics(
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
        YouLyPlus.getAllLyrics(
            title = title,
            artist = artist,
            album = album,
            durationSeconds = duration,
            callback = callback,
        )
    }
}
