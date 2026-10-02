/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.EqCapabilities
import moe.rukamori.archivetune.playback.PlayerConnection

@Composable
internal fun EqualizerDialogContent(
    eqEnabled: Boolean,
    selectedProfileId: String,
    currentProfileTitle: String,
    currentProfileSupportingText: String,
    caps: EqCapabilities?,
    bandCount: Int,
    bandLevelsMb: List<Int>,
    minMb: Int,
    maxMb: Int,
    outputGainEnabled: Boolean,
    outputGainLocal: Int,
    bassBoostEnabled: Boolean,
    bassBoostStrengthLocal: Int,
    virtualizerEnabled: Boolean,
    virtualizerStrengthLocal: Int,
    playerConnection: PlayerConnection,
    onEnabledChange: (Boolean) -> Unit,
    onOpenSystemEqualizer: () -> Unit,
    onSelectProfileId: (String) -> Unit,
    onShowManageProfiles: () -> Unit,
    onShowSaveProfile: () -> Unit,
    onImportClick: () -> Unit,
    onResetBands: () -> Unit,
    onBandLevelsChange: (List<Int>) -> Unit,
    onBandLevelsChangeFinished: () -> Unit,
    onOutputGainEnabledChange: (Boolean) -> Unit,
    onOutputGainLocalChange: (Int) -> Unit,
    onOutputGainFinished: (Int) -> Unit,
    onBassBoostEnabledChange: (Boolean) -> Unit,
    onBassBoostStrengthChange: (Int) -> Unit,
    onBassBoostFinished: (Int) -> Unit,
    onVirtualizerEnabledChange: (Boolean) -> Unit,
    onVirtualizerStrengthChange: (Int) -> Unit,
    onVirtualizerFinished: (Int) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EqActivateRow(
            enabled = eqEnabled,
            onEnabledChange = onEnabledChange,
        )

        EqHeroCard(
            enabled = eqEnabled,
            profileTitle = currentProfileTitle,
            profileSubtitle = currentProfileSupportingText,
            onOpenSystemEqualizer = onOpenSystemEqualizer,
        )

        if (caps == null || bandCount <= 0) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    CircularWavyProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.eq_waiting_for_audio_session),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            return@Column
        }

        EqualizerPresetsSection(
            caps = caps,
            selectedProfileId = selectedProfileId,
            playerConnection = playerConnection,
            onSelectProfileId = onSelectProfileId,
        )

        EqualizerProfilesSection(
            currentProfileTitle = currentProfileTitle,
            eqEnabled = eqEnabled,
            onShowManageProfiles = onShowManageProfiles,
            onShowSaveProfile = onShowSaveProfile,
            onImportClick = onImportClick,
        )

        EqualizerBandsSection(
            caps = caps,
            bandCount = bandCount,
            bandLevelsMb = bandLevelsMb,
            minMb = minMb,
            maxMb = maxMb,
            onResetBands = onResetBands,
            onBandLevelsChange = onBandLevelsChange,
            onBandLevelsChangeFinished = onBandLevelsChangeFinished,
        )

        EqualizerEffectsSections(
            outputGainEnabled = outputGainEnabled,
            outputGainLocal = outputGainLocal,
            bassBoostEnabled = bassBoostEnabled,
            bassBoostStrengthLocal = bassBoostStrengthLocal,
            virtualizerEnabled = virtualizerEnabled,
            virtualizerStrengthLocal = virtualizerStrengthLocal,
            onOutputGainEnabledChange = onOutputGainEnabledChange,
            onOutputGainLocalChange = onOutputGainLocalChange,
            onOutputGainFinished = onOutputGainFinished,
            onBassBoostEnabledChange = onBassBoostEnabledChange,
            onBassBoostStrengthChange = onBassBoostStrengthChange,
            onBassBoostFinished = onBassBoostFinished,
            onVirtualizerEnabledChange = onVirtualizerEnabledChange,
            onVirtualizerStrengthChange = onVirtualizerStrengthChange,
            onVirtualizerFinished = onVirtualizerFinished,
        )
    }
}
