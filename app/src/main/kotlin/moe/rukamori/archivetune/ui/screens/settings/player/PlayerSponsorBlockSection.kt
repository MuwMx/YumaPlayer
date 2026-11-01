/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings.player

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.sponsorblock.DEFAULT_SPONSOR_BLOCK_API_URL
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.sponsorblock.SponsorBlockCategoryUiModel
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSettingsScreenState

@Composable
internal fun PlayerSponsorBlockSection(
    state: SponsorBlockSettingsScreenState,
    onEnabledChange: (Boolean) -> Unit,
    onCategorySheetOpen: () -> Unit,
    onCategorySheetDismiss: () -> Unit,
    onCategoryCheckedChange: (SponsorBlockCategoryUiModel, Boolean) -> Unit,
    onCategorySelectionConfirm: () -> Unit,
    onApiUrlEditorOpen: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = (state as? SponsorBlockSettingsScreenState.Success)?.data
    val controlsEnabled = data != null
    val configurationEnabled = data != null && data.enabled
    val categoryOptions = data?.categoryOptions.orEmpty()
    val draftCategoryOptions = data?.draftCategoryOptions.orEmpty()
    val selectedCount = data?.selectedCategoryOptions?.size ?: 0

    if (data?.isCategorySheetVisible == true) {
        SponsorBlockCategoryBottomSheet(
            title = { Text(stringResource(R.string.sponsor_block_categories)) },
            values = categoryOptions,
            checkedValues = draftCategoryOptions,
            onDismiss = onCategorySheetDismiss,
            onValueCheckedChange = onCategoryCheckedChange,
            onConfirm = onCategorySelectionConfirm,
        )
    }

    PreferenceGroup(
        title = stringResource(R.string.sponsor_block_group),
        modifier = modifier.animateContentSize(SettingsAnimations.resizeSpring()),
    ) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.sponsor_block_use)) },
                icon = { Icon(painterResource(R.drawable.block), null) },
                checked = data?.enabled ?: false,
                onCheckedChange = onEnabledChange,
                isEnabled = controlsEnabled,
            )
        }

        item(visible = configurationEnabled) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.sponsor_block_categories)) },
                description = stringResource(R.string.sponsor_block_categories_desc),
                icon = { Icon(painterResource(R.drawable.fast_forward), null) },
                content = {
                    Spacer(Modifier.height(SettingsDimensions.PreferenceChipTopSpacing))
                    Box(
                        modifier = Modifier
                            .height(SettingsDimensions.PreferenceChipHeight)
                            .clip(RoundedCornerShape(SettingsDimensions.PreferenceChipCornerRadius))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = SettingsDimensions.PreferenceChipBgAlpha))
                            .padding(
                                horizontal = SettingsDimensions.PreferenceChipPaddingH,
                                vertical = SettingsDimensions.PreferenceChipPaddingV,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.n_selected,
                                selectedCount,
                                selectedCount,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                },
                showChevron = true,
                onClick = onCategorySheetOpen,
                isEnabled = configurationEnabled,
            )
        }

        item(visible = configurationEnabled) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.sponsor_block_api_url)) },
                description = data?.apiUrl ?: DEFAULT_SPONSOR_BLOCK_API_URL,
                icon = { Icon(painterResource(R.drawable.link), null) },
                onClick = onApiUrlEditorOpen,
                isEnabled = configurationEnabled,
            )
        }

        if (state is SponsorBlockSettingsScreenState.Error) {
            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.retry)) },
                    description = stringResource(state.messageRes),
                    onClick = onRetry,
                )
            }
        }
    }
}
