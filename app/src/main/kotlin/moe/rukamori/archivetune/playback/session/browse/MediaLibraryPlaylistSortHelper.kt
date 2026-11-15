/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.session.browse

import android.content.Context
import androidx.annotation.StringRes
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.playback.session.MediaLibraryTreeConstants
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale

internal data class AutoPlaylistSortOption(
    val sortType: PlaylistSongSortType,
    val descending: Boolean,
    @StringRes val titleRes: Int,
)

internal object MediaLibraryPlaylistSortHelper {
    const val PLAYLIST_ACTION_SHUFFLE = "_shuffle"
    const val PLAYLIST_ACTION_SORT = "_sort"
    const val PLAYLIST_ACTION_ADD_CURRENT_SONG = "_add_current_song"
    const val SORT_ORDER_ASC = "asc"
    const val SORT_ORDER_DESC = "desc"

    val PLAYLIST_SORT_OPTIONS =
        listOf(
            AutoPlaylistSortOption(
                sortType = PlaylistSongSortType.CUSTOM,
                descending = false,
                titleRes = R.string.sort_by_custom,
            ),
            AutoPlaylistSortOption(
                sortType = PlaylistSongSortType.CREATE_DATE,
                descending = true,
                titleRes = R.string.sort_by_create_date,
            ),
            AutoPlaylistSortOption(
                sortType = PlaylistSongSortType.CREATE_DATE,
                descending = false,
                titleRes = R.string.sort_by_create_date,
            ),
            AutoPlaylistSortOption(
                sortType = PlaylistSongSortType.NAME,
                descending = false,
                titleRes = R.string.sort_by_name,
            ),
            AutoPlaylistSortOption(
                sortType = PlaylistSongSortType.NAME,
                descending = true,
                titleRes = R.string.sort_by_name,
            ),
            AutoPlaylistSortOption(
                sortType = PlaylistSongSortType.ARTIST,
                descending = false,
                titleRes = R.string.sort_by_artist,
            ),
            AutoPlaylistSortOption(
                sortType = PlaylistSongSortType.ARTIST,
                descending = true,
                titleRes = R.string.sort_by_artist,
            ),
            AutoPlaylistSortOption(
                sortType = PlaylistSongSortType.PLAY_TIME,
                descending = true,
                titleRes = R.string.sort_by_play_time,
            ),
            AutoPlaylistSortOption(
                sortType = PlaylistSongSortType.PLAY_TIME,
                descending = false,
                titleRes = R.string.sort_by_play_time,
            ),
        )

    fun playlistSortPath(
        playlistId: String,
        option: AutoPlaylistSortOption,
    ): String = "${MediaLibraryTreeConstants.PLAYLIST}/$playlistId/$PLAYLIST_ACTION_SORT/${option.sortType.name}/${option.orderValue}"

    fun playlistSortOption(
        sortType: String?,
        order: String?,
    ): AutoPlaylistSortOption? {
        val parsedSortType =
            sortType?.let {
                runCatching { PlaylistSongSortType.valueOf(it) }.getOrNull()
            } ?: return null
        val descending =
            when (order) {
                SORT_ORDER_ASC -> false
                SORT_ORDER_DESC -> true
                else -> return null
            }
        return PLAYLIST_SORT_OPTIONS.firstOrNull {
            it.sortType == parsedSortType && it.descending == descending
        }
    }

    fun playlistSortTitle(
        context: Context,
        option: AutoPlaylistSortOption,
    ): String =
        context.getString(
            R.string.android_auto_sort_option_format,
            context.getString(option.titleRes),
            context.getString(
                if (option.descending) {
                    R.string.sort_order_descending
                } else {
                    R.string.sort_order_ascending
                },
            ),
        )

    private val AutoPlaylistSortOption.orderValue: String
        get() = if (descending) SORT_ORDER_DESC else SORT_ORDER_ASC

    fun PlaylistSongSortType.toSongSortType(): SongSortType =
        when (this) {
            PlaylistSongSortType.CUSTOM,
            PlaylistSongSortType.CREATE_DATE,
            -> SongSortType.CREATE_DATE

            PlaylistSongSortType.NAME -> SongSortType.NAME

            PlaylistSongSortType.ARTIST -> SongSortType.ARTIST

            PlaylistSongSortType.PLAY_TIME -> SongSortType.PLAY_TIME
        }

    fun sortDownloadedSongs(
        songs: List<Song>,
        sortOption: AutoPlaylistSortOption?,
    ): List<Song> {
        val option = sortOption ?: return songs
        val sorted =
            when (option.sortType) {
                PlaylistSongSortType.CUSTOM,
                PlaylistSongSortType.CREATE_DATE,
                -> {
                    songs.sortedBy { it.song.dateDownload ?: it.song.inLibrary ?: LocalDateTime.MIN }
                }

                PlaylistSongSortType.NAME -> {
                    songs.sortedWith(songTitleComparator())
                }

                PlaylistSongSortType.ARTIST -> {
                    songs.sortedWith(songArtistComparator())
                }

                PlaylistSongSortType.PLAY_TIME -> {
                    songs.sortedBy { it.song.totalPlayTime }
                }
            }
        return if (option.descending && option.sortType != PlaylistSongSortType.CUSTOM) {
            sorted.asReversed()
        } else {
            sorted
        }
    }

    fun List<PlaylistSong>.sortedPlaylistSongs(sortOption: AutoPlaylistSortOption?): List<PlaylistSong> {
        val option = sortOption ?: return sortedWith(compareBy({ it.map.position }, { it.map.id }))
        val sorted =
            when (option.sortType) {
                PlaylistSongSortType.CUSTOM -> {
                    sortedWith(compareBy({ it.map.position }, { it.map.id }))
                }

                PlaylistSongSortType.CREATE_DATE -> {
                    sortedWith(compareBy({ it.map.id }, { it.map.position }))
                }

                PlaylistSongSortType.NAME -> {
                    sortedWith(compareBy(songTitleCollator()) { it.song.song.title })
                }

                PlaylistSongSortType.ARTIST -> {
                    sortedWith(
                        compareBy(songTitleCollator()) { playlistSong ->
                            playlistSong.song.artists.joinToString("") { artist -> artist.name }
                        },
                    )
                }

                PlaylistSongSortType.PLAY_TIME -> {
                    sortedBy { it.song.song.totalPlayTime }
                }
            }
        return if (option.descending && option.sortType != PlaylistSongSortType.CUSTOM) {
            sorted.asReversed()
        } else {
            sorted
        }
    }

    private fun songTitleComparator(): Comparator<Song> = compareBy(songTitleCollator()) { it.song.title }

    private fun songArtistComparator(): Comparator<Song> =
        compareBy(songTitleCollator()) { song ->
            song.artists.joinToString("") { artist -> artist.name }
        }

    private fun songTitleCollator(): Collator =
        Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.PRIMARY
        }
}
