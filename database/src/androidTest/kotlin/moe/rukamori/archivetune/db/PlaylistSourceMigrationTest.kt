package moe.rukamori.archivetune.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistSourceMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), InternalDatabase::class.java)

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: MusicDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(InternalDatabase.DB_NAME)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(InternalDatabase.DB_NAME)
    }

    private fun createVersion37Database() {
        helper.createDatabase(InternalDatabase.DB_NAME, 37).apply {
            listOf("s1", "s2", "s3").forEach { id ->
                execSQL(
                    "INSERT INTO song (id, title, duration, liked, totalPlayTime) VALUES (?, ?, 180, 0, 0)",
                    arrayOf(id, "Song $id"),
                )
            }
            execSQL(
                "INSERT INTO playlist (id, name, browseId, isEditable, isLocal) VALUES ('local', 'Mine', 'PL1', 1, 0)",
            )
            execSQL(
                "INSERT INTO playlist (id, name, spotifyId, isEditable, isLocal) VALUES ('mirror', 'Mirror', 'sp1', 0, 0)",
            )
            execSQL(
                "INSERT INTO playlist_song_map (playlistId, songId, position, setVideoId) VALUES " +
                    "('local', 's2', 0, 'set-a'), ('local', 's1', 1, 'set-b'), ('mirror', 's3', 0, NULL)",
            )
            close()
        }
    }

    private fun openMigratedDatabase(): MusicDatabase = InternalDatabase.newInstance(context).also { database = it }

    @Test
    fun playlistsAndTheirSongsSurviveTheMigrationToVersion38() =
        runBlocking {
            createVersion37Database()

            val db = openMigratedDatabase()

            val local = db.getPlaylistById("local")
            assertNotNull(local)
            assertEquals("Mine", local!!.playlist.name)
            assertEquals("PL1", local.playlist.browseId)
            assertEquals(2, local.songCount)
            assertNull(local.playlist.spotifySourceId)
            assertEquals("sp1", db.getPlaylistById("mirror")!!.playlist.spotifyId)

            val entries = db.playlistSongMaps("local", 0)
            assertEquals(listOf("s2", "s1"), entries.map { it.songId })
            assertEquals(listOf("set-a", "set-b"), entries.map { it.setVideoId })
            assertEquals(listOf("s3"), db.playlistSongMaps("mirror", 0).map { it.songId })
        }

    @Test
    fun aMigratedPlaylistCanBeLinkedToItsSpotifySource() =
        runBlocking {
            createVersion37Database()

            val db = openMigratedDatabase()
            val local = db.getPlaylistById("local")!!
            db.update(local.playlist.copy(spotifySourceId = "sp1"))

            assertEquals("local", db.playlistBySpotifySourceId("sp1")?.playlist?.id)
            assertNull(db.playlistBySpotifySourceId("unknown"))
        }
}
