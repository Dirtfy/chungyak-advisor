package com.chungyak.advisor

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import com.chungyak.advisor.ui.EmptyState
import com.chungyak.advisor.ui.KeyGuide
import com.chungyak.advisor.ui.SettingsScreen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chungyak.advisor.data.Competition
import com.chungyak.advisor.data.HouseModel
import com.chungyak.advisor.data.Notice
import com.chungyak.advisor.map.LocationCard
import com.chungyak.advisor.match.AccountPeriod
import com.chungyak.advisor.match.Eligibility
import com.chungyak.advisor.match.Gajeom
import com.chungyak.advisor.ui.GajeomCard
import java.time.LocalDate
import com.chungyak.advisor.ui.CompetitionFormat
import com.chungyak.advisor.ui.ProfileScreen
import com.chungyak.advisor.ui.ScheduleBadge
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

/** 하단 탭(리디자인 3단계, docs/11). */
private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    NOTICES("공고", Icons.Outlined.Apartment, Icons.Filled.Apartment),
    PROFILE("내 조건", Icons.Outlined.Person, Icons.Filled.Person),
    SETTINGS("설정", Icons.Outlined.Settings, Icons.Filled.Settings),
}

@Composable
private fun HomeScreen(vm: NoticeViewModel = viewModel()) {
    val notices by vm.notices.collectAsState()
    // 백그라운드 수집이 DB를 바꾸면 상태 문구/경쟁률 신청 안내도 다시 읽는다.
    LaunchedEffect(notices) { vm.refreshStatus() }
    // 처음 켜서 서비스키가 없으면 설정 탭부터.
    var tab by rememberSaveable { mutableStateOf(if (vm.hasKey) Tab.NOTICES else Tab.SETTINGS) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = notices.firstOrNull { it.id == selectedId }
    val profile by vm.profile.collectAsState()
    val matches by vm.matches.collectAsState()

    // 상세는 탭 바 없이 전체 화면(지도·본문 영역을 넓게).
    if (selected != null) {
        BackHandler { selectedId = null }
        NoticeDetailScreen(selected, vm, matches[selected.id], onBack = { selectedId = null })
        return
    }
    // 다른 탭에서 뒤로 가기 → 공고 탭.
    if (tab != Tab.NOTICES) BackHandler { tab = Tab.NOTICES }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tab = t },
                        icon = { Icon(if (t == tab) t.selectedIcon else t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.NOTICES -> NoticesTab(vm, notices, onOpen = { selectedId = it }, onGoTab = { tab = it })
                Tab.PROFILE -> ProfileScreen(
                    initial = profile, initialMode = vm.notifyMode,
                    onSave = { p, m -> vm.saveProfile(p, m); tab = Tab.NOTICES },
                    onClear = { vm.clearProfile() },
                )
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}

/** 공고 탭: 상태 → 개수·필터 → 정렬 → 카드 목록. 머리 부분도 목록과 함께 스크롤된다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticesTab(vm: NoticeViewModel, notices: List<Notice>, onOpen: (String) -> Unit, onGoTab: (Tab) -> Unit) {
    val status by vm.status.collectAsState()
    val sort by vm.sort.collectAsState()
    val profile by vm.profile.collectAsState()
    val matches by vm.matches.collectAsState()
    val scores by vm.scores.collectAsState()
    val today = vm.today()
    var onlyMatched by rememberSaveable { mutableStateOf(false) }
    val shown = if (onlyMatched && profile.isSet)
        notices.filter { matches[it.id]?.verdict != Eligibility.Verdict.INELIGIBLE } else notices

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("청약 레이더 · 수도권 일반공급") },
                actions = {
                    IconButton(onClick = { vm.checkNow(); vm.refreshStatus() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "지금 확인")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!vm.hasKey) item(key = "nokey") { KeyMissingCard { onGoTab(Tab.SETTINGS) } }
            item(key = "status") { StatusCard(status = status, onCheck = { vm.checkNow(); vm.refreshStatus() }) }
            item(key = "head") {
                Column {
                    Text(
                        if (profile.isSet) "수집된 공고 ${notices.size}건 · 내 조건에 맞음 " +
                            "${notices.count { matches[it.id]?.verdict == Eligibility.Verdict.ELIGIBLE }}건"
                        else "수집된 공고 ${notices.size}건",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (profile.isSet) {
                        FilterChip(
                            selected = onlyMatched, onClick = { onlyMatched = !onlyMatched },
                            label = { Text("가능·확인 필요만 보기") },
                        )
                    } else {
                        TextButton(onClick = { onGoTab(Tab.PROFILE) }) { Text("내 조건을 입력하면 맞는 공고만 알려 드려요 〉") }
                    }
                    SortBar(sort, vm::setSort)
                    if (sort == SortOrder.RECOMMEND) Text(
                        "추천 점수(100) = 내 조건 판정 40 + 가점 20 + 경쟁률(낮을수록) 25 + 분양가 상한 적합 15. " +
                            "접수 예정·진행 중인 공고가 먼저, 마감된 공고는 뒤에." +
                            if (!profile.isSet) " 내 조건을 입력하면 더 정확해져요." else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                notices.isEmpty() -> item(key = "empty") {
                    if (vm.hasKey) EmptyState(
                        Icons.Outlined.Inbox, "아직 수집된 공고가 없어요",
                        "위의 새로고침(지금 확인)을 누르면 바로 조회합니다. 이후엔 6시간마다 자동으로 확인해요.",
                        action = "지금 확인", onAction = { vm.checkNow(); vm.refreshStatus() },
                    ) else EmptyState(
                        Icons.Outlined.Key, "서비스키가 필요해요",
                        "설정 탭에서 본인의 data.go.kr 서비스키를 입력하면 공고를 모아 드립니다.",
                        action = "설정으로 가기", onAction = { onGoTab(Tab.SETTINGS) },
                    )
                }
                shown.isEmpty() -> item(key = "filtered") {
                    EmptyState(
                        Icons.Outlined.FilterAltOff, "조건에 맞는 공고가 없어요",
                        "지금 모인 공고는 모두 내 조건으로는 신청이 어려워 보여요. 필터를 끄면 전체를 볼 수 있어요.",
                        action = "전체 보기", onAction = { onlyMatched = false },
                    )
                }
                else -> items(shown, key = { it.id }) {
                    NoticeRow(
                        it, today, CompetitionFormat.summary(it, today, vm.cmpetUnauthorized), matches[it.id],
                        recommend = if (sort == SortOrder.RECOMMEND) scores[it.id]?.total else null,
                    ) { onOpen(it.id) }
                }
            }
        }
    }
}

/** 서비스키 미입력 안내. 키는 각자 data.go.kr에서 받아 넣는다(앱·저장소에 내장하지 않음). */
@Composable
private fun KeyMissingCard(onOpenSettings: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("서비스키를 입력하세요", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "공고를 받으려면 본인의 공공데이터포털(data.go.kr) 서비스키가 필요합니다. 키는 이 폰에만 저장됩니다.",
                style = MaterialTheme.typography.bodySmall,
            )
            KeyGuide()
            Button(onClick = onOpenSettings) { Text("설정에서 입력하기") }
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

/** 최근 확인 결과. 오류면 빨간 아이콘, 정상이면 파란 체크(리디자인 4단계). */
@Composable
private fun StatusCard(status: String, onCheck: () -> Unit) {
    val error = status.contains("오류")
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when {
                    status.isBlank() -> Icons.Outlined.Notifications
                    error -> Icons.Outlined.ErrorOutline
                    else -> Icons.Outlined.CheckCircle
                },
                contentDescription = null,
                tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (status.isBlank()) "아직 확인한 적이 없습니다." else "최근: $status",
                style = MaterialTheme.typography.bodyMedium,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onCheck) { Text("지금 확인") }
        }
    }
}

