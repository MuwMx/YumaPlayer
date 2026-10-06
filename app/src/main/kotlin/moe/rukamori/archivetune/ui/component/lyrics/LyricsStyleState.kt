/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.constants.LyricsClickKey
import moe.rukamori.archivetune.constants.LyricsLineBlurKey
import moe.rukamori.archivetune.constants.LyricsLineSpacingKey
import moe.rukamori.archivetune.constants.LyricsRomanizeChineseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeHindiKey
import moe.rukamori.archivetune.constants.LyricsRomanizeJapaneseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeKoreanKey
import moe.rukamori.archivetune.constants.LyricsRomanizeOtherLanguagesKey
import moe.rukamori.archivetune.constants.LyricsTextSizeKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.lyrics.LyricsRomanizationPreferences
import moe.rukamori.archivetune.ui.theme.rememberArchiveTuneLyricsFontFamily
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

@Stable
internal class LyricsStyleState(
    val lyricsClick: Boolean,
    val lyricsTextSize: Float,
    val lyricsLineSpacing: Float,
    val lyricsLineBlur: Boolean,
    val romanizationPreferences: LyricsRomanizationPreferences,
    val textColor: Color,
    val normalTextStyle: TextStyle,
    val accompanimentTextStyle: TextStyle,
    val phoneticTextStyle: TextStyle,
)

@Composable
internal fun rememberLyricsStyleState(
    textColorOverride: Color?,
    lyricsLineBlurOverride: Boolean?,
): LyricsStyleState {
    val (lyricsClick) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsTextSize) = rememberPreference(LyricsTextSizeKey, defaultValue = 26f)
    val (lyricsLineSpacing) = rememberPreference(LyricsLineSpacingKey, defaultValue = 1.3f)
    val (lyricsLineBlurPreference) = rememberPreference(LyricsLineBlurKey, defaultValue = true)
    val (romanizeChinese) = rememberPreference(LyricsRomanizeChineseKey, defaultValue = true)
    val (romanizeHindi) = rememberPreference(LyricsRomanizeHindiKey, defaultValue = true)
    val (romanizeJapanese) = rememberPreference(LyricsRomanizeJapaneseKey, defaultValue = true)
    val (romanizeKorean) = rememberPreference(LyricsRomanizeKoreanKey, defaultValue = true)
    val (romanizeOtherLanguages) = rememberPreference(LyricsRomanizeOtherLanguagesKey, defaultValue = true)

    val romanizationPreferences =
        remember(
            romanizeJapanese,
            romanizeKorean,
            romanizeChinese,
            romanizeHindi,
            romanizeOtherLanguages,
        ) {
            LyricsRomanizationPreferences(
                romanizeJapanese = romanizeJapanese,
                romanizeKorean = romanizeKorean,
                romanizeChinese = romanizeChinese,
                romanizeHindi = romanizeHindi,
                romanizeOther = romanizeOtherLanguages,
            )
        }

    val lyricsFontFamily = rememberArchiveTuneLyricsFontFamily()
    val playerBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val textColor =
        textColorOverride ?: if (playerBackground == PlayerBackgroundStyle.DEFAULT) {
            MaterialTheme.colorScheme.onBackground
        } else {
            Color.White
        }
    val lyricsLineBlur = lyricsLineBlurOverride ?: lyricsLineBlurPreference

    val typography = MaterialTheme.typography
    val normalTextStyle =
        remember(lyricsTextSize, lyricsLineSpacing, lyricsFontFamily, typography) {
            typography.headlineMedium.copy(
                fontSize = lyricsTextSize.sp,
                lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = lyricsFontFamily,
            )
        }
    val accompanimentTextStyle =
        remember(lyricsTextSize, lyricsLineSpacing, lyricsFontFamily, typography) {
            typography.titleLarge.copy(
                fontSize = (lyricsTextSize * 0.82f).sp,
                lineHeight = (lyricsTextSize * 0.82f * lyricsLineSpacing).sp,
                fontFamily = lyricsFontFamily,
            )
        }
    val phoneticTextStyle =
        remember(lyricsTextSize, lyricsLineSpacing, typography) {
            typography.bodyMedium.copy(
                fontSize = (lyricsTextSize * 0.55f).sp,
                lineHeight = (lyricsTextSize * 0.55f * lyricsLineSpacing).sp,
                fontWeight = FontWeight.Normal,
            )
        }

    return remember(
        lyricsClick,
        lyricsTextSize,
        lyricsLineSpacing,
        lyricsLineBlur,
        romanizationPreferences,
        textColor,
        normalTextStyle,
        accompanimentTextStyle,
        phoneticTextStyle,
    ) {
        LyricsStyleState(
            lyricsClick = lyricsClick,
            lyricsTextSize = lyricsTextSize,
            lyricsLineSpacing = lyricsLineSpacing,
            lyricsLineBlur = lyricsLineBlur,
            romanizationPreferences = romanizationPreferences,
            textColor = textColor,
            normalTextStyle = normalTextStyle,
            accompanimentTextStyle = accompanimentTextStyle,
            phoneticTextStyle = phoneticTextStyle,
        )
    }
}
