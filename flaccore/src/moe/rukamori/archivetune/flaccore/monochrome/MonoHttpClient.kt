/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.flaccore.monochrome

data class MonoHttpResponse(
    val code: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
    val bytes: ByteArray? = null,
) {
    val ok: Boolean get() = code in 200..299

    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

fun interface MonoHttpClient {
    suspend fun get(url: String, headers: Map<String, String>): MonoHttpResponse

    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray? =
        get(url, headers).takeIf { it.ok }?.bytes
}
