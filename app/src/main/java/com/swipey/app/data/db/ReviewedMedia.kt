package com.swipey.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "reviewed_media")
data class ReviewedMediaEntity(
    @PrimaryKey val mediaId: Long,
    val decision: String,   // "KEEP" | "TRASHED"
    val reviewedAt: Long,
)

@Dao
interface ReviewedMediaDao {
    @Upsert suspend fun upsert(row: ReviewedMediaEntity)
    @Upsert suspend fun upsertAll(rows: List<ReviewedMediaEntity>)

    @Query("SELECT mediaId FROM reviewed_media WHERE decision = 'KEEP'")
    suspend fun keptIds(): List<Long>

    @Query("DELETE FROM reviewed_media WHERE mediaId = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM reviewed_media WHERE mediaId IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("DELETE FROM reviewed_media")
    suspend fun clear()
}
