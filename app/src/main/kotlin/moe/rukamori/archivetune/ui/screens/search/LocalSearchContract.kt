/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.search

import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CONTENT_TYPE_LIST
import moe.rukamori.archivetune.viewmodels.LocalFilter

internal const val CONTENT_TYPE_LOCAL_SEARCH_HEADER = "header"
internal const val CONTENT_TYPE_LOCAL_SEARCH_ITEM = CONTENT_TYPE_LIST
internal const val CONTENT_TYPE_LOCAL_SEARCH_EMPTY = "empty"

internal const val KEY_LOCAL_SEARCH_EMPTY = "no_result"

internal val LOCAL_SEARCH_FILTER_CHIPS: List<Pair<LocalFilter, Int>> =
    listOf(
        LocalFilter.ALL to R.string.filter_all,
        LocalFilter.SONG to R.string.filter_songs,
        LocalFilter.ALBUM to R.string.filter_albums,
        LocalFilter.ARTIST to R.string.filter_artists,
        LocalFilter.PLAYLIST to R.string.filter_playlists,
    )

internal val LOCAL_SEARCH_FILTER_ICONS: Map<LocalFilter, Int> =
    mapOf(
        LocalFilter.ALL to R.drawable.ic_search,
        LocalFilter.SONG to R.drawable.music_note,
        LocalFilter.ALBUM to R.drawable.album,
        LocalFilter.ARTIST to R.drawable.person,
        LocalFilter.PLAYLIST to R.drawable.queue_music,
    )

internal fun localFilterIcon(filter: LocalFilter): Int =
    when (filter) {
        LocalFilter.SONG -> R.drawable.music_note
        LocalFilter.ALBUM -> R.drawable.album
        LocalFilter.ARTIST -> R.drawable.person
        LocalFilter.PLAYLIST -> R.drawable.queue_music
        LocalFilter.ALL -> R.drawable.ic_search
    }

internal fun localFilterTitle(filter: LocalFilter): Int =
    when (filter) {
        LocalFilter.SONG -> R.string.filter_songs
        LocalFilter.ALBUM -> R.string.filter_albums
        LocalFilter.ARTIST -> R.string.filter_artists
        LocalFilter.PLAYLIST -> R.string.filter_playlists
        LocalFilter.ALL -> error("")
    }
