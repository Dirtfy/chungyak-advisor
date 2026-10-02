package com.chungyak.advisor

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chungyak.advisor.data.Competition
import com.chungyak.advisor.data.HouseModel
import com.chungyak.advisor.data.Notice
import com.chungyak.advisor.data.Settings
import com.chungyak.advisor.ui.CompetitionFormat
import com.chungyak.advisor.ui.NoticeViewModel
import com.chungyak.advisor.ui.PriceFormat
import com.chungyak.advisor.ui.SortOrder
import com.chungyak.advisor.ui.theme.ChungyakTheme
import com.chungyak.advisor.update.UpdateDialog

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val askNotif = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* result ignored — collection still works, just no notifications */ }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            ChungyakTheme {
                HomeScreen()
                UpdateDialog()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: NoticeViewModel = viewModel()) {
    val notices by vm.notices.collectAsState()
    val status by vm.status.collectAsState()
    val sort by vm.sort.collectAsState()
    // 백그라운드 수집이 DB를 바꾸면 상태 문구/경쟁률 신청 안내도 다시 읽는다.
    LaunchedEffect(notices) { vm.refreshStatus() }
    val today = vm.today()
    var showSettings by remember { mutableStateOf(!vm.hasKey) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = notices.firstOrNull { it.id == selectedId }

    if (selected != null) {
        BackHandler { selectedId = null }
        NoticeDetailScreen(selected, vm, onBack = { selectedId = null })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("청약 레이더 · 수도권 일반공급") },
                actions = {
                    TextButton(onClick = { showSettings = !showSettings }) {
                        Text(if (showSettings) "닫기" else "설정")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            StatusCard(status = status, onCheck = { vm.checkNow(); vm.refreshStatus() })

            if (showSettings) {
                Spacer(Modifier.height(12.dp))
                SettingsCard(vm) { showSettings = false }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "수집된 공고 ${notices.size}건",
                style = MaterialTheme.typography.titleMedium,
            )
            SortBar(sort, vm::setSort)
            Spacer(Modifier.height(4.dp))

            if (notices.isEmpty()) {
                Text(
                    if (vm.hasKey)
                        "아직 수집된 공고가 없습니다. '지금 확인'을 눌러보세요."
                    else
                        "먼저 설정에서 data.go.kr 서비스키를 입력하세요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notices, key = { it.id }) {
                        NoticeRow(it, CompetitionFormat.summary(it, today, vm.cmpetUnauthorized)) { selectedId = it.id }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortBar(selected: SortOrder, onSelect: (SortOrder) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SortOrder.entries.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(o.label) })
        }
    }
}

@Composable
private fun StatusCard(status: String, onCheck: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (status.isBlank()) "아직 확인한 적이 없습니다." else "최근: $status",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onCheck) { Text("지금 확인") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsCard(vm: NoticeViewModel, onSaved: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("설정", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.serviceKey,
                onValueChange = { vm.serviceKey = it },
                label = { Text("data.go.kr 서비스키 (Decoding)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.lookbackDays,
                onValueChange = { vm.lookbackDays = it.filter { c -> c.isDigit() } },
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
                        onClick = { vm.toggleRegion(region) },
                        label = { Text(region) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveSettings(); onSaved() }) { Text("저장") }
                OutlinedButton(onClick = { vm.clearAll() }) { Text("수집목록 비우기") }
            }
            Spacer(Modifier.height(12.dp))
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
        }
    }
}

@Composable
private fun NoticeRow(n: Notice, cmpet: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(n.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("${n.areaName} · ${n.address}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            val schedule = if (n.rank1Start.isNotBlank())
                "1순위 접수 ${n.rank1Start}" else "모집공고 ${n.noticeDate}"
            Text(
                "${n.totalUnits}세대 · $schedule · 발표 ${n.resultDate}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "분양가 ${priceSummary(n)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            Text("경쟁률 $cmpet", style = MaterialTheme.typography.bodySmall)
            if (n.speculationArea || n.adjustmentArea) {
                Spacer(Modifier.height(2.dp))
                val tags = buildList {
                    if (n.speculationArea) add("투기과열지구")
                    if (n.adjustmentArea) add("조정대상지역")
                }.joinToString(" · ")
                Text(tags, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** 목록용 분양가 범위. 아직 상세를 못 받았으면 "확인 중", 받았는데 값이 없으면 "정보 없음". */
private fun priceSummary(n: Notice): String =
    if (n.modelsFetchedAt == 0L && n.priceMaxManwon <= 0) "확인 중"
    else PriceFormat.range(n.priceMinManwon, n.priceMaxManwon)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticeDetailScreen(n: Notice, vm: NoticeViewModel, onBack: () -> Unit) {
    val models by remember(n.id) { vm.models(n.id) }.collectAsState(initial = emptyList())
    val cmpets by remember(n.id) { vm.competitions(n.id) }.collectAsState(initial = emptyList())
    val cmpetSummary = CompetitionFormat.summary(n, vm.today(), vm.cmpetUnauthorized)
    val uri = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(n.name, maxLines = 1) },
                navigationIcon = { TextButton(onClick = onBack) { Text("〈 목록") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("${n.areaName} · ${n.address}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "${n.totalUnits}세대 · 모집공고 ${n.noticeDate}" +
                    (if (n.rank1Start.isNotBlank()) " · 1순위 ${n.rank1Start}~${n.rank1End}" else "") +
                    " · 발표 ${n.resultDate}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Text("분양가 ${priceSummary(n)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("주택형별 분양가 (최고가 기준)", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    if (models.isEmpty()) {
                        Text(
                            if (n.modelsFetchedAt == 0L) "주택형별 정보를 아직 받지 못했습니다. '지금 확인' 후 다시 열어보세요."
                            else PriceFormat.NONE,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        ModelHeader()
                        models.forEach { ModelRow(it) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("경쟁률 $cmpetSummary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("주택형별 경쟁률 (일반공급)", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    if (cmpets.isEmpty()) {
                        Text(
                            when (cmpetSummary) {
                                CompetitionFormat.BEFORE -> "접수 전입니다. 접수가 시작되면 경쟁률을 받아옵니다."
                                CompetitionFormat.NEED_APPLY ->
                                    "경쟁률은 별도 공공데이터 서비스입니다. data.go.kr에서 " +
                                        "'한국부동산원_청약홈 청약접수 경쟁률 및 특별공급 신청현황 조회 서비스'(15098905) 활용신청 후 같은 키로 자동 표시됩니다."
                                CompetitionFormat.PENDING -> "접수 결과 집계 중입니다. 다음 확인 때 다시 받아옵니다."
                                else -> CompetitionFormat.NONE
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        CmpetHeader()
                        cmpets.forEach { CmpetRow(it) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "· 분양가: 청약홈 주택형별 공급금액(분양최고금액). 평당가 = 분양가 ÷ 공급면적(평, 1평=3.3058㎡).\n" +
                    "· 세대수: 일반공급 기준. 옵션·발코니 확장비 제외 — 정확한 금액은 모집공고문 확인.\n" +
                    "· 경쟁률: 청약홈 접수 결과(목록 값은 1순위 해당지역 기준, 평균 = 접수 합 ÷ 공급 합). △ = 미달 세대수.",
                style = MaterialTheme.typography.labelSmall,
            )
            if (n.url.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { runCatching { uri.openUri(n.url) } }) { Text("청약홈 공고 열기") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModelHeader() {
    Row(Modifier.fillMaxWidth()) {
        Cell("주택형", 1.1f, bold = true)
        Cell("공급면적", 1.3f, bold = true)
        Cell("세대", 0.5f, bold = true, end = true)
        Cell("분양가", 1.3f, bold = true, end = true)
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
}

@Composable
private fun ModelRow(m: HouseModel) {
    val perPyeong = PriceFormat.perPyeongManwon(m.priceManwon, m.supplyArea)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Cell(m.houseType.ifBlank { "-" }, 1.1f)
        Cell(PriceFormat.area(m.supplyArea), 1.3f)
        Cell("${m.units}", 0.5f, end = true)
        Column(Modifier.weight(1.3f)) {
            Text(
                PriceFormat.full(m.priceManwon),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
            if (perPyeong > 0) {
                Text(
                    "평당 ${PriceFormat.full(perPyeong)}",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CmpetHeader() {
    Row(Modifier.fillMaxWidth()) {
        Cell("주택형", 1.1f, bold = true)
        Cell("순위·지역", 1.1f, bold = true)
        Cell("공급", 0.5f, bold = true, end = true)
        Cell("접수", 0.7f, bold = true, end = true)
        Cell("경쟁률", 0.9f, bold = true, end = true)
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
}

@Composable
private fun CmpetRow(c: Competition) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Cell(c.houseType.ifBlank { "-" }, 1.1f)
        Cell("${c.rank}순위 ${c.resideName.ifBlank { c.resideCode }}", 1.1f)
        Cell("${c.units}", 0.5f, end = true)
        Cell("%,d".format(c.requests), 0.7f, end = true)
        Cell(c.rateText.ifBlank { CompetitionFormat.rate(c.rate) }, 0.9f, end = true)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(
    text: String,
    weight: Float,
    bold: Boolean = false,
    end: Boolean = false,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else null,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        modifier = Modifier.weight(weight).padding(end = 4.dp),
    )
}
