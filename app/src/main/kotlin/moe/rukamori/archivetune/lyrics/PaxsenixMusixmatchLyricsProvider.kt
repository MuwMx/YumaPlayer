/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import moe.rukamori.archivetune.constants.EnablePaxsenixMusixmatchLyricsKey
import moe.rukamori.archivetune.paxsenix.PaxsenixLyrics
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import timber.log.Timber

object PaxsenixMusixmatchLyricsProvider : LyricsProvider {
    override val name = "Paxsenix: Musixmatch"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnablePaxsenixMusixmatchLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> {
        if (!PaxsenixLyrics.hasApiKey()) {
            Timber.tag("Paxsenix").w("Paxsenix API key is not configured; skipping $name")
            return Result.failure(IllegalStateException("Paxsenix API key is not configured"))
        }
        return PaxsenixLyrics.getMusixmatchLyrics(title, artist, duration)
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        if (!PaxsenixLyrics.hasApiKey()) {
            Timber.tag("Paxsenix").w("Paxsenix API key is not configured; skipping $name")
            return
        }
        getLyrics(id, title, artist, album, duration).onSuccess(callback)
    }
}
