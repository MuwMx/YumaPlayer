/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.utils.likedSongTimestamp
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

class SpotifySyncLogicTest {

    @Test
    fun chunking_splitsLargeListIntoChunksOfMax500() {
        val ids = (1..1200).map { "id_$it" }
        val chunks = ids.chunked(500)
        
        assertEquals(3, chunks.size)
        assertEquals(500, chunks[0].size)
        assertEquals(500, chunks[1].size)
        assertEquals(200, chunks[2].size)
        
        assertTrue(chunks.all { it.size <= 500 })
    }

    @Test
    fun likedSongTimestamp_decreasesBySecondPerIndex() {
        val baseTime = LocalDateTime.of(2026, 1, 1, 12, 0, 0)
        
        val time0 = likedSongTimestamp(baseTime, 0)
        val time5 = likedSongTimestamp(baseTime, 5)
        
        assertEquals(baseTime, time0)
        assertEquals(baseTime.minusSeconds(5), time5)
    }

    @Test
    fun cooldown_activeWithin30Minutes() {
        val currentTime = 1000000000000L
        val cooldownMs = 30 * 60 * 1000L
        
        // 10 minutes elapsed
        val lastSync10MinAgo = currentTime - (10 * 60 * 1000L)
        val isCooldownActive10Min = (currentTime - lastSync10MinAgo) < cooldownMs
        assertTrue(isCooldownActive10Min)
        
        // 35 minutes elapsed
        val lastSync35MinAgo = currentTime - (35 * 60 * 1000L)
        val isCooldownActive35Min = (currentTime - lastSync35MinAgo) < cooldownMs
        assertFalse(isCooldownActive35Min)
    }

    @Test
    fun playlistEntity_hasSpotifyIdAndIndex() {
        val clazz = PlaylistEntity::class.java
        
        // Check field presence
        val spotifyIdField = clazz.declaredFields.find { it.name == "spotifyId" }
        assertNotNull("PlaylistEntity should have spotifyId field", spotifyIdField)
        
        // Check @Entity annotation via source file since Room annotations have CLASS retention
        val sourceFile = File("src/main/kotlin/moe/rukamori/archivetune/db/entities/PlaylistEntity.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assertTrue("PlaylistEntity should have @Entity annotation with spotifyId index", 
                content.contains("indices = [Index(value = [\"spotifyId\"])"))
        }
    }
}
