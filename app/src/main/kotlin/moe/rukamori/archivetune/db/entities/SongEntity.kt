/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.innertube.YouTube
import java.time.LocalDateTime

@Immutable
@Entity(
    tableName = "song",
    indices = [
        Index(
            value = ["albumId"],
        ),
    ],
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val duration: Int = -1, // in seconds
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    @ColumnInfo(defaultValue = "0")
    val explicit: Boolean = false,
    val year: Int? = null,
    val date: LocalDateTime? = null, // ID3 tag property
    val dateModified: LocalDateTime? = null, // file property
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val totalPlayTime: Long = 0, // in milliseconds
    val inLibrary: LocalDateTime? = null,
    val dateDownload: LocalDateTime? = null,
    @ColumnInfo(name = "isLocal", defaultValue = "0")
    val isLocal: Boolean = false,
    val isrc: String? = null,
    @ColumnInfo(defaultValue = "0")
    val likedYtm: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val likedSpotify: Boolean = false,
) {
    fun localToggleLike(source: LikeSource = LikeSource.YTM): SongEntity {
        val isCurrentlyLiked = likedYtm || likedSpotify || liked
        return if (isCurrentlyLiked) {
            copy(
                liked = false,
                likedYtm = false,
                likedSpotify = false,
                likedDate = null,
            )
        } else {
            when (source) {
                LikeSource.YTM -> copy(
                    liked = true,
                    likedYtm = true,
                    likedDate = LocalDateTime.now(),
                )
                LikeSource.SPOTIFY -> copy(
                    liked = true,
                    likedSpotify = true,
                    likedDate = LocalDateTime.now(),
                )
            }
        }
    }

    fun toggleLike(source: LikeSource = LikeSource.YTM): SongEntity =
        if (isLocal) {
            localToggleLike(source)
        } else {
            val isCurrentlyLiked = likedYtm || likedSpotify || liked
            val newLiked = !isCurrentlyLiked
            
            val updated = if (isCurrentlyLiked) {
                copy(
                    liked = false,
                    likedYtm = false,
                    likedSpotify = false,
                    likedDate = null,
                    inLibrary = inLibrary,
                )
            } else {
                when (source) {
                    LikeSource.YTM -> copy(
                        liked = true,
                        likedYtm = true,
                        likedDate = LocalDateTime.now(),
                        inLibrary = inLibrary ?: LocalDateTime.now(),
                    )
                    LikeSource.SPOTIFY -> copy(
                        liked = true,
                        likedSpotify = true,
                        likedDate = LocalDateTime.now(),
                        inLibrary = inLibrary ?: LocalDateTime.now(),
                    )
                }
            }
            
            if (source == LikeSource.YTM) {
                updated.also {
                    CoroutineScope(Dispatchers.IO).launch {
                        YouTube.likeVideo(id, newLiked)
                        this.cancel()
                    }
                }
            } else {
                updated
            }
        }

    fun toggleLibrary() =
        copy(
            liked = if (inLibrary == null) liked else false,
            likedYtm = if (inLibrary == null) likedYtm else false,
            likedSpotify = if (inLibrary == null) likedSpotify else false,
            inLibrary = if (inLibrary == null) LocalDateTime.now() else null,
            likedDate = if (inLibrary == null) likedDate else null,
        )
}
