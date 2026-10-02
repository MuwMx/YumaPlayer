/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.menu

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.component.TextFieldDialog

internal val TextFieldValueSaver: Saver<TextFieldValue, *> =
    Saver(
        save = { it.text },
        restore = { text -> TextFieldValue(text, TextRange(text.length)) },
    )

@Composable
internal fun SongMenuEditDialog(
    isVisible: Boolean,
    song: Song,
    database: MusicDatabase,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    onDismissMenu: () -> Unit,
) {
    if (!isVisible) return

    var titleField by rememberSaveable(stateSaver = TextFieldValueSaver) {
        mutableStateOf(TextFieldValue(song.song.title))
    }

    var artistField by rememberSaveable(stateSaver = TextFieldValueSaver) {
        mutableStateOf(
            TextFieldValue(
                song.artists.mapNotNull { it.name.takeIf(String::isNotBlank) }.joinToString(", "),
            ),
        )
    }

    TextFieldDialog(
        icon = {
            Icon(
                painter = painterResource(R.drawable.edit),
                contentDescription = null,
            )
        },
        title = {
            Text(text = stringResource(R.string.edit_song))
        },
        textFields =
            listOf(
                stringResource(R.string.song_title) to titleField,
                stringResource(R.string.artist_name) to artistField,
            ),
        onTextFieldsChange = { index, newValue ->
            if (index == 0) {
                titleField = newValue
            } else {
                artistField = newValue
            }
        },
        onDoneMultiple = { values ->
            val newTitle = values[0]
            val newArtist = values[1]

            coroutineScope.launch {
                database.query {
                    update(song.song.copy(title = newTitle))
                    val artist = song.artists.firstOrNull()
                    if (artist != null) {
                        update(artist.copy(name = newArtist))
                    }
                }
                onDismiss()
                onDismissMenu()
            }
        },
        onDismiss = onDismiss,
    )
}
