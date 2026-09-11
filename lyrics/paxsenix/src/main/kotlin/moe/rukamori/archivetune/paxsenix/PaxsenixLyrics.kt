/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package moe.rukamori.archivetune.paxsenix

import io.ktor.client.HttpClient
import moe.rukamori.archivetune.paxsenix.models.PaxsenixStats

object PaxsenixLyrics {
    var userAgent: String
        get() = PaxsenixApi.userAgent
        private set(value) {
            PaxsenixApi.userAgent = value
        }

    fun setUserAgent(
        appName: String,
        versionName: String,
    ) {
        PaxsenixApi.setUserAgent(appName, versionName)
    }

    fun setApiKey(apiKey: String) {
        PaxsenixApi.setApiKey(apiKey)
    }

    fun setClient(client: HttpClient) {
        PaxsenixApi.setClient(client)
    }

    suspend fun getAppleMusicLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> = PaxsenixAppleMusicBackend.getLyrics(title, artist, durationSeconds)

    suspend fun getSpotifyLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> = PaxsenixSpotifyBackend.getLyrics(title, artist, durationSeconds)

    suspend fun getMusixmatchLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> = PaxsenixMusixmatchBackend.getLyrics(title, artist, durationSeconds)

    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSeconds: Int,
    ): Result<String> = PaxsenixDefaultBackend.getLyrics(title, artist, durationSeconds)

    suspend fun getAllLyrics(
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        getLyrics(title, artist, duration).onSuccess(callback)
    }

    suspend fun getStats(): Result<PaxsenixStats> = PaxsenixStatsBackend.getStats()
}
