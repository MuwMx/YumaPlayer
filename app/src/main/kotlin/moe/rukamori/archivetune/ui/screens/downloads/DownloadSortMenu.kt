/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.DownloadLibrarySortField
import moe.rukamori.archivetune.viewmodels.DownloadLibrarySortOrder

@Composable
internal fun DownloadSortMenu(
    sortField: DownloadLibrarySortField,
    sortOrder: DownloadLibrarySortOrder,
    onSortFieldSelected: (DownloadLibrarySortField) -> Unit,
    onSortOrderSelected: (DownloadLibrarySortOrder) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var sortOrderMenuExpanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                painter = painterResource(R.drawable.tune),
                contentDescription = stringResource(R.string.sort_by_name),
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = {
                menuExpanded = false
                sortOrderMenuExpanded = false
            },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_by_name)) },
                trailingIcon = {
                    Icon(
                        painterResource(
                            if (sortField == DownloadLibrarySortField.NAME) {
                                R.drawable.radio_button_checked
                            } else {
                                R.drawable.radio_button_unchecked
                            },
                        ),
                        contentDescription = null,
                    )
                },
                onClick = { onSortFieldSelected(DownloadLibrarySortField.NAME) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_by_last_updated)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.calendar_today), contentDescription = null)
                },
                trailingIcon = {
                    Icon(
                        painterResource(
                            if (sortField == DownloadLibrarySortField.DATE_MODIFIED) {
                                R.drawable.radio_button_checked
                            } else {
                                R.drawable.radio_button_unchecked
                            },
                        ),
                        contentDescription = null,
                    )
                },
                onClick = { onSortFieldSelected(DownloadLibrarySortField.DATE_MODIFIED) },
            )
            Spacer(Modifier.height(6.dp))
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (sortOrder == DownloadLibrarySortOrder.ASCENDING) {
                                R.string.sort_order_ascending
                            } else {
                                R.string.sort_order_descending
                            },
                        ),
                    )
                },
                trailingIcon = {
                    Icon(painterResource(R.drawable.arrow_forward), contentDescription = null)
                },
                onClick = { sortOrderMenuExpanded = true },
            )
        }
        DropdownMenu(
            expanded = menuExpanded && sortOrderMenuExpanded,
            onDismissRequest = { sortOrderMenuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_order_ascending)) },
                leadingIcon = { Icon(painterResource(R.drawable.arrow_upward), contentDescription = null) },
                trailingIcon = {
                    Icon(
                        painterResource(
                            if (sortOrder == DownloadLibrarySortOrder.ASCENDING) {
                                R.drawable.radio_button_checked
                            } else {
                                R.drawable.radio_button_unchecked
                            },
                        ),
                        contentDescription = null,
                    )
                },
                onClick = {
                    onSortOrderSelected(DownloadLibrarySortOrder.ASCENDING)
                    menuExpanded = false
                    sortOrderMenuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_order_descending)) },
                leadingIcon = { Icon(painterResource(R.drawable.arrow_downward), contentDescription = null) },
                trailingIcon = {
                    Icon(
                        painterResource(
                            if (sortOrder == DownloadLibrarySortOrder.DESCENDING) {
                                R.drawable.radio_button_checked
                            } else {
                                R.drawable.radio_button_unchecked
                            },
                        ),
                        contentDescription = null,
                    )
                },
                onClick = {
                    onSortOrderSelected(DownloadLibrarySortOrder.DESCENDING)
                    menuExpanded = false
                    sortOrderMenuExpanded = false
                },
            )
        }
    }
}
