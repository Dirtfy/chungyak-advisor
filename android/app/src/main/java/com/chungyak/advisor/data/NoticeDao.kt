package com.chungyak.advisor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoticeDao {

    /** Newest 공고 first, for the list screen. */
    @Query("SELECT * FROM notices ORDER BY noticeDate DESC, firstSeen DESC")
    fun observeAll(): Flow<List<Notice>>

    @Query("SELECT id FROM notices")
    suspend fun allIds(): List<String>

    /** Insert new rows; existing ids are kept untouched (IGNORE) so we never
     *  overwrite the [Notice.notified] flag on a re-poll. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(notices: List<Notice>): List<Long>

    @Query("UPDATE notices SET notified = 1 WHERE id IN (:ids)")
    suspend fun markNotified(ids: List<String>)

    @Query("DELETE FROM notices")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM notices")
    suspend fun count(): Int
}
