/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.LyricsEntity
import javax.inject.Inject
import javax.inject.Singleton

interface LyricsRepository {
    suspend fun getLyricsById(id: String): LyricsEntity?
    suspend fun replaceLyrics(id: String, lyrics: String, source: String)
}

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val database: MusicDatabase,
) : LyricsRepository {
    override suspend fun getLyricsById(id: String): LyricsEntity? = withContext(Dispatchers.IO) {
        database.getLyricsById(id)
    }

    override suspend fun replaceLyrics(id: String, lyrics: String, source: String): Unit = withContext(Dispatchers.IO) {
        database.query {
            replaceLyrics(id, lyrics, source)
        }
    }
}
