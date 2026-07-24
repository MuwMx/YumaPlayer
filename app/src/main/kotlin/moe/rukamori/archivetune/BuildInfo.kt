/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune

import moe.rukamori.archivetune.constants.UpdateChannel

private val DailyNightlyVersionRegex = Regex("""^N\d{8}$""")

internal val isDailyNightlyBuild: Boolean
    get() = DailyNightlyVersionRegex.matches(BuildConfig.VERSION_NAME)

internal val defaultUpdateChannel: UpdateChannel
    get() = if (isDailyNightlyBuild) UpdateChannel.DAILY_NIGHTLY else UpdateChannel.STABLE

internal val currentBuildHash: String?
    get() = BuildConfig.NIGHTLY_BUILD_HASH.takeIf { it.isNotBlank() }

internal fun formatVersionName(
    versionName: String = BuildConfig.VERSION_NAME,
    buildHash: String? = currentBuildHash,
): String = listOfNotNull(versionName.takeIf { it.isNotBlank() }, buildHash).joinToString(" ")
