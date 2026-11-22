/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.lossless

import android.content.Context
import moe.rukamori.archivetune.constants.EnableMonochromeKey
import moe.rukamori.archivetune.constants.LosslessProviderOrderKey
import moe.rukamori.archivetune.constants.MonochromeServersKey
import moe.rukamori.archivetune.flaccore.monochrome.MonochromeConfig
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.getAsync

class MonochromeConfigImpl(private val context: Context) : MonochromeConfig {
    override suspend fun monochromeEnabled(): Boolean {
        return context.dataStore.getAsync(EnableMonochromeKey, true)
    }

    override suspend fun customServers(): List<String> {
        val raw = context.dataStore.getAsync(MonochromeServersKey, "")
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    override suspend fun providerOrder(): List<String> {
        val raw = context.dataStore.getAsync(LosslessProviderOrderKey, "monochrome,qbdlx")
        if (raw.isBlank()) return listOf("monochrome", "qbdlx")
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
}