/** 목록 카드(리디자인, docs/11): 지역·규제·일정 배지 → 단지명 → 주소 → 핵심 숫자 3칸 → 내 조건 판정. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoticeRow(
    n: Notice,
    today: String,
    cmpet: String,
    match: Eligibility.Result?,
    /** 추천순일 때만: 추천 점수(0~100). */
    recommend: Int? = null,
    onClick: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(n.areaName, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    if (n.speculationArea) Pill("투기과열", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
                    if (n.adjustmentArea) Pill("조정대상", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
                    if (recommend != null) Pill("추천 $recommend", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                }
                ScheduleBadge.of(n, today)?.let { b ->
                    val (bg, fg) = when (b.tone) {
                        ScheduleBadge.Tone.OPEN -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                        ScheduleBadge.Tone.UPCOMING -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Pill(b.text, bg, fg)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(n.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(n.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("세대", if (n.totalUnits > 0) "${n.totalUnits}" else "-", Modifier.weight(0.7f))
                Stat("분양가", priceSummary(n), Modifier.weight(1.3f))
                Stat("경쟁률", cmpet, Modifier.weight(1.3f))
            }
            Spacer(Modifier.height(10.dp))
            val schedule = if (n.rank1Start.isNotBlank()) "1순위 ${n.rank1Start}" else "모집공고 ${n.noticeDate}"
            Text(
                "$schedule · 발표 ${n.resultDate.ifBlank { "-" }}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (match != null) {
                Spacer(Modifier.height(10.dp))
                MatchLine(match)
            }
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        maxLines = 1,
        modifier = Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(end = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2)
    }
}

/** 목록용 분양가 범위. 아직 상세를 못 받았으면 "확인 중", 받았는데 값이 없으면 "정보 없음". */
private fun priceSummary(n: Notice): String =
    if (n.modelsFetchedAt == 0L && n.priceMaxManwon <= 0) "확인 중"
    else PriceFormat.range(n.priceMinManwon, n.priceMaxManwon)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticeDetailScreen(n: Notice, vm: NoticeViewModel, match: Eligibility.Result?, onBack: () -> Unit) {
    val models by remember(n.id) { vm.models(n.id) }.collectAsState(initial = emptyList())
    val cmpets by remember(n.id) { vm.competitions(n.id) }.collectAsState(initial = emptyList())
    val cmpetSummary = CompetitionFormat.summary(n, vm.today(), vm.cmpetUnauthorized)
    val profile by vm.profile.collectAsState()
    // 가점은 입주자모집공고일 기준(공고일을 못 읽으면 오늘).
    val gajeom = if (profile.isSet) Gajeom.calc(profile, AccountPeriod.parse(n.noticeDate) ?: LocalDate.now()) else null
    val uri = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(n.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "목록으로") }
                },
            )
        },
    ) { padding ->
        NoticeDetailBody(
            n, vm.today(), cmpetSummary, models, cmpets, match, gajeom,
            modifier = Modifier.fillMaxSize().padding(padding),
            onOpenNotice = { runCatching { uri.openUri(n.url) } },
            location = { LocationCard(n.name, n.address) },
        )
    }
}

