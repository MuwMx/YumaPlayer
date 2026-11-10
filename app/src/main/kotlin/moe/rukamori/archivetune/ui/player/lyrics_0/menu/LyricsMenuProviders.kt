package moe.rukamori.archivetune.ui.player.lyrics_0.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PreferredLyricsProvider
import moe.rukamori.archivetune.constants.PreferredLyricsProviderKey
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.lyrics.LyricsProvider
import moe.rukamori.archivetune.lyrics.parsing.LrcParser
import moe.rukamori.archivetune.lyrics.providers.BetterLyricsProvider
import moe.rukamori.archivetune.lyrics.providers.KuGouLyricsProvider
import moe.rukamori.archivetune.lyrics.providers.LrcLibLyricsProvider
import moe.rukamori.archivetune.lyrics.providers.SimpMusicLyricsProvider
import moe.rukamori.archivetune.lyrics.providers.UnisonLyricsProvider
import moe.rukamori.archivetune.lyrics.providers.YouLyPlusLyricsProvider
import moe.rukamori.archivetune.ui.main.LocalDatabase
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.ui.player.player_0.buttons.PlayerAction
import moe.rukamori.archivetune.ui.player.player_0.sett.SettingsMenuRow
import moe.rukamori.archivetune.ui.screens.settings.displayName
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.PlayerUiState
import moe.rukamori.archivetune.ui.theme.LocalArchiveTuneFontFamily
import moe.rukamori.archivetune.utils.rememberPreference

private enum class LyricsSyncKind {
    NONE,
    PLAIN,
    LINE,
    WORD,
}

private data class ProviderState(
    val isChecking: Boolean = true,
    val lyrics: String? = null,
    val syncKind: LyricsSyncKind = LyricsSyncKind.NONE,
)

private fun getLyricsProvider(provider: PreferredLyricsProvider): LyricsProvider =
    when (provider) {
        PreferredLyricsProvider.BETTER_LYRICS -> BetterLyricsProvider
        PreferredLyricsProvider.YOULY_PLUS -> YouLyPlusLyricsProvider
        PreferredLyricsProvider.LRCLIB -> LrcLibLyricsProvider
        PreferredLyricsProvider.KUGOU -> KuGouLyricsProvider
        PreferredLyricsProvider.SIMPMUSIC -> SimpMusicLyricsProvider
        PreferredLyricsProvider.UNISON -> UnisonLyricsProvider
    }

@Composable
internal fun LyricsMenuProviders(
    state: PlayerUiState,
    onAction: (PlayerAction) -> Unit,
    onBack: () -> Unit
) {
    val font = LocalArchiveTuneFontFamily.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) }

    val (currentProviderStr, setProviderStr) = rememberPreference(
        PreferredLyricsProviderKey,
        PreferredLyricsProvider.BETTER_LYRICS.name
    )

    val currentProvider = PreferredLyricsProvider.entries.find { it.name == currentProviderStr }
        ?: PreferredLyricsProvider.BETTER_LYRICS

    val availableProviders = PreferredLyricsProvider.entries

    val songId = mediaMetadata?.id ?: state.trackUrl
    val title = mediaMetadata?.title ?: state.title
    val artist = mediaMetadata?.artists?.joinToString { it.name } ?: state.artist
    val album = mediaMetadata?.album?.title ?: state.album
    val duration = mediaMetadata?.duration ?: (state.durationMs / 1000).toInt()

    var providerStates by remember(songId) {
        mutableStateOf(
            availableProviders.associateWith { ProviderState(isChecking = true) }
        )
    }

    LaunchedEffect(songId) {
        if (!songId.isNullOrBlank() && title.isNotBlank()) {
            availableProviders.forEach { provider ->
                launch(Dispatchers.IO) {
                    val providerImpl = getLyricsProvider(provider)
                    val result = runCatching {
                        withTimeoutOrNull(7000L) {
                            providerImpl.getLyrics(
                                id = songId,
                                title = title,
                                artist = artist,
                                album = album,
                                duration = duration,
                            ).getOrNull()
                        }
                    }.getOrNull()

                    val validLyrics = result?.takeIf {
                        it.isNotBlank() && it != LyricsEntity.LYRICS_NOT_FOUND
                    }
                    val syncKind = when {
                        validLyrics == null -> LyricsSyncKind.NONE
                        LrcParser.hasWordSyncedLyrics(validLyrics) || LrcParser.isTtml(validLyrics) -> LyricsSyncKind.WORD
                        LrcParser.isLineSyncedLrc(validLyrics) -> LyricsSyncKind.LINE
                        LrcParser.hasMeaningfulLyricsContent(validLyrics) -> LyricsSyncKind.PLAIN
                        else -> LyricsSyncKind.NONE
                    }

                    withContext(Dispatchers.Main) {
                        providerStates = providerStates + (provider to ProviderState(
                            isChecking = false,
                            lyrics = validLyrics,
                            syncKind = syncKind,
                        ))
                    }
                }
            }
        } else {
            providerStates = availableProviders.associateWith {
                ProviderState(isChecking = false)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.lyrics_provider),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = font,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )

            TextButton(onClick = onBack) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = font,
                )
            }
        }

        availableProviders.forEachIndexed { index, provider ->
            val isSelected = currentProvider == provider
            val providerState = providerStates[provider] ?: ProviderState(isChecking = false)

            val subtitle = when {
                isSelected -> stringResource(R.string.lyrics_provider_active)
                providerState.isChecking -> stringResource(R.string.lyrics_provider_checking)
                providerState.lyrics != null -> stringResource(R.string.lyrics_provider_found)
                else -> stringResource(R.string.lyrics_provider_not_found)
            }

            SettingsMenuRow(
                title = provider.displayName(),
                subtitle = subtitle,
                iconResId = if (isSelected) R.drawable.check else R.drawable.queue_music,
                onClick = {
                    setProviderStr(provider.name)
                    val lyrics = providerState.lyrics
                    if (lyrics != null && !songId.isNullOrBlank()) {
                        coroutineScope.launch(Dispatchers.IO) {
                            database.query {
                                replaceLyrics(songId, lyrics, LyricsEntity.Source.USER_SELECTION.value)
                            }
                        }
                    } else {
                        onAction(PlayerAction.SearchLyrics)
                    }
                    onBack()
                },
                index = index,
                count = availableProviders.size,
                trailingContent = {
                    if (providerState.isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (providerState.syncKind != LyricsSyncKind.NONE) {
                        val badgeLabel = when (providerState.syncKind) {
                            LyricsSyncKind.WORD -> "SYNCED"
                            LyricsSyncKind.LINE -> "LRC"
                            LyricsSyncKind.PLAIN -> "PLAIN"
                            LyricsSyncKind.NONE -> ""
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when (providerState.syncKind) {
                                        LyricsSyncKind.WORD -> MaterialTheme.colorScheme.primaryContainer
                                        LyricsSyncKind.LINE -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeLabel,
                                color = when (providerState.syncKind) {
                                    LyricsSyncKind.WORD -> MaterialTheme.colorScheme.onPrimaryContainer
                                    LyricsSyncKind.LINE -> MaterialTheme.colorScheme.onSecondaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = font,
                            )
                        }
                    }
                }
            )

            if (index < availableProviders.size - 1) {
                Spacer(modifier = Modifier.height(SettingsDimensions.SegmentedItemGap))
            }
        }
    }
}
