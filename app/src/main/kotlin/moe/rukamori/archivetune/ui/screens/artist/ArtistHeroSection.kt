/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.artist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.HeaderType
import moe.rukamori.archivetune.ui.component.YumaMorphingHeader
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.ui.utils.formatCompactCount
import java.util.Locale

@Composable
fun ArtistMorphingHeader(
    imageUrl: String?,
    collapseFraction: Float,
    modifier: Modifier = Modifier,
) {
    if (imageUrl != null) {
        YumaMorphingHeader(
            imageUrl = imageUrl,
            collapseFraction = collapseFraction,
            type = HeaderType.ARTIST,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistHeroContent(
    artistPage: ArtistPage?,
    libraryArtist: Artist?,
    librarySongs: List<Song>,
    libraryAlbums: List<Album>,
    showLocal: Boolean,
    expandedHeight: Dp,
    topPadding: Dp,
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
    database: MusicDatabase = LocalDatabase.current,
) {
    val artistName = artistPage?.artist?.title ?: libraryArtist?.artist?.name

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = topPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(expandedHeight * 0.6f))

        // Artist Name
        Text(
            text = artistName ?: stringResource(R.string.unknown_artist),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        // Artist Description (expandable)
        val description = artistPage?.description
        if (!description.isNullOrBlank()) {
            var isExpanded by rememberSaveable { mutableStateOf(false) }
            val maxLines = if (isExpanded) Int.MAX_VALUE else 4

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .combinedClickable(
                            onClick = { isExpanded = !isExpanded },
                            onLongClick = {},
                        ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!isExpanded) {
                    Text(
                        text = stringResource(R.string.more),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        val songsLabel = stringResource(R.string.songs)
        val albumsLabel = stringResource(R.string.albums)
        val monthlyListenersLabel = stringResource(R.string.monthly_listeners)
        val subscribersLabel = stringResource(R.string.subscribers)
        val artistStats =
            remember(
                showLocal,
                artistPage,
                librarySongs.size,
                libraryAlbums.size,
                songsLabel,
                albumsLabel,
                monthlyListenersLabel,
                subscribersLabel,
            ) {
                buildArtistStats(
                    showLocal = showLocal,
                    artistPage = artistPage,
                    librarySongCount = librarySongs.size,
                    libraryAlbumCount = libraryAlbums.size,
                    songsLabel = songsLabel,
                    albumsLabel = albumsLabel,
                    monthlyListenersLabel = monthlyListenersLabel,
                    subscribersLabel = subscribersLabel,
                )
            }

        if (artistStats.isNotEmpty()) {
            ArtistStatsButtonGroup(stats = artistStats)
        }

        // Action Buttons
        val shuffleEnabled = if (showLocal) librarySongs.isNotEmpty() else artistPage?.artist?.shuffleEndpoint != null
        val shuffleLabel = stringResource(R.string.shuffle)

        val playEndpoint = if (!showLocal) artistPage?.artist?.playEndpoint ?: artistPage?.artist?.shuffleEndpoint else null
        val playEnabled = if (showLocal) librarySongs.isNotEmpty() else playEndpoint != null

        val isSubscribed = libraryArtist?.artist?.bookmarkedAt != null
        val subscribeContentDescription =
            stringResource(
                if (isSubscribed) R.string.subscribed else R.string.subscribe,
            )
        val subscribeBg =
            if (isSubscribed) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                LocalYumaColors.current.glassBackground
            }
        val subscribeFg =
            if (isSubscribed) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }

        val radioEndpoint = artistPage?.artist?.radioEndpoint
        val radioLabel = stringResource(R.string.radio)
        val defaultArtistName = stringResource(R.string.unknown_artist)
        val queueTitle = libraryArtist?.artist?.name ?: artistName ?: defaultArtistName

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding, vertical = 8.dp)
                    .animateContentSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .yumaClickable(
                            enabled = shuffleEnabled,
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = {
                                if (!showLocal) {
                                    artistPage?.artist?.shuffleEndpoint?.let { shuffleEndpoint ->
                                        playerConnection.playQueue(YouTubeQueue(shuffleEndpoint))
                                    }
                                } else if (librarySongs.isNotEmpty()) {
                                    val shuffledSongs = librarySongs.shuffled()
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = queueTitle,
                                            items = shuffledSongs.map { it.toMediaItem() },
                                        ),
                                    )
                                }
                            },
                        )
                        .yumaGlassCard(
                            shape = CircleShape,
                            backgroundColor = LocalYumaColors.current.glassBackground,
                        )
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) {
                            contentDescription = shuffleLabel
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = null,
                    tint =
                        if (shuffleEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                )
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .yumaClickable(
                            enabled = playEnabled,
                            pressedScale = SettingsAnimations.PressScale,
                            onClick = {
                                if (!showLocal) {
                                    playEndpoint?.let {
                                        playerConnection.playQueue(YouTubeQueue(it))
                                    }
                                } else if (librarySongs.isNotEmpty()) {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = queueTitle,
                                            items = librarySongs.map { it.toMediaItem() },
                                        ),
                                    )
                                }
                            },
                        )
                        .background(
                            color =
                                if (playEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                                },
                            shape = CircleShape,
                        )
                        .clip(CircleShape)
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint =
                            if (playEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                            },
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.play),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color =
                            if (playEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            AnimatedVisibility(
                visible = !showLocal,
                enter = fadeIn(tween(150)) + expandHorizontally(tween(150)),
                exit = fadeOut(tween(150)) + shrinkHorizontally(tween(150)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .yumaClickable(
                                pressedScale = SettingsAnimations.PressScale,
                                onClick = {
                                    database.transaction {
                                        val artist = libraryArtist?.artist
                                        if (artist != null) {
                                            update(artist.toggleLike())
                                        } else {
                                            artistPage?.artist?.let {
                                                insert(
                                                    ArtistEntity(
                                                        id = it.id,
                                                        name = it.title,
                                                        channelId = it.channelId,
                                                        thumbnailUrl = it.thumbnail,
                                                    ).toggleLike(),
                                                )
                                            }
                                        }
                                    }
                                },
                            )
                            .yumaGlassCard(
                                shape = CircleShape,
                                backgroundColor = subscribeBg,
                            )
                            .clip(CircleShape)
                            .semantics(mergeDescendants = true) {
                                contentDescription = subscribeContentDescription
                                role = Role.Checkbox
                                toggleableState = ToggleableState(isSubscribed)
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (isSubscribed) R.drawable.done else R.drawable.add,
                            ),
                        contentDescription = null,
                        tint = subscribeFg,
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                    )
                }
            }

            AnimatedVisibility(
                visible = radioEndpoint != null,
                enter = fadeIn(tween(150)) + expandHorizontally(tween(150)),
                exit = fadeOut(tween(150)) + shrinkHorizontally(tween(150)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .yumaClickable(
                                pressedScale = SettingsAnimations.PressScale,
                                onClick = {
                                    radioEndpoint?.let {
                                        playerConnection.playQueue(YouTubeQueue(it))
                                    }
                                },
                            )
                            .yumaGlassCard(
                                shape = CircleShape,
                                backgroundColor = LocalYumaColors.current.glassBackground,
                            )
                            .clip(CircleShape)
                            .semantics(mergeDescendants = true) {
                                contentDescription = radioLabel
                                role = Role.Button
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.radio),
                        contentDescription = null,
                        modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ArtistStatsButtonGroup(
    stats: List<ArtistStatItemUiModel>,
    modifier: Modifier = Modifier,
) {
    val chipShape = remember { RoundedCornerShape(SettingsDimensions.LibraryCardRadius) }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = SettingsDimensions.ScreenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.fastForEach { stat ->
            Row(
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .yumaGlassCard(
                            shape = chipShape,
                            backgroundColor = LocalYumaColors.current.glassBackground,
                        )
                        .clip(chipShape)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = stat.contentDescription
                        },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = stat.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stat.value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun buildArtistStats(
    showLocal: Boolean,
    artistPage: ArtistPage?,
    librarySongCount: Int,
    libraryAlbumCount: Int,
    songsLabel: String,
    albumsLabel: String,
    monthlyListenersLabel: String,
    subscribersLabel: String,
): List<ArtistStatItemUiModel> {
    val songSections =
        artistPage?.sections?.filter { section ->
            section.items.any { it is SongItem }
        }
    val songCount =
        if (showLocal) {
            librarySongCount
        } else {
            songSections
                ?.asSequence()
                ?.flatMap { it.items.asSequence() }
                ?.filterIsInstance<SongItem>()
                ?.distinctBy { it.id }
                ?.count() ?: librarySongCount
        }
    val hasMoreSongs = !showLocal && songSections?.any { it.moreEndpoint != null } == true

    val albumSections =
        artistPage?.sections?.filter { section ->
            section.items.any { it is AlbumItem }
        }
    val albumCount =
        if (showLocal) {
            libraryAlbumCount
        } else {
            albumSections
                ?.asSequence()
                ?.flatMap { it.items.asSequence() }
                ?.filterIsInstance<AlbumItem>()
                ?.distinctBy { it.id }
                ?.count() ?: libraryAlbumCount
        }
    val hasMoreAlbums = !showLocal && albumSections?.any { it.moreEndpoint != null } == true

    return buildList {
        artistPage?.artist?.monthlyListenerCountText?.toArtistCompactCountText()?.let { value ->
            add(
                ArtistStatItemUiModel(
                    icon = Icons.Outlined.Hearing,
                    value = value,
                    contentDescription = "$monthlyListenersLabel $value",
                ),
            )
        }

        artistPage?.artist?.subscriberCountText?.toArtistCompactCountText()?.let { value ->
            add(
                ArtistStatItemUiModel(
                    icon = Icons.Outlined.PersonAdd,
                    value = value,
                    contentDescription = "$subscribersLabel $value",
                ),
            )
        }

        if (songCount > 0) {
            val value = compactCountText(songCount, hasMoreSongs)
            add(
                ArtistStatItemUiModel(
                    icon = Icons.Outlined.MusicNote,
                    value = value,
                    contentDescription = "$songsLabel $value",
                ),
            )
        }

        if (albumCount > 0) {
            val value = compactCountText(albumCount, hasMoreAlbums)
            add(
                ArtistStatItemUiModel(
                    icon = Icons.Outlined.Album,
                    value = value,
                    contentDescription = "$albumsLabel $value",
                ),
            )
        }
    }
}

fun compactCountText(
    count: Int,
    hasMore: Boolean,
): String {
    val value = formatCompactCount(count.toLong())
    return if (hasMore) "$value+" else value
}

private val CompactArtistCountPattern = Regex("""\d+(?:[.,]\d+)?\s*[KMB]""", RegexOption.IGNORE_CASE)
private val ArtistCountPattern = Regex("""\d+(?:[.,]\d+)*""")

fun String.toArtistCompactCountText(): String? {
    val compactText = CompactArtistCountPattern.find(this)?.value
    if (compactText != null) {
        return compactText
            .filterNot { it.isWhitespace() }
            .replace(',', '.')
            .uppercase(Locale.US)
    }

    val count =
        ArtistCountPattern
            .find(this)
            ?.value
            ?.filter { it.isDigit() }
            ?.toLongOrNull()
            ?: return null

    return formatCompactCount(count)
}
