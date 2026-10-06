package com.chungyak.advisor.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chungyak.advisor.BuildConfig

/** 새 릴리스 팝업. 상태가 Hidden이면 아무것도 그리지 않는다. */
@Composable
fun UpdateDialog(vm: UpdateViewModel = viewModel()) {
    // 앱이 화면에 나올 때마다 확인(최근 앱에서 복귀해도). 간격 제한은 ViewModel이 건다.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_START) vm.onForeground() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val state by vm.state.collectAsState()
    val s = state
    if (s is UpdateState.Hidden) return
    val info = when (s) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.Ready -> s.info
        is UpdateState.Failed -> s.info
        UpdateState.Hidden -> return
    }

    AlertDialog(
        // 다운로드 중엔 바깥 터치로 닫히지 않게.
        onDismissRequest = { if (s !is UpdateState.Downloading) vm.later() },
        title = { Text("새 버전 v${info.version}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("현재 v${BuildConfig.VERSION_NAME} → v${info.version}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                if (info.notes.isNotBlank()) Text(info.notes, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                when (s) {
                    is UpdateState.Downloading -> {
                        if (s.progress >= 0) {
                            LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
                            Text("다운로드 ${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("다운로드 중…", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    is UpdateState.Ready -> Text(
                        if (s.needsPermission)
                            "설치하려면 이 앱의 '알 수 없는 앱 설치'를 허용해야 합니다. [권한 설정]에서 허용한 뒤 돌아와 [설치]를 누르세요."
                        else "설치 화면이 열리지 않았다면 [설치]를 다시 누르세요.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    is UpdateState.Failed -> {
                        Text(
                            "업데이트를 받지 못했습니다: ${s.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "계속 안 되면 [웹에서 받기]로 APK를 직접 받아 설치하세요(데이터 유지).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            when (s) {
                is UpdateState.Available -> TextButton(onClick = vm::update) { Text("업데이트") }
                is UpdateState.Downloading -> {}
                is UpdateState.Ready ->
                    if (s.needsPermission) {
                        Column {
                            TextButton(onClick = vm::openInstallPermission) { Text("권한 설정") }
                            TextButton(onClick = { vm.install(s.info) }) { Text("설치") }
                        }
                    } else TextButton(onClick = { vm.install(s.info) }) { Text("설치") }
                is UpdateState.Failed -> Column {
                    TextButton(onClick = vm::update) { Text("다시 시도") }
                    TextButton(onClick = vm::openReleasePage) { Text("웹에서 받기") }
                }
                UpdateState.Hidden -> {}
            }
        },
        dismissButton = {
            if (s !is UpdateState.Downloading) TextButton(onClick = vm::later) { Text("나중에") }
        },
    )
}
