/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Thumbnails(
    val thumbnails: List<Thumbnail>,
)

@Serializable
data class Thumbnail(
    val url: String,
    val width: Int?,
    val height: Int?,
) {
    val normalizedUrl: String get() = if (url.startsWith("//")) "https:$url" else url
}

private val AVATAR_SIZE_REGEX = Regex("=s\\d+[^?&#]*")

fun String.withHighResAvatar(size: Int = 512): String =
    replace(AVATAR_SIZE_REGEX, "=s${size.coerceAtLeast(1)}")
