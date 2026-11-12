/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.account

import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.innertube.models.body.AccountMenuBody
import moe.rukamori.archivetune.innertube.models.body.AccountsListBody
import moe.rukamori.archivetune.innertube.network.InnerTubeHttp
import moe.rukamori.archivetune.innertube.network.PlaybackAuthState

internal class InnerTubeAccount(private val http: InnerTubeHttp) {
    suspend fun accountMenu(
        client: YouTubeClient,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        http.client.post("account/account_menu") {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            setBody(AccountMenuBody(client.toContext(locale, authState.visitorData, authState.dataSyncId)))
        }
    }

    suspend fun accountChannels(
        client: YouTubeClient,
        authState: PlaybackAuthState,
        locale: YouTubeLocale,
    ): HttpResponse = http.withRetry {
        val delegatedId = authState.delegatedSessionId
        val requestUrl = client.requestApiUrl("account/accounts_list")
        http.client.post(requestUrl) {
            with(http) {
                ytClient(client, setLogin = true, authState = authState)
            }
            headers {
                append("X-Goog-AuthUser", "0")
                if (!delegatedId.isNullOrBlank()) {
                    append("X-Goog-PageId", delegatedId)
                }
            }
            setBody(AccountsListBody(client.toContext(locale, authState.visitorData, authState.dataSyncId)))
        }
    }
}
