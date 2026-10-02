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

    /** 주택형별 상세가 아직 캐시되지 않은 공고(최신 우선). */
    @Query("SELECT * FROM notices WHERE modelsFetchedAt = 0 ORDER BY noticeDate DESC LIMIT :limit")
    suspend fun withoutModels(limit: Int): List<Notice>

    @Query("UPDATE notices SET priceMinManwon = :min, priceMaxManwon = :max, modelsFetchedAt = :at WHERE id = :id")
    suspend fun setPrice(id: String, min: Int, max: Int, at: Long)

    /** 경쟁률 미확정 공고(최근 공고 우선). 접수 전 여부는 호출 측 [CompetitionPolicy]가 거른다. */
    @Query("SELECT * FROM notices WHERE cmpetFinal = 0 ORDER BY noticeDate DESC")
    suspend fun withoutFinalCompetition(): List<Notice>

    @Query(
        "UPDATE notices SET cmpetMaxRate = :max, cmpetAvgRate = :avg, cmpetFetchedAt = :at, " +
            "cmpetFinal = :isFinal WHERE id = :id"
    )
    suspend fun setCompetition(id: String, max: Double, avg: Double, at: Long, isFinal: Boolean)

    @Query("DELETE FROM notices")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM notices")
    suspend fun count(): Int
}
