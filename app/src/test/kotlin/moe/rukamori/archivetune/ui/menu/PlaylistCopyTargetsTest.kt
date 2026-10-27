package moe.rukamori.archivetune.ui.menu

import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.playlistcopy.PlaylistCopySource
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistCopyTargetsTest {
    private fun playlist(
        id: String,
        browseId: String? = null,
        spotifyId: String? = null,
        isEditable: Boolean = true,
    ) = Playlist(
        playlist = PlaylistEntity(id = id, name = id, browseId = browseId, spotifyId = spotifyId, isEditable = isEditable),
        songCount = 0,
        songThumbnails = emptyList(),
    )

    private val personal = playlist("personal")
    private val youTube = playlist("youtube", browseId = "PL1")
    private val spotifyMirror = playlist("mirror", spotifyId = "sp", isEditable = false)
    private val readOnly = playlist("read-only", isEditable = false)
    private val readOnlyYouTube = playlist("read-only-youtube", browseId = "PL2", isEditable = false)
    private val all = listOf(personal, youTube, spotifyMirror, readOnly, readOnlyYouTube)
    private val otherSource = PlaylistCopySource.Personal("elsewhere", "Elsewhere")

    @Test
    fun signedInUsersCanCopyIntoPersonalAndYouTubePlaylists() {
        assertEquals(setOf("personal", "youtube"), copyTargets(all, isLoggedIn = true, otherSource).map { it.id }.toSet())
    }

    @Test
    fun youTubePlaylistsAreNotOfferedWhileSignedOut() {
        assertEquals(listOf("personal"), copyTargets(all, isLoggedIn = false, otherSource).map { it.id })
    }

    @Test
    fun spotifyMirrorsAndReadOnlyPlaylistsAreNeverTargets() {
        val ids = copyTargets(all, isLoggedIn = true, otherSource).map { it.id }

        assertEquals(false, "mirror" in ids)
        assertEquals(false, "read-only" in ids)
        assertEquals(false, "read-only-youtube" in ids)
    }

    @Test
    fun libraryPlaylistsMapToTheSourceTheirSongsComeFrom() {
        assertEquals(PlaylistCopySource.Personal("personal", "personal", null), personal.playlist.toCopySource())
        assertEquals(PlaylistCopySource.YouTube("PL1", "youtube", null), youTube.playlist.toCopySource())
        assertEquals(PlaylistCopySource.Spotify("sp", "mirror", null), spotifyMirror.playlist.toCopySource())
    }

    @Test
    fun aPlaylistIsNotOfferedAsATargetForItself() {
        val fromPersonal = copyTargets(all, isLoggedIn = true, personal.playlist.toCopySource()).map { it.id }
        val fromYouTube = copyTargets(all, isLoggedIn = true, youTube.playlist.toCopySource()).map { it.id }

        assertEquals(listOf("youtube"), fromPersonal)
        assertEquals(listOf("personal"), fromYouTube)
    }
}
