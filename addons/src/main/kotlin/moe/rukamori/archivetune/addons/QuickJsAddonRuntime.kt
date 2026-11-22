package moe.rukamori.archivetune.addons

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.evaluate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

class QuickJsAddonRuntime(
    httpClient: OkHttpClient = defaultOkHttpClient,
) {
    private val transport = QuickJsHttpTransport(httpClient)
    private val engineLock = Any()
    private val pools = LinkedHashMap<String, Pool>(16, 0.75f, true)

    private class Pool(val jsCode: String, val fetchBase: String) {
        val free = Channel<QuickJs>(Channel.UNLIMITED)
        val lock = Mutex()
        var started = 0
        val made = mutableListOf<QuickJs>()
    }

    suspend fun loadModule(addonId: String, jsCode: String, fetchBase: String = ""): Result<Unit> {
        val evicted = synchronized(engineLock) {
            if (pools.containsKey(addonId)) return Result.success(Unit)
            buildList {
                while (pools.size >= MAX_MODULES) {
                    val lruId = pools.keys.firstOrNull() ?: break
                    pools.remove(lruId)?.let { addAll(it.made) }
                }
            }
        }
        evicted.forEach { runCatching { it.close() } }

        return withContext(Dispatchers.Default) {
            runCatching {
                val qjs = newEngine(jsCode, fetchBase)
                val pool = Pool(jsCode, fetchBase)
                pool.started = 1
                pool.made += qjs
                pool.free.trySend(qjs)
                synchronized(engineLock) { pools[addonId] = pool }
            }.onFailure {
                Timber.tag(TAG).e(it, "Failed to load QuickJS module: $addonId")
            }
        }
    }

    private suspend fun newEngine(jsCode: String, fetchBase: String): QuickJs {
        val qjs = QuickJs.create(Dispatchers.Default)
        qjs.memoryLimit = MEMORY_LIMIT_BYTES
        qjs.maxStackSize = STACK_LIMIT_BYTES
        try {
            bindHost(qjs, fetchBase)
            qjs.evaluate<Unit>(AddonJsHostPolyfills.HOST_INIT_SCRIPT)
            val cleanCode = AddonJsPreprocessor.preprocess(jsCode)
            val wrapped = """
                var __yuma_iife_error = null;
                var __yuma_mod = (function() {
                    try {
                        var module = { exports: {} };
                        var exports = module.exports;
                        var self = {};
                        $cleanCode
                        if (module.exports && (module.exports.searchTracks || module.exports.getTrackStreamUrl)) {
                            return module.exports;
                        }
                        return {};
                    } catch(e) {
                        __yuma_iife_error = e && e.message ? e.message : String(e);
                        return {};
                    }
                })();
            """.trimIndent()
            qjs.evaluate<Unit>(wrapped)
            val error = qjs.evaluate<String?>("__yuma_iife_error")
            if (!error.isNullOrBlank()) throw IllegalStateException("Addon init error: $error")
            return qjs
        } catch (t: Throwable) {
            qjs.close()
            throw t
        }
    }

    private suspend fun bindHost(qjs: QuickJs, fetchBase: String) {
        qjs.function("__yumaLog") { args: Array<Any?> -> Timber.tag(TAG).d("[JS] ${args.joinToString(" ")}") }
        qjs.function("__yumaInfo") { args: Array<Any?> -> Timber.tag(TAG).i("[JS] ${args.joinToString(" ")}") }
        qjs.function("__yumaWarn") { args: Array<Any?> -> Timber.tag(TAG).w("[JS] ${args.joinToString(" ")}") }
        qjs.function("__yumaError") { args: Array<Any?> -> Timber.tag(TAG).e("[JS] ${args.joinToString(" ")}") }
        qjs.asyncFunction<String, Unit>("__yumaDelay") { msStr: String ->
            delay(msStr.toLongOrNull()?.coerceIn(0L, 15_000L) ?: 0L)
        }
        qjs.asyncFunction<String, String>("__yumaFetch") { reqJson: String ->
            transport.executeFetch(reqJson, fetchBase)
        }
    }

    suspend fun callExport(addonId: String, functionName: String, args: List<String>): Result<String> {
        val pool = synchronized(engineLock) { pools[addonId] }
            ?: return Result.failure(IllegalStateException("Module $addonId is not loaded"))

        return withContext(Dispatchers.Default) {
            val qjs = acquireEngine(pool)
            try {
                withTimeout(TIMEOUT_MS) {
                    val argsJoined = args.joinToString(",")
                    val script = """
                        var __yuma_resolved_json = undefined;
                        (async function() {
                            var fn = __yuma_mod['$functionName'];
                            if (!fn) {
                                __yuma_resolved_json = JSON.stringify({ error: '$functionName not found' });
                                return;
                            }
                            try {
                                var r = await fn($argsJoined);
                                __yuma_resolved_json = typeof r === 'string' ? r : JSON.stringify(r);
                            } catch(e) {
                                __yuma_resolved_json = JSON.stringify({ error: e && e.message ? e.message : String(e) });
                            }
                        })();
                    """.trimIndent()
                    qjs.evaluate<Unit>(script)
                    val raw = qjs.evaluate<String?>("__yuma_resolved_json").orEmpty()
                    Result.success(raw)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Result.failure(t)
            } finally {
                pool.free.trySend(qjs)
            }
        }
    }

    private suspend fun acquireEngine(pool: Pool): QuickJs {
        pool.free.tryReceive().getOrNull()?.let { return it }
        val canCreate = pool.lock.withLock {
            if (pool.started < ENGINES_PER_MODULE) {
                pool.started++
                true
            } else false
        }
        if (canCreate) {
            return try {
                newEngine(pool.jsCode, pool.fetchBase).also { fresh ->
                    pool.lock.withLock { pool.made += fresh }
                }
            } catch (t: Throwable) {
                pool.lock.withLock { pool.started-- }
                throw t
            }
        }
        return pool.free.receive()
    }

    suspend fun searchTracks(
        addonId: String,
        query: String,
        limit: Int = 50,
        settings: Map<String, String> = emptyMap(),
    ): Result<List<AddonTrack>> {
        val ctxArg = AddonResponseParser.buildContextArg(settings)
        val escQuery = JSONObject.quote(query)
        return callExport(addonId, "searchTracks", listOf(escQuery, limit.toString(), ctxArg)).mapCatching { raw ->
            AddonResponseParser.parseTracks(raw)
        }
    }

    suspend fun getTrackStreamUrl(
        addonId: String,
        trackId: String,
        quality: String = "LOSSLESS",
        settings: Map<String, String> = emptyMap(),
    ): Result<AddonStream?> {
        val ctxArg = AddonResponseParser.buildContextArg(settings)
        val escId = JSONObject.quote(trackId)
        val escQuality = JSONObject.quote(quality)
        return callExport(addonId, "getTrackStreamUrl", listOf(escId, escQuality, ctxArg)).mapCatching { raw ->
            AddonResponseParser.parseStream(raw, addonId)
        }
    }

    fun unloadAll() {
        val all = synchronized(engineLock) {
            val made = pools.values.flatMap { it.made }
            pools.clear()
            made
        }
        all.forEach { runCatching { it.close() } }
    }

    companion object {
        private const val TAG = "QuickJsAddonRuntime"
        private const val MAX_MODULES = 12
        private const val ENGINES_PER_MODULE = 2
        private const val MEMORY_LIMIT_BYTES = 32 * 1024 * 1024L
        private const val STACK_LIMIT_BYTES = 512 * 1024L
        private const val TIMEOUT_MS = 15_000L

        private val defaultOkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }
}
