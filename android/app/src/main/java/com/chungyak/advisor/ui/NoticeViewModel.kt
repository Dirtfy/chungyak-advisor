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
import com.chungyak.advisor.match.Eligibility
import com.chungyak.advisor.match.NotifyMode
import com.chungyak.advisor.match.Profile
import com.chungyak.advisor.match.ProfileStore
import com.chungyak.advisor.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
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

    // ---- 개인화(내 조건) — 프로필은 기기 안에만 저장 ----
    private val profileStore = ProfileStore(app)
    private val _profile = MutableStateFlow(profileStore.profile)
    val profile: StateFlow<Profile> = _profile
    var notifyMode by mutableStateOf(profileStore.notifyMode)
        private set

    /** 공고별 매칭 결과. 프로필이 없으면 비어 있다. */
    val matches: StateFlow<Map<String, Eligibility.Result>> =
        combine(dao.observeAll(), db.houseModelDao().observeAll(), _profile) { list, models, p ->
            if (!p.isSet) emptyMap() else {
                val byNotice = models.groupBy { it.noticeId }
                list.associate { it.id to Eligibility.evaluate(p, it, byNotice[it.id].orEmpty()) }
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun saveProfile(p: Profile, mode: NotifyMode) {
        profileStore.profile = p
        profileStore.notifyMode = mode
        _profile.value = p
        notifyMode = mode
    }

    fun clearProfile() {
        profileStore.clear()
        _profile.value = Profile()
        notifyMode = profileStore.notifyMode
    }

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

    /** 저장된 서비스키가 있는가(화면 갱신용 상태). 키는 사용자가 입력한 값뿐 — 앱에 내장된 기본값은 없다. */
    var hasKey by mutableStateOf(settings.hasKey)
        private set

    fun saveSettings() {
        settings.serviceKey = serviceKey
        hasKey = settings.hasKey
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

    /** 백업 내보내기/가져오기 결과 문구(설정 카드에 표시). */
    var backupMessage by mutableStateOf("")
        private set

    fun exportBackup(uri: android.net.Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            backupMessage = runCatching {
                val text = com.chungyak.advisor.data.Backup.export(app)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }
                "백업 저장 완료 (공고 ${notices.value.size}건 + 설정·서비스키)"
            }.getOrElse { "백업 실패: ${it.message}" }
        }
    }

    fun importBackup(uri: android.net.Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            backupMessage = runCatching {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                }
                val r = com.chungyak.advisor.data.Backup.import(app, text)
                serviceKey = settings.serviceKey
                hasKey = settings.hasKey
                lookbackDays = settings.lookbackDays.toString()
                regions = settings.regions
                _sort.value = SortOrder.of(settings.sortOrder)
                "가져오기 완료: 공고 ${r.notices}건, 주택형 ${r.models}건, 경쟁률 ${r.competitions}건 + 설정"
            }.getOrElse { "가져오기 실패: ${it.message}" }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            db.houseModelDao().clear()
            db.competitionDao().clear()
            dao.clear()
        }
    }
}
