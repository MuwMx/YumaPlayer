/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import moe.rukamori.archivetune.constants.EnablePaxsenixLyricsKey
import moe.rukamori.archivetune.paxsenix.PaxsenixLyrics
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get

object PaxsenixLyricsProvider : LyricsProvider {
    override val name = "Paxsenix (Auto)"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnablePaxsenixLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = PaxsenixLyrics.getLyrics(title, artist, duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        PaxsenixLyrics.getAllLyrics(title, artist, duration, callback)
    }
}
