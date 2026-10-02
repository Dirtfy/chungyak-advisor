package com.chungyak.advisor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CompetitionDao {
    /** 상세 화면용: 주택형 → 순위 → 거주지역 순. */
    @Query("SELECT * FROM competitions WHERE noticeId = :noticeId ORDER BY modelNo, houseType, rank, resideCode")
    fun observe(noticeId: String): Flow<List<Competition>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<Competition>)

    @Query("DELETE FROM competitions WHERE noticeId = :noticeId")
    suspend fun deleteFor(noticeId: String)

    /** 재조회 결과로 한 공고의 경쟁률을 통째로 교체. */
    @Transaction
    suspend fun replaceFor(noticeId: String, rows: List<Competition>) {
        deleteFor(noticeId)
        insertAll(rows)
    }

    @Query("DELETE FROM competitions")
    suspend fun clear()
}
