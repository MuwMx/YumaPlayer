package moe.rukamori.archivetune.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.IntentCompat
import moe.rukamori.archivetune.models.ParsedIntentAction
import moe.rukamori.archivetune.musicrecognition.ACTION_MUSIC_RECOGNITION
import moe.rukamori.archivetune.aod.ACTION_AOD_MODE
import moe.rukamori.archivetune.ui.screens.LOGIN_URL_ARGUMENT
import java.util.Locale

object IntentParser {
    fun parse(intent: Intent?, context: Context): ParsedIntentAction? {
        if (intent == null) return null

        fun isBackupUri(uri: Uri?): Boolean {
            if (uri == null) return false
            val path = uri.lastPathSegment?.lowercase(java.util.Locale.US)
            return path?.endsWith(".backup") == true || uri.toString().lowercase(java.util.Locale.US).endsWith(".backup")
        }

        val dataUri = intent.data
        if (isBackupUri(dataUri)) {
            return dataUri?.let { ParsedIntentAction.BackupRestore(it) }
        }

        if (intent.action == ACTION_MUSIC_RECOGNITION) {
            return ParsedIntentAction.MusicRecognition
        }

        if (intent.action == ACTION_AOD_MODE) {
            return ParsedIntentAction.AodMode
        }

        if (intent.action == "android.media.action.MEDIA_PLAY_FROM_SEARCH") {
            val query = (
                    intent.getStringExtra("query")
                        ?: intent.getStringExtra("android.intent.extra.TITLE")
                        ?: ""
                    ).trim()
            if (query.isNotBlank()) {
                return ParsedIntentAction.VoiceSearch(query)
            }
        }

        val externalAudioUris = getExternalAudioUris(intent, context)
        if (externalAudioUris.isNotEmpty()) {
            return ParsedIntentAction.ExternalAudio(externalAudioUris)
        }

        return parseDeepLink(intent)
    }

    private fun getExternalAudioUris(intent: Intent, context: Context): List<Uri> {
        val incomingUris = buildList {
            intent.data?.let(::add)
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(::add)
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    addAll(
                        IntentCompat.getParcelableArrayListExtra(
                            intent,
                            Intent.EXTRA_STREAM,
                            Uri::class.java
                        ).orEmpty()
                    )
                }
            }
        }.distinct()

        if (incomingUris.isEmpty()) return emptyList()

        val fallbackMimeType = intent.type
        val contentResolver = context.contentResolver
        return incomingUris.filter { uri ->
            val mimeType = contentResolver.getType(uri)
            mimeType.isAudioMimeType() || fallbackMimeType.isAudioMimeType() || uri.hasAudioExtension()
        }
    }

    private fun String?.isAudioMimeType(): Boolean = this?.startsWith("audio/", ignoreCase = true) == true

    private fun Uri.hasAudioExtension(): Boolean {
        val extension = MimeTypeMap.getFileExtensionFromUrl(toString()).orEmpty()
        val normalized = extension.lowercase(Locale.US)
        return normalized in setOf("aac", "flac", "m4a", "mp3", "ogg", "opus", "wav", "webm")
    }

    private fun parseDeepLink(intent: Intent): ParsedIntentAction? {
        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.let { Uri.parse(it) } ?: return null

        val authority = uri.authority?.lowercase()
        if (uri.scheme.equals("yuma", ignoreCase = true) && authority == "together") {
            return ParsedIntentAction.TogetherJoin(uri)
        }

        if (uri.scheme.equals("yuma", ignoreCase = true) && authority == "login") {
            return ParsedIntentAction.Login(uri.getQueryParameter(LOGIN_URL_ARGUMENT))
        }

        val path = uri.pathSegments.firstOrNull()
        return when (path) {
            "playlist" -> {
                val playlistId = uri.getQueryParameter("list") ?: return null
                ParsedIntentAction.YouTubePlaylist(playlistId)
            }
            "browse" -> {
                val browseId = uri.lastPathSegment ?: return null
                ParsedIntentAction.YouTubeAlbum(browseId)
            }
            "channel", "c" -> {
                val artistId = uri.lastPathSegment ?: return null
                ParsedIntentAction.YouTubeArtist(artistId)
            }
            else -> {
                val videoId = when {
                    path == "watch" -> uri.getQueryParameter("v")
                    uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                    else -> null
                }
                val playlistId = uri.getQueryParameter("list")
                val shouldShufflePlaylist = uri.requestsShuffledPlayback()

                if (videoId != null) {
                    ParsedIntentAction.YouTubeVideo(videoId, playlistId)
                } else if (path == "watch" && !playlistId.isNullOrBlank()) {
                    ParsedIntentAction.YouTubeWatchPlaylist(playlistId, shouldShufflePlaylist)
                } else {
                    null
                }
            }
        }
    }

    private fun Uri.requestsShuffledPlayback(): Boolean {
        val value = getQueryParameter("shuffle")?.trim()?.lowercase(Locale.US) ?: return false
        return value == "1" || value == "true"
    }

    fun resolveExternalAudioTitle(context: Context, uri: Uri): String {
        val displayName = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex >= 0 && cursor.moveToFirst()) cursor.getString(columnIndex) else null
            }
        }.getOrNull()
        return displayName?.trim()?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBefore('?') ?.trim()?.takeIf { it.isNotBlank() }
            ?: "Unknown"
    }
}