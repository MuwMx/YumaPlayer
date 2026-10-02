/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.musicrecognition

import moe.rukamori.archivetune.viewmodels.MusicRecognitionScreenState
import moe.rukamori.archivetune.viewmodels.RecognitionPhaseUi

internal enum class MusicRecognitionContentKey {
    Empty,
    Listening,
    Processing,
    Success,
    Error,
}
internal fun MusicRecognitionScreenState.contentKey(): MusicRecognitionContentKey =
    when (this) {
        is MusicRecognitionScreenState.Empty -> MusicRecognitionContentKey.Empty
        is MusicRecognitionScreenState.Loading ->
            when (phase) {
                RecognitionPhaseUi.Listening -> MusicRecognitionContentKey.Listening
                RecognitionPhaseUi.Processing -> MusicRecognitionContentKey.Processing
            }
        is MusicRecognitionScreenState.Success -> MusicRecognitionContentKey.Success
        is MusicRecognitionScreenState.Error -> MusicRecognitionContentKey.Error
    }
