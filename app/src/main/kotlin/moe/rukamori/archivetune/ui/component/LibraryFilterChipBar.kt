package moe.rukamori.archivetune.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.constants.LibraryFilter
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard

@Immutable
data class LibraryFilterChip(
    val id: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int? = null,
)

@Composable
fun LibraryFilterChipBar(
    selectedId: String?,
    onSelected: (String) -> Unit,
    chips: List<LibraryFilterChip>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedId, chips) {
        val index = chips.indexOfFirst { it.id == selectedId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(
            items = chips,
            key = { it.id },
            contentType = { "library_filter_chip" },
        ) { chip ->
            LibraryFilterChipItem(
                label = stringResource(chip.labelRes),
                iconRes = chip.iconRes,
                selected = chip.id == selectedId,
                onClick = { onSelected(chip.id) },
            )
        }
    }
}

@Composable
fun LibraryFilterChipBar(
    selected: LibraryFilter?,
    onSelected: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
    chips: List<LibraryFilter> = listOf(
        LibraryFilter.SONGS,
        LibraryFilter.ARTISTS,
        LibraryFilter.ALBUMS,
        LibraryFilter.PLAYLISTS,
    ),
) {
    val chipItems = chips.map { filter ->
        LibraryFilterChip(
            id = filter.name,
            labelRes = filter.toLabelRes(),
            iconRes = filter.toIconRes(),
        )
    }
    LibraryFilterChipBar(
        selectedId = selected?.name,
        onSelected = { id -> onSelected(LibraryFilter.valueOf(id)) },
        chips = chipItems,
        modifier = modifier,
    )
}

@Composable
private fun LibraryFilterChipItem(
    label: String,
    iconRes: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val yumaColors = LocalYumaColors.current
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        yumaColors.glassBackground
    }
    val borderColor = if (selected) {
        Color.Transparent
    } else {
        yumaColors.glassBorder
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .height(SettingsDimensions.LibraryTabHeight)
            .yumaClickable(onClick = onClick)
            .yumaGlassCard(
                shape = CircleShape,
                backgroundColor = backgroundColor,
                borderColor = borderColor,
                strokeWidth = SettingsDimensions.GlassBorderThickness,
            )
            .clip(CircleShape)
            .padding(horizontal = SettingsDimensions.LibraryTabPaddingH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
            color = contentColor,
            maxLines = 1,
        )
    }
}

private fun LibraryFilter.toLabelRes(): Int = when (this) {
    LibraryFilter.SONGS -> moe.rukamori.archivetune.R.string.songs
    LibraryFilter.ARTISTS -> moe.rukamori.archivetune.R.string.artists
    LibraryFilter.ALBUMS -> moe.rukamori.archivetune.R.string.albums
    LibraryFilter.PLAYLISTS -> moe.rukamori.archivetune.R.string.playlists
    LibraryFilter.SPOTIFY -> moe.rukamori.archivetune.R.string.spotify_playlists
    LibraryFilter.LIBRARY -> moe.rukamori.archivetune.R.string.filter_library
}

private fun LibraryFilter.toIconRes(): Int? = when (this) {
    LibraryFilter.SONGS -> moe.rukamori.archivetune.R.drawable.music_note
    LibraryFilter.ARTISTS -> moe.rukamori.archivetune.R.drawable.person
    LibraryFilter.ALBUMS -> moe.rukamori.archivetune.R.drawable.album
    LibraryFilter.PLAYLISTS -> moe.rukamori.archivetune.R.drawable.queue_music
    LibraryFilter.SPOTIFY -> moe.rukamori.archivetune.R.drawable.spotify_icon
    LibraryFilter.LIBRARY -> moe.rukamori.archivetune.R.drawable.graphic_eq
}
