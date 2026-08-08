/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.SpotifyLikedSongsQueue
import moe.rukamori.archivetune.spotify.SpotifyLikedSongsViewModel
import moe.rukamori.archivetune.spotify.SpotifyMapper
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.component.DraggableScrollbar
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.SpotifyTrackListItem
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyLikedSongsScreen(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: SpotifyLikedSongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()

    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()
    val isPlaying by playerConnection?.isPlaying?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<MediaMetadata?>(null) }
    val lazyListState = rememberLazyListState()
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    val showTopBarTitle by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
    }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var resolvingTrackId by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    val filteredTracks =
        remember(tracks, query.text) {
            if (query.text.isBlank()) {
                tracks
            } else {
                tracks.filter { track ->
                    track.name.contains(query.text, ignoreCase = true) ||
                        track.artists.any { artist -> artist.name.contains(query.text, ignoreCase = true) } ||
                        track.album?.name?.contains(query.text, ignoreCase = true) == true
                }
            }
        }

    val loadedDurationMs =
        remember(tracks) {
            tracks.sumOf { track -> track.durationMs.toLong() }
        }

    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val errorContainerColor = MaterialTheme.colorScheme.errorContainer

    val gradientColors = remember(errorContainerColor) {
        listOf(errorContainerColor)
    }

    val gradientAlpha by remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex == 0) {
                val offset = lazyListState.firstVisibleItemScrollOffset
                (1f - (offset / 600f)).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    val transparentAppBar by remember {
        derivedStateOf { !disableBlur && !showTopBarTitle && !isSearching }
    }

    val topAppBarColors =
        if (transparentAppBar) {
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = MaterialTheme.colorScheme.onBackground,
            )
        } else {
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
            )
        }

    LaunchedEffect(isSearching) {
        if (isSearching) focusRequester.requestFocus()
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.collect { lastVisibleIndex ->
            if (
                tracks.size >= 5 &&
                lastVisibleIndex != null &&
                lastVisibleIndex >= tracks.size - 5
            ) {
                viewModel.loadMoreSongs()
            }
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    }

    fun playPlaylist(
        startIndex: Int = 0,
        shuffled: Boolean = false,
    ) {
        val queueTracks = if (shuffled) tracks.shuffled() else tracks
        if (queueTracks.isEmpty()) return
        val boundedStartIndex = startIndex.coerceIn(queueTracks.indices)
        val preloadTrack = queueTracks[boundedStartIndex]
        if (resolvingTrackId != null) return

        coroutineScope.launch {
            resolvingTrackId = preloadTrack.id
            try {
                val preloadItem = SpotifyPlaybackResolver.resolveToMetadata(preloadTrack)
                playerConnection?.playQueue(
                    SpotifyLikedSongsQueue(
                        title = context.getString(R.string.spotify_liked_songs),
                        initialTracks = queueTracks,
                        startIndex = boundedStartIndex,
                        preloadItem = preloadItem,
                    ),
                )
            } finally {
                resolvingTrackId = null
            }
        }
    }

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier =
            Modifier
                .fillMaxSize()
                .background(surfaceColor),
    ) {
        if (!disableBlur && gradientColors.isNotEmpty() && gradientAlpha > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxSize(0.55f)
                        .align(Alignment.TopCenter)
                        .zIndex(-1f)
                        .drawBehind {
                            val width = size.width
                            val height = size.height

                            drawRect(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                gradientColors[0].copy(alpha = gradientAlpha * 0.7f),
                                                gradientColors[0].copy(alpha = gradientAlpha * 0.35f),
                                                Color.Transparent,
                                            ),
                                        center = Offset(width * 0.5f, height * 0.25f),
                                        radius = width * 0.85f,
                                    ),
                            )
                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        colors =
                                            listOf(
                                                Color.Transparent,
                                                Color.Transparent,
                                                surfaceColor.copy(alpha = gradientAlpha * 0.22f),
                                                surfaceColor.copy(alpha = gradientAlpha * 0.55f),
                                                surfaceColor,
                                            ),
                                        startY = height * 0.4f,
                                        endY = height,
                                    ),
                            )
                        },
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!isSearching) {
                item(key = "header") {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = systemBarsTopPadding + AppBarHeight),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .padding(top = 8.dp, bottom = 20.dp),
                        ) {
                            Surface(
                                modifier =
                                    Modifier
                                        .size(240.dp)
                                        .shadow(
                                            elevation = 24.dp,
                                            shape = RoundedCornerShape(16.dp),
                                            spotColor =
                                                gradientColors.getOrNull(0)?.copy(alpha = 0.5f)
                                                    ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                        ),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.favorite),
                                        contentDescription = null,
                                        modifier = Modifier.size(80.dp),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.spotify_liked_songs),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )

                        Text(
                            text = stringResource(R.string.spotify_account),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .padding(top = 8.dp)
                                    .padding(horizontal = 32.dp),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val trackCount = if (total > 0) total else tracks.size
                            MetadataChip(
                                icon = R.drawable.music_note,
                                text = pluralStringResource(R.plurals.n_song, trackCount, trackCount),
                            )

                            if (loadedDurationMs > 0L) {
                                MetadataChip(
                                    icon = R.drawable.timer,
                                    text = makeTimeString(loadedDurationMs),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ToggleButton(
                                checked = false,
                                onCheckedChange = { viewModel.refresh() },
                                modifier = Modifier.size(48.dp),
                                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                colors =
                                    ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        checkedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.sync),
                                    contentDescription = stringResource(R.string.spotify_reload_playlist),
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            ToggleButton(
                                checked = false,
                                onCheckedChange = { playPlaylist() },
                                enabled = tracks.isNotEmpty(),
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                colors =
                                    ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = stringResource(R.string.play),
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            ToggleButton(
                                checked = false,
                                onCheckedChange = { playPlaylist(shuffled = true) },
                                enabled = tracks.isNotEmpty(),
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                colors =
                                    ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.shuffle),
                                    contentDescription = stringResource(R.string.shuffle),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = { playPlaylist(shuffled = true) },
                                enabled = tracks.isNotEmpty(),
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.mix),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            if (isLoading && tracks.isEmpty()) {
                item(key = "loading") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularWavyProgressIndicator()
                    }
                }
            }

            error?.let { errorMessage ->
                item(key = "error") {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }

            if (!isLoading && error == null && filteredTracks.isEmpty()) {
                item(key = "empty") {
                    EmptyPlaceholder(
                        icon = R.drawable.music_note,
                        text =
                            stringResource(
                                if (query.text.isBlank()) {
                                    R.string.spotify_no_tracks
                                } else {
                                    R.string.ai_model_no_results
                                },
                            ),
                    )
                }
            }

            itemsIndexed(
                items = filteredTracks,
                key = { index, track -> "spotify_track_${track.id}_$index" },
                contentType = { _, _ -> "spotify_track" },
            ) { index, track ->
                val trackIsActive =
                    remember(track, mediaMetadata) {
                        track.isResolvedAs(mediaMetadata)
                    }
                val trackIsResolving = resolvingTrackId == track.id

                SpotifyTrackListItem(
                    track = track,
                    isActive = trackIsActive || trackIsResolving,
                    isPlaying = isPlaying && !trackIsResolving,
                    trailingContent = {
                        if (trackIsResolving) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = resolvingTrackId == null || trackIsActive) {
                                if (trackIsActive) {
                                    playerConnection?.player?.togglePlayPause()
                                } else {
                                    val startIndex =
                                        tracks
                                            .indexOfFirst { item -> item.id == track.id }
                                            .takeIf { itemIndex -> itemIndex >= 0 }
                                            ?: index
                                    playPlaylist(startIndex = startIndex)
                                }
                            },
                )
            }
        }

        DraggableScrollbar(
            modifier =
                Modifier
                    .padding(
                        LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
                    ).align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = if (!isSearching) 1 else 0,
        )

        TopAppBar(
            colors = topAppBarColors,
            title = {
                if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                    )
                } else if (showTopBarTitle) {
                    Text(
                        text = stringResource(R.string.spotify_liked_songs),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (isSearching) {
                            isSearching = false
                            query = TextFieldValue()
                        } else {
                            navController.navigateUp()
                        }
                    },
                    onLongClick = {
                        if (!isSearching) navController.backToMain()
                    },
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (isSearching) R.drawable.close else R.drawable.arrow_back,
                            ),
                        contentDescription = null,
                    )
                }
            },
            actions = {
                if (!isSearching) {
                    IconButton(
                        onClick = { isSearching = true },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = null,
                        )
                    }
                }
            },
            scrollBehavior = scrollBehavior,
        )
    }
}

