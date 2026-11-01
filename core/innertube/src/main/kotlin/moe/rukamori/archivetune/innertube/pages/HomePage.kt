/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.innertube.pages

import kotlinx.serialization.Serializable
import moe.rukamori.archivetune.innertube.models.Album
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.MusicCardShelfRenderer
import moe.rukamori.archivetune.innertube.models.MusicCarouselShelfRenderer
import moe.rukamori.archivetune.innertube.models.MusicShelfRenderer
import moe.rukamori.archivetune.innertube.models.MusicTwoRowItemRenderer
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SectionListRenderer
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.oddElements

@Serializable
data class HomePage(
    val chips: List<Chip>?,
    val sections: List<Section>,
    val continuation: String? = null,
) {
    @Serializable
    data class Chip(
        val title: String,
        val endpoint: BrowseEndpoint?,
        val deselectEndPoint: BrowseEndpoint?,
    ) {
        companion object {
            fun fromChipCloudChipRenderer(renderer: SectionListRenderer.Header.ChipCloudRenderer.Chip): Chip? {
                return Chip(
                    title =
                        renderer.chipCloudChipRenderer.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text ?: return null,
                    endpoint = renderer.chipCloudChipRenderer.navigationEndpoint.browseEndpoint,
                    deselectEndPoint = renderer.chipCloudChipRenderer.onDeselectedCommand?.browseEndpoint,
                )
            }
        }
    }

    @Serializable
    data class Section(
        val title: String,
        val label: String?,
        val thumbnail: String?,
        val endpoint: BrowseEndpoint?,
        val items: List<YTItem>,
        val playEndpoint: WatchEndpoint? = null,
        val featuredCards: List<FeaturedCard> = emptyList(),
    ) {
        @Serializable
        data class FeaturedCard(
            val id: String,
            val title: String,
            val subtitle: String?,
            val thumbnail: String?,
            val endpoint: BrowseEndpoint?,
            val playEndpoint: WatchEndpoint?,
            val shuffleEndpoint: WatchEndpoint?,
            val radioEndpoint: WatchEndpoint?,
            val itemIds: List<String>,
        )

        private data class ParsedFeaturedCard(
            val card: FeaturedCard,
            val items: List<SongItem>,
        )

        companion object {
            fun fromMusicCarouselShelfRenderer(renderer: MusicCarouselShelfRenderer): Section? {
                val featuredCards =
                    renderer.contents.mapNotNull { content ->
                        content.musicCardShelfRenderer?.toFeaturedCard()
                    }.distinctBy { featuredCard -> featuredCard.card.id }
                val items =
                    renderer.contents
                        .mapNotNull { content ->
                            content.musicTwoRowItemRenderer?.let { fromMusicTwoRowItemRenderer(it) }
                                ?: content.musicResponsiveListItemRenderer?.let { SearchPage.toYTItem(it) }
                        } + featuredCards.flatMap { card -> card.items }
                if (items.isEmpty() && featuredCards.isEmpty()) return null

                return Section(
                    title =
                        renderer.header
                            ?.musicCarouselShelfBasicHeaderRenderer
                            ?.title
                            ?.runs
                            ?.firstOrNull()
                            ?.text ?: return null,
                    label =
                        renderer.header.musicCarouselShelfBasicHeaderRenderer.strapline
                            ?.runs
                            ?.firstOrNull()
                            ?.text,
                    thumbnail =
                        renderer.header.musicCarouselShelfBasicHeaderRenderer.thumbnail
                            ?.musicThumbnailRenderer
                            ?.getThumbnailUrl(),
                    endpoint =
                        renderer.header.musicCarouselShelfBasicHeaderRenderer.moreContentButton
                            ?.buttonRenderer
                            ?.navigationEndpoint
                            ?.browseEndpoint,
                    items = items,
                    playEndpoint =
                        renderer.header.musicCarouselShelfBasicHeaderRenderer.moreContentButton
                            ?.buttonRenderer
                            ?.let { button ->
                                button.command?.anyWatchEndpoint ?: button.navigationEndpoint?.anyWatchEndpoint
                            }
                            ?.takeIf { endpoint -> !endpoint.playlistId.isNullOrBlank() },
                    featuredCards = featuredCards.map { it.card },
                )
            }

            fun fromMusicCardShelfRenderer(renderer: MusicCardShelfRenderer): Section? {
                val featuredCard = renderer.toFeaturedCard() ?: return null

                val title =
                    renderer.header
                        ?.musicCardShelfHeaderBasicRenderer
                        ?.title
                        ?.runs
                        ?.joinToString(separator = "") { it.text }
                        ?.takeIf(String::isNotBlank)
                        ?: renderer.title.runs
                            ?.joinToString(separator = "") { it.text }
                            ?.takeIf(String::isNotBlank)
                        ?: return null

                return Section(
                    title = title,
                    label = null,
                    thumbnail = null,
                    endpoint = null,
                    items = featuredCard.items,
                    featuredCards = listOf(featuredCard.card),
                )
            }

            fun fromMusicShelfRenderer(renderer: MusicShelfRenderer): Section? {
                val title = renderer.title?.runs?.firstOrNull()?.text ?: return null
                val items =
                    renderer.contents.orEmpty().mapNotNull { content ->
                        content.musicResponsiveListItemRenderer?.let { SearchPage.toYTItem(it) }
                    }
                if (items.isEmpty()) return null

                return Section(
                    title = title,
                    label = null,
                    thumbnail = null,
                    endpoint =
                        renderer.moreContentButton
                            ?.buttonRenderer
                            ?.navigationEndpoint
                            ?.browseEndpoint,
                    items = items,
                    playEndpoint =
                        renderer.moreContentButton
                            ?.buttonRenderer
                            ?.let { button ->
                                button.command?.anyWatchEndpoint ?: button.navigationEndpoint?.anyWatchEndpoint
                            }
                            ?.takeIf { endpoint -> !endpoint.playlistId.isNullOrBlank() },
                )
            }

            private fun MusicCardShelfRenderer.toFeaturedCard(): ParsedFeaturedCard? {
                val items =
                    contents.orEmpty().mapNotNull { content ->
                        content.musicResponsiveListItemRenderer
                            ?.let { SearchPage.toYTItem(it) }
                            ?.let { it as? SongItem }
                    }

                val title =
                    title.runs
                        ?.joinToString(separator = "") { it.text }
                        ?.takeIf(String::isNotBlank)
                        ?: return null
                val endpoint =
                    onTap.browseEndpoint
                        ?.takeIf { browseEndpoint ->
                            browseEndpoint.isPlaylistEndpoint || browseEndpoint.browseId.startsWith("VL")
                        } ?: return null
                val buttonEndpoints =
                    buttons.associate { button ->
                        button.buttonRenderer.icon?.iconType to
                            (button.buttonRenderer.command?.anyWatchEndpoint
                                ?: button.buttonRenderer.navigationEndpoint?.anyWatchEndpoint)
                    }
                val playEndpoint = buttonEndpoints["PLAY_ARROW"]
                val shuffleEndpoint = buttonEndpoints["MUSIC_SHUFFLE"]
                val radioEndpoint = buttonEndpoints["MIX"]
                val id = endpoint.browseId.removePrefix("VL").takeIf(String::isNotBlank) ?: return null

                return ParsedFeaturedCard(
                    card =
                        FeaturedCard(
                            id = id,
                            title = title,
                            subtitle = subtitle.runs?.joinToString(separator = "") { it.text }?.takeIf(String::isNotBlank),
                            thumbnail = thumbnail.musicThumbnailRenderer?.getThumbnailUrl(),
                            endpoint = endpoint,
                            playEndpoint = playEndpoint,
                            shuffleEndpoint = shuffleEndpoint,
                            radioEndpoint = radioEndpoint,
                            itemIds = items.map(SongItem::id),
                        ),
                    items = items,
                )
            }

            private fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
                return when {
                    renderer.isSong -> {
                        val subtitleRuns = renderer.subtitle?.runs ?: return null
                        val (artistRuns, albumRuns) =
                            subtitleRuns.partition { run ->
                                run.navigationEndpoint
                                    ?.browseEndpoint
                                    ?.browseId
                                    ?.startsWith("UC") == true
                            }
                        val artists =
                            artistRuns.map {
                                Artist(
                                    name = it.text,
                                    id = it.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                                )
                            }
                        SongItem(
                            id = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null,
                            title =
                                renderer.title.runs
                                    ?.firstOrNull()
                                    ?.text ?: return null,
                            artists = artists,
                            album =
                                albumRuns
                                    .firstOrNull { run ->
                                        run.navigationEndpoint
                                            ?.browseEndpoint
                                            ?.browseId
                                            ?.startsWith("MPREb_") == true
                                    }?.let { run ->
                                        val endpoint = run.navigationEndpoint?.browseEndpoint ?: return null
                                        Album(
                                            name = run.text,
                                            id = endpoint.browseId,
                                        )
                                    },
                            duration = null,
                            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                            explicit =
                                renderer.subtitleBadges?.any {
                                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                                } == true,
                        )
                    }

                    renderer.isAlbum -> {
                        AlbumItem(
                            browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                            playlistId =
                                renderer.thumbnailOverlay
                                    ?.musicItemThumbnailOverlayRenderer
                                    ?.content
                                    ?.musicPlayButtonRenderer
                                    ?.playNavigationEndpoint
                                    ?.watchPlaylistEndpoint
                                    ?.playlistId ?: return null,
                            title =
                                renderer.title.runs
                                    ?.firstOrNull()
                                    ?.text ?: return null,
                            artists =
                                renderer.subtitle?.runs?.oddElements()?.drop(1)?.map {
                                    Artist(
                                        name = it.text,
                                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                                    )
                                },
                            year = null,
                            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                            explicit =
                                renderer.subtitleBadges?.find {
                                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                                } != null,
                        )
                    }

                    renderer.isPlaylist -> {
                        PlaylistItem(
                            id =
                                renderer.navigationEndpoint.browseEndpoint
                                    ?.browseId
                                    ?.removePrefix("VL") ?: return null,
                            title =
                                renderer.title.runs
                                    ?.firstOrNull()
                                    ?.text ?: return null,
                            author =
                                Artist(
                                    name =
                                        renderer.subtitle
                                            ?.runs
                                            ?.lastOrNull()
                                            ?.text ?: return null,
                                    id = null,
                                ),
                            songCountText = null,
                            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                            playEndpoint =
                                renderer.thumbnailOverlay
                                    ?.musicItemThumbnailOverlayRenderer
                                    ?.content
                                    ?.musicPlayButtonRenderer
                                    ?.playNavigationEndpoint
                                    ?.watchPlaylistEndpoint,
                            shuffleEndpoint =
                                renderer.menu
                                    ?.menuRenderer
                                    ?.items
                                    ?.find {
                                        it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                                    }?.menuNavigationItemRenderer
                                    ?.navigationEndpoint
                                    ?.watchPlaylistEndpoint,
                            radioEndpoint =
                                renderer.menu
                                    ?.menuRenderer
                                    ?.items
                                    ?.find {
                                        it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                                    }?.menuNavigationItemRenderer
                                    ?.navigationEndpoint
                                    ?.watchPlaylistEndpoint,
                        )
                    }

                    renderer.isArtist -> {
                        ArtistItem(
                            id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                            title =
                                renderer.title.runs
                                    ?.lastOrNull()
                                    ?.text ?: return null,
                            thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                            shuffleEndpoint =
                                renderer.menu
                                    ?.menuRenderer
                                    ?.items
                                    ?.find {
                                        it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                                    }?.menuNavigationItemRenderer
                                    ?.navigationEndpoint
                                    ?.watchPlaylistEndpoint ?: return null,
                            radioEndpoint =
                                renderer.menu.menuRenderer.items
                                    .find {
                                        it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                                    }?.menuNavigationItemRenderer
                                    ?.navigationEndpoint
                                    ?.watchPlaylistEndpoint ?: return null,
                        )
                    }

                    else -> {
                        null
                    }
                }
            }
        }
    }

    fun filterExplicit(enabled: Boolean = true) =
        if (enabled) {
            copy(
                sections =
                    sections.map {
                        it.copy(items = it.items.filterExplicit())
                    },
            )
        } else {
            this
        }
}

fun HomePage.Section.FeaturedCard.toPlaylistItem(): PlaylistItem =
    PlaylistItem(
        id = id,
        title = title,
        author = null,
        songCountText = subtitle,
        thumbnail = thumbnail,
        playEndpoint = playEndpoint,
        shuffleEndpoint = shuffleEndpoint,
        radioEndpoint = radioEndpoint,
    )
