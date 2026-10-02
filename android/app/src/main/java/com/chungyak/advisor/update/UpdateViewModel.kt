package com.chungyak.advisor.update

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chungyak.advisor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 업데이트 팝업 상태. */
sealed class UpdateState {
    data object Hidden : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState()
    /** 받기 완료. [needsPermission]이면 '알 수 없는 앱 설치' 허용이 먼저 필요. */
    data class Ready(val info: UpdateInfo, val needsPermission: Boolean) : UpdateState()
    data class Failed(val info: UpdateInfo, val message: String) : UpdateState()
}

/**
 * 콜드 스타트마다 1회 최신 릴리스를 확인해 새 버전이면 팝업. [업데이트] → APK 다운로드 →
 * 시스템 설치 화면. [나중에]는 이 프로세스(실행 세션) 동안 같은 버전을 다시 묻지 않는다.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Hidden)
    val state: StateFlow<UpdateState> = _state

    init {
        if (!Session.checked) {
            Session.checked = true
            viewModelScope.launch {
                val info = withContext(Dispatchers.IO) { runCatching { UpdateClient.latest() }.getOrNull() }
                if (info != null && info.version != Session.dismissed &&
                    Versions.isNewer(info.version, BuildConfig.VERSION_NAME)
                ) {
                    _state.value = UpdateState.Available(info)
                }
            }
        }
    }

    fun later() {
        currentInfo()?.let { Session.dismissed = it.version }
        _state.value = UpdateState.Hidden
    }

    fun update() {
        val info = currentInfo() ?: return
        val file = apkFile(info)
        if (file.isFile && file.length() > 0 && (info.apkSize <= 0 || file.length() == info.apkSize)) {
            install(info)
            return
        }
        _state.value = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    file.parentFile?.listFiles()?.forEach { it.delete() } // 이전 버전 APK 정리
                    UpdateClient.download(info.apkUrl, info.apkSize, file) { p ->
                        _state.value = UpdateState.Downloading(info, p)
                    }
                }
            }
            ok.onSuccess { install(info) }
                .onFailure { _state.value = UpdateState.Failed(info, it.message ?: "다운로드 실패") }
        }
    }

    /** 설치 화면 실행. 권한이 없으면 Ready(needsPermission)로 두고 설정 화면 안내. */
    fun install(info: UpdateInfo) {
        val ctx = getApplication<Application>()
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.Ready(info, needsPermission = true)
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", apkFile(info))
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        _state.value = UpdateState.Ready(info, needsPermission = false)
    }

    /** '알 수 없는 앱 설치' 허용 화면(이 앱 대상). 돌아와서 [설치]를 다시 누르면 된다. */
    fun openInstallPermission() {
        val ctx = getApplication<Application>()
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun dismiss() {
        _state.value = UpdateState.Hidden
    }

    private fun currentInfo(): UpdateInfo? = when (val s = _state.value) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.Ready -> s.info
        is UpdateState.Failed -> s.info
        UpdateState.Hidden -> null
    }

    private fun apkFile(info: UpdateInfo) =
        File(File(getApplication<Application>().cacheDir, "updates"), "chungyak-radar-${info.version}.apk")

    /** 프로세스 수명 = 실행 세션. 화면 회전 등으로 다시 묻지 않게 여기 둔다. */
    private object Session {
        var checked = false
        var dismissed: String? = null
    }
}
