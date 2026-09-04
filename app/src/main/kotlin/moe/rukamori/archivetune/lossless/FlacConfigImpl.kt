package moe.rukamori.archivetune.lossless

import android.content.Context
import moe.rukamori.archivetune.constants.EnableLosslessKey
import moe.rukamori.archivetune.constants.QobuzAppIdKey
import moe.rukamori.archivetune.constants.QobuzAppSecretKey
import moe.rukamori.archivetune.constants.QobuzUserAuthTokenKey
import moe.rukamori.archivetune.flaccore.FlacConfig
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.getAsync

class FlacConfigImpl(private val context: Context) : FlacConfig {
    override suspend fun qbdlxEnabled(): Boolean {
        return context.dataStore.getAsync(EnableLosslessKey, true)
    }

    override suspend fun qbdlxAppId(): String {
        return context.dataStore.getAsync(QobuzAppIdKey, "").takeIf { it.isNotEmpty() } ?: LosslessTokens.QOBUZ_APP_ID
    }

    override suspend fun qbdlxAppSecret(): String {
        return context.dataStore.getAsync(QobuzAppSecretKey, "").takeIf { it.isNotEmpty() } ?: LosslessTokens.QOBUZ_APP_SECRET
    }

    override suspend fun qbdlxTokenPool(): String {
        return context.dataStore.getAsync(QobuzUserAuthTokenKey, "").takeIf { it.isNotEmpty() } ?: LosslessTokens.QOBUZ_USER_AUTH_TOKEN
    }
}
