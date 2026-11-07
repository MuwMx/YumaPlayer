package moe.rukamori.archivetune.ui.player.player_0.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ArtistSeparatorsKey
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.main.LocalDatabase
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerArtistChoiceSheet(
    isVisible: Boolean,
    artists: List<MediaMetadata.Artist>,
    onArtistSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isVisible || artists.isEmpty()) return

    val (artistSeparators) = rememberPreference(ArtistSeparatorsKey, defaultValue = ",;/&")
    val splitArtists = remember(artists, artistSeparators) {
        val separatorRegex = if (artistSeparators.isNotEmpty()) "[${Regex.escape(artistSeparators)}]".toRegex() else null
        artists.flatMap { artist ->
            val id = artist.id
            if (separatorRegex != null && id != null) {
                val parts = artist.name.split(separatorRegex).map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size > 1) {
                    parts.mapIndexed { index, name ->
                        MediaMetadata.Artist(
                            id = if (index == 0) id else null,
                            name = name,
                        )
                    }
                } else listOf(artist)
            } else listOf(artist)
        }.filter { !it.id.isNullOrBlank() }.distinctBy { it.name }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(SettingsDimensions.BottomSheetCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                width = SettingsDimensions.GlassBorderThickness,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 12.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )

                Text(
                    text = stringResource(R.string.artists),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )

                splitArtists.forEach { artist ->
                    ArtistChoiceItem(
                        artist = artist,
                        onClick = {
                            artist.id?.let { id ->
                                onArtistSelect(id)
                                onDismiss()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistChoiceItem(
    artist: MediaMetadata.Artist,
    onClick: () -> Unit,
) {
    val database = LocalDatabase.current
    val artistId = artist.id
    val dbArtist by remember(artistId) {
        if (!artistId.isNullOrBlank()) {
            database.artist(artistId)
        } else {
            flowOf<Artist?>(null)
        }
    }.collectAsStateWithLifecycle(initialValue = null)

    var avatarUrl by remember(artistId, artist.thumbnailUrl) {
        mutableStateOf(artist.thumbnailUrl)
    }

    LaunchedEffect(artistId) {
        if (artistId.isNullOrBlank()) return@LaunchedEffect

        val cachedThumb = database.artist(artistId).firstOrNull()?.artist?.thumbnailUrl
        if (!cachedThumb.isNullOrBlank()) {
            avatarUrl = cachedThumb
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            YouTube.artist(artistId).onSuccess { page ->
                val fetchedUrl = page.artist.thumbnail
                if (!fetchedUrl.isNullOrBlank()) {
                    avatarUrl = fetchedUrl
                    database.query {
                        val existing = getArtistById(artistId)
                        if (existing != null) {
                            update(existing, page)
                        } else {
                            insert(
                                ArtistEntity(
                                    id = artistId,
                                    name = page.artist.title.ifEmpty { artist.name },
                                    thumbnailUrl = fetchedUrl.resize(1080, 1080),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    val displayThumbnail = avatarUrl ?: dbArtist?.artist?.thumbnailUrl ?: artist.thumbnailUrl

    ListItem(
        headlineContent = {
            Text(
                text = artist.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
        },
        leadingContent = {
            if (displayThumbnail.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.artist),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                AsyncImage(
                    model = displayThumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
