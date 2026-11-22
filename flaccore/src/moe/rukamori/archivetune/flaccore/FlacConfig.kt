package moe.rukamori.archivetune.flaccore

interface FlacConfig {
    suspend fun qbdlxEnabled(): Boolean
    suspend fun qbdlxAppId(): String
    suspend fun qbdlxAppSecret(): String
    suspend fun qbdlxTokenPool(): String
    suspend fun qobuzEnabled(): Boolean = true
}

interface FlacKvStore {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String?)
}
