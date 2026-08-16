package com.swipey.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.swipey.app.domain.LocalTrashRecord
import com.swipey.app.domain.TrashState

@Entity(tableName = "trashed_by_swipey")
data class TrashedItemEntity(
    @PrimaryKey val mediaId: Long,
    val isVideo: Boolean,
    val displayName: String,
    val sizeBytes: Long,
    val trashedAt: Long,
    val state: String,   // TrashState.name
)

fun TrashedItemEntity.toDomain(): LocalTrashRecord = LocalTrashRecord(
    mediaId = mediaId,
    isVideo = isVideo,
    displayName = displayName,
    sizeBytes = sizeBytes,
    trashedAt = trashedAt,
    state = TrashState.valueOf(state),
)

@Dao
interface TrashedItemDao {
    @Upsert suspend fun upsertAll(rows: List<TrashedItemEntity>)

    @Query("SELECT * FROM trashed_by_swipey")
    suspend fun all(): List<TrashedItemEntity>

    @Query("SELECT * FROM trashed_by_swipey WHERE state != 'TRASHED'")
    suspend fun pending(): List<TrashedItemEntity>

    @Query("SELECT COUNT(*) FROM trashed_by_swipey WHERE state = 'TRASHED'")
    suspend fun trashedCount(): Int

    @Query("UPDATE trashed_by_swipey SET state = :state WHERE mediaId IN (:ids)")
    suspend fun setState(ids: List<Long>, state: String)

    @Query("DELETE FROM trashed_by_swipey WHERE mediaId IN (:ids)")
    suspend fun delete(ids: List<Long>)
}
