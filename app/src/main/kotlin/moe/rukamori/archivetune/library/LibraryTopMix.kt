/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.library

import androidx.compose.runtime.Immutable
import com.google.common.collect.ImmutableList
import moe.rukamori.archivetune.models.MediaMetadata

@Immutable
data class LibraryTopMix(
    val id: String,
    val title: String,
    val description: String,
    val tracks: ImmutableList<MediaMetadata>,
)

@Immutable
data class GeneratedLibraryTopMix(
    val id: String,
    val title: String,
    val description: String,
    val tracks: ImmutableList<MediaMetadata>,
)
