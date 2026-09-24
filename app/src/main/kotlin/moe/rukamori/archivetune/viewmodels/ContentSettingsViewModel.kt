/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.lyrics.LyricsHelper
import javax.inject.Inject

@HiltViewModel
class ContentSettingsViewModel
    @Inject
    constructor(
        private val lyricsHelper: LyricsHelper,
        private val database: MusicDatabase,
    ) : ViewModel() {
        fun clearLyricsCache() {
            viewModelScope.launch(Dispatchers.IO) {
                lyricsHelper.clearCache()
                database.query {
                    clearAllLyrics()
                }
            }
        }
    }
