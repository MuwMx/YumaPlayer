/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.viewmodels.AboutUiModel

@Composable
internal fun AboutOverflowMenu(
    expanded: Boolean,
    onShowMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onOpenTranslationContributors: () -> Unit,
    onOpenDependencyLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        IconButton(
            onClick = onShowMenu,
            onLongClick = {},
        ) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = stringResource(R.string.more_options),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissMenu,
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.about_contributor_translation)) },
                onClick = onOpenTranslationContributors,
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.translate),
                        contentDescription = null,
                    )
                },
            )

            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.about_license)) },
                onClick = onOpenDependencyLicenses,
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_about),
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

@Composable
internal fun AboutLoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(modifier = Modifier.size(40.dp))
    }
}

@Composable
internal fun AboutMessageContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun AboutSuccessContent(
    model: AboutUiModel,
    onOpenUri: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    listState: LazyListState,
    onRetryContributors: () -> Unit = {},
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "identity", contentType = "about_identity") {
            AboutContentContainer {
                AboutIdentityCard(
                    model = model,
                    onOpenUri = onOpenUri,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "lead_developer", contentType = "about_lead_developer") {
            AboutContentContainer {
                LeadDeveloperSection(
                    member = model.leadDeveloper,
                    onOpenUri = onOpenUri,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (!model.collaborators.isEmpty) {
            item(key = "team", contentType = "about_team_section") {
                AboutContentContainer {
                    TeamMemberSection(
                        title = stringResource(R.string.about_archive_tune_team),
                        members = model.collaborators,
                        onOpenUri = onOpenUri,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (!model.respecters.isEmpty) {
            item(key = "respecters", contentType = "about_team_section") {
                AboutContentContainer {
                    TeamMemberSection(
                        title = stringResource(R.string.about_respecter),
                        members = model.respecters,
                        onOpenUri = onOpenUri,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item(key = "contributors", contentType = "about_contributors") {
            AboutContentContainer {
                ContributorsSection(
                    state = model.contributorsState,
                    readMoreUrl = model.contributorsReadMoreUrl,
                    onOpenProfile = onOpenUri,
                    onRetry = onRetryContributors,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun AboutContentContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp),
        ) {
            content()
        }
    }
}
