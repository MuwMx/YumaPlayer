/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import android.app.SearchManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.viewmodels.LyricsMenuViewModel

@Composable
internal fun LyricsMenuDialogs(
    showEditDialog: Boolean,
    showRefetchLoadingDialog: Boolean,
    showSearchDialog: Boolean,
    showSearchResultDialog: Boolean,
    titleField: TextFieldValue,
    onTitleFieldChange: (TextFieldValue) -> Unit,
    artistField: TextFieldValue,
    onArtistFieldChange: (TextFieldValue) -> Unit,
    searchMediaMetadata: MediaMetadata,
    lyricsSearchState: moe.rukamori.archivetune.viewmodels.LyricsSearchScreenState,
    expandedSearchResultId: String?,
    onExpandedResultChange: (String?) -> Unit,
    onOpenSearchResult: () -> Unit,
    isNetworkAvailable: Boolean,
    lyricsProvider: () -> LyricsEntity?,
    mediaMetadataProvider: () -> MediaMetadata,
    viewModel: LyricsMenuViewModel,
    onDismissEdit: () -> Unit,
    onDismissSearch: () -> Unit,
    onDismissSearchResult: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    if (showEditDialog) {
        TextFieldDialog(
            onDismiss = onDismissEdit,
            icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
            title = { Text(text = mediaMetadataProvider().title) },
            initialTextFieldValue = TextFieldValue(lyricsProvider()?.lyrics.orEmpty()),
            singleLine = false,
            onDone = {
                viewModel.updateLyrics(mediaMetadataProvider(), it)
            },
        )
    }

    if (showRefetchLoadingDialog) {
        DefaultDialog(onDismiss = {}) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(12.dp),
            ) {
                LoadingIndicator(modifier = Modifier.size(40.dp))
            }
        }
    }

    if (showSearchDialog) {
        SearchLyricsInputDialog(
            titleField = titleField,
            onTitleFieldChange = onTitleFieldChange,
            artistField = artistField,
            onArtistFieldChange = onArtistFieldChange,
            onDismiss = onDismissSearch,
            onSearchOnline = {
                onDismissSearch()
                onDismiss()
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(
                                SearchManager.QUERY,
                                "${artistField.text} ${titleField.text} lyrics",
                            )
                        },
                    )
                } catch (_: Exception) {
                }
            },
            onSearch = {
                viewModel.search(
                    searchMediaMetadata.id,
                    titleField.text,
                    artistField.text,
                    searchMediaMetadata.album?.title,
                    searchMediaMetadata.duration,
                )
                onOpenSearchResult()

                if (!isNetworkAvailable) {
                    Toast.makeText(context, context.getString(R.string.error_no_internet), Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    if (showSearchResultDialog) {
        LyricsSearchResultDialog(
            state = lyricsSearchState,
            expandedResultId = expandedSearchResultId,
            onExpandedResultChange = { resultId ->
                onExpandedResultChange(resultId)
            },
            onResultSelected = { result ->
                onDismiss()
                viewModel.cancelSearch()
                viewModel.updateLyrics(
                    mediaMetadata = searchMediaMetadata,
                    lyrics = result.lyrics,
                    source = LyricsEntity.Source.USER_SELECTION,
                )
            },
            onDismiss = onDismissSearchResult,
        )
    }
}
