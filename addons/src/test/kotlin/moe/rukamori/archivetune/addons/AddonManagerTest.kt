package moe.rukamori.archivetune.addons

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.flaccore.model.TrackQuery
import okhttp3.Call
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonManagerTest {

    private class InMemoryConfigStore : AddonConfigStore {
        private var repos = mutableListOf<AddonRepository>()
        override suspend fun getRepositories(): List<AddonRepository> = repos
        override suspend fun saveRepositories(repositories: List<AddonRepository>) {
            repos = repositories.toMutableList()
        }
    }

    private val sampleAddon = AddonDescriptor(
        id = "test.addon",
        name = "Test Addon",
        downloadUrl = "https://example.com/test.js",
    )

    @Test
    fun syncRepositories_clearsRemovedRepos() = runTest {
        val configStore = InMemoryConfigStore()
        val indexLoader = mockk<BitChordIndexLoader>()
        val runtime = mockk<QuickJsAddonRuntime>(relaxed = true)

        val repo1 = AddonRepository(
            id = "repo1",
            name = "Repo 1",
            url = "https://example.com/repo1/index.json",
            enabled = true,
        )
        configStore.saveRepositories(listOf(repo1))

        coEvery { indexLoader.loadIndex(repo1.url) } returns Result.success(listOf(sampleAddon))

        val manager = AddonManager(
            configStore = configStore,
            indexLoader = indexLoader,
            runtime = runtime,
        )

        val initial = manager.syncRepositories()
        assertEquals(1, initial.size)
        assertEquals(listOf(sampleAddon.copy(repositoryUrl = repo1.url)), manager.getActiveAddons())

        configStore.saveRepositories(emptyList())

        val afterSync = manager.syncRepositories()
        assertTrue(afterSync.isEmpty())
        assertTrue(manager.getActiveAddons().isEmpty())
    }

    @Test
    fun loadAddon_skipsReDownload_whenAlreadyLoaded() = runTest {
        val configStore = InMemoryConfigStore()
        val runtime = mockk<QuickJsAddonRuntime>()
        val httpClient = mockk<OkHttpClient>()

        coEvery { runtime.isLoaded("test.addon") } returns true

        val manager = AddonManager(
            configStore = configStore,
            runtime = runtime,
            httpClient = httpClient,
        )

        val result = manager.loadAddon(sampleAddon)
        assertTrue(result.isSuccess)

        coVerify(exactly = 0) { httpClient.newCall(any()) }
        coVerify(exactly = 0) { runtime.loadModule(any(), any(), any()) }
    }

    @Test
    fun testAddon_returnsSuccess_whenSearchSucceeds() = runTest {
        val configStore = InMemoryConfigStore()
        val indexLoader = mockk<BitChordIndexLoader>()
        val runtime = mockk<QuickJsAddonRuntime>()

        val repo = AddonRepository(
            id = "repo1",
            name = "Repo 1",
            url = "https://example.com/repo/index.json",
            enabled = true,
        )
        configStore.saveRepositories(listOf(repo))
        coEvery { indexLoader.loadIndex(repo.url) } returns Result.success(listOf(sampleAddon))
        coEvery { runtime.isLoaded(sampleAddon.id) } returns true
        coEvery { runtime.searchTracks(sampleAddon.id, "Daft Punk", limit = 1) } returns Result.success(
            listOf(AddonTrack(id = "trk1", title = "Get Lucky", artist = "Daft Punk"))
        )

        val manager = AddonManager(
            configStore = configStore,
            indexLoader = indexLoader,
            runtime = runtime,
        )
        manager.syncRepositories()

        val checkResult = manager.testAddon(sampleAddon.id)
        assertTrue(checkResult.ok)
        assertEquals(1, checkResult.tracksFound)
        assertEquals("OK", checkResult.message)
    }

    @Test
    fun testAddon_returnsError_whenAddonNotFound() = runTest {
        val configStore = InMemoryConfigStore()
        val manager = AddonManager(configStore = configStore)

        val checkResult = manager.testAddon("missing.addon")
        assertFalse(checkResult.ok)
        assertEquals(0, checkResult.tracksFound)
        assertTrue(checkResult.message.contains("not found"))
    }

    @Test
    fun setRepositoryQuality_persistsAndSyncs() = runTest {
        val configStore = InMemoryConfigStore()
        val repo = AddonRepository(
            id = "repo1",
            name = "Repo 1",
            url = "https://example.com/repo/index.json",
            enabled = true,
        )
        configStore.saveRepositories(listOf(repo))
        val manager = AddonManager(configStore = configStore)

        manager.setRepositoryQuality("https://example.com/repo/index.json", "HIGH")
        val saved = configStore.getRepositories().first()
        assertEquals("HIGH", saved.pickedQuality)
    }

    @Test
    fun resolveTrack_passesQualitySettingsToJsRuntime() = runTest {
        val configStore = InMemoryConfigStore()
        val indexLoader = mockk<BitChordIndexLoader>()
        val runtime = mockk<QuickJsAddonRuntime>()

        val repo = AddonRepository(
            id = "repo1",
            name = "Repo 1",
            url = "https://example.com/repo/index.json",
            enabled = true,
            pickedQuality = "HIGH",
        )
        configStore.saveRepositories(listOf(repo))
        coEvery { indexLoader.loadIndex(repo.url) } returns Result.success(listOf(sampleAddon))
        coEvery { runtime.isLoaded(sampleAddon.id) } returns true
        coEvery {
            runtime.searchTracks(sampleAddon.id, any(), settings = mapOf("quality" to "HIGH"))
        } returns Result.success(listOf(AddonTrack(id = "trk1", title = "Get Lucky", artist = "Daft Punk")))

        coEvery {
            runtime.getTrackStreamUrl(
                addonId = sampleAddon.id,
                trackId = "trk1",
                quality = "HIGH",
                settings = mapOf("quality" to "HIGH", "fallbackMode" to "flexible"),
            )
        } returns Result.success(AddonStream(url = "https://stream.example.com/audio.mp3", codec = "mp3"))

        val manager = AddonManager(
            configStore = configStore,
            indexLoader = indexLoader,
            runtime = runtime,
        )
        manager.syncRepositories()

        val query = TrackQuery(title = "Get Lucky", artist = "Daft Punk")
        val resolved = manager.resolveTrack(sampleAddon.id, query, requestedQuality = 3)

        assertNotNull(resolved)
        assertEquals("https://stream.example.com/audio.mp3", resolved?.url)
        coVerify {
            runtime.getTrackStreamUrl(
                addonId = sampleAddon.id,
                trackId = "trk1",
                quality = "HIGH",
                settings = mapOf("quality" to "HIGH", "fallbackMode" to "flexible"),
            )
        }
    }
}
