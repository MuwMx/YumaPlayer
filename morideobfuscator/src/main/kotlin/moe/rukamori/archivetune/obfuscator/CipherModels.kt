/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.obfuscator

import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.net.Proxy

const val MORI_CIPHER_REFRESH_INTERVAL_MILLIS: Long = 6L * 60L * 60L * 1000L

data class MoriCipherConfig(
    val cacheDirectory: File,
    val proxyProvider: () -> Proxy? = { null },
    val refreshIntervalMillis: Long = MORI_CIPHER_REFRESH_INTERVAL_MILLIS,
)

enum class CipherRuntimeStatus {
    UNINITIALIZED,
    READY,
    REFRESHING,
    DEGRADED,
}

data class CipherSnapshot(
    val status: CipherRuntimeStatus = CipherRuntimeStatus.UNINITIALIZED,
    val playerId: String? = null,
    val lastSuccessfulRefreshMillis: Long? = null,
    val nextRefreshAtMillis: Long? = null,
    val lastFailure: String? = null,
    val refreshProgressPercent: Int? = null,
)

data class CipherRefreshResult(
    val playerId: String,
    val refreshedAtMillis: Long,
    val changed: Boolean,
)

interface MoriCipherResolver {
    val snapshot: StateFlow<CipherSnapshot>

    suspend fun refresh(
        force: Boolean = false,
        videoId: String? = null,
    ): Result<CipherRefreshResult>

    suspend fun signatureTimestamp(videoId: String): Result<Int?>

    suspend fun resolveStreamUrl(
        videoId: String,
        signatureCipher: String,
    ): Result<String>

    suspend fun transformNParameter(
        videoId: String,
        url: String,
    ): Result<String>
}

open class MoriCipherException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

internal class MoriCipherCapabilityException(
    message: String,
) : MoriCipherException(message)
