package moe.rukamori.archivetune.playback

import moe.rukamori.archivetune.innertube.PlaybackAuthState
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal fun PlaybackAuthState.resolveExtractorPoToken(): String? =
    resolveExtractorGvsToken()
        ?: poTokenPlayer.normalizeExtractorRequestValue()

internal fun PlaybackAuthState.resolveExtractorGvsToken(): String? =
    resolveGvsPoToken().normalizeExtractorRequestValue()
        ?: poTokenGvs.normalizeExtractorRequestValue()
        ?: poToken.normalizeExtractorRequestValue()

internal fun PlaybackAuthState.resolveExtractorCookies(): String? = cookie.normalizeExtractorRequestValue()

internal fun String?.normalizeExtractorRequestValue(): String? {
    val trimmed = this?.trim()
    return trimmed?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

internal fun String.toYouTubeWatchUrl(): String = "https://music.youtube.com/watch?v=$this"

internal fun Throwable.isNetworkConnectionFailure(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is ConnectException || current is UnknownHostException) return true
        current = current.cause
    }
    return false
}

internal fun Throwable.isRequestTimeout(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is SocketTimeoutException) return true
        if (current.message?.contains("Request timeout has expired", ignoreCase = true) == true) return true
        current = current.cause
    }
    return false
}
