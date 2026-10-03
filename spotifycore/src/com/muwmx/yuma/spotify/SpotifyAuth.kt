/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.spotify.auth.SpotifyTotpGenerator
import moe.rukamori.archivetune.spotify.models.SpotifyInternalToken

object SpotifyAuth {
    private const val TOKEN_URL = "https://open.spotify.com/api/token"
    const val LOGIN_URL = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
        }

    suspend fun fetchAccessToken(
        spDc: String,
        spKey: String = "",
    ): Result<SpotifyInternalToken> =
        try {
            val totpResult = SpotifyTotpGenerator.generateTotpToken()

            val tokenUrl =
                buildString {
                    append(TOKEN_URL)
                    append("?reason=transport")
                    append("&productType=web-player")
                    append("&totp=${totpResult.code}")
                    append("&totpServer=${totpResult.code}")
                    append("&totpVer=${totpResult.version}")
                }

            val cookieHeader =
                buildString {
                    append("sp_dc=$spDc")
                    if (spKey.isNotEmpty()) {
                        append("; sp_key=$spKey")
                    }
                }

            val body =
                withContext(Dispatchers.IO) {
                    SpotifyTotpGenerator.httpGet(tokenUrl, mapOf("Cookie" to cookieHeader))
                }

            val token = json.decodeFromString<SpotifyInternalToken>(body)

            if (token.isAnonymous || token.accessToken.isBlank()) {
                throw Spotify.SpotifyException(
                    401,
                    "Received anonymous token — sp_dc cookie is invalid or expired",
                )
            }

            Result.success(token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}
