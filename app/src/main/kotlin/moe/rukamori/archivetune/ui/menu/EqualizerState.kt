/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.runtime.Composable
import moe.rukamori.archivetune.constants.EqualizerBandLevelsMbKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostEnabledKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostStrengthKey
import moe.rukamori.archivetune.constants.EqualizerCustomProfilesJsonKey
import moe.rukamori.archivetune.constants.EqualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainMbKey
import moe.rukamori.archivetune.constants.EqualizerSelectedProfileIdKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerStrengthKey
import moe.rukamori.archivetune.playback.EqProfile
import moe.rukamori.archivetune.playback.EqProfilesPayload
import moe.rukamori.archivetune.utils.rememberPreference

internal class EqualizerState(
    val eqEnabled: Boolean,
    val setEqEnabled: (Boolean) -> Unit,
    val selectedProfileId: String,
    val setSelectedProfileId: (String) -> Unit,
    val bandLevelsRaw: String,
    val setBandLevelsRaw: (String) -> Unit,
    val outputGainEnabled: Boolean,
    val setOutputGainEnabled: (Boolean) -> Unit,
    val outputGainMb: Int,
    val setOutputGainMb: (Int) -> Unit,
    val bassBoostEnabled: Boolean,
    val setBassBoostEnabled: (Boolean) -> Unit,
    val bassBoostStrength: Int,
    val setBassBoostStrength: (Int) -> Unit,
    val virtualizerEnabled: Boolean,
    val setVirtualizerEnabled: (Boolean) -> Unit,
    val virtualizerStrength: Int,
    val setVirtualizerStrength: (Int) -> Unit,
    val customProfilesJson: String,
    val setCustomProfilesJson: (String) -> Unit,
)

@Composable
internal fun rememberEqualizerState(): EqualizerState {
    val (eqEnabled, setEqEnabled) = rememberPreference(EqualizerEnabledKey, defaultValue = false)
    val (selectedProfileId, setSelectedProfileId) = rememberPreference(EqualizerSelectedProfileIdKey, defaultValue = "flat")
    val (bandLevelsRaw, setBandLevelsRaw) = rememberPreference(EqualizerBandLevelsMbKey, defaultValue = "")
    val (outputGainEnabled, setOutputGainEnabled) = rememberPreference(EqualizerOutputGainEnabledKey, defaultValue = false)
    val (outputGainMb, setOutputGainMb) = rememberPreference(EqualizerOutputGainMbKey, defaultValue = 0)
    val (bassBoostEnabled, setBassBoostEnabled) = rememberPreference(EqualizerBassBoostEnabledKey, defaultValue = false)
    val (bassBoostStrength, setBassBoostStrength) = rememberPreference(EqualizerBassBoostStrengthKey, defaultValue = 0)
    val (virtualizerEnabled, setVirtualizerEnabled) = rememberPreference(EqualizerVirtualizerEnabledKey, defaultValue = false)
    val (virtualizerStrength, setVirtualizerStrength) = rememberPreference(EqualizerVirtualizerStrengthKey, defaultValue = 0)
    val (customProfilesJson, setCustomProfilesJson) = rememberPreference(EqualizerCustomProfilesJsonKey, defaultValue = "")

    return EqualizerState(
        eqEnabled = eqEnabled,
        setEqEnabled = setEqEnabled,
        selectedProfileId = selectedProfileId,
        setSelectedProfileId = setSelectedProfileId,
        bandLevelsRaw = bandLevelsRaw,
        setBandLevelsRaw = setBandLevelsRaw,
        outputGainEnabled = outputGainEnabled,
        setOutputGainEnabled = setOutputGainEnabled,
        outputGainMb = outputGainMb,
        setOutputGainMb = setOutputGainMb,
        bassBoostEnabled = bassBoostEnabled,
        setBassBoostEnabled = setBassBoostEnabled,
        bassBoostStrength = bassBoostStrength,
        setBassBoostStrength = setBassBoostStrength,
        virtualizerEnabled = virtualizerEnabled,
        setVirtualizerEnabled = setVirtualizerEnabled,
        virtualizerStrength = virtualizerStrength,
        setVirtualizerStrength = setVirtualizerStrength,
        customProfilesJson = customProfilesJson,
        setCustomProfilesJson = setCustomProfilesJson,
    )
}

internal fun applyProfileToEqualizer(
    state: EqualizerState,
    profile: EqProfile,
    updatedPayload: EqProfilesPayload? = null,
) {
    state.setEqEnabled(true)
    state.setBandLevelsRaw(encodeBandLevelsMb(profile.bandLevelsMb))
    state.setOutputGainMb(profile.outputGainMb)
    state.setOutputGainEnabled(profile.outputGainMb != 0)
    state.setBassBoostStrength(profile.bassBoostStrength)
    state.setBassBoostEnabled(profile.bassBoostStrength != 0)
    state.setVirtualizerStrength(profile.virtualizerStrength)
    state.setVirtualizerEnabled(profile.virtualizerStrength != 0)
    if (updatedPayload != null) {
        state.setCustomProfilesJson(encodeProfilesPayload(updatedPayload))
    }
    state.setSelectedProfileId("profile:${profile.id}")
}

internal fun resolveProfileTitleAndSubtitle(
    selectedProfileId: String,
    systemPresets: List<String>?,
    profiles: List<EqProfile>,
    flatString: String,
    manualString: String,
    customProfileString: String,
    systemPresetString: String,
    profileHintString: String,
): Pair<String, String> {
    val activeProfileId = selectedProfileId.removePrefix("profile:").takeIf { selectedProfileId.startsWith("profile:") }
    val activeProfile = profiles.firstOrNull { it.id == activeProfileId }
    val selectedSystemPresetName =
        selectedProfileId
            .removePrefix("system:")
            .toIntOrNull()
            ?.let { index -> systemPresets?.getOrNull(index) }

    val title =
        when {
            selectedProfileId == "flat" -> flatString
            selectedSystemPresetName != null -> selectedSystemPresetName
            activeProfile != null -> activeProfile.name
            else -> manualString
        }

    val subtitle =
        when {
            activeProfile != null -> customProfileString
            selectedSystemPresetName != null -> systemPresetString
            else -> profileHintString
        }

    return title to subtitle
}
