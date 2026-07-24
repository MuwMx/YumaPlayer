/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.obfuscator

import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object MoriCipherRuntime : MoriCipherResolver {
    private val mutableSnapshot = MutableStateFlow(CipherSnapshot())
    override val snapshot: StateFlow<CipherSnapshot> = mutableSnapshot.asStateFlow()

    private val initializationMutex = Mutex()
    private val refreshMutex = Mutex()
    private val executionMutex = Mutex()

    @Volatile
    private var engine: Engine? = null
    private var activeRefresh: CompletableDeferred<Result<CipherRefreshResult>>? = null

    fun initialize(config: MoriCipherConfig) {
        if (engine != null) return
        synchronized(this) {
            if (engine != null) return
            val store = CipherArtifactStore(config.cacheDirectory)
            engine =
                Engine(
                    config = config,
                    client = PlayerScriptClient(config.proxyProvider),
                    compiler = JavaScriptPlanCompiler(),
                    executor = RhinoTransformExecutor(),
                    store = store,
                    artifact = null,
                )
            mutableSnapshot.value = CipherSnapshot()
        }
    }

    override suspend fun refresh(
        force: Boolean,
        videoId: String?,
    ): Result<CipherRefreshResult> {
        val currentEngine = requireEngine()
        ensureCacheLoaded(currentEngine)
        val (shared, ownsRefresh) =
            refreshMutex.withLock {
                activeRefresh?.takeIf { !it.isCompleted }?.let { return@withLock it to false }
                CompletableDeferred<Result<CipherRefreshResult>>()
                    .also { activeRefresh = it } to true
            }
        if (!ownsRefresh) return shared.await()

        val result =
            try {
                runCatching {
                    val current = currentEngine.artifact
                    val now = System.currentTimeMillis()
                    if (
                        !force &&
                        current != null &&
                        now - current.plan.createdAtMillis < currentEngine.config.refreshIntervalMillis
                    ) {
                        return@runCatching CipherRefreshResult(
                            playerId = current.plan.playerId,
                            refreshedAtMillis = current.plan.createdAtMillis,
                            changed = false,
                        )
                    }

                    mutableSnapshot.value =
                        mutableSnapshot.value.copy(
                            status = CipherRuntimeStatus.REFRESHING,
                            lastFailure = null,
                            refreshProgressPercent = REFRESH_DISCOVERY_PROGRESS,
                        )
                    val script =
                        currentEngine.client.fetch(videoId) { stage ->
                            updateRefreshProgress(
                                when (stage) {
                                    PlayerScriptFetchStage.DISCOVERING -> REFRESH_DISCOVERY_PROGRESS
                                    PlayerScriptFetchStage.DOWNLOADING -> REFRESH_DOWNLOAD_PROGRESS
                                },
                            )
                        }
                    updateRefreshProgress(REFRESH_DOWNLOADED_PROGRESS)
                    val compiledPlan =
                        withContext(Dispatchers.Default) {
                            currentEngine.compiler.compile(script, now)
                        }
                    updateRefreshProgress(REFRESH_COMPILED_PROGRESS)
                    val plan =
                        withContext(Dispatchers.Default) {
                            executionMutex.withLock {
                                validatePlan(currentEngine, compiledPlan)
                            }
                        }
                    updateRefreshProgress(REFRESH_VALIDATED_PROGRESS)
                    if (
                        plan.signatureProgram == null &&
                        plan.nProgram == null &&
                        plan.signatureTimestamp == null &&
                        plan.nTransformState != NTransformState.NOT_REQUIRED
                    ) {
                        throw MoriCipherException("Player configuration contained no usable capability")
                    }
                    val completedAtMillis = System.currentTimeMillis()
                    val completedPlan = plan.copy(createdAtMillis = completedAtMillis)
                    val artifact = CachedTransformArtifact(completedPlan, script.source)
                    withContext(Dispatchers.IO) { currentEngine.store.write(artifact) }
                    updateRefreshProgress(REFRESH_PERSISTED_PROGRESS)
                    currentEngine.artifact = artifact
                    mutableSnapshot.value =
                        CipherSnapshot(
                            status = completedPlan.runtimeStatus(),
                            playerId = completedPlan.playerId,
                            lastSuccessfulRefreshMillis = completedAtMillis,
                            nextRefreshAtMillis = completedAtMillis + currentEngine.config.refreshIntervalMillis,
                            lastFailure = completedPlan.capabilityWarning(),
                        )
                    CipherRefreshResult(
                        playerId = completedPlan.playerId,
                        refreshedAtMillis = completedAtMillis,
                        changed = current?.plan?.sourceSha256 != completedPlan.sourceSha256,
                    )
                }.onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    val cached = currentEngine.artifact?.plan
                    mutableSnapshot.value =
                        CipherSnapshot(
                            status = if (cached == null) CipherRuntimeStatus.UNINITIALIZED else CipherRuntimeStatus.DEGRADED,
                            playerId = cached?.playerId,
                            lastSuccessfulRefreshMillis = cached?.createdAtMillis,
                            nextRefreshAtMillis = cached?.createdAtMillis?.plus(currentEngine.config.refreshIntervalMillis),
                            lastFailure = failure.message,
                        )
                }
            } catch (cancellation: CancellationException) {
                shared.cancel(cancellation)
                refreshMutex.withLock {
                    if (activeRefresh === shared) activeRefresh = null
                }
                throw cancellation
            }
        shared.complete(result)
        refreshMutex.withLock {
            if (activeRefresh === shared) activeRefresh = null
        }
        return result
    }

    private fun updateRefreshProgress(progressPercent: Int) {
        mutableSnapshot.value =
            mutableSnapshot.value.copy(
                status = CipherRuntimeStatus.REFRESHING,
                refreshProgressPercent = progressPercent.coerceIn(0, 100),
            )
    }

    override suspend fun signatureTimestamp(videoId: String): Result<Int?> =
        withSelfHealingPlan(videoId) { plan -> plan.signatureTimestamp }

    override suspend fun resolveStreamUrl(
        videoId: String,
        signatureCipher: String,
    ): Result<String> {
        val signatureResult =
            withSelfHealingPlan(videoId) { plan ->
                executionMutex.withLock {
                    val cipherParameters = parseQueryString(signatureCipher)
                    val sourceUrl =
                        cipherParameters["url"]
                            ?: throw MoriCipherException("Cipher URL parameter was missing")
                    val signature =
                        cipherParameters["s"]
                            ?: throw MoriCipherException("Cipher signature parameter was missing")
                    val signatureParameter =
                        cipherParameters["sp"]?.takeIf { it.isNotBlank() }
                            ?: "signature"
                    val deciphered = requireEngine().executor.executeSignature(plan, signature)
                    URLBuilder(sourceUrl)
                        .apply {
                            parameters[signatureParameter] = deciphered
                        }.buildString()
                }
            }
        val signedUrl = signatureResult.getOrElse { return Result.failure(it) }
        return transformNParameter(videoId, signedUrl)
    }

    override suspend fun transformNParameter(
        videoId: String,
        url: String,
    ): Result<String> {
        val builder =
            runCatching { URLBuilder(url) }
                .getOrElse { return Result.failure(MoriCipherException("Stream URL was invalid", it)) }
        val nValue = builder.parameters["n"] ?: return Result.success(url)
        return withSelfHealingPlan(videoId) { plan ->
            if (plan.nTransformState == NTransformState.NOT_REQUIRED) return@withSelfHealingPlan url
            executionMutex.withLock {
                val transformed = requireEngine().executor.executeN(plan, nValue)
                builder.parameters["n"] = transformed
                builder.buildString()
            }
        }
    }

    private suspend fun <T> withSelfHealingPlan(
        videoId: String,
        operation: suspend (TransformPlan) -> T,
    ): Result<T> {
        val currentEngine = requireEngine()
        ensureCacheLoaded(currentEngine)
        val firstPlan =
            currentEngine.artifact?.plan
                ?: refresh(force = true, videoId = videoId)
                    .getOrElse { return Result.failure(it) }
                    .let { currentEngine.artifact?.plan }
                ?: return Result.failure(MoriCipherException("Cipher plan was unavailable"))
        return try {
            Result.success(operation(firstPlan))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (firstFailure: Exception) {
            val recoveredPlan = recoverPlan(currentEngine, firstPlan, videoId) ?: return Result.failure(firstFailure)
            try {
                Result.success(operation(recoveredPlan))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (recoveryFailure: Exception) {
                Result.failure(recoveryFailure)
            }
        }
    }

    private suspend fun recoverPlan(
        engine: Engine,
        failedPlan: TransformPlan,
        videoId: String,
    ): TransformPlan? =
        engine.recoveryMutex.withLock {
            val currentPlan = engine.artifact?.plan
            if (currentPlan != null && currentPlan.sourceSha256 != failedPlan.sourceSha256) {
                return@withLock currentPlan
            }
            if (engine.recoveryAttemptedForSourceSha256 == failedPlan.sourceSha256) {
                return@withLock null
            }
            engine.recoveryAttemptedForSourceSha256 = failedPlan.sourceSha256
            refresh(force = true, videoId = videoId).getOrElse { return@withLock null }
            engine.artifact?.plan
        }

    private fun validatePlan(
        engine: Engine,
        plan: TransformPlan,
    ): TransformPlan {
        val signatureIsValid =
            plan.signatureProgram?.let {
                val sample = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                runCatching { engine.executor.executeSignature(plan, sample) }
                    .getOrNull()
                    ?.let { result -> result.isNotBlank() && result != sample }
                    ?: false
            } ?: false
        val nIsValid =
            plan.nProgram?.let {
                val sample = "abcdefghijklmnopqrstuvwxyz0123456789_-"
                runCatching { engine.executor.executeN(plan, sample) }
                    .getOrNull()
                    ?.let { result -> result.isNotBlank() && result != sample }
                    ?: false
            } ?: false
        return plan.copy(
            signatureProgram = plan.signatureProgram.takeIf { signatureIsValid },
            signatureFunction = plan.signatureFunction.takeIf { signatureIsValid },
            nProgram = plan.nProgram.takeIf { nIsValid },
            nFunction = plan.nFunction.takeIf { nIsValid },
        )
    }

    private suspend fun requireEngine(): Engine {
        engine?.let { return it }
        initializationMutex.withLock {
            engine?.let { return it }
            throw MoriCipherException("Mori cipher runtime was not initialized")
        }
    }

    private suspend fun ensureCacheLoaded(engine: Engine) {
        if (engine.cacheLoaded.get()) return
        engine.cacheLoadMutex.withLock {
            if (engine.cacheLoaded.get()) return
            val stored = withContext(Dispatchers.IO) { engine.store.read() }
            val cached =
                stored?.let { artifact ->
                    if (artifact.plan.compilerVersion == JAVA_SCRIPT_PLAN_COMPILER_VERSION) {
                        artifact
                    } else {
                        recompileCachedArtifact(engine, artifact)
                            .onFailure { failure ->
                                mutableSnapshot.value =
                                    CipherSnapshot(
                                        status = CipherRuntimeStatus.UNINITIALIZED,
                                        lastFailure = failure.message,
                                    )
                            }.getOrNull()
                    }
                }
            if (cached != null) {
                engine.artifact = cached
                val plan = cached.plan
                mutableSnapshot.value =
                    CipherSnapshot(
                        status = plan.runtimeStatus(),
                        playerId = plan.playerId,
                        lastSuccessfulRefreshMillis = plan.createdAtMillis,
                        nextRefreshAtMillis = plan.createdAtMillis + engine.config.refreshIntervalMillis,
                        lastFailure = plan.capabilityWarning(),
                    )
            }
            engine.cacheLoaded.set(true)
        }
    }

    private suspend fun recompileCachedArtifact(
        engine: Engine,
        artifact: CachedTransformArtifact,
    ): Result<CachedTransformArtifact> =
        try {
            val script =
                PlayerScript(
                    playerId = artifact.plan.playerId,
                    url = artifact.plan.playerUrl,
                    source = artifact.playerJavaScript,
                )
            val plan =
                withContext(Dispatchers.Default) {
                    executionMutex.withLock {
                        validatePlan(engine, engine.compiler.compile(script, System.currentTimeMillis()))
                    }
                }
            Result.success(
                CachedTransformArtifact(plan, artifact.playerJavaScript).also { upgraded ->
                    withContext(Dispatchers.IO) { engine.store.write(upgraded) }
                },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Result.failure(failure)
        }

    private class Engine(
        val config: MoriCipherConfig,
        val client: PlayerScriptClient,
        val compiler: JavaScriptPlanCompiler,
        val executor: RhinoTransformExecutor,
        val store: CipherArtifactStore,
        @Volatile var artifact: CachedTransformArtifact?,
    ) {
        val cacheLoaded = AtomicBoolean(false)
        val cacheLoadMutex = Mutex()
        val recoveryMutex = Mutex()

        @Volatile
        var recoveryAttemptedForSourceSha256: String? = null
    }

    private fun TransformPlan.runtimeStatus(): CipherRuntimeStatus =
        if (nProgram != null || nTransformState == NTransformState.NOT_REQUIRED) {
            CipherRuntimeStatus.READY
        } else {
            CipherRuntimeStatus.DEGRADED
        }

    private fun TransformPlan.capabilityWarning(): String? =
        if (nProgram != null || nTransformState == NTransformState.NOT_REQUIRED) {
            null
        } else {
            "One or more player transforms require the playback fallback"
        }

    private const val REFRESH_DISCOVERY_PROGRESS = 10
    private const val REFRESH_DOWNLOAD_PROGRESS = 30
    private const val REFRESH_DOWNLOADED_PROGRESS = 50
    private const val REFRESH_COMPILED_PROGRESS = 75
    private const val REFRESH_VALIDATED_PROGRESS = 90
    private const val REFRESH_PERSISTED_PROGRESS = 100
}
