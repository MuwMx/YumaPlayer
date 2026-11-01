/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.home.feed

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeFeedCache
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs by lazy {
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        }

        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        suspend fun loadHomePage(): HomePage? =
            withContext(Dispatchers.IO) {
                val raw =
                    try {
                        prefs.getString(KEY_HOME_PAGE, null)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                        return@withContext null
                    } ?: return@withContext null

                try {
                    json.decodeFromString<HomePage>(raw)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportException(e)
                    clear()
                    null
                }
            }

        suspend fun saveHomePage(page: HomePage) =
            withContext(Dispatchers.IO) {
                try {
                    val encoded = json.encodeToString(HomePage.serializer(), page)
                    prefs.edit().putString(KEY_HOME_PAGE, encoded).apply()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportException(e)
                }
            }

        suspend fun clear() =
            withContext(Dispatchers.IO) {
                try {
                    prefs.edit().remove(KEY_HOME_PAGE).apply()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportException(e)
                }
            }

        companion object {
            private const val PREFERENCES_NAME = "home_feed_cache"
            private const val KEY_HOME_PAGE = "cached_home_page"
        }
    }