private fun SpotifyTrack.isResolvedAs(mediaMetadata: MediaMetadata?): Boolean {
    if (mediaMetadata == null) return false

    mediaMetadata.spotifyTrackId?.let { spotifyTrackId ->
        return id.isNotBlank() && spotifyTrackId == id
    }

    val titleMatches = name.equals(mediaMetadata.title, ignoreCase = true)
    val durationMatches =
        durationMs <= 0 ||
            mediaMetadata.duration <= 0 ||
            abs(durationMs.toLong() - mediaMetadata.duration * 1000L) <= 1_000L
    val albumMatches =
        album?.let { spotifyAlbum ->
            val currentAlbum = mediaMetadata.album ?: return false
            spotifyAlbum.id.isNotBlank() && spotifyAlbum.id == currentAlbum.id ||
                spotifyAlbum.name.equals(currentAlbum.title, ignoreCase = true)
        } ?: true
    val artistMatches =
        artists.isEmpty() ||
            mediaMetadata.artists.isEmpty() ||
            artists.any { spotifyArtist ->
                mediaMetadata.artists.any { artist ->
                    spotifyArtist.name.equals(artist.name, ignoreCase = true)
                }
            }
    val thumbnailMatches =
        SpotifyMapper.getTrackThumbnail(this)?.let { thumbnail ->
            thumbnail == mediaMetadata.thumbnailUrl
        } ?: true

    return titleMatches && durationMatches && albumMatches && artistMatches && thumbnailMatches
}

@Composable
private fun MetadataChip(
    icon: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
