/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.db

import android.util.Log
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.db.entities.PlaylistTagMap
import moe.rukamori.archivetune.db.entities.TagEntity

@Dao
interface TagDao {
    @Transaction
    @Query("SELECT * FROM tag ORDER BY name")
    fun allTags(): Flow<List<TagEntity>>

    @Transaction
    @Query("SELECT * FROM tag WHERE id = :tagId")
    fun tag(tagId: String): Flow<TagEntity?>

    @Transaction
    @Query("SELECT * FROM tag WHERE id IN (SELECT tagId FROM playlist_tag_map WHERE playlistId = :playlistId)")
    fun playlistTags(playlistId: String): Flow<List<TagEntity>>

    @Transaction
    @Query("SELECT DISTINCT playlistId FROM playlist_tag_map WHERE tagId IN (:tagIds)")
    fun playlistIdsByTags(tagIds: List<String>): Flow<List<String>>

    @Transaction
    @Query("SELECT COUNT(*) FROM playlist_tag_map WHERE playlistId = :playlistId AND tagId = :tagId")
    fun isPlaylistTagged(
        playlistId: String,
        tagId: String,
    ): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: PlaylistTagMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAllPlaylistTagMaps(maps: List<PlaylistTagMap>)

    @Update
    fun update(tag: TagEntity)

    @Delete
    fun delete(tag: TagEntity)

    @Transaction
    fun deleteTag(tag: TagEntity) {
        removeAllTagPlaylists(tag.id)
        delete(tag)
    }

    @Delete
    fun delete(playlistTagMap: PlaylistTagMap)

    @Query("DELETE FROM playlist_tag_map WHERE playlistId = :playlistId")
    fun removeAllPlaylistTags(playlistId: String)

    @Query("DELETE FROM playlist_tag_map WHERE tagId = :tagId")
    fun removeAllTagPlaylists(tagId: String)

    @Query("DELETE FROM playlist_tag_map WHERE playlistId = :playlistId AND tagId = :tagId")
    fun removePlaylistTag(
        playlistId: String,
        tagId: String,
    )

    @Transaction
    fun addTagToPlaylist(
        playlistId: String,
        tagId: String,
    ) {
        insert(PlaylistTagMap(playlistId = playlistId, tagId = tagId))
    }

    @Transaction
    fun addTagsToPlaylists(
        playlistIds: List<String>,
        tagIds: List<String>,
    ) {
        if (playlistIds.isEmpty() || tagIds.isEmpty()) return
        val maps =
            playlistIds.flatMap { playlistId ->
                tagIds.map { tagId ->
                    PlaylistTagMap(playlistId = playlistId, tagId = tagId)
                }
            }
        insertAllPlaylistTagMaps(maps)
    }

    @Transaction
    suspend fun togglePlaylistTag(
        playlistId: String,
        tagId: String,
    ) {
        val t0 = System.currentTimeMillis()
        val isTagged = isPlaylistTagged(playlistId, tagId).first()
        Log.d("DB_STRESS", "TagDao.togglePlaylistTag READ playlistId=$playlistId tagId=$tagId isTagged=$isTagged th=${Thread.currentThread().name}")
        if (isTagged > 0) {
            removePlaylistTag(playlistId, tagId)
            Log.d("DB_STRESS", "TagDao.togglePlaylistTag WRITE remove playlistId=$playlistId tagId=$tagId dt=${System.currentTimeMillis() - t0}ms th=${Thread.currentThread().name}")
        } else {
            addTagToPlaylist(playlistId, tagId)
            Log.d("DB_STRESS", "TagDao.togglePlaylistTag WRITE add playlistId=$playlistId tagId=$tagId dt=${System.currentTimeMillis() - t0}ms th=${Thread.currentThread().name}")
        }
    }
}
