/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas.models

import java.text.Normalizer
import java.util.Locale

fun CanvasArtwork.matchesSongIdentity(
    song: String,
    artist: String,
): Boolean {
    val requestedSong = song.toCanvasSongIdentity()
    val requestedArtist = artist.toCanvasPrimaryArtistIdentity()
    val resolvedSong = name?.toCanvasSongIdentity().orEmpty()
    val resolvedArtist = this.artist?.toCanvasPrimaryArtistIdentity().orEmpty()

    return requestedSong.isNotEmpty() &&
        requestedArtist.isNotEmpty() &&
        resolvedSong == requestedSong &&
        resolvedArtist == requestedArtist
}

private fun String.toCanvasSongIdentity(): String =
    replace(CanvasBracketQualifierRegex, "")
        .replace(CanvasFeaturingQualifierRegex, "")
        .replace(CanvasParenthesizedQualifierRegex, "")
        .replace(CanvasSeparatedVariantQualifierRegex, "")
        .replace(CanvasPresentationQualifierRegex, "")
        .toCanvasIdentity()

private fun String.toCanvasPrimaryArtistIdentity(): String =
    split(CanvasArtistSeparatorRegex, limit = 2)
        .firstOrNull()
        .orEmpty()
        .toCanvasIdentity()

private fun String.toCanvasIdentity(): String =
    Normalizer
        .normalize(this, Normalizer.Form.NFKD)
        .replace(CanvasCombiningMarkRegex, "")
        .lowercase(Locale.ROOT)
        .replace(CanvasIdentitySeparatorRegex, " ")
        .trim()

private val CanvasBracketQualifierRegex =
    Regex(
        """\s*\[[^]]*]""",
    )

private val CanvasFeaturingQualifierRegex =
    Regex(
        """\s*[\[(](?:feat\.?|ft\.?|featuring|with)\b[^\])]*[\])]""",
        RegexOption.IGNORE_CASE,
    )

private val CanvasParenthesizedQualifierRegex =
    Regex(
        """\s*\((?:official\s*)?(?:music\s*)?(?:[0-9]{4}\s+)?(?:video|mv|lyric\s+video|lyrics?|audio|visualizer|visualiser|live|remaster(?:ed)?|version|edit|mix|remix)[^)]*\)""",
        RegexOption.IGNORE_CASE,
    )

private val CanvasSeparatedVariantQualifierRegex =
    Regex(
        """\s*[-–—:]\s*(?:official\s*)?(?:music\s*)?(?:[0-9]{4}\s+)?(?:live|remaster(?:ed)?|version|edit|mix|remix)\b.*$""",
        RegexOption.IGNORE_CASE,
    )

private val CanvasPresentationQualifierRegex =
    Regex(
        """\s*(?:[-–—:]\s*)?[\[(]?(?:official\s+)?(?:music\s+)?(?:lyric\s+video|video|mv|audio|lyrics?|visualizer|visualiser)[\])]?\s*$""",
        RegexOption.IGNORE_CASE,
    )

private val CanvasArtistSeparatorRegex =
    Regex(
        """(?:\s*,\s*|\s*&\s*|\s+x\s+|\s+(?:feat\.?|ft\.?|featuring|with)\s+)""",
        RegexOption.IGNORE_CASE,
    )

private val CanvasCombiningMarkRegex = Regex("\\p{M}+")
private val CanvasIdentitySeparatorRegex = Regex("[^\\p{L}\\p{N}]+")
