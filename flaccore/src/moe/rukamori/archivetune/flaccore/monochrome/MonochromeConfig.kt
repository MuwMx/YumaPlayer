/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.flaccore.monochrome

interface MonochromeConfig {
    suspend fun monochromeEnabled(): Boolean
    suspend fun customServers(): List<String>
    suspend fun providerOrder(): List<String>
}
