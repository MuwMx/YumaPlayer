/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.db.entities.AlbumWithSongs
import moe.rukamori.archivetune.ui.component.ImmersiveArtworkTokens
import moe.rukamori.archivetune.ui.component.YumaImmersiveArtworkHeader
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState

@Composable
internal fun AlbumHeroHeader(
    albumWithSongs: AlbumWithSongs,
    songCount: Int,
    downloadState: HeaderDownloadState,
    systemBarsTopPadding: Dp,
    actions: AlbumActions,
    headerProgress: () -> Float,
    disableBlur: Boolean,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbnailUrl = albumWithSongs.album.thumbnailUrl
    val hasArtwork = !thumbnailUrl.isNullOrBlank()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (hasArtwork) {
                    Modifier.heightIn(min = ImmersiveArtworkTokens.HeaderMinHeight)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (hasArtwork) {
            YumaImmersiveArtworkHeader(
                thumbnailUrl = thumbnailUrl,
                progress = headerProgress,
                disableBlur = disableBlur,
                modifier = Modifier.matchParentSize(),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (hasArtwork) systemBarsTopPadding + AppBarHeight else 16.dp,
                    bottom = 16.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            AlbumHeroTitleMeta(
                albumWithSongs = albumWithSongs,
                songCount = songCount,
                onArtistClick = onArtistClick,
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumHeroLikeButton(
                    albumWithSongs = albumWithSongs,
                    actions = actions,
                )

                AlbumHeroPlayButton(
                    actions = actions,
                )

                AlbumHeroShuffleButton(
                    actions = actions,
                )

                AlbumHeroDownloadButton(
                    downloadState = downloadState,
                    actions = actions,
                )

                AlbumHeroMenuButton(
                    actions = actions,
                )
            }
        }
    }
}
