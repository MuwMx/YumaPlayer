/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Spotui / ArchiveTune (2026) | Original work by © Spotui & Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.constants.SpotifyAccessTokenExpiresAtKey
import moe.rukamori.archivetune.constants.SpotifyAccessTokenKey
import moe.rukamori.archivetune.constants.SpotifySpDcKey
import moe.rukamori.archivetune.constants.SpotifySpKeyKey
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.spotify.SpotifyAuth
import moe.rukamori.archivetune.utils.dataStore
import timber.log.Timber

object SpotifyTokenManager {
    private const val TAG = "SpotifyTokenManager"
    private const val TOKEN_EXPIRY_GRACE_MS = 60_000L
    private val tokenMutex = Mutex()

    suspend fun ensureToken(context: Context): Boolean {
        val prefs = context.dataStore.data.first()
        val token = prefs[SpotifyAccessTokenKey].orEmpty()
        val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
        if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS) {
            Spotify.accessToken = token
            return true
        }
        return refreshToken(context)
    }

    suspend fun refreshToken(context: Context): Boolean =
        tokenMutex.withLock {
            val prefs = context.dataStore.data.first()
            val token = prefs[SpotifyAccessTokenKey].orEmpty()
            val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
            if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS) {
                Spotify.accessToken = token
                return true
            }

            val spDc = prefs[SpotifySpDcKey].orEmpty()
            if (spDc.isBlank()) return false
            val spKey = prefs[SpotifySpKeyKey].orEmpty()

            SpotifyAuth.fetchAccessToken(spDc = spDc, spKey = spKey)
                .mapCatching { internalToken ->
                    Spotify.accessToken = internalToken.accessToken
                    context.dataStore.edit { editPrefs ->
                        editPrefs[SpotifyAccessTokenKey] = internalToken.accessToken
                        editPrefs[SpotifyAccessTokenExpiresAtKey] = internalToken.accessTokenExpirationTimestampMs
                    }
                    true
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    Timber.tag(TAG).w(error, "Failed to refresh Spotify token in background sync")
                    false
                }
        }
}
