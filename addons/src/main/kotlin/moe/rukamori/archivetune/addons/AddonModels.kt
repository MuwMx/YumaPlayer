package moe.rukamori.archivetune.addons

import kotlinx.serialization.Serializable

enum class AddonFormat {
    BITCHORD_INDEX,
    ARCHIVETUNE_ARCH,
}

@Serializable
data class AddonDescriptor(
    val id: String,
    val name: String,
    val author: String = "",
    val version: String = "",
    val code: Int = 0,
    val type: String = "MODULE",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val downloadUrl: String = "",
    val icon: String? = null,
    val lastUpdated: String = "",
    val format: AddonFormat = AddonFormat.BITCHORD_INDEX,
)

@Serializable
data class AddonRepository(
    val id: String,
    val name: String,
    val url: String,
    val format: AddonFormat = AddonFormat.BITCHORD_INDEX,
    val enabled: Boolean = true,
    val lastSyncedAtMs: Long = 0L,
)

@Serializable
data class AddonTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
)

@Serializable
data class AddonStream(
    val url: String,
    val expiresAtMs: Long = 0L,
    val codec: String? = null,
    val bitsPerSample: Int? = null,
    val sampleRateHz: Int? = null,
    val bitrateKbps: Int? = null,
    val coverArtUrl: String? = null,
    val origin: String = "",
)

@Serializable
data class AddonCheckResult(
    val ok: Boolean,
    val latencyMs: Long,
    val tracksFound: Int,
    val message: String,
)

interface AddonConfigStore {
    suspend fun getRepositories(): List<AddonRepository>
    suspend fun saveRepositories(repositories: List<AddonRepository>)
}
