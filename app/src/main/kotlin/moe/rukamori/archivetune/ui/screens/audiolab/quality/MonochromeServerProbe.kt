/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab.quality

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal sealed interface ServerProbeState {
    data object Checking : ServerProbeState
    data class Online(val latencyMs: Long) : ServerProbeState
    data object Unreachable : ServerProbeState
}

@Composable
internal fun ServerStatusText(
    state: ServerProbeState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is ServerProbeState.Checking -> Text(
            text = stringResource(R.string.server_status_checking),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        is ServerProbeState.Online -> Text(
            text = stringResource(R.string.server_status_online, state.latencyMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )
        is ServerProbeState.Unreachable -> Text(
            text = stringResource(R.string.server_status_unreachable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
    }
}

internal object MonochromeServerProbe {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    suspend fun probeServer(url: String): ServerProbeState = withContext(Dispatchers.IO) {
        val targetUrl = if ("://" in url) url else "https://$url"
        runCatching {
            val start = SystemClock.elapsedRealtime()
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)
                .header("Range", "bytes=0-0")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val elapsed = SystemClock.elapsedRealtime() - start
                if (response.code in 200..499) {
                    ServerProbeState.Online(elapsed)
                } else {
                    ServerProbeState.Unreachable
                }
            }
        }.getOrElse {
            ServerProbeState.Unreachable
        }
    }

    suspend fun probeAll(urls: List<String>): Map<String, ServerProbeState> = coroutineScope {
        urls.map { url ->
            async { url to probeServer(url) }
        }.awaitAll().toMap()
    }
}

internal fun parseRelayServers(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
}

internal fun isValidRelayUrl(url: String): Boolean {
    if (url.contains(",")) return false
    val afterScheme = when {
        url.startsWith("https://") -> url.removePrefix("https://")
        url.startsWith("http://") -> url.removePrefix("http://")
        else -> return false
    }
    return afterScheme.isNotBlank() && !afterScheme.contains(" ")
}

internal fun normalizeProviderOrder(raw: String): List<String> =
    raw.split(",").map { it.trim() }.filter { it == "monochrome" || it == "qbdlx" }.distinct().let { p ->
        p + listOf("monochrome", "qbdlx").filterNot { it in p }
    }

internal fun resolveFolderPath(context: Context, downloadLocationUri: String): String? =
    downloadLocationUri.takeIf { it.isNotBlank() }?.let { raw ->
        runCatching {
            val uri = Uri.parse(raw)
            DocumentFile.fromTreeUri(context, uri)?.name?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment?.substringAfterLast(":")?.takeIf { it.isNotBlank() }
                ?: raw
        }.getOrNull() ?: raw
    }
