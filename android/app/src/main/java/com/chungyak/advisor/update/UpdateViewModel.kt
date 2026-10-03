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
 * 앱이 화면에 나올 때마다(최대 [AUTO_INTERVAL_MS]에 1회) 최신 릴리스를 확인해 새 버전이면 팝업.
 * [업데이트] → APK 다운로드 → 시스템 설치 화면. [나중에]는 이 프로세스 동안 같은 버전을 자동으로 다시 묻지 않는다.
 * 설정의 [업데이트 확인]은 간격·[나중에]와 무관하게 바로 확인한다.
 *
 * v0.4.0~0.4.3은 프로세스당 1회만 확인했다. 백그라운드 수집(WorkManager)이나 최근 앱 복귀로
 * 프로세스가 살아 있으면 새 릴리스가 나와도 팝업이 뜨지 않았다.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Hidden)
    val state: StateFlow<UpdateState> = _state

    /** 수동 확인 결과 문구(설정 화면). */
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    private var checking = false

    /** 화면에 나올 때마다 호출(ON_START). 간격 안이면 건너뛴다. */
    fun onForeground() {
        val now = System.currentTimeMillis()
        if (now - Session.lastCheck < AUTO_INTERVAL_MS) return
        check(manual = false)
    }

    fun check(manual: Boolean) {
        if (checking || _state.value is UpdateState.Downloading) return
        checking = true
        Session.lastCheck = System.currentTimeMillis()
        if (manual) _message.value = "확인 중…"
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { runCatching { UpdateClient.latest() }.getOrNull() }
            checking = false
            val newer = info != null && Versions.isNewer(info.version, BuildConfig.VERSION_NAME)
            if (newer && (manual || info!!.version != Session.dismissed)) {
                _state.value = UpdateState.Available(info!!)
            }
            if (manual) _message.value = when {
                info == null -> "업데이트 정보를 가져오지 못했습니다(네트워크 확인). 잠시 후 다시 시도하세요."
                newer -> "새 버전 v${info.version}이 있습니다."
                else -> "최신 버전입니다 (v${BuildConfig.VERSION_NAME})."
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
        val started = runCatching { ctx.startActivity(intent) }
        _state.value = started.fold(
            { UpdateState.Ready(info, needsPermission = false) },
            { UpdateState.Failed(info, "설치 화면을 열 수 없습니다: ${it.message}") },
        )
    }

    /** '알 수 없는 앱 설치' 허용 화면(이 앱 대상). 돌아와서 [설치]를 다시 누르면 된다. */
    fun openInstallPermission() {
        val ctx = getApplication<Application>()
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** 팝업 경로가 막혔을 때: 브라우저로 릴리스 페이지를 열어 APK를 직접 받게 한다. */
    fun openReleasePage() {
        val ctx = getApplication<Application>()
        val url = currentInfo()?.pageUrl ?: UpdateClient.RELEASES_PAGE
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
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
        var lastCheck = 0L
        var dismissed: String? = null
    }

    companion object {
        /** 자동 확인 최소 간격. GitHub 비인증 API 한도(60회/시간/IP)에 넉넉하다. */
        const val AUTO_INTERVAL_MS = 30 * 60 * 1000L
    }
}
