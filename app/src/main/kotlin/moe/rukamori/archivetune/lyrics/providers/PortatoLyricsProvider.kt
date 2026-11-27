/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.lyrics.providers

import android.content.Context
import android.util.Log
import moe.rukamori.archivetune.betterlyrics.BetterLyrics
import moe.rukamori.archivetune.constants.EnablePortatoLyricsKey
import moe.rukamori.archivetune.lyrics.LyricsProvider
import moe.rukamori.archivetune.utils.GlobalLog
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get

object PortatoLyricsProvider : LyricsProvider {
    init {
        if (BetterLyrics.logger == null) {
            BetterLyrics.logger = { message ->
                GlobalLog.append(Log.INFO, "BetterLyrics", message)
            }
        }
    }

    override val name = "Better Lyrics Portato"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnablePortatoLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = BetterLyrics.getPortatoLyrics(title = title, artist = artist, album = album, durationSeconds = duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        BetterLyrics.getAllPortatoLyrics(
            title = title,
            artist = artist,
            album = album,
            durationSeconds = duration,
            callback = callback,
        )
    }
}
