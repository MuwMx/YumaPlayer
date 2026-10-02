/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.about

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.GitHubContributorsJsonKey
import moe.rukamori.archivetune.utils.dataStore
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class AboutContributor(
    val login: String,
    val avatarUrl: String,
    val profileUrl: String,
)

@Immutable
data class AboutContributorCollection private constructor(
    private val values: List<AboutContributor>,
) {
    val isEmpty: Boolean get() = values.isEmpty()

    fun take(count: Int): AboutContributorCollection = AboutContributorCollection(values.take(count))

    fun forEach(action: (AboutContributor) -> Unit) {
        values.forEach(action)
    }

    companion object {
        val Empty = AboutContributorCollection(emptyList())

        fun from(values: List<AboutContributor>): AboutContributorCollection = AboutContributorCollection(values.toList())
    }
}

class FetchAboutContributorsUseCase
@Inject
constructor(
    private val repository: AboutContributorsRepository,
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): Result<AboutContributorCollection> =
        repository.contributors(forceRefresh)
}

@Singleton
class AboutContributorsRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    private val client =
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(10, TimeUnit.SECONDS)
                    readTimeout(10, TimeUnit.SECONDS)
                    writeTimeout(10, TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                }
            }
        }

    suspend fun contributors(forceRefresh: Boolean = false): Result<AboutContributorCollection> =
        withContext(Dispatchers.IO) {
            val preferences = context.dataStore.data.first()
            val cachedJson = preferences[GitHubContributorsJsonKey]
            val cachedContributors =
                cachedJson
                    ?.takeIf { it.isNotBlank() }
                    ?.let { parseContributorsJsonSafely(it) }
                    ?.takeIf { !it.isEmpty }

            if (!forceRefresh && cachedContributors != null) {
                return@withContext Result.success(cachedContributors)
            }

            val networkResult =
                try {
                    fetchRepoContributorsNetwork(owner = GitHubOwner, repo = GitHubRepo)
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    null
                }

            if (networkResult != null && networkResult.status.value in 200..299 && networkResult.body.isNotBlank()) {
                val contributors = parseContributorsJsonSafely(networkResult.body)
                if (!contributors.isEmpty) {
                    context.dataStore.edit { prefs ->
                        prefs[GitHubContributorsJsonKey] = networkResult.body
                    }
                    return@withContext Result.success(contributors)
                }
            }

            if (cachedContributors != null) {
                return@withContext Result.success(cachedContributors)
            }

            val errorMessage = when (networkResult?.status) {
                HttpStatusCode.Forbidden -> "GitHub API rate limit exceeded"
                null -> "Network connection error"
                else -> "Failed to load contributors: ${networkResult.status.value}"
            }
            Result.failure(IOException(errorMessage))
        }

    private suspend fun fetchRepoContributorsNetwork(
        owner: String,
        repo: String,
    ): ContributorsNetworkResult {
        val rawUrl = "https://raw.githubusercontent.com/$owner/$repo/main/.github/contributors.json"
        val response: HttpResponse = client.get(rawUrl)
        return ContributorsNetworkResult(
            status = response.status,
            body = response.bodyAsText(),
        )
    }

    private fun parseContributorsJsonSafely(
        json: String,
    ): AboutContributorCollection =
        try {
            val jsonArray = JSONArray(json)
            val contributors = ArrayList<AboutContributor>(minOf(jsonArray.length(), ContributorsLimit))
            for (index in 0 until jsonArray.length()) {
                if (contributors.size >= ContributorsLimit) break
                val item = jsonArray.getJSONObject(index)
                val login = item.optString("login", "")
                val type = item.optString("type", "")
                val avatarUrl = item.optString("avatar_url", "")
                val profileUrl = item.optString("html_url", "")
                val isBot = type.equals("Bot", ignoreCase = true) || login.lowercase().endsWith("[bot]")

                if (!isBot && login.isNotBlank() && avatarUrl.isNotBlank()) {
                    contributors.add(
                        AboutContributor(
                            login = login,
                            avatarUrl = avatarUrl,
                            profileUrl = profileUrl,
                        ),
                    )
                }
            }
            AboutContributorCollection.from(contributors)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            AboutContributorCollection.Empty
        }

    private data class ContributorsNetworkResult(
        val status: HttpStatusCode,
        val body: String,
    )

    private companion object {
        const val ContributorsLimit = 20
        const val GitHubOwner = "MuwMix"
        const val GitHubRepo = "YumaPlayer"
    }
}