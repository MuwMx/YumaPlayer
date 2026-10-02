/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

@Composable
internal fun SearchLyricsInputDialog(
    titleField: TextFieldValue,
    onTitleFieldChange: (TextFieldValue) -> Unit,
    artistField: TextFieldValue,
    onArtistFieldChange: (TextFieldValue) -> Unit,
    onDismiss: () -> Unit,
    onSearchOnline: () -> Unit,
    onSearch: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val useStackedActions = configuration.screenWidthDp < 600
    val contentArrangement = remember { Arrangement.spacedBy(20.dp) }
    val fieldArrangement = remember { Arrangement.spacedBy(16.dp) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier =
            Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                verticalArrangement = contentArrangement,
            ) {
                LyricsSearchInputHeader(onDismiss = onDismiss)

                Column(
                    verticalArrangement = fieldArrangement,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LyricsSearchTextField(
                        value = titleField,
                        onValueChange = onTitleFieldChange,
                        label = stringResource(R.string.song_title),
                        iconResId = R.drawable.music_note,
                        onSearch = onSearch,
                    )
                    LyricsSearchTextField(
                        value = artistField,
                        onValueChange = onArtistFieldChange,
                        label = stringResource(R.string.song_artists),
                        iconResId = R.drawable.artist,
                        onSearch = onSearch,
                    )
                }

                LyricsSearchInputActions(
                    useStackedActions = useStackedActions,
                    onSearchOnline = onSearchOnline,
                    onSearch = onSearch,
                )
            }
        }
    }
}
