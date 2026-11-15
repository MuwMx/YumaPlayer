/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.session

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.SongItem
import javax.inject.Inject
import kotlin.math.min

internal fun String.pathSegments(): List<String> = split("/").filter { it.isNotBlank() }

internal class MediaLibraryItemFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun browsableExtras(
            browsableHint: Int = CONTENT_STYLE_GRID_ITEM,
            playableHint: Int = CONTENT_STYLE_LIST_ITEM,
        ): Bundle =
            Bundle().apply {
                putBoolean(EXTRA_CONTENT_STYLE_SUPPORTED, true)
                putInt(EXTRA_CONTENT_STYLE_BROWSABLE_HINT, browsableHint)
                putInt(EXTRA_CONTENT_STYLE_PLAYABLE_HINT, playableHint)
            }

        fun playableExtras(playableHint: Int = CONTENT_STYLE_LIST_ITEM): Bundle =
            Bundle().apply {
                putBoolean(EXTRA_CONTENT_STYLE_SUPPORTED, true)
                putInt(EXTRA_CONTENT_STYLE_PLAYABLE_HINT, playableHint)
            }

        fun drawableUri(
            @DrawableRes id: Int,
        ): Uri =
            Uri
                .Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(context.resources.getResourcePackageName(id))
                .appendPath(context.resources.getResourceTypeName(id))
                .appendPath(context.resources.getResourceEntryName(id))
                .build()

        fun rootMediaItem(mediaId: String = MediaLibraryTreeConstants.ROOT): MediaItem =
            MediaItem
                .Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setIsPlayable(false)
                        .setIsBrowsable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setExtras(browsableExtras())
                        .build(),
                ).build()

        fun browsableMediaItem(
            id: String,
            title: String,
            subtitle: String?,
            iconUri: Uri?,
            mediaType: Int = MediaMetadata.MEDIA_TYPE_MUSIC,
        ): MediaItem =
            MediaItem
                .Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setArtist(subtitle)
                        .setArtworkUri(iconUri)
                        .setIsPlayable(false)
                        .setIsBrowsable(true)
                        .setMediaType(mediaType)
                        .setExtras(browsableExtras())
                        .build(),
                ).build()

        fun queueMediaItem(
            id: String,
            title: String,
            subtitle: String?,
            iconUri: Uri?,
            mediaType: Int = MediaMetadata.MEDIA_TYPE_PLAYLIST,
        ): MediaItem =
            MediaItem
                .Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setArtist(subtitle)
                        .setArtworkUri(iconUri)
                        .setIsPlayable(true)
                        .setIsBrowsable(true)
                        .setMediaType(mediaType)
                        .setExtras(browsableExtras())
                        .build(),
                ).build()

        fun toLibraryMediaItem(
            song: Song,
            path: String? = null,
        ): MediaItem {
            val mediaId = if (path != null) "$path/${song.id}" else song.id
            return MediaItem
                .Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(song.song.title)
                        .setSubtitle(song.artists.joinToString { it.name })
                        .setArtist(song.artists.joinToString { it.name })
                        .setArtworkUri(song.song.thumbnailUrl?.toUri())
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .setExtras(playableExtras())
                        .build(),
                ).build()
        }

        fun toLibraryMediaItem(
            songItem: SongItem,
            path: String? = null,
        ): MediaItem {
            val base = songItem.toMediaItem()
            val mediaId = if (path != null) "$path/${songItem.id}" else songItem.id
            return base
                .buildUpon()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    base.mediaMetadata
                        .buildUpon()
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .setExtras(playableExtras())
                        .build(),
                ).build()
        }

        fun ensureLibraryMediaItem(
            item: MediaItem,
            isPlayable: Boolean = item.mediaMetadata.isPlayable ?: true,
            isBrowsable: Boolean = item.mediaMetadata.isBrowsable ?: false,
        ): MediaItem {
            val metadata = item.mediaMetadata
            if (metadata.isPlayable != null && metadata.isBrowsable != null) {
                return item
            }
            return item
                .buildUpon()
                .setMediaMetadata(
                    metadata
                        .buildUpon()
                        .setIsPlayable(isPlayable)
                        .setIsBrowsable(isBrowsable)
                        .build(),
                ).build()
        }

        fun List<MediaItem>.paged(
            page: Int,
            pageSize: Int,
        ): List<MediaItem> {
            if (page < 0 || pageSize <= 0) return this
            val from = page.toLong() * pageSize.toLong()
            if (from >= size) return emptyList()
            val to = min(from + pageSize, size.toLong()).toInt()
            return subList(from.toInt(), to)
        }

        companion object {
            const val AUTO_BROWSE_LIMIT = 100
            const val EXTRA_CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
            const val EXTRA_CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
            const val EXTRA_CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"

            const val CONTENT_STYLE_LIST_ITEM = 1
            const val CONTENT_STYLE_GRID_ITEM = 2
        }
    }
