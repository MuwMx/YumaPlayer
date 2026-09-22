/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.bush.translator.Language
import moe.rukamori.archivetune.ai.AiLyricsTranslator
import moe.rukamori.archivetune.ai.AiServiceConfig
import moe.rukamori.archivetune.constants.AiApiKeyKey
import moe.rukamori.archivetune.constants.AiApiValidationStatus
import moe.rukamori.archivetune.constants.AiApiValidationStatusKey
import moe.rukamori.archivetune.constants.AiCustomEndpointKey
import moe.rukamori.archivetune.constants.AiCustomModelKey
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
import moe.rukamori.archivetune.constants.AiSelectedModelKey
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

interface LyricsTranslationUseCase {
    suspend fun translateAi(lyrics: String, targetLanguage: String): String
    suspend fun onAiTranslationFailed()
    suspend fun translateStandard(lyrics: String, targetLanguage: String): String
}

@Singleton
class LyricsTranslationUseCaseImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : LyricsTranslationUseCase {
    override suspend fun translateAi(lyrics: String, targetLanguage: String): String = withContext(Dispatchers.IO) {
        val prefs = context.dataStore.data.first()
        val config = AiServiceConfig(
            provider = prefs[AiProviderKey].toEnum(AiProvider.NONE),
            apiKey = prefs[AiApiKeyKey].orEmpty(),
            customEndpoint = prefs[AiCustomEndpointKey].orEmpty(),
            model = if (prefs[AiProviderKey].toEnum(AiProvider.NONE) == AiProvider.CUSTOM) {
                prefs[AiCustomModelKey].orEmpty()
            } else {
                prefs[AiSelectedModelKey].orEmpty()
            },
        )
        val translatedLyrics = AiLyricsTranslator().translate(
            config = config,
            lyrics = lyrics,
            targetLanguage = targetLanguage.ifBlank { "ENGLISH" },
        )
        context.dataStore.edit { settings ->
            settings[AiApiValidationStatusKey] = AiApiValidationStatus.SUCCESS.name
        }
        translatedLyrics
    }

    override suspend fun onAiTranslationFailed(): Unit = withContext(Dispatchers.IO) {
        context.dataStore.edit { settings ->
            settings[AiApiValidationStatusKey] = AiApiValidationStatus.FAILED.name
        }
    }

    override suspend fun translateStandard(lyrics: String, targetLanguage: String): String = withContext(Dispatchers.IO) {
        val lang = try {
            Language(targetLanguage)
        } catch (_: Exception) {
            null
        } ?: throw IllegalArgumentException("Unsupported language code: $targetLanguage")
        LyricsTranslator.translate(lyrics, lang)
    }
}