/**
 * 상세 본문(리디자인 2단계, docs/11): 요약 → 위치 → 내 조건 판정 → 분양가 → 경쟁률, 모두 같은 폭의 흰 카드.
 * 스크롤 상태와 위치 카드는 밖에서 받는다 — 테스트가 여러 스크롤 위치에서 지도 겹침을 검사한다.
 */
@Composable
internal fun NoticeDetailBody(
    n: Notice,
    today: String,
    cmpetSummary: String,
    models: List<HouseModel>,
    cmpets: List<Competition>,
    match: Eligibility.Result?,
    gajeom: Gajeom.Result? = null,
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    onOpenNotice: () -> Unit = {},
    location: @Composable () -> Unit,
) {
    Column(
        modifier.padding(horizontal = 16.dp).verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(0.dp))
        DetailSummaryCard(n, today, cmpetSummary)
        location()
        if (match != null) MatchCard(match)
        if (gajeom != null) GajeomCard(gajeom, title = "이 공고 기준 내 가점")
        PriceCard(n, models)
        CompetitionCard(cmpetSummary, cmpets)
        Text(
            "· 분양가: 청약홈 주택형별 공급금액(분양최고금액). 평당가 = 분양가 ÷ 공급면적(평, 1평=3.3058㎡).\n" +
                "· 세대수: 일반공급 기준. 옵션·발코니 확장비 제외 — 정확한 금액은 모집공고문 확인.\n" +
                "· 경쟁률: 청약홈 접수 결과(목록 값은 1순위 해당지역 기준, 평균 = 접수 합 ÷ 공급 합). △ = 미달 세대수.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (n.url.isNotBlank()) {
            Button(onClick = onOpenNotice, modifier = Modifier.fillMaxWidth()) { Text("청약홈 공고 열기") }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** 상세 화면의 흰 카드 틀: 굵은 제목, 강조 값, 그 아래 내용. */
@Composable
private fun SectionCard(title: String, value: String? = null, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            // 값이 길 수 있어(경쟁률 "최고 … · 평균 …") 제목 옆이 아니라 다음 줄에 둔다.
            if (value != null) Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PriceCard(n: Notice, models: List<HouseModel>) {
    SectionCard("분양가", priceSummary(n)) {
        if (models.isEmpty()) {
            Caption(
                if (n.modelsFetchedAt == 0L) "주택형별 정보를 아직 받지 못했습니다. '지금 확인' 후 다시 열어보세요."
                else PriceFormat.NONE,
            )
        } else {
            Caption("주택형별 · 최고가 기준")
            models.forEachIndexed { i, m ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ModelRow(m)
            }
        }
    }
}

/** 주택형 한 줄: 왼쪽 주택형·면적·세대, 오른쪽 분양가·평당가. */
@Composable
private fun ModelRow(m: HouseModel) {
    val perPyeong = PriceFormat.perPyeongManwon(m.priceManwon, m.supplyArea)
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(m.houseType.ifBlank { "-" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Caption("공급 ${PriceFormat.area(m.supplyArea)} · ${m.units}세대")
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(PriceFormat.full(m.priceManwon), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (perPyeong > 0) Caption("평당 ${PriceFormat.full(perPyeong)}")
        }
    }
}

@Composable
private fun CompetitionCard(cmpetSummary: String, cmpets: List<Competition>) {
    SectionCard("경쟁률", cmpetSummary) {
        if (cmpets.isEmpty()) {
            Caption(
                when (cmpetSummary) {
                    CompetitionFormat.BEFORE -> "접수 전입니다. 접수가 시작되면 경쟁률을 받아옵니다."
                    CompetitionFormat.NEED_APPLY ->
                        "경쟁률은 별도 공공데이터 서비스입니다. data.go.kr에서 " +
                            "'한국부동산원_청약홈 청약접수 경쟁률 및 특별공급 신청현황 조회 서비스'(15098905) 활용신청 후 같은 키로 자동 표시됩니다."
                    CompetitionFormat.PENDING -> "접수 결과 집계 중입니다. 다음 확인 때 다시 받아옵니다."
                    else -> CompetitionFormat.NONE
                },
            )
        } else {
            Caption("주택형별 · 일반공급")
            cmpets.forEachIndexed { i, c ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CmpetRow(c)
            }
        }
    }
}

/** 경쟁률 한 줄: 왼쪽 주택형·순위·지역, 오른쪽 경쟁률(미달은 주황)·접수/공급. */
@Composable
private fun CmpetRow(c: Competition) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(c.houseType.ifBlank { "-" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Caption("${c.rank}순위 ${c.resideName.ifBlank { c.resideCode }}")
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                c.rateText.ifBlank { CompetitionFormat.rate(c.rate) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (c.shortfall) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
            Caption("접수 ${"%,d".format(c.requests)} / 공급 ${c.units}")
        }
    }
}

/** 상세 상단 요약(리디자인, docs/11): 배지 · 단지명 · 주소 · 핵심 숫자 · 일정. */
@Composable
internal fun DetailSummaryCard(n: Notice, today: String, cmpet: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(n.areaName, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                ScheduleBadge.of(n, today)?.let { Pill(it.text, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary) }
            }
            Spacer(Modifier.height(10.dp))
            Text(n.name, style = MaterialTheme.typography.titleLarge)
            Text(n.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("세대", if (n.totalUnits > 0) "${n.totalUnits}" else "-", Modifier.weight(0.7f))
                Stat("분양가", priceSummary(n), Modifier.weight(1.3f))
                Stat("경쟁률", cmpet, Modifier.weight(1.3f))
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            ScheduleLine("모집공고", n.noticeDate)
            if (n.rank1Start.isNotBlank()) ScheduleLine("1순위 접수", "${n.rank1Start} ~ ${n.rank1End}")
            ScheduleLine("당첨자 발표", n.resultDate)
        }
    }
}

@Composable
private fun ScheduleLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun verdictColor(v: Eligibility.Verdict) = when (v) {
    Eligibility.Verdict.ELIGIBLE -> MaterialTheme.colorScheme.primary
    Eligibility.Verdict.CHECK -> MaterialTheme.colorScheme.tertiary
    Eligibility.Verdict.INELIGIBLE -> MaterialTheme.colorScheme.outline
}

/** 목록: 내 조건 판정 한 줄. */
@Composable
private fun MatchLine(m: Eligibility.Result) {
    Text(
        "내 조건: ${m.verdict.label} · ${m.summary}",
        style = MaterialTheme.typography.labelMedium,
        color = verdictColor(m.verdict),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .background(verdictColor(m.verdict).copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** 상세: 유형별 판정과 근거. 판정은 색 배지, 근거는 그 아래 목록. */
@Composable
private fun MatchCard(m: Eligibility.Result) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("내 조건 판정", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                VerdictPill(m.verdict)
            }
            m.filteredOut?.let {
                Spacer(Modifier.height(4.dp))
                Text("관심 조건 제외: $it", style = MaterialTheme.typography.bodySmall)
            }
            if (m.notes.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Caption(m.notes.joinToString(" · "))
            }
            // 가능 → 확인 필요 → 불가 순.
            m.tracks.sortedByDescending { it.verdict.rank }.forEach { t ->
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(verdictColor(t.verdict).copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        VerdictPill(t.verdict)
                    }
                    if (t.reasons.isNotEmpty()) Spacer(Modifier.height(4.dp))
                    t.reasons.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "기관추천·청년·이전기관 특공은 판정하지 않습니다. 기준: ${Eligibility.RULES_SOURCE} " +
                    "(${Eligibility.RULES_DATE}). 참고용 — 최종 자격은 모집공고문·청약홈에서 확인.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VerdictPill(v: Eligibility.Verdict) {
    val c = verdictColor(v)
    // 불가(회색)는 옅은 바탕 위 글자가 흐려서 글자만 진한 보조색으로.
    Pill(v.label, c.copy(alpha = 0.14f), if (v == Eligibility.Verdict.INELIGIBLE) MaterialTheme.colorScheme.onSurfaceVariant else c)
}
