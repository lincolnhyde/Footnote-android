package com.footnote.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SelectionDao {
    @Insert
    suspend fun insert(entity: SelectionEntity): Long

    @Query("SELECT COUNT(*) FROM selections")
    suspend fun count(): Int

    @Query("SELECT * FROM selections WHERE slotId = :slotId ORDER BY ts DESC LIMIT :limit")
    suspend fun recentBySlot(slotId: String, limit: Int = 50): List<SelectionEntity>

    @Query("SELECT * FROM selections ORDER BY ts DESC LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<SelectionEntity>

    @Query("DELETE FROM selections WHERE ts < :before")
    suspend fun pruneBefore(before: Long): Int
}
