/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import androidx.datastore.preferences.core.edit
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import moe.rukamori.archivetune.App
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.constants.GitHubReleasesEtagKey
import moe.rukamori.archivetune.constants.GitHubReleasesFingerprintKey
import moe.rukamori.archivetune.constants.GitHubReleasesJsonKey
import moe.rukamori.archivetune.constants.GitHubReleasesLastCheckedAtKey
import org.json.JSONArray
import org.json.JSONObject

data class GitCommit(
    val sha: String,
    val message: String,
    val author: String,
    val date: String,
    val url: String,
    val authorAvatarUrl: String? = null,
)

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String?,
    val publishedAt: String,
    val htmlUrl: String,
    val prerelease: Boolean = false,
    val imageUrl: String? = null,
    val downloadUrl: String? = null,
)

private data class ReleasesNetworkResult(
    val status: HttpStatusCode,
    val body: String?,
    val etag: String?,
)

object Updater {
    private val client = HttpClient()
    private const val ReleaseCacheCheckIntervalMs: Long = 6 * 60 * 60 * 1000L
    private const val StableReleaseBaseUrl = "https://github.com/MuwMx/YumaPlayer/releases"
    private const val CanaryReleaseBaseUrl = "https://github.com/MuwMx/YumaCanary/releases"
    var lastCheckTime = -1L
        private set
    private var latestReleaseTag: String? = null
    private var latestReleaseDownloadUrl: String? = null
    private var latestCanaryReleaseTag: String? = null

    private val isUpdaterDistribution: Boolean
        get() =
            BuildConfig.UPDATER_AVAILABLE &&
                when (BuildConfig.DISTRIBUTION) {
                    "gms", "foss" -> true
                    else -> false
                }

    private val canDownloadUpdatesDirectly: Boolean
        get() = BuildConfig.DISTRIBUTION == "gms"

    private val artifactPrefix: String
        get() =
            when (BuildConfig.DISTRIBUTION) {
                "gms" -> "gms-"
                "foss" -> "foss-"
                else -> ""
            }

    private fun releaseArtifactName(isCanary: Boolean = false): String {
        val suffix = if (isCanary) "canary.apk" else "release.apk"
        return "app-$artifactPrefix${BuildConfig.DEVICE}-${BuildConfig.ARCHITECTURE}-$suffix"
    }

