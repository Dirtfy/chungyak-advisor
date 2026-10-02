package com.chungyak.advisor.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chungyak.advisor.data.AppDatabase
import com.chungyak.advisor.data.Competition
import com.chungyak.advisor.data.HouseModel
import com.chungyak.advisor.data.Notice
import com.chungyak.advisor.data.Settings
import com.chungyak.advisor.work.Scheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoticeViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)
    private val db = AppDatabase.get(app)
    private val dao = db.noticeDao()

    private val _sort = MutableStateFlow(SortOrder.of(settings.sortOrder))
    val sort: StateFlow<SortOrder> = _sort

    /** 선택한 정렬을 적용한 목록. */
    val notices: StateFlow<List<Notice>> =
        combine(dao.observeAll(), _sort) { list, order -> NoticeSort.sort(list, order, today()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSort(order: SortOrder) {
        settings.sortOrder = order.name
        _sort.value = order
    }

    /** 경쟁률 서비스 미신청(401) 상태 — 목록/상세 안내용. 상태 갱신 때 다시 읽는다. */
    var cmpetUnauthorized by mutableStateOf(settings.cmpetUnauthorized)
        private set

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())

    /** 상세 화면: 한 공고의 주택형별 경쟁률(캐시). */
    fun competitions(noticeId: String): Flow<List<Competition>> = db.competitionDao().observe(noticeId)

    /** 상세 화면: 한 공고의 주택형별 분양가(캐시). */
    fun models(noticeId: String): Flow<List<HouseModel>> = db.houseModelDao().observe(noticeId)

    private val _status = MutableStateFlow(settings.lastResult)
    val status: StateFlow<String> = _status

    var serviceKey by mutableStateOf(settings.serviceKey)
    var lookbackDays by mutableStateOf(settings.lookbackDays.toString())
    var regions by mutableStateOf(settings.regions)

    val hasKey: Boolean get() = settings.hasKey

    fun saveSettings() {
        settings.serviceKey = serviceKey
        settings.lookbackDays = lookbackDays.toIntOrNull()?.coerceIn(1, 365) ?: 30
        settings.regions = regions
    }

    fun toggleRegion(region: String) {
        regions = if (region in regions) regions - region else regions + region
    }

    fun checkNow() {
        saveSettings()
        Scheduler.runNow(getApplication())
    }

    fun refreshStatus() {
        _status.value = settings.lastResult
        cmpetUnauthorized = settings.cmpetUnauthorized
    }

    fun clearAll() {
        viewModelScope.launch {
            db.houseModelDao().clear()
            db.competitionDao().clear()
            dao.clear()
        }
    }
}
