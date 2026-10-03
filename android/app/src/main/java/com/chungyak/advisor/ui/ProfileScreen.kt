package com.chungyak.advisor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chungyak.advisor.match.AccountType
import com.chungyak.advisor.match.Eligibility
import com.chungyak.advisor.match.NotifyMode
import com.chungyak.advisor.match.Profile

/** 내 청약 조건 입력 화면. 저장 값은 기기 안(ProfileStore)에만 남는다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(initial: Profile, initialMode: NotifyMode, onSave: (Profile, NotifyMode) -> Unit, onClear: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var p by remember { mutableStateOf(initial) }
    var mode by remember { mutableStateOf(initialMode) }
    // 숫자 입력칸은 문자열로 들고 있다가 저장 때 변환(빈칸 = 미입력 -1).
    val nums = remember {
        mutableStateOf(
            mapOf(
                "residenceMonths" to initial.residenceMonths, "homesOwned" to initial.homesOwned,
                "children" to initial.children, "accountMonths" to initial.accountMonths,
                "payments" to initial.payments, "depositManwon" to initial.depositManwon,
                "householdSize" to initial.householdSize, "incomePct" to initial.incomePct,
                "realEstateManwon" to initial.realEstateManwon, "maxPriceManwon" to initial.maxPriceManwon,
                "minAreaM2" to initial.minAreaM2, "maxAreaM2" to initial.maxAreaM2,
            ).mapValues { (_, v) -> if (v >= 0) v.toString() else "" }
        )
    }
    var error by remember { mutableStateOf("") }
    val uri = LocalUriHandler.current

    @Composable
    fun num(key: String, label: String) = OutlinedTextField(
        value = nums.value[key].orEmpty(),
        onValueChange = { v -> nums.value = nums.value + (key to v.filter { it.isDigit() }.take(7)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    fun build(): Profile {
        fun n(k: String, unset: Int = -1) = nums.value[k]?.toIntOrNull() ?: unset
        return p.copy(
            residenceMonths = n("residenceMonths"), homesOwned = n("homesOwned", 0), children = n("children", 0),
            accountMonths = n("accountMonths"), payments = n("payments"), depositManwon = n("depositManwon"),
            householdSize = n("householdSize"), incomePct = n("incomePct"), realEstateManwon = n("realEstateManwon"),
            maxPriceManwon = n("maxPriceManwon"), minAreaM2 = n("minAreaM2"), maxAreaM2 = n("maxAreaM2"),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("내 청약 조건") },
                navigationIcon = { TextButton(onClick = onBack) { Text("〈 닫기") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "입력한 조건과 공고의 자격 요건을 비교해 맞는 공고만 알려 드립니다. " +
                    "이 정보는 이 폰 안에만 저장되고 어디로도 전송되지 않습니다(자동 백업·백업 파일에도 제외).",
                style = MaterialTheme.typography.bodySmall,
            )

            Section("알림 범위")
            Chips(NotifyMode.entries, mode, { it.label }) { mode = it }

            Section("거주")
            Chips(listOf("서울", "경기", "인천", "기타"), p.sido, { it }) { p = p.copy(sido = it) }
            OutlinedTextField(
                value = p.sigungu, onValueChange = { p = p.copy(sigungu = it.take(20)) },
                label = { Text("시·군·구 (예: 성남시, 강남구)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            num("residenceMonths", "현 거주지 연속 거주 기간(개월)")

            Section("세대·주택")
            SwitchRow("세대주", p.householdHead) { p = p.copy(householdHead = it) }
            num("homesOwned", "세대 보유 주택 수 (무주택이면 0)")
            SwitchRow("세대원 누구라도 과거에 집을 가진 적 있음", p.everOwned) { p = p.copy(everOwned = it) }
            SwitchRow("세대원 5년 안에 청약 당첨된 적 있음", p.wonWithin5y) { p = p.copy(wonWithin5y = it) }
            SwitchRow("특별공급에 당첨된 적 있음", p.usedSpecial) { p = p.copy(usedSpecial = it) }

            Section("혼인·자녀·부양")
            SwitchRow("혼인 중", p.married) { p = p.copy(married = it) }
            if (p.married) OutlinedTextField(
                value = p.marriageYm, onValueChange = { p = p.copy(marriageYm = it.filter { c -> c.isDigit() || c == '-' }.take(7)) },
                label = { Text("혼인신고 연월 (예: 2022-05)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            num("children", "미성년 자녀 수 (태아 포함)")
            SwitchRow("2세 미만 자녀 있음(임신 포함)", p.hasNewborn) { p = p.copy(hasNewborn = it) }
            SwitchRow("만 65세 이상 부모님을 3년 이상 같은 등본으로 부양", p.supportsParent) { p = p.copy(supportsParent = it) }

            Section("청약통장")
            Chips(AccountType.entries, p.account, { it.label }) { p = p.copy(account = it) }
            num("accountMonths", "가입 기간(개월)")
            num("payments", "납입 인정 회차(국민주택용)")
            num("depositManwon", "예치금·납입 총액(만원)")

            Section("소득·자산")
            num("householdSize", "가구원 수")
            num("incomePct", "세대 월평균소득 (전년도 도시근로자 대비 %)")
            TextButton(onClick = { uri.openUri("https://www.applyhome.co.kr/ar/ara/selectSubscrptIntroSpetialView.do") }) {
                Text("청약홈 소득 기준표 열기(가구원수별 금액과 비교해 %를 넣으세요)")
            }
            SwitchRow("맞벌이", p.dualIncome) { p = p.copy(dualIncome = it) }
            num("realEstateManwon", "세대 부동산 자산(만원, 선택)")
            SwitchRow("소득세 5년 이상 납부(생애최초용)", p.taxYears5) { p = p.copy(taxYears5 = it) }

            Section("관심 조건(선택 — 비우면 전체)")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("서울", "경기", "인천").forEach { s ->
                    FilterChip(
                        selected = s in p.interestSido,
                        onClick = { p = p.copy(interestSido = if (s in p.interestSido) p.interestSido - s else p.interestSido + s) },
                        label = { Text(s) },
                    )
                }
            }
            num("maxPriceManwon", "분양가 상한(만원)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { num("minAreaM2", "전용 최소(㎡)") }
                Column(Modifier.weight(1f)) { num("maxAreaM2", "전용 최대(㎡)") }
            }

            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val built = build()
                    error = when {
                        built.sido.isBlank() -> "거주 시·도를 골라 주세요."
                        built.married && built.marriageYm.isNotBlank() &&
                            !Regex("^\\d{4}-\\d{1,2}$").matches(built.marriageYm) -> "혼인신고 연월은 2022-05 형식으로 입력하세요."
                        else -> ""
                    }
                    if (error.isBlank()) onSave(built, mode)
                }) { Text("저장") }
                OutlinedButton(onClick = onClear) { Text("조건 지우기(전체 알림)") }
            }
            Text(
                "판정 기준: ${Eligibility.RULES_SOURCE} (${Eligibility.RULES_DATE} 기준). 참고용이며 최종 자격은 " +
                    "입주자모집공고문과 청약홈에서 확인하세요. 모르는 항목은 비워 두면 '확인 필요'로 표시됩니다.",
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(6.dp))
    Text(title, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun <T> Chips(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { FilterChip(selected = it == selected, onClick = { onSelect(it) }, label = { Text(label(it)) }) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
