/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.models

/**
 * Personalized Spotify home feed returned by the `home` GQL operation.
 * Mirrors what open.spotify.com shows on its landing page: Daily Mix,
 * Discover Weekly, Release Radar, Jump back in, recently played, etc.
 */
data class SpotifyHomeFeed(
    val greeting: String?,
    val sections: List<SpotifyHomeFeedSection>,
)

data class SpotifyHomeFeedSection(
    val sectionUri: String,
    val title: String?,
    val typename: String,
    val totalCount: Int,
    val items: List<SpotifyHomeFeedItem>,
) {
    val isShortsOrRecent: Boolean
        get() = typename == "HomeShortsSectionData" || typename == "HomeRecentlyPlayedSectionData" || (title.isNullOrBlank() && items.isNotEmpty())
}

sealed class SpotifyHomeFeedItem {
    abstract val uri: String
    abstract val imageUrl: String?
    abstract val images: List<SpotifyImage>

    data class Playlist(
        override val uri: String,
        val id: String,
        val name: String,
        val description: String?,
        val format: String?,
        val totalCount: Int,
        override val imageUrl: String?,
        val extractedColorHex: String?,
        val ownerName: String?,
        val madeForUsername: String?,
        override val images: List<SpotifyImage> = emptyList(),
    ) : SpotifyHomeFeedItem()

    data class Album(
        override val uri: String,
        val id: String,
        val name: String,
        val albumType: String?,
        val artists: List<SpotifySimpleArtist>,
        override val imageUrl: String?,
        override val images: List<SpotifyImage> = emptyList(),
    ) : SpotifyHomeFeedItem()

    data class Artist(
        override val uri: String,
        val id: String,
        val name: String,
        override val imageUrl: String?,
        override val images: List<SpotifyImage> = emptyList(),
    ) : SpotifyHomeFeedItem()
}
