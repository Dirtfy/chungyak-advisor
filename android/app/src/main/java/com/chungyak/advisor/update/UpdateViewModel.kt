package com.chungyak.advisor.update

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chungyak.advisor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 업데이트 팝업 상태. */
sealed class UpdateState {
    data object Hidden : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    /** [waiting]: 네트워크 대기 등으로 잠시 멈춘 이유(멈추지 않았으면 null). */
    data class Downloading(val info: UpdateInfo, val progress: Float, val waiting: String? = null) : UpdateState()
    /** 받기 완료. [needsPermission]이면 '알 수 없는 앱 설치' 허용이 먼저 필요. */
    data class Ready(val info: UpdateInfo, val needsPermission: Boolean) : UpdateState()
    data class Failed(val info: UpdateInfo, val message: String) : UpdateState()
}

/**
 * 앱이 화면에 나올 때마다(최대 [AUTO_INTERVAL_MS]에 1회) 최신 릴리스를 확인해 새 버전이면 팝업.
 * [업데이트] → 시스템 DownloadManager로 APK 다운로드([UpdateDownloads]) → 시스템 설치 화면.
 * [나중에]는 이 프로세스 동안 같은 버전을 자동으로 다시 묻지 않는다.
 * 설정의 [업데이트 확인]은 간격·[나중에]와 무관하게 바로 확인한다.
 *
 * 다운로드는 앱을 나가도 이어진다. 화면에 있는 동안만 진행률을 읽고([poll]), 화면으로 돌아오면
 * 진행 중이면 진행률을, 다 받았으면 [설치] 팝업을 다시 보여 준다. 화면 밖에서 끝나면
 * [UpdateDownloadReceiver]가 "업데이트 준비됨" 알림을 띄운다.
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
    private var poller: Job? = null
    /** 다운로드 중 팝업을 [숨기기]로 닫았나. 끝나면 다시 보여 준다. */
    private var hiddenWhileDownloading = false

    private val ctx get() = getApplication<Application>()

    /** 화면에 나올 때마다 호출(ON_START). 다운로드 상태를 이어 보여 주고, 간격 밖이면 새 버전을 확인한다. */
    fun onForeground() {
        sync()
        val now = System.currentTimeMillis()
        if (now - Session.lastCheck < AUTO_INTERVAL_MS) return
        check(manual = false)
    }

    /** 화면에서 사라질 때(ON_STOP). 진행률 읽기만 멈춘다 — 다운로드 자체는 시스템이 계속한다. */
    fun onBackground() {
        poller?.cancel()
        poller = null
    }

    /** 저장된 다운로드 상태를 화면 상태로 옮긴다. */
    private fun sync() {
        UpdateDownloads.cleanupIfInstalled(ctx, BuildConfig.VERSION_NAME)
        when (val d = UpdateDownloads.status(ctx)) {
            is UpdateDownloads.Status.Running -> {
                if (!hiddenWhileDownloading) _state.value = UpdateState.Downloading(d.info, d.progress, d.waiting)
                poll()
            }
            is UpdateDownloads.Status.Done ->
                if (d.info.version != Session.dismissed && _state.value !is UpdateState.Ready) {
                    hiddenWhileDownloading = false
                    UpdateDownloads.cancelNotification(ctx)
                    _state.value = UpdateState.Ready(d.info, needsPermission = !canInstall())
                }
            is UpdateDownloads.Status.Failed ->
                if (_state.value is UpdateState.Downloading || hiddenWhileDownloading) {
                    hiddenWhileDownloading = false
                    _state.value = UpdateState.Failed(d.info, d.message)
                }
            UpdateDownloads.Status.None -> {}
        }
    }

    fun check(manual: Boolean) {
        if (checking || downloading()) return
        checking = true
        Session.lastCheck = System.currentTimeMillis()
        if (manual) _message.value = "확인 중…"
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { runCatching { UpdateClient.latest() }.getOrNull() }
            checking = false
            val newer = info != null && Versions.isNewer(info.version, BuildConfig.VERSION_NAME)
            if (newer && (manual || info!!.version != Session.dismissed) && !downloading()) {
                // 이미 다 받아 둔 APK가 있으면 다시 받지 않고 바로 [설치]를 보여 준다.
                _state.value = if (UpdateDownloads.isComplete(ctx, info!!)) UpdateState.Ready(info, !canInstall())
                else UpdateState.Available(info)
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

    /** 다운로드 중 팝업 닫기. 다운로드는 계속되고, 끝나면 팝업(화면 밖이면 알림)으로 알린다. */
    fun hideWhileDownloading() {
        hiddenWhileDownloading = true
        _state.value = UpdateState.Hidden
    }

    fun update() {
        val info = currentInfo() ?: return
        if (UpdateDownloads.isComplete(ctx, info)) {
            install(info)
            return
        }
        val started = runCatching { UpdateDownloads.start(ctx, info) }
        if (started.isFailure) {
            _state.value = UpdateState.Failed(info, started.exceptionOrNull()?.message ?: "다운로드를 시작하지 못했습니다")
            return
        }
        hiddenWhileDownloading = false
        _state.value = UpdateState.Downloading(info, 0f)
        poll()
    }

    /** 화면에 있는 동안 0.5초마다 진행률을 읽는다. 다 받으면 바로 설치 화면을 연다. */
    private fun poll() {
        if (poller?.isActive == true) return
        poller = viewModelScope.launch {
            while (isActive) {
                delay(POLL_MS)
                when (val d = withContext(Dispatchers.IO) { UpdateDownloads.status(ctx) }) {
                    is UpdateDownloads.Status.Running ->
                        if (!hiddenWhileDownloading) _state.value = UpdateState.Downloading(d.info, d.progress, d.waiting)
                    is UpdateDownloads.Status.Done -> {
                        hiddenWhileDownloading = false
                        install(d.info)
                        break
                    }
                    is UpdateDownloads.Status.Failed -> {
                        hiddenWhileDownloading = false
                        _state.value = UpdateState.Failed(d.info, d.message)
                        break
                    }
                    UpdateDownloads.Status.None -> {
                        // 사용자가 시스템 다운로드 알림에서 취소한 경우 등.
                        if (_state.value is UpdateState.Downloading) {
                            val info = currentInfo()!!
                            _state.value = UpdateState.Failed(info, "다운로드가 취소됐습니다")
                        }
                        hiddenWhileDownloading = false
                        break
                    }
                }
            }
            poller = null
        }
    }

    private fun downloading() = poller?.isActive == true || _state.value is UpdateState.Downloading

    private fun canInstall() = ctx.packageManager.canRequestPackageInstalls()

    /** 설치 화면 실행. 권한이 없으면 Ready(needsPermission)로 두고 설정 화면 안내. */
    fun install(info: UpdateInfo) {
        UpdateDownloads.cancelNotification(ctx)
        if (!canInstall()) {
            _state.value = UpdateState.Ready(info, needsPermission = true)
            return
        }
        val started = runCatching { ctx.startActivity(UpdateDownloads.installIntent(ctx, info)) }
        _state.value = started.fold(
            { UpdateState.Ready(info, needsPermission = false) },
            { UpdateState.Failed(info, "설치 화면을 열 수 없습니다: ${it.message}") },
        )
    }

    /** '알 수 없는 앱 설치' 허용 화면(이 앱 대상). 돌아와서 [설치]를 다시 누르면 된다. */
    fun openInstallPermission() {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** 팝업 경로가 막혔을 때: 브라우저로 릴리스 페이지를 열어 APK를 직접 받게 한다. */
    fun openReleasePage() {
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

    /** 프로세스 수명 = 실행 세션. 화면 회전 등으로 다시 묻지 않게 여기 둔다. */
    private object Session {
        var lastCheck = 0L
        var dismissed: String? = null
    }

    companion object {
        /** 자동 확인 최소 간격. GitHub 비인증 API 한도(60회/시간/IP)에 넉넉하다. */
        const val AUTO_INTERVAL_MS = 30 * 60 * 1000L
        const val POLL_MS = 500L
    }
}
