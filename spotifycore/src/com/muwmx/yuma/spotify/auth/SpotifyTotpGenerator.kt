/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.spotify.Spotify
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor

object SpotifyTotpGenerator {
    private const val SERVER_TIME_URL = "https://open.spotify.com/api/server-time"
    private const val NUANCE_URL =
        "https://gist.githubusercontent.com/sonic-liberation/22ed9c6ba463899e933427f7de1f0eef/raw/nuances.json"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val NUANCE_CACHE_TTL_NANOS = 5L * 60L * 1_000_000_000L

    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
        }

    @Serializable
    private data class Nuance(
        val s: String,
        val v: Int,
    )

    private data class CachedNuance(val nuance: Nuance, val timestampNanos: Long)

    private var cachedNuance: CachedNuance? = null
    private val nuanceMutex = Mutex()

    @Serializable
    private data class ServerTimeResponse(
        val serverTime: Long,
    )

    data class TotpResult(
        val code: String,
        val version: Int,
    )

    suspend fun generateTotpToken(): TotpResult {
        val nuance = fetchNuance()
        val serverTimeSec = fetchServerTime()
        val totp = generateTotp(nuance.s, serverTimeSec)
        return TotpResult(code = totp, version = nuance.v)
    }

    private suspend fun fetchNuance(): Nuance =
        nuanceMutex.withLock {
            val now = System.nanoTime()
            val cached = cachedNuance
            if (cached != null && now - cached.timestampNanos < NUANCE_CACHE_TTL_NANOS) {
                return cached.nuance
            }

            val body =
                withContext(Dispatchers.IO) {
                    try {
                        httpGet(NUANCE_URL, emptyMap())
                    } catch (e: Exception) {
                        if (cached != null) {
                            return@withContext null
                        }
                        throw Spotify.SpotifyException(
                            503,
                            "Failed to fetch TOTP secret from gist: ${e.message}",
                        )
                    }
                }

            if (body == null) {
                return cached!!.nuance
            }

            val nuances = json.decodeFromString<List<Nuance>>(body)
            val validNuance =
                nuances
                    .filter { it.v > 0 && it.s.isValidBase32Secret() }
                    .maxByOrNull { it.v }
                    ?: throw Spotify.SpotifyException(500, "No valid nuance data found in gist")

            cachedNuance = CachedNuance(validNuance, now)
            validNuance
        }

    private suspend fun fetchServerTime(): Long =
        withContext(Dispatchers.IO) {
            val body =
                try {
                    httpGet(SERVER_TIME_URL, emptyMap())
                } catch (e: Exception) {
                    throw Spotify.SpotifyException(
                        503,
                        "Failed to fetch Spotify server time: ${e.message}",
                    )
                }
            val response = json.decodeFromString<ServerTimeResponse>(body)
            response.serverTime
        }

    private fun generateTotp(
        secret: String,
        serverTimeSec: Long,
    ): String {
        val key = base32Decode(secret)
        val interval = 30L
        val timeStep = floor(serverTimeSec.toDouble() / interval).toLong()

        val timeBytes = ByteArray(8)
        var value = timeStep
        for (i in 7 downTo 0) {
            timeBytes[i] = (value and 0xFF).toByte()
            value = value shr 8
        }

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(timeBytes)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val code =
            ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val otp = code % 1_000_000
        return otp.toString().padStart(6, '0')
    }

    private fun String.isValidBase32Secret(): Boolean = matches(Regex("^[A-Z2-7]+=*$"))

    private fun base32Decode(input: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleaned = input.uppercase().replace("=", "")

        val output = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0

        for (c in cleaned) {
            val value = alphabet.indexOf(c)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }

        return output.toByteArray()
    }

    internal fun httpGet(
        urlString: String,
        extraHeaders: Map<String, String>,
    ): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            connection.setRequestProperty("Accept-Language", "en")
            for ((key, value) in extraHeaders) {
                connection.setRequestProperty(key, value)
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw Spotify.SpotifyException(
                    responseCode,
                    "HTTP $responseCode: $errorBody",
                )
            }

            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
