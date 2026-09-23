/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.innertube.YouTube
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Response
import okhttp3.Route
import java.net.Authenticator as JavaAuthenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.util.concurrent.atomic.AtomicBoolean

object ProxyAuth {
    private val initialized = AtomicBoolean(false)

    fun init() {
        if (initialized.compareAndSet(false, true)) {
            JavaAuthenticator.setDefault(
                object : JavaAuthenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication? {
                        if (requestorType != RequestorType.PROXY && requestorType != RequestorType.SERVER) return null
                        val targetProxy = YouTube.proxy ?: return null
                        val address = targetProxy.address() as? InetSocketAddress ?: return null
                        val reqHost = requestingHost ?: requestingSite?.hostAddress ?: requestingSite?.hostName
                        val hostMatches =
                            reqHost != null && (
                                reqHost.equals(address.hostString, ignoreCase = true) ||
                                    reqHost.equals(address.hostName, ignoreCase = true) ||
                                    (address.address != null && reqHost.equals(address.address?.hostAddress, ignoreCase = true))
                            )
                        if (!hostMatches || requestingPort != address.port) {
                            return null
                        }
                        val username = YouTube.proxyUsername?.takeIf { it.isNotBlank() } ?: return null
                        val password = YouTube.proxyPassword?.takeIf { it.isNotBlank() } ?: return null
                        return PasswordAuthentication(username, password.toCharArray())
                    }
                },
            )
        }
    }

    val proxyAuthenticator: Authenticator =
        Authenticator { _, response ->
            if (response.request.header("Proxy-Authorization") != null) {
                return@Authenticator null
            }
            val username = YouTube.proxyUsername?.takeIf { it.isNotBlank() } ?: return@Authenticator null
            val password = YouTube.proxyPassword?.takeIf { it.isNotBlank() } ?: return@Authenticator null
            val credential = Credentials.basic(username, password)
            response.request
                .newBuilder()
                .header("Proxy-Authorization", credential)
                .build()
        }
}
