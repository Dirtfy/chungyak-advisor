package com.chungyak.advisor.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chungyak.advisor.data.AppDatabase
import com.chungyak.advisor.data.Notice
import com.chungyak.advisor.data.Settings
import com.chungyak.advisor.work.Scheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoticeViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)
    private val dao = AppDatabase.get(app).noticeDao()

    val notices: StateFlow<List<Notice>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    }

    fun clearAll() {
        viewModelScope.launch { dao.clear() }
    }
}
