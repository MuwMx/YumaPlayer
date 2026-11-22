package moe.rukamori.archivetune.lossless

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.addons.AddonConfigStore
import moe.rukamori.archivetune.addons.AddonRepository
import moe.rukamori.archivetune.constants.AddonRepositoriesKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonConfigStoreImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AddonConfigStore {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val serializer = ListSerializer(AddonRepository.serializer())

    override suspend fun getRepositories(): List<AddonRepository> {
        val raw = dataStore.data.first()[AddonRepositoriesKey] ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrElse { emptyList() }
    }

    override suspend fun saveRepositories(repositories: List<AddonRepository>) {
        val raw = json.encodeToString(serializer, repositories)
        dataStore.edit { prefs ->
            prefs[AddonRepositoriesKey] = raw
        }
    }
}
