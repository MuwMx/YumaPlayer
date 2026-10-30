package moe.rukamori.archivetune.lyrics

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.constants.LyricsProviderOrderKey
import moe.rukamori.archivetune.constants.PreferredLyricsProvider
import moe.rukamori.archivetune.utils.NetworkConnectivityObserver
import moe.rukamori.archivetune.utils.dataStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LyricsHelperOrderTest {

    private lateinit var context: Context
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var networkConnectivity: NetworkConnectivityObserver
    private lateinit var lyricsHelper: LyricsHelper

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        // DataStore creates the file itself; an existing one cannot be replaced on Windows.
        val tempFile = Files.createTempFile("test_prefs", ".preferences_pb").toFile().also { it.delete() }
        testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFile }
        )
        
        mockkStatic("moe.rukamori.archivetune.utils.DataStoreKt")
        every { context.dataStore } returns testDataStore
        
        networkConnectivity = mockk(relaxed = true)
        lyricsHelper = LyricsHelper(context, networkConnectivity)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `orderedProviders returns providers in DataStore order`() = runTest {
        // Set custom order in DataStore
        val customOrder = listOf(
            PreferredLyricsProvider.KUGOU,
            PreferredLyricsProvider.SIMPMUSIC,
            PreferredLyricsProvider.LRCLIB
        ).joinToString(",") { it.name }
        
        testDataStore.edit { prefs ->
            prefs[LyricsProviderOrderKey] = customOrder
        }

        val providers = lyricsHelper.orderedProviders()

        // Verify the first 3 providers match our custom order
        assertEquals(KuGouLyricsProvider, providers[0])
        assertEquals(SimpMusicLyricsProvider, providers[1])
        assertEquals(LrcLibLyricsProvider, providers[2])
    }
}
