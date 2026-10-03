package com.chungyak.advisor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HouseModelDao {
    /** 상세 화면용: 공급면적 작은 순. */
    @Query("SELECT * FROM house_models WHERE noticeId = :noticeId ORDER BY supplyArea, houseType")
    fun observe(noticeId: String): Flow<List<HouseModel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<HouseModel>)

    @Query("SELECT * FROM house_models")
    suspend fun all(): List<HouseModel>

    /** 목록 매칭 표시용: 전체 주택형(공고 수 × 주택형 수 — 수백 행 수준). */
    @Query("SELECT * FROM house_models")
    fun observeAll(): Flow<List<HouseModel>>

    @Query("SELECT * FROM house_models WHERE noticeId IN (:ids)")
    suspend fun forNotices(ids: List<String>): List<HouseModel>

    @Query("DELETE FROM house_models")
    suspend fun clear()
}
