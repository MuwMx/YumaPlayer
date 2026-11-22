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
import timber.log.Timber

class AddonManager(
    private val configStore: AddonConfigStore,
    private val indexLoader: BitChordIndexLoader = BitChordIndexLoader(defaultOkHttpClient),
    private val runtime: QuickJsAddonRuntime = QuickJsAddonRuntime(defaultOkHttpClient),
    private val httpClient: OkHttpClient = defaultOkHttpClient,
    private val urlDetector: AddonUrlDetector = AddonUrlDetector(httpClient, indexLoader),
    private val httpAddonClient: BitChordHttpAddonClient = BitChordHttpAddonClient(httpClient),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val indexShared = SharedCalls<List<AddonDescriptor>>(SharedCalls.INDEX_TTL_MS, scope)
    private val searchShared = SharedCalls<List<AddonTrack>>(SharedCalls.SEARCH_TTL_MS, scope)
    private val streamShared = SharedCalls<AddonStream>(SharedCalls.STREAM_TTL_MS, scope)

    private val moduleLock = Mutex()
    private val activeAddons = mutableMapOf<String, AddonDescriptor>()
    private val repoByUrl = mutableMapOf<String, AddonRepository>()

    suspend fun listRepositories(): List<AddonRepository> = configStore.getRepositories()

    suspend fun getActiveAddons(): List<AddonDescriptor> = moduleLock.withLock {
        activeAddons.values.toList()
    }

    suspend fun syncRepositories(): List<AddonDescriptor> = moduleLock.withLock {
        val repos = configStore.getRepositories().filter { it.enabled }
        val loaded = mutableListOf<AddonDescriptor>()
        activeAddons.clear()
        repoByUrl.clear()
        for (repo in repos) {
            repoByUrl[repo.url] = repo
            val descriptors = indexShared.get(
                key = repo.url,
                describe = { "syncRepo(${repo.url})" },
            ) {
                if (repo.format == AddonFormat.BITCHORD_HTTP) {
                    urlDetector.identify(repo.url).map { detected ->
                        when (detected) {
                            is DetectedAddonSource.HttpAddon -> listOf(detected.descriptor)
                            is DetectedAddonSource.JsModuleIndex -> detected.descriptors
                        }
                    }
                } else {
                    indexLoader.loadIndex(repo.url)
                }
            }.getOrElse { emptyList() }
            for (desc in descriptors) {
                val attached = desc.copy(repositoryUrl = repo.url)
                activeAddons[attached.id] = attached
                loaded += attached
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
            val detected = urlDetector.identify(trimmed).getOrThrow()
            val (repoUrl, format, descriptors) = when (detected) {
                is DetectedAddonSource.HttpAddon -> Triple(detected.baseUrl, AddonFormat.BITCHORD_HTTP, listOf(detected.descriptor))
                is DetectedAddonSource.JsModuleIndex -> Triple(detected.indexUrl, AddonFormat.BITCHORD_INDEX, detected.descriptors)
            }
            val repoName = name?.trim()?.ifEmpty { null }
                ?: descriptors.firstOrNull()?.name?.ifBlank { null }
                ?: trimmed.substringAfter("://").substringBefore("/").ifEmpty { repoUrl }
            val current = configStore.getRepositories().toMutableList()
            current.removeAll { it.url == repoUrl }
            current.add(
                AddonRepository(
                    id = repoUrl,
                    name = repoName,
                    url = repoUrl,
                    format = format,
                    enabled = true,
                    lastSyncedAtMs = System.currentTimeMillis(),
                ),
            )
            configStore.saveRepositories(current)
            syncRepositories()
            descriptors
        }
    }

    suspend fun setRepositoryQuality(url: String, quality: String?) {
        val current = configStore.getRepositories()
        val updated = current.map { repo ->
            if (repo.url == url) repo.copy(pickedQuality = quality?.trim()?.ifEmpty { null }) else repo
        }
        configStore.saveRepositories(updated)
        searchShared.clear()
        streamShared.clear()
        syncRepositories()
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
            ?: return AddonCheckResult(ok = false, latencyMs = 0L, tracksFound = 0, message = "Addon $addonId not found")

        val searchRes = if (addon.format == AddonFormat.BITCHORD_HTTP) {
            httpAddonClient.search(addon, "Daft Punk", quality = "LOW")
        } else {
            val loadRes = loadAddon(addon)
            if (loadRes.isFailure) {
                return AddonCheckResult(
                    ok = false,
                    latencyMs = System.currentTimeMillis() - start,
                    tracksFound = 0,
                    message = loadRes.exceptionOrNull()?.message ?: "Failed to load addon",
                )
            }
            runtime.searchTracks(addonId, "Daft Punk", limit = 1)
        }
        val latency = System.currentTimeMillis() - start
        return searchRes.fold(
            onSuccess = { tracks ->
                AddonCheckResult(ok = true, latencyMs = latency, tracksFound = tracks.size, message = "OK")
            },
            onFailure = { err ->
                AddonCheckResult(ok = false, latencyMs = latency, tracksFound = 0, message = err.message ?: "Search failed")
            },
        )
    }

    suspend fun resolveTrack(addonId: String, query: TrackQuery, requestedQuality: Int): FlacStreamUrl? {
        val addon = moduleLock.withLock { activeAddons[addonId] } ?: run {
            Timber.tag(TAG).w("resolveTrack: addon $addonId not found in activeAddons")
            return null
        }
        val repo = moduleLock.withLock { repoByUrl[addon.repositoryUrl] ?: repoByUrl[addon.downloadUrl] }
        val effectiveQuality = httpAddonClient.resolveQualityParam(addon, repo?.pickedQuality, requestedQuality)

        if (addon.format == AddonFormat.BITCHORD_HTTP) {
            return resolveHttpTrack(addon, query, effectiveQuality)
        }

        loadAddon(addon).getOrNull() ?: run {
            Timber.tag(TAG).w("resolveTrack: failed to load JS addon $addonId")
            return null
        }
        val candidates = searchCandidates(addonId, query, effectiveQuality) { term ->
            runtime.searchTracks(addonId, term, settings = mapOf("quality" to effectiveQuality))
        } ?: return null

        val best = matchBestTrack(query, candidates) ?: return null
        val streamKey = SharedCalls.keyOf(addonId, best.track.id, effectiveQuality)
        val stream = streamShared.get(
            key = streamKey,
            describe = { "getTrackStreamUrl($addonId, ${best.track.id}, $effectiveQuality)" },
        ) {
            val settings = mapOf(
                "quality" to effectiveQuality,
                "fallbackMode" to if (effectiveQuality == "LOSSLESS") "strict" else "flexible",
            )
            runtime.getTrackStreamUrl(addonId, best.track.id, effectiveQuality, settings)
                .mapCatching { it ?: throw NoSuchElementException("No stream returned") }
        }.getOrNull() ?: run {
            Timber.tag(TAG).w("resolveTrack: no stream returned for ${best.track.id} from $addonId")
            return null
        }
        return toFlacStreamUrl(stream, addonId)
    }

    private suspend fun resolveHttpTrack(
        addon: AddonDescriptor,
        query: TrackQuery,
        effectiveQuality: String,
    ): FlacStreamUrl? {
        val candidates = searchCandidates(addon.id, query, effectiveQuality) { term ->
            httpAddonClient.search(addon, term, query.title, query.artist, effectiveQuality)
        } ?: return null

        val best = matchBestTrack(query, candidates) ?: return null
        val bestTrack = candidates.firstOrNull { it.id == best.track.id }
        val streamKey = SharedCalls.keyOf(addon.id, best.track.id, effectiveQuality)
        val stream = streamShared.get(
            key = streamKey,
            describe = { "httpStream(${addon.id}, ${best.track.id}, $effectiveQuality)" },
        ) {
            val res = httpAddonClient.stream(addon, best.track.id, effectiveQuality)
            res.mapCatching {
                it ?: bestTrack?.streamUrl?.let { url -> AddonStream(url = url, origin = addon.id) }
                ?: throw NoSuchElementException("No stream returned")
            }
        }.getOrNull() ?: run {
            Timber.tag(TAG).w("resolveHttpTrack: no stream for ${best.track.id} from ${addon.id}")
            return null
        }
        return toFlacStreamUrl(stream, addon.id)
    }

    private suspend fun searchCandidates(
        addonId: String,
        query: TrackQuery,
        quality: String,
        fetcher: suspend (String) -> Result<List<AddonTrack>>,
    ): List<AddonTrack>? {
        for (term in query.searchTerms()) {
            val tracks = searchShared.get(
                key = SharedCalls.keyOf(addonId, term, quality),
                describe = { "searchTracks($addonId, $term, $quality)" },
            ) { fetcher(term) }.getOrElse { emptyList() }
            if (tracks.isNotEmpty()) return tracks
        }
        Timber.tag(TAG).w("searchCandidates: 0 tracks found in $addonId for $query")
        return null
    }

    private fun matchBestTrack(query: TrackQuery, candidates: List<AddonTrack>) =
        TrackMatcher.best(query, candidates.map {
            CatalogTrack(it.id, it.title, it.artist, it.album, it.durationMs, it.isrc)
        })

    private fun toFlacStreamUrl(stream: AddonStream, addonId: String) = FlacStreamUrl(
        url = stream.url,
        expiresAtMs = if (stream.expiresAtMs > 0L) stream.expiresAtMs else System.currentTimeMillis() + 300_000L,
        codec = stream.codec,
        bitsPerSample = stream.bitsPerSample,
        sampleRateHz = stream.sampleRateHz,
        bitrateKbps = stream.bitrateKbps,
        coverArtUrl = stream.coverArtUrl,
        origin = addonId,
    )

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

    companion object {
        private const val TAG = "AddonManager"
        private val defaultOkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder().build()
        }
    }
}
