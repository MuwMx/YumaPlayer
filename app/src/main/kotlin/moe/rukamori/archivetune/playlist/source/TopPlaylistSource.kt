/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.MyTopFilter
import moe.rukamori.archivetune.db.MusicDatabase
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Source for the top-songs playlist ("top_playlist/{top}" — currently a
 * registered route with no navigation entry points; unified here so the
 * dead-route cleanup is a one-line flip later). The top filter period is
 * mutable state (mirrors TopPlaylistViewModel.topPeriod).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TopPlaylistSource
    @Inject
    constructor(
        private val top: String,
        private val database: MusicDatabase,
    ) : PlaylistTracksSource {
    override val id: String = "top:$top"
    override val capabilities: PlaylistCapabilities = PlaylistCapabilities.Top

    /** Period filter; the unified screen's sort header chip drives this. */
    val topPeriod = MutableStateFlow(MyTopFilter.ALL_TIME)

    /** Chart size parsed from the "top" route arg; drives the localized title.
     *  Lazy: tests construct this source with placeholder ids. */
    val maxSize: Int by lazy { top.toInt() }

    override fun observe(): Flow<PlaylistTracksResult> =
        topPeriod
            .flatMapLatest { period ->
                database.mostPlayedSongs(period.toTimeMillis(), maxSize)
            }.map { songs ->
                val tracks = songs.map { it.toPlaylistTrack() }
                PlaylistTracksResult(
                    // TOP HERO — legacy TopPlaylistScreen.kt:283+637 renders a
                    // full hero (first-song artwork, "My Top N" title, plural
                    // count chip + duration chip). The localized title is
                    // resolved UI-side (sources hold no string resources) in
                    // PlaylistDetailScreen via [maxSize].
                    header =
                        PlaylistHeaderInfo(
                            id = id,
                            title = "", // localized "My Top N" resolved UI-side
                            thumbnailUrl = tracks.firstOrNull()?.thumbnailUrl,
                            songCount = tracks.size,
                            totalDurationMs = tracks.sumOf { it.durationMs ?: 0L },
                        ),
                    tracks = tracks,
                )
            }

    fun setTopPeriod(period: MyTopFilter) {
        topPeriod.value = period
    }
}
