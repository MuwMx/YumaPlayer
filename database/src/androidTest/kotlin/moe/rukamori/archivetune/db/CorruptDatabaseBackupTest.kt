package moe.rukamori.archivetune.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CorruptDatabaseBackupTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `backupCorruptDatabaseFiles copies db wal and shm without deleting originals`() {
        val dir = tempFolder.newFolder("db")
        val db = File(dir, "song.db").apply { writeText("main-db-bytes") }
        val wal = File(dir, "song.db-wal").apply { writeText("wal-bytes") }
        val shm = File(dir, "song.db-shm").apply { writeText("shm-bytes") }

        assertTrue(backupCorruptDatabaseFiles(db))
        assertTrue(db.exists())
        assertTrue(wal.exists())
        assertTrue(shm.exists())

        val backups = dir.listFiles { f -> f.name.startsWith("song.db.corrupt.") && !f.name.contains(".tmp") }.orEmpty()
        assertEquals(3, backups.size)
        assertTrue(backups.none { it.name.contains(".tmp") })
    }

    @Test
    fun `backupCorruptDatabaseFiles returns false when db file is missing`() {
        val dir = tempFolder.newFolder("empty")
        val missing = File(dir, "song.db")
        assertFalse(backupCorruptDatabaseFiles(missing))
    }

    @Test
    fun `backupCorruptDatabaseFiles does not overwrite existing backups`() {
        val dir = tempFolder.newFolder("collision")
        val db = File(dir, "song.db").apply { writeText("v1") }

        assertTrue(backupCorruptDatabaseFiles(db))
        db.writeText("v2")
        assertTrue(backupCorruptDatabaseFiles(db))

        val mainBackups = dir.listFiles { f -> f.name.startsWith("song.db.corrupt.") && !f.name.endsWith("-wal") && !f.name.endsWith("-shm") }.orEmpty()
        assertEquals(2, mainBackups.size)
    }
}