    private data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: List<PreReleaseIdentifier>,
    ) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int {
            val majorCompare = major.compareTo(other.major)
            if (majorCompare != 0) return majorCompare
            val minorCompare = minor.compareTo(other.minor)
            if (minorCompare != 0) return minorCompare
            val patchCompare = patch.compareTo(other.patch)
            if (patchCompare != 0) return patchCompare

            val thisIsStable = preRelease.isEmpty()
            val otherIsStable = other.preRelease.isEmpty()
            if (thisIsStable && !otherIsStable) return 1
            if (!thisIsStable && otherIsStable) return -1

            val maxIndex = minOf(preRelease.size, other.preRelease.size)
            for (i in 0 until maxIndex) {
                val c = preRelease[i].compareTo(other.preRelease[i])
                if (c != 0) return c
            }
            return preRelease.size.compareTo(other.preRelease.size)
        }

        fun normalizedName(): String =
            if (preRelease.isEmpty()) {
                "$major.$minor.$patch"
            } else {
                "$major.$minor.$patch-" + preRelease.joinToString(".") { it.raw }
            }
    }

    private sealed interface PreReleaseIdentifier : Comparable<PreReleaseIdentifier> {
        val raw: String
    }

    private data class NumericIdentifier(
        override val raw: String,
        val value: Long,
    ) : PreReleaseIdentifier {
        override fun compareTo(other: PreReleaseIdentifier): Int =
            when (other) {
                is NumericIdentifier -> value.compareTo(other.value)
                is AlphaIdentifier -> -1
            }
    }

    private data class AlphaIdentifier(
        override val raw: String,
    ) : PreReleaseIdentifier {
        override fun compareTo(other: PreReleaseIdentifier): Int =
            when (other) {
                is NumericIdentifier -> 1
                is AlphaIdentifier -> raw.compareTo(other.raw)
            }
    }

    private val semVerRegex =
        Regex("""(?i)\bv?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?\b""")

    private fun parseSemVerOrNull(text: String): SemVer? {
        val match = semVerRegex.find(text) ?: return null
        val major = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val minor = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        val patch = match.groupValues.getOrNull(3)?.toIntOrNull() ?: return null
        val preReleaseText = match.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }
        val preRelease =
            preReleaseText
                ?.split('.')
                ?.filter { it.isNotBlank() }
                ?.map { identifier ->
                    if (identifier.all { it.isDigit() }) {
                        NumericIdentifier(raw = identifier, value = identifier.toLong())
                    } else {
                        AlphaIdentifier(raw = identifier)
                    }
                }
                ?: emptyList()
        return SemVer(
            major = major,
            minor = minor,
            patch = patch,
            preRelease = preRelease,
        )
    }

    private fun parseReleaseSemVerOrNull(release: ReleaseInfo): SemVer? =
        parseSemVerOrNull(release.tagName) ?: parseSemVerOrNull(release.name)

    internal fun isSameVersion(
        a: String,
        b: String,
    ): Boolean {
        val aSemVer = parseSemVerOrNull(a)
        val bSemVer = parseSemVerOrNull(b)
        return if (aSemVer != null && bSemVer != null) {
            aSemVer.major == bSemVer.major &&
                aSemVer.minor == bSemVer.minor &&
                aSemVer.patch == bSemVer.patch &&
                aSemVer.preRelease == bSemVer.preRelease
        } else {
            a.trim() == b.trim()
        }
    }

    internal fun isUpdateAvailable(
        latestVersion: String,
        currentVersion: String,
    ): Boolean {
        val latestSemVer = parseSemVerOrNull(latestVersion)
        val currentSemVer = parseSemVerOrNull(currentVersion)
        return if (latestSemVer != null && currentSemVer != null) {
            latestSemVer > currentSemVer
        } else {
            !isSameVersion(latestVersion, currentVersion)
        }
    }

    internal fun findLatestRelease(releases: List<ReleaseInfo>): ReleaseInfo? {
        if (releases.isEmpty()) return null
        val stableReleases = releases.filter { !it.prerelease }
        val target = stableReleases.ifEmpty { return null }
        val parsed =
            target.mapNotNull { release ->
                parseReleaseSemVerOrNull(release)?.let { version -> version to release }
            }

        if (parsed.isEmpty()) return target.firstOrNull()

        val stable = parsed.filter { it.first.preRelease.isEmpty() }
        val candidates = stable.ifEmpty { parsed }
        return candidates.maxWithOrNull(compareBy({ it.first }, { it.second.publishedAt }))?.second
    }

    internal fun findLatestCanaryRelease(releases: List<ReleaseInfo>): ReleaseInfo? {
        if (releases.isEmpty()) return null
        val parsed =
            releases.mapNotNull { release ->
                parseReleaseSemVerOrNull(release)?.let { version -> version to release }
            }
        if (parsed.isEmpty()) return releases.firstOrNull { it.prerelease } ?: releases.firstOrNull()
        val canary = parsed.filter { it.second.prerelease }
        val candidates = canary.ifEmpty { parsed }
        return candidates.maxWithOrNull(compareBy({ it.first }, { it.second.publishedAt }))?.second
    }

    private fun preferredReleaseVersionNameOrNull(release: ReleaseInfo): String? = parseReleaseSemVerOrNull(release)?.normalizedName()

    private val markdownImageRegex = Regex("""!\[.*?]\((https?://[^)]+)\)""")
    private val directImageUrlRegex = Regex("""https?://\S+\.(gif|png|jpg|jpeg|webp)(\?\S*)?""", RegexOption.IGNORE_CASE)

    internal fun parseImageUrlOrNull(body: String?): String? {
        if (body.isNullOrBlank()) return null
        markdownImageRegex.find(body)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        directImageUrlRegex.find(body)?.value?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        return null
    }

    private fun parseReleasesJson(
        json: String,
        expectedArtifactName: String = releaseArtifactName(),
    ): List<ReleaseInfo> {
        val jsonArray = JSONArray(json)
        val releases = ArrayList<ReleaseInfo>(jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val assets = item.optJSONArray("assets")
            val assetDownloadUrl =
                assets?.let { releaseAssets ->
                    (0 until releaseAssets.length())
                        .asSequence()
                        .mapNotNull(releaseAssets::optJSONObject)
                        .firstOrNull { asset -> asset.optString("name") == expectedArtifactName }
                        ?.optString("browser_download_url")
                        ?.takeIf { it.isNotBlank() }
                }
            val body = if (item.has("body") && !item.isNull("body")) item.optString("body") else null
            val downloadUrl =
                if (item.isNull("download_url")) {
                    null
                } else {
                    item.optString("download_url").takeIf { it.isNotBlank() }
                } ?: assetDownloadUrl
            val imageUrl =
                if (item.has("image_url") && !item.isNull("image_url")) {
                    item.optString("image_url").takeIf { it.isNotBlank() }
                } else {
                    parseImageUrlOrNull(body)
                }
            releases.add(
                ReleaseInfo(
                    tagName = item.optString("tag_name", ""),
                    name = item.optString("name", ""),
                    body = body,
                    publishedAt = item.optString("published_at", ""),
                    htmlUrl = item.optString("html_url", ""),
                    prerelease = item.optBoolean("prerelease", false),
                    imageUrl = imageUrl,
                    downloadUrl = downloadUrl,
                ),
            )
        }
        return releases
    }

    private fun encodeReleasesJson(releases: List<ReleaseInfo>): String =
        JSONArray().apply {
            releases.forEach { release ->
                put(
                    JSONObject().apply {
                        put("tag_name", release.tagName)
                        put("name", release.name)
                        put("body", release.body ?: JSONObject.NULL)
                        put("published_at", release.publishedAt)
                        put("html_url", release.htmlUrl)
                        put("prerelease", release.prerelease)
                        put("image_url", release.imageUrl ?: JSONObject.NULL)
                        put("download_url", release.downloadUrl ?: JSONObject.NULL)
                    },
                )
            }
        }.toString()

    private fun getTopReleaseFingerprint(releases: List<ReleaseInfo>): String {
        val latest = findLatestRelease(releases) ?: findLatestCanaryRelease(releases) ?: return ""
        return listOf(
            latest.tagName,
            latest.name,
            latest.publishedAt,
            latest.body.orEmpty(),
            latest.htmlUrl,
            latest.prerelease.toString(),
            latest.imageUrl.orEmpty(),
            latest.downloadUrl.orEmpty(),
        ).joinToString("||")
    }

    private suspend fun fetchReleasesNetwork(
        perPage: Int,
        cachedEtag: String?,
    ): ReleasesNetworkResult {
        val response: HttpResponse =
            client.get("https://api.github.com/repos/MuwMx/YumaPlayer/releases?per_page=$perPage") {
                headers {
                    append("Accept", "application/vnd.github+json")
                    append("User-Agent", "YumaPlayerApp")
                    if (!cachedEtag.isNullOrBlank()) {
                        append("If-None-Match", cachedEtag)
                    }
                }
            }
        val etag = response.headers["ETag"]
        return when (response.status) {
            HttpStatusCode.NotModified -> {
                ReleasesNetworkResult(
                    status = response.status,
                    body = null,
                    etag = cachedEtag ?: etag,
                )
            }

            else -> {
                ReleasesNetworkResult(
                    status = response.status,
                    body = response.bodyAsText(),
                    etag = etag,
                )
            }
        }
    }

    suspend fun getCachedReleases(): List<ReleaseInfo> {
        if (!isUpdaterDistribution) {
            return emptyList()
        }

        val cachedJson = App.instance.dataStore.getAsync(GitHubReleasesJsonKey)
        return cachedJson
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { parseReleasesJson(it) }.getOrNull() }
            ?: emptyList()
    }

    fun getReleaseVersionName(release: ReleaseInfo): String =
        preferredReleaseVersionNameOrNull(release) ?: release.name.ifBlank { release.tagName }

    fun getCanaryReleaseVersionName(release: ReleaseInfo): String =
        preferredReleaseVersionNameOrNull(release) ?: release.tagName.ifBlank { release.name }

    suspend fun getLatestVersionName(forceRefresh: Boolean = false): Result<String> =
        getLatestReleaseInfo(forceRefresh = forceRefresh).map(::getReleaseVersionName)

    suspend fun getLatestReleaseNotes(forceRefresh: Boolean = false): Result<String?> =
        getLatestReleaseInfo(forceRefresh = forceRefresh).map { it.body }

    suspend fun getLatestReleaseInfo(forceRefresh: Boolean = false): Result<ReleaseInfo> =
        runCatching {
            if (!isUpdaterDistribution) {
                throw IllegalStateException("Updater is not available for this distribution")
            }

            val releases = getAllReleases(forceRefresh = forceRefresh).getOrThrow()
            val latest =
                findLatestRelease(releases)
                    ?: throw IllegalStateException("No releases found")
            lastCheckTime = System.currentTimeMillis()
            latestReleaseTag = latest.tagName
            latestReleaseDownloadUrl = latest.downloadUrl
            latest
        }

    suspend fun getLatestCanaryVersionName(): Result<String> =
        getLatestCanaryReleaseInfo().map { latest ->
            preferredReleaseVersionNameOrNull(latest) ?: latest.tagName.ifBlank { latest.name }
        }

    suspend fun getLatestCanaryReleaseNotes(): Result<String?> = getLatestCanaryReleaseInfo().map { it.body }

    suspend fun getAllCanaryReleases(perPage: Int = 10): Result<List<ReleaseInfo>> =
        runCatching {
            if (!isUpdaterDistribution) return@runCatching emptyList()
            val response: HttpResponse =
                client.get("https://api.github.com/repos/MuwMx/YumaCanary/releases?per_page=$perPage") {
                    headers {
                        append("Accept", "application/vnd.github+json")
                        append("User-Agent", "YumaPlayerApp")
                    }
                }
            if (response.status.value in 200..299) {
                parseReleasesJson(response.bodyAsText(), expectedArtifactName = releaseArtifactName(isCanary = true))
            } else {
                emptyList()
            }
        }

    suspend fun getLatestCanaryReleaseInfo(): Result<ReleaseInfo> =
        runCatching {
            if (!isUpdaterDistribution) {
                throw IllegalStateException("Updater is not available for this distribution")
            }
            val releases = getAllCanaryReleases().getOrThrow()
            val latest =
                releases.firstOrNull()
                    ?: throw IllegalStateException("No canary releases found")
            lastCheckTime = System.currentTimeMillis()
            latestCanaryReleaseTag = latest.tagName
            latest
        }

    suspend fun getCommitHistory(
        count: Int = 30,
        branch: String = "",
    ): Result<List<GitCommit>> =
        runCatching {
            val url = if (branch.isNotBlank()) {
                "https://api.github.com/repos/MuwMx/YumaPlayer/commits?sha=$branch&per_page=$count"
            } else {
                "https://api.github.com/repos/MuwMx/YumaPlayer/commits?per_page=$count"
            }
            val response = client.get(url) {
                headers {
                    append("User-Agent", "YumaPlayerApp")
                    append("Accept", "application/vnd.github+json")
                }
            }.bodyAsText()
            if (response.startsWith("[")) {
                val jsonArray = JSONArray(response)
                val commits = mutableListOf<GitCommit>()
                for (i in 0 until jsonArray.length()) {
                    val commitObj = jsonArray.getJSONObject(i)
                    val commit = commitObj.getJSONObject("commit")
                    val authorObj = commit.optJSONObject("author")
                    val githubAuthorObj = commitObj.optJSONObject("author")
                    commits.add(
                        GitCommit(
                            sha = commitObj.optString("sha", "").take(7),
                            message = commit.optString("message", "").lines().firstOrNull() ?: "",
                            author = authorObj?.optString("name", "Unknown") ?: "Unknown",
                            date = authorObj?.optString("date", "") ?: "",
                            url = commitObj.optString("html_url", ""),
                            authorAvatarUrl = githubAuthorObj?.optString("avatar_url")?.takeIf { it.isNotBlank() },
                        ),
                    )
                }
                commits
            } else {
                emptyList()
            }
        }

    fun getLatestDownloadUrl(): String {
        if (!isUpdaterDistribution) {
            return ""
        }

        if (!canDownloadUpdatesDirectly) {
            return "$StableReleaseBaseUrl/latest"
        }

        latestReleaseDownloadUrl?.let { return it }
        val artifactName = releaseArtifactName()
        val tag = latestReleaseTag
        if (tag != null) {
            return "$StableReleaseBaseUrl/download/$tag/$artifactName"
        }
        return "$StableReleaseBaseUrl/latest/download/$artifactName"
    }

    fun getLatestCanaryDownloadUrl(): String {
        if (!isUpdaterDistribution) {
            return ""
        }

        if (!canDownloadUpdatesDirectly) {
            return "$CanaryReleaseBaseUrl/latest"
        }

        val artifactName = releaseArtifactName(isCanary = true)
        val tag = latestCanaryReleaseTag
        if (tag != null) {
            return "$CanaryReleaseBaseUrl/download/$tag/$artifactName"
        }
        return "$CanaryReleaseBaseUrl/latest/download/$artifactName"
    }

    suspend fun getAllReleases(
        perPage: Int = 30,
        forceRefresh: Boolean = false,
    ): Result<List<ReleaseInfo>> {
        if (!isUpdaterDistribution) {
            return Result.success(emptyList())
        }

        return runCatching {
            val now = System.currentTimeMillis()
            val cachedJson = App.instance.dataStore.getAsync(GitHubReleasesJsonKey)
            val cachedEtag = App.instance.dataStore.getAsync(GitHubReleasesEtagKey)
            val lastCheckedAt = App.instance.dataStore.getAsync(GitHubReleasesLastCheckedAtKey, 0L)
            val cachedFingerprint = App.instance.dataStore.getAsync(GitHubReleasesFingerprintKey)

            val cachedReleases =
                cachedJson
                    ?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { parseReleasesJson(it) }.getOrNull() }

            val shouldCheckNetwork =
                forceRefresh || cachedReleases.isNullOrEmpty() || (now - lastCheckedAt) >= ReleaseCacheCheckIntervalMs

            if (!shouldCheckNetwork) {
                lastCheckTime = now
                return@runCatching cachedReleases
            }

            val networkResult =
                runCatching {
                    fetchReleasesNetwork(
                        perPage = perPage,
                        cachedEtag = cachedEtag,
                    )
                }.getOrNull()

            if (networkResult == null) {
                // no-cache-on-failure: preserve previous cache without overwriting stored state
                lastCheckTime = now
                return@runCatching cachedReleases ?: emptyList()
            }

            when {
                networkResult.status == HttpStatusCode.NotModified -> {
                    val fallback = cachedReleases
                    if (!fallback.isNullOrEmpty()) {
                        App.instance.dataStore.edit { settings ->
                            settings[GitHubReleasesLastCheckedAtKey] = now
                            networkResult.etag?.let { settings[GitHubReleasesEtagKey] = it }
                        }
                        lastCheckTime = now
                        return@runCatching fallback
                    }
                    return@runCatching emptyList()
                }

                networkResult.status.value in 200..299 && !networkResult.body.isNullOrBlank() -> {
                    val networkBody = networkResult.body
                    val releases = runCatching { parseReleasesJson(networkBody) }.getOrNull().orEmpty()
                    if (releases.isNotEmpty()) {
                        val newFingerprint = getTopReleaseFingerprint(releases)
                        val hasPayloadChanged = cachedJson != networkBody
                        val hasTopReleaseChanged = cachedFingerprint != newFingerprint

                        App.instance.dataStore.edit { settings ->
                            settings[GitHubReleasesLastCheckedAtKey] = now
                            networkResult.etag?.let { settings[GitHubReleasesEtagKey] = it }
                            if (hasPayloadChanged || hasTopReleaseChanged || cachedJson.isNullOrBlank()) {
                                settings[GitHubReleasesJsonKey] = networkBody
                                settings[GitHubReleasesFingerprintKey] = newFingerprint
                            }
                        }
                        lastCheckTime = now
                        releases
                    } else {
                        cachedReleases ?: emptyList()
                    }
                }

                else -> {
                    cachedReleases ?: emptyList()
                }
            }
        }
    }
}
