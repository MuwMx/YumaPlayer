/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PreferredLyricsProvider
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun LyricsProviderOrderDialog(
    initialOrder: List<PreferredLyricsProvider>,
    onDismiss: () -> Unit,
    onConfirm: (List<PreferredLyricsProvider>) -> Unit,
) {
    val providers = remember { mutableStateListOf(*initialOrder.toTypedArray()) }
    val lazyListState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            val item = providers.removeAt(from.index)
            providers.add(to.index, item)
        }

    DefaultDialog(
        onDismiss = onDismiss,
        buttons = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { onConfirm(providers.toList()) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = stringResource(R.string.set_first_lyrics_provider),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyColumn(
                state = lazyListState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
            ) {
                itemsIndexed(providers, key = { _, item -> item.name }) { index, provider ->
                    ReorderableItem(reorderableState, key = provider.name) {
                        val isFirst = index == 0
                        val containerColor =
                            if (isFirst) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        val contentColor =
                            if (isFirst) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = if (index < providers.size - 1) 4.dp else 0.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(containerColor)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = provider.displayName(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                painter = painterResource(R.drawable.drag_handle),
                                contentDescription = null,
                                tint = contentColor.copy(alpha = 0.6f),
                                modifier =
                                    Modifier
                                        .size(20.dp)
                                        .draggableHandle(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LyricsProvidersSection(
    enableBetterLyrics: Boolean,
    onEnableBetterLyricsChange: (Boolean) -> Unit,
    enableYouLyPlusLyrics: Boolean,
    onEnableYouLyPlusLyricsChange: (Boolean) -> Unit,
    enableLrclib: Boolean,
    onEnableLrclibChange: (Boolean) -> Unit,
    enableKugou: Boolean,
    onEnableKugouChange: (Boolean) -> Unit,
    enableUnisonLyrics: Boolean,
    onEnableUnisonLyricsChange: (Boolean) -> Unit,
    enableSimpMusicLyrics: Boolean,
    onEnableSimpMusicLyricsChange: (Boolean) -> Unit,
    enablePaxsenixLyrics: Boolean,
    onEnablePaxsenixLyricsChange: (Boolean) -> Unit,
    enablePaxsenixAppleMusicLyrics: Boolean,
    onEnablePaxsenixAppleMusicLyricsChange: (Boolean) -> Unit,
    enablePaxsenixNeteaseLyrics: Boolean,
    onEnablePaxsenixNeteaseLyricsChange: (Boolean) -> Unit,
    enablePaxsenixSpotifyLyrics: Boolean,
    onEnablePaxsenixSpotifyLyricsChange: (Boolean) -> Unit,
    enablePaxsenixMusixmatchLyrics: Boolean,
    onEnablePaxsenixMusixmatchLyricsChange: (Boolean) -> Unit,
    enablePaxsenixYouTubeLyrics: Boolean,
    onEnablePaxsenixYouTubeLyricsChange: (Boolean) -> Unit,
    providerOrder: List<PreferredLyricsProvider>,
    onOpenPaxsenixStats: () -> Unit,
    onOpenProviderOrderDialog: () -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.providers)) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.enable_betterlyrics)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = enableBetterLyrics,
                onCheckedChange = onEnableBetterLyricsChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.enable_youlyplus_lyrics)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = enableYouLyPlusLyrics,
                onCheckedChange = onEnableYouLyPlusLyricsChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.enable_lrclib)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = enableLrclib,
                onCheckedChange = onEnableLrclibChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.enable_kugou)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = enableKugou,
                onCheckedChange = onEnableKugouChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.enable_unison_lyrics)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = enableUnisonLyrics,
                onCheckedChange = onEnableUnisonLyricsChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.enable_simpmusic_lyrics)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = enableSimpMusicLyrics,
                onCheckedChange = onEnableSimpMusicLyricsChange,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.enable_paxsenix_lyrics)) },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = enablePaxsenixLyrics,
                onCheckedChange = onEnablePaxsenixLyricsChange,
            )
        }

        item(visible = enablePaxsenixLyrics) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.paxsenix_stats)) },
                icon = { Icon(painterResource(R.drawable.stats), null) },
                onClick = onOpenPaxsenixStats,
            )
        }

        item(visible = enablePaxsenixLyrics) {
            SwitchPreference(
                title = { Text("Paxsenix: Apple Music") },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = enablePaxsenixAppleMusicLyrics,
                onCheckedChange = onEnablePaxsenixAppleMusicLyricsChange,
            )
        }

        item(visible = enablePaxsenixLyrics) {
            SwitchPreference(
                title = { Text("Paxsenix: NetEase") },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = enablePaxsenixNeteaseLyrics,
                onCheckedChange = onEnablePaxsenixNeteaseLyricsChange,
            )
        }

        item(visible = enablePaxsenixLyrics) {
            SwitchPreference(
                title = { Text("Paxsenix: Spotify") },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = enablePaxsenixSpotifyLyrics,
                onCheckedChange = onEnablePaxsenixSpotifyLyricsChange,
            )
        }

        item(visible = enablePaxsenixLyrics) {
            SwitchPreference(
                title = { Text("Paxsenix: Musixmatch") },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = enablePaxsenixMusixmatchLyrics,
                onCheckedChange = onEnablePaxsenixMusixmatchLyricsChange,
            )
        }

        item(visible = enablePaxsenixLyrics) {
            SwitchPreference(
                title = { Text("Paxsenix: YouTube") },
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                checked = enablePaxsenixYouTubeLyrics,
                onCheckedChange = onEnablePaxsenixYouTubeLyricsChange,
            )
        }

        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.set_first_lyrics_provider)) },
                description = providerOrder.firstOrNull()?.displayName(),
                icon = { Icon(painterResource(R.drawable.lyrics), null) },
                onClick = onOpenProviderOrderDialog,
            )
        }
    }
}
