package moe.rukamori.archivetune.models

import android.net.Uri

sealed interface ParsedIntentAction {
    data class BackupRestore(val uri: Uri) : ParsedIntentAction
    data object MusicRecognition : ParsedIntentAction
    data object AodMode : ParsedIntentAction
    data class VoiceSearch(val query: String) : ParsedIntentAction
    data class TogetherJoin(val uri: Uri) : ParsedIntentAction
    data class Login(val loginUrl: String?) : ParsedIntentAction
    data class ExternalAudio(val uris: List<Uri>) : ParsedIntentAction

    // YouTube Диплинки
    data class YouTubePlaylist(val playlistId: String) : ParsedIntentAction
    data class YouTubeAlbum(val browseId: String) : ParsedIntentAction
    data class YouTubeArtist(val artistId: String) : ParsedIntentAction
    data class YouTubeVideo(val videoId: String, val playlistId: String?) : ParsedIntentAction
    data class YouTubeWatchPlaylist(val playlistId: String, val shuffle: Boolean) : ParsedIntentAction
}