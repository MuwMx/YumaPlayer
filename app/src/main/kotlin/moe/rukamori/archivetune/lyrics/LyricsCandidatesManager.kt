/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.lyrics

import androidx.collection.LruCache
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.constants.PreferredLyricsProvider
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.lyrics.parsing.LrcParser
import moe.rukamori.archivetune.lyrics.providers.BetterLyricsProvider
import moe.rukamori.archivetune.lyrics.providers.KuGouLyricsProvider
import moe.rukamori.archivetune.lyrics.providers.LrcLibLyricsProvider
import moe.rukamori.archivetune.lyrics.providers.PortatoLyricsProvider
import moe.rukamori.archivetune.lyrics.providers.SimpMusicLyricsProvider
import moe.rukamori.archivetune.lyrics.providers.UnisonLyricsProvider
import moe.rukamori.archivetune.lyrics.providers.YouLyPlusLyricsProvider

enum class LyricsSyncKind {
    NONE,
    PLAIN,
    LINE,
    WORD,
}

data class CandidateState(
    val isChecking: Boolean = true,
    val lyrics: String? = null,
    val syncKind: LyricsSyncKind = LyricsSyncKind.NONE,
)

object LyricsCandidatesManager {
    private val cache = LruCache<String, Map<PreferredLyricsProvider, CandidateState>>(20)

    fun getCached(songId: String): Map<PreferredLyricsProvider, CandidateState>? = cache[songId]

    fun updateCache(songId: String, provider: PreferredLyricsProvider, state: CandidateState) {
        synchronized(cache) {
            val current = cache[songId]?.toMutableMap() ?: mutableMapOf()
            current[provider] = state
            cache.put(songId, current)
        }
    }

    fun resolveProvider(provider: PreferredLyricsProvider): LyricsProvider =
        when (provider) {
            PreferredLyricsProvider.BETTER_LYRICS -> BetterLyricsProvider
            PreferredLyricsProvider.PORTATO -> PortatoLyricsProvider
            PreferredLyricsProvider.YOULY_PLUS -> YouLyPlusLyricsProvider
            PreferredLyricsProvider.LRCLIB -> LrcLibLyricsProvider
            PreferredLyricsProvider.KUGOU -> KuGouLyricsProvider
            PreferredLyricsProvider.SIMPMUSIC -> SimpMusicLyricsProvider
            PreferredLyricsProvider.UNISON -> UnisonLyricsProvider
        }

    suspend fun checkCandidate(
        provider: PreferredLyricsProvider,
        songId: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): CandidateState {
        val providerImpl = resolveProvider(provider)
        val result = runCatching {
            withTimeoutOrNull(7000L.milliseconds) {
                providerImpl.getLyrics(
                    id = songId,
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                ).getOrNull()
            }
        }.getOrNull()

        val validLyrics = result?.takeIf {
            it.isNotBlank() && it != LyricsEntity.LYRICS_NOT_FOUND
        }

        val syncKind = when {
            validLyrics == null -> LyricsSyncKind.NONE
            LrcParser.isTtml(validLyrics) -> {
                val parsed = runCatching { LrcParser.parseTtml(validLyrics) }.getOrNull().orEmpty()
                val hasText = parsed.any { !it.isInstrumental && it.text.isNotBlank() }
                when {
                    !hasText -> LyricsSyncKind.NONE
                    parsed.any { !it.words.isNullOrEmpty() } -> LyricsSyncKind.WORD
                    else -> LyricsSyncKind.LINE
                }
            }
            LrcParser.hasWordSyncedLyrics(validLyrics) -> {
                val parsed = runCatching { LrcParser.parseLyrics(validLyrics) }.getOrNull().orEmpty()
                if (parsed.any { !it.isInstrumental && it.text.isNotBlank() }) LyricsSyncKind.WORD else LyricsSyncKind.NONE
            }
            LrcParser.isLineSyncedLrc(validLyrics) -> {
                val parsed = runCatching { LrcParser.parseLyrics(validLyrics) }.getOrNull().orEmpty()
                if (parsed.any { !it.isInstrumental && it.text.isNotBlank() }) LyricsSyncKind.LINE else LyricsSyncKind.NONE
            }
            LrcParser.hasMeaningfulLyricsContent(validLyrics) -> LyricsSyncKind.PLAIN
            else -> LyricsSyncKind.NONE
        }

        return CandidateState(
            isChecking = false,
            lyrics = if (syncKind == LyricsSyncKind.NONE) null else validLyrics,
            syncKind = syncKind,
        )
    }
}
