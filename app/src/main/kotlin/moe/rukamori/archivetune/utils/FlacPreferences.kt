/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import androidx.compose.runtime.Composable
import moe.rukamori.archivetune.constants.EnableLosslessKey
import moe.rukamori.archivetune.constants.EnableQobuzKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.constants.PlaybackSourceKey
import moe.rukamori.archivetune.constants.QobuzAppIdKey
import moe.rukamori.archivetune.constants.QobuzAppSecretKey
import moe.rukamori.archivetune.constants.QobuzUserAuthTokenKey

@Composable
fun rememberCanDownloadFlac(): Boolean {
    val (enableLossless) = rememberPreference(EnableLosslessKey, false)
    val (playbackSource) = rememberEnumPreference(PlaybackSourceKey, PlaybackSource.YT_MUSIC)
    val (enableQobuz) = rememberPreference(EnableQobuzKey, true)
    val (qobuzAppId) = rememberPreference(QobuzAppIdKey, "")
    val (qobuzAppSecret) = rememberPreference(QobuzAppSecretKey, "")
    val (qobuzUserAuthToken) = rememberPreference(QobuzUserAuthTokenKey, "")

    return (enableLossless || playbackSource == PlaybackSource.FLAC) &&
        enableQobuz &&
        qobuzAppId.isNotBlank() &&
        qobuzAppSecret.isNotBlank() &&
        qobuzUserAuthToken.isNotBlank()
}
