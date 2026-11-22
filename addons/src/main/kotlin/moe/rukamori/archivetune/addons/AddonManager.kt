package moe.rukamori.archivetune.addons

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.flaccore.match.CatalogTrack
import moe.rukamori.archivetune.flaccore.match.TrackMatcher
import moe.rukamori.archivetune.flaccore.model.TrackQuery
import moe.rukamori.archivetune.flaccore.streaming.FlacStreamUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class AddonManager(
    private val configStore: AddonConfigStore,
    private val indexLoader: BitChordIndexLoader = BitChordIndexLoader(),
    private val runtime: QuickJsAddonRuntime = QuickJsAddonRuntime(),
    private val httpClient: OkHttpClient = defaultOkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val indexShared = SharedCalls<List<AddonDescriptor>>(SharedCalls.INDEX_TTL_MS, scope)
    private val searchShared = SharedCalls<List<AddonTrack>>(SharedCalls.SEARCH_TTL_MS, scope)
    private val streamShared = SharedCalls<AddonStream>(SharedCalls.STREAM_TTL_MS, scope)

    private val moduleLock = Mutex()
    private val activeAddons = mutableMapOf<String, AddonDescriptor>()

    suspend fun listRepositories(): List<AddonRepository> = configStore.getRepositories()

    suspend fun getActiveAddons(): List<AddonDescriptor> = moduleLock.withLock {
        activeAddons.values.toList()
    }

    suspend fun syncRepositories(): List<AddonDescriptor> = moduleLock.withLock {
        val repos = configStore.getRepositories().filter { it.enabled }
        val loaded = mutableListOf<AddonDescriptor>()
        activeAddons.clear()
        for (repo in repos) {
            val descriptors = indexShared.get(
                key = repo.url,
                describe = { "syncRepo(${repo.url})" },
            ) {
                indexLoader.loadIndex(repo.url)
            }.getOrElse { emptyList() }
            for (desc in descriptors) {
                activeAddons[desc.id] = desc
                loaded += desc
            }
        }
        loaded
    }

    suspend fun refreshRepositories(): List<AddonDescriptor> {
        indexShared.clear()
        return syncRepositories()
    }

    suspend fun addRepository(url: String, name: String? = null): Result<List<AddonDescriptor>> {
        val trimmed = url.trim()
        return runCatching {
            BitChordIndexLoader.validateUrl(trimmed)
            val descriptors = indexLoader.loadIndex(trimmed).getOrThrow()
            val current = configStore.getRepositories().toMutableList()
            val repoName = name?.trim()?.ifEmpty { null }
                ?: trimmed.substringAfter("://").substringBefore("/").ifEmpty { trimmed }
            val newRepo = AddonRepository(
                id = trimmed,
                name = repoName,
                url = trimmed,
                enabled = true,
                lastSyncedAtMs = System.currentTimeMillis(),
            )
            current.removeAll { it.url == trimmed }
            current.add(newRepo)
            configStore.saveRepositories(current)
            syncRepositories()
            descriptors
        }
    }

    suspend fun setRepositoryEnabled(url: String, enabled: Boolean) {
        val current = configStore.getRepositories()
        val updated = current.map { repo ->
            if (repo.url == url) repo.copy(enabled = enabled) else repo
        }
        configStore.saveRepositories(updated)
        syncRepositories()
    }

    suspend fun removeRepository(url: String) {
        val current = configStore.getRepositories()
        val updated = current.filterNot { it.url == url }
        configStore.saveRepositories(updated)
        syncRepositories()
    }

    suspend fun loadAddon(addon: AddonDescriptor): Result<Unit> {
        if (runtime.isLoaded(addon.id)) return Result.success(Unit)
        val jsCodeResult = runCatching {
            val req = Request.Builder().url(addon.downloadUrl).build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} downloading ${addon.id}")
                resp.body.string().ifEmpty { throw IllegalStateException("Empty body for ${addon.id}") }
            }
        }
        val jsCode = jsCodeResult.getOrElse { return Result.failure(it) }
        val base = addon.downloadUrl.substringBeforeLast("/")
        return runtime.loadModule(addon.id, jsCode, base)
    }

    suspend fun testAddon(addonId: String): AddonCheckResult {
        val start = System.currentTimeMillis()
        val addon = moduleLock.withLock { activeAddons[addonId] }
            ?: return AddonCheckResult(
                ok = false,
                latencyMs = 0L,
                tracksFound = 0,
                message = "Addon $addonId not found",
            )
        val loadRes = loadAddon(addon)
        if (loadRes.isFailure) {
            val latency = System.currentTimeMillis() - start
            return AddonCheckResult(
                ok = false,
                latencyMs = latency,
                tracksFound = 0,
                message = loadRes.exceptionOrNull()?.message ?: "Failed to load addon",
            )
        }
        val searchRes = runtime.searchTracks(addonId, "Daft Punk", limit = 1)
        val latency = System.currentTimeMillis() - start
        return searchRes.fold(
            onSuccess = { tracks ->
                AddonCheckResult(
                    ok = true,
                    latencyMs = latency,
                    tracksFound = tracks.size,
                    message = "OK",
                )
            },
            onFailure = { err ->
                AddonCheckResult(
                    ok = false,
                    latencyMs = latency,
                    tracksFound = 0,
                    message = err.message ?: "Search failed",
                )
            },
        )
    }

    suspend fun resolveTrack(addonId: String, query: TrackQuery, requestedQuality: Int): FlacStreamUrl? {
        val addon = moduleLock.withLock { activeAddons[addonId] } ?: return null
        loadAddon(addon).getOrNull() ?: return null

        val searchTerms = query.searchTerms()
        var candidates: List<AddonTrack> = emptyList()

        for (term in searchTerms) {
            val key = SharedCalls.keyOf(addonId, term)
            val tracks = searchShared.get(
                key = key,
                describe = { "searchTracks($addonId, $term)" },
            ) {
                runtime.searchTracks(addonId, term)
            }.getOrElse { emptyList() }

            if (tracks.isNotEmpty()) {
                candidates = tracks
                break
            }
        }

        if (candidates.isEmpty()) return null

        val catalogTracks = candidates.map {
            CatalogTrack(
                id = it.id,
                title = it.title,
                artist = it.artist,
                album = it.album,
                durationMs = it.durationMs,
                isrc = it.isrc,
            )
        }

        val best = TrackMatcher.best(query, catalogTracks) ?: return null
        val qualityStr = mapQuality(requestedQuality)

        val streamKey = SharedCalls.keyOf(addonId, best.track.id, qualityStr)
        val stream = streamShared.get(
            key = streamKey,
            describe = { "getTrackStreamUrl($addonId, ${best.track.id}, $qualityStr)" },
        ) {
            val res = runtime.getTrackStreamUrl(addonId, best.track.id, qualityStr)
            res.mapCatching { it ?: throw NoSuchElementException("No stream returned") }
        }.getOrNull() ?: return null

        return FlacStreamUrl(
            url = stream.url,
            expiresAtMs = if (stream.expiresAtMs > 0L) stream.expiresAtMs else System.currentTimeMillis() + 5 * 60 * 1000L,
            codec = stream.codec,
            bitsPerSample = stream.bitsPerSample,
            sampleRateHz = stream.sampleRateHz,
            bitrateKbps = stream.bitrateKbps,
            coverArtUrl = stream.coverArtUrl,
            origin = addonId,
        )
    }

    suspend fun getResolvers(): Map<String, suspend (TrackQuery, Int) -> FlacStreamUrl?> {
        syncRepositories()
        val addons = moduleLock.withLock { activeAddons.values.toList() }
        return addons.associate { addon ->
            addon.id to { query: TrackQuery, quality: Int ->
                resolveTrack(addon.id, query, quality)
            }
        }
    }

    fun unloadAll() {
        runtime.unloadAll()
        indexShared.clear()
        searchShared.clear()
        streamShared.clear()
    }

    private fun mapQuality(qualityInt: Int): String = when {
        qualityInt >= 3 -> "LOSSLESS"
        qualityInt == 2 -> "HIGH"
        else -> "LOW"
    }

    companion object {
        private val defaultOkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder().build()
        }
    }
}
