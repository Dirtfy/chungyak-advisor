package com.chungyak.advisor.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chungyak.advisor.BuildConfig
import com.chungyak.advisor.data.Settings
import com.chungyak.advisor.match.Affordability
import com.chungyak.advisor.update.UpdateViewModel

/** 설정 탭(리디자인 3단계, docs/11): 서비스키 → 수집 범위 → 알림 → 내 조건 → 내 자금 → 백업 → 앱 정보, 섹션마다 흰 카드. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: NoticeViewModel, onEditProfile: () -> Unit = {}, onEditFunds: () -> Unit = {}) {
    val profile by vm.profile.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("설정") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            var saved by remember { mutableStateOf(false) }
            SettingsSection(Icons.Outlined.Key, "공공데이터 서비스키") {
                OutlinedTextField(
                    value = vm.serviceKey,
                    onValueChange = { vm.serviceKey = it; saved = false },
                    label = { Text("data.go.kr 서비스키 (Decoding)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                KeyGuide()
            }
            SettingsSection(Icons.Outlined.TravelExplore, "수집 범위") {
                OutlinedTextField(
                    value = vm.lookbackDays,
                    onValueChange = { vm.lookbackDays = it.filter { c -> c.isDigit() }; saved = false },
                    label = { Text("최근 며칠 공고 조회") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("지역 (수도권)", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Settings.DEFAULT_REGIONS.forEach { region ->
                        FilterChip(
                            selected = region in vm.regions,
                            onClick = { vm.toggleRegion(region); saved = false },
                            label = { Text(region) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveSettings(); saved = true }) {
                        Icon(Icons.Outlined.Save, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("저장")
                    }
                    if (saved) Text("저장했습니다", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            SettingsSection(Icons.Outlined.NotificationsActive, "알림") {
                SwitchLine(
                    "청약 일정 알림",
                    "내 조건·관심 조건에 맞는 공고의 1순위 접수 시작일·당첨 발표일을 전날 저녁(놓치면 당일 아침)에 한 번 알려 드립니다. 밴드로도 전달돼요.",
                    vm.scheduleAlerts,
                    vm::updateScheduleAlerts,
                )
            }
            SettingsSection(Icons.Outlined.Person, "내 조건") {
                Text(
                    "처음 실행할 때 나온 단계별 안내로 내 조건을 다시 입력합니다. 지금 저장된 값이 채워진 채로 시작해요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::openOnboarding) { Text("온보딩 다시 하기") }
                    TextButton(onClick = onEditProfile) { Text("내 조건 탭에서 고치기") }
                }
            }
            SettingsSection(Icons.Outlined.AccountBalanceWallet, "내 자금(매매 가능 판정)") {
                Text(
                    "보유 현금·대출 한도·월 상환액 상한으로 공고마다 살 수 있는지(가능/빠듯/불가) 보여 드립니다. " +
                        "입력값 기반 추정이며 실제 대출 심사와 다를 수 있어요. 이 폰 안에만 저장됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BudgetSummary(profile)
                OutlinedButton(onClick = onEditFunds) { Text(if (Affordability.isSet(profile)) "자금 고치기" else "자금 입력하기") }
            }
            SettingsSection(Icons.Outlined.Info, "데이터·앱 정보") {
                Text("백업 (재설치·기기 변경 시 데이터 옮기기)", style = MaterialTheme.typography.bodyMedium)
                val exporter = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri -> uri?.let(vm::exportBackup) }
                val importer = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> uri?.let(vm::importBackup) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exporter.launch("chungyak-radar-backup.json") }) { Text("백업 내보내기") }
                    OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                        Text("백업 가져오기")
                    }
                }
                Text(
                    "백업 파일에는 서비스키가 들어 있습니다. 다른 사람과 공유하지 마세요.",
                    style = MaterialTheme.typography.labelSmall,
                )
                if (vm.backupMessage.isNotBlank()) {
                    Text(vm.backupMessage, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { vm.clearAll() }) { Text("수집목록 비우기") }
                Spacer(Modifier.height(12.dp))
                val uvm: UpdateViewModel = viewModel()
                val updateMsg by uvm.message.collectAsState()
                Text("앱 버전 v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { uvm.check(manual = true) }) { Text("업데이트 확인") }
                    OutlinedButton(onClick = uvm::openReleasePage) { Text("웹에서 받기") }
                }
                if (updateMsg.isNotBlank()) {
                    Text(updateMsg, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 설정 섹션 카드: 왼쪽 아이콘 + 제목, 아래 내용. */
@Composable
private fun SettingsSection(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SwitchLine(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** data.go.kr 서비스키 발급 방법(두 서비스 활용신청). */
@Composable
internal fun KeyGuide() {
    val uri = LocalUriHandler.current
    Text(
        "발급: data.go.kr 회원가입 → 아래 두 서비스에서 [활용신청](자동 승인) → 마이페이지의 '일반 인증키(Decoding)'를 복사해 붙여넣기. " +
            "신청 후 반영까지 최대 1시간 걸릴 수 있습니다.",
        style = MaterialTheme.typography.bodySmall,
    )
    TextButton(onClick = { uri.openUri("https://www.data.go.kr/data/15098547/openapi.do") }) {
        Text("① 청약홈 분양정보 조회 서비스 (15098547)")
    }
    TextButton(onClick = { uri.openUri("https://www.data.go.kr/data/15098905/openapi.do") }) {
        Text("② 청약접수 경쟁률 조회 서비스 (15098905, 경쟁률용)")
    }
}
