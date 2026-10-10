package com.chungyak.advisor.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.chungyak.advisor.match.AccountPeriod
import com.chungyak.advisor.match.AccountType
import com.chungyak.advisor.match.Gajeom
import com.chungyak.advisor.match.NotifyMode
import com.chungyak.advisor.match.Profile
import com.chungyak.advisor.match.Repayment
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 한 입력 묶음([section])의 입력칸들. '내 조건' 탭과 온보딩이 함께 쓴다(같은 칸·같은 데이터).
 * 제목은 그리지 않는다 — 부르는 쪽이 탭에서는 소제목, 온보딩에서는 단계 머리로 그린다.
 */
@Composable
fun ProfileFields(section: FormSection, d: ProfileDraft, onChange: (ProfileDraft) -> Unit) {
    val p = d.profile

    @Composable
    fun num(key: String, label: String) = OutlinedTextField(
        value = d.num(key),
        onValueChange = { onChange(d.withNum(key, it)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

    fun edit(f: (Profile) -> Profile) = onChange(d.edit(f))

    when (section) {
        FormSection.NOTIFY -> Chips(NotifyMode.entries, d.mode, { it.label }) { onChange(d.copy(mode = it)) }

        FormSection.RESIDENCE -> {
            Chips(listOf("서울", "경기", "인천", "기타"), p.sido, { it }) { v -> edit { it.copy(sido = v) } }
            OutlinedTextField(
                value = p.sigungu, onValueChange = { v -> edit { it.copy(sigungu = v.take(20)) } },
                label = { Text("시·군·구 (예: 성남시, 강남구)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            ResidenceSinceField(
                since = p.residenceSince,
                legacyMonths = p.residenceMonths,
                onChange = { v -> edit { it.copy(residenceSince = v, residenceMonths = -1) } },
            )
        }

        FormSection.HOUSEHOLD -> {
            SwitchRow("세대주", p.householdHead) { v -> edit { it.copy(householdHead = v) } }
            num("homesOwned", "세대 보유 주택 수 (무주택이면 0)")
            SwitchRow("세대원 누구라도 과거에 집을 가진 적 있음", p.everOwned) { v -> edit { it.copy(everOwned = v) } }
            SwitchRow("세대원 5년 안에 청약 당첨된 적 있음", p.wonWithin5y) { v -> edit { it.copy(wonWithin5y = v) } }
            SwitchRow("특별공급에 당첨된 적 있음", p.usedSpecial) { v -> edit { it.copy(usedSpecial = v) } }
        }

        FormSection.FAMILY -> {
            SwitchRow("혼인 중", p.married) { v -> edit { it.copy(married = v) } }
            if (p.married) OutlinedTextField(
                value = p.marriageYm,
                onValueChange = { v -> edit { it.copy(marriageYm = v.filter { c -> c.isDigit() || c == '-' }.take(7)) } },
                label = { Text("혼인신고 연월 (예: 2022-05)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            num("children", "미성년 자녀 수 (태아 포함)")
            SwitchRow("2세 미만 자녀 있음(임신 포함)", p.hasNewborn) { v -> edit { it.copy(hasNewborn = v) } }
            SwitchRow("만 65세 이상 부모님을 3년 이상 같은 등본으로 부양", p.supportsParent) { v -> edit { it.copy(supportsParent = v) } }
        }

        FormSection.ACCOUNT -> {
            Chips(AccountType.entries, p.account, { it.label }) { v -> edit { it.copy(account = v) } }
            AccountOpenedField(
                opened = p.accountOpened,
                onChange = { v -> edit { it.copy(accountOpened = v) } },
                manualMonths = d.num("accountMonths").toIntOrNull(),
            )
            num("accountMonths", "또는 가입 기간 직접 입력(개월) — 가입 일자가 있으면 그걸 우선")
            num("payments", "납입 인정 회차(국민주택용)")
            num("depositManwon", "예치금·납입 총액(만원)")
        }

        FormSection.GAJEOM -> {
            DateField("생년월일", p.birthDate, { v -> edit { it.copy(birthDate = v) } })
            if (p.everOwned) DateField("무주택이 된 날(집 처분일)", p.homelessSince, { v -> edit { it.copy(homelessSince = v) } }, minYear = 1970)
            num("dependents", "부양가족 수(본인 제외, 비우면 배우자+자녀로 추정)")
            Text(
                "부양가족: 배우자, 같은 등본의 미혼 자녀, 3년 이상 같은 등본으로 부양한 부모님(배우자 부모 포함).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GajeomCard(Gajeom.calc(d.build(), LocalDate.now()), title = "오늘 기준 내 가점")
        }

        FormSection.INCOME -> {
            val uri = LocalUriHandler.current
            num("householdSize", "가구원 수")
            num("incomePct", "세대 월평균소득 (전년도 도시근로자 대비 %)")
            TextButton(onClick = { uri.openUri("https://www.applyhome.co.kr/ar/ara/selectSubscrptIntroSpetialView.do") }) {
                Text("청약홈 소득 기준표 열기(가구원수별 금액과 비교해 %를 넣으세요)")
            }
            SwitchRow("맞벌이", p.dualIncome) { v -> edit { it.copy(dualIncome = v) } }
            num("realEstateManwon", "세대 부동산 자산(만원, 선택)")
            SwitchRow("소득세 5년 이상 납부(생애최초용)", p.taxYears5) { v -> edit { it.copy(taxYears5 = v) } }
        }

        FormSection.INTEREST -> {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("서울", "경기", "인천").forEach { s ->
                    FilterChip(
                        selected = s in p.interestSido,
                        onClick = { edit { it.copy(interestSido = if (s in it.interestSido) it.interestSido - s else it.interestSido + s) } },
                        label = { Text(s) },
                    )
                }
            }
            num("maxPriceManwon", "분양가 상한(만원)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { num("minAreaM2", "전용 최소(㎡)") }
                Column(Modifier.weight(1f)) { num("maxAreaM2", "전용 최대(㎡)") }
            }
        }

        FormSection.FUNDS -> {
            num("cashManwon", "보유 현금·가용 자본(만원) — 예: 30000 = 3억")
            num("loanLimitManwon", "대출 가능 한도(만원, 은행·앱에서 조회한 금액)")
            num("monthlyCapManwon", "대출 시 월 상환액 상한(만원) — 매달 이만큼까지 낼 수 있음")
            Text("계산 가정(바꿀 수 있어요)", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = d.num("loanRatePct"),
                    onValueChange = { onChange(d.withDecimal("loanRatePct", it)) },
                    label = { Text("대출 금리(연 %)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Column(Modifier.weight(1f)) { num("loanYears", "상환 기간(년)") }
            }
            Chips(Repayment.entries, p.repayment, { it.label }) { v -> edit { it.copy(repayment = v) } }
            num("downPaymentPct", "계약금 비율(%) — 현금으로 낼 몫, 보통 10~20")
            BudgetSummary(d.build())
        }
    }
}

/** 청약통장 가입 일자: 날짜 선택기(미래 날짜 선택 불가) + 오늘 기준 계산된 가입 기간 표시. */
@Composable
private fun AccountOpenedField(opened: String, onChange: (String) -> Unit, manualMonths: Int?) {
    var picking by remember { mutableStateOf(false) }
    val date = AccountPeriod.parse(opened)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (date != null) "가입 일자 $date" else "가입 일자 미입력",
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = { picking = true }) { Text(if (date != null) "변경" else "날짜 선택") }
        if (date != null) TextButton(onClick = { onChange("") }) { Text("지우기") }
    }
    val info = when {
        date != null -> "가입 ${AccountPeriod.label(AccountPeriod.months(date, LocalDate.now()))} · 오늘 기준. " +
            "판정은 공고일 기준으로 매번 다시 계산합니다." + if (manualMonths != null) " (직접 입력한 기간보다 우선)" else ""
        manualMonths != null -> "직접 입력: 가입 ${AccountPeriod.label(manualMonths)} — 가입 일자를 넣으면 기간이 자동으로 늘어납니다."
        else -> "가입 일자를 넣으면 기간을 자동 계산합니다."
    }
    Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

    if (picking) PastDatePicker("청약통장 가입 일자", date, 1977, onPick = onChange, onDismiss = { picking = false })
}

/** 현 거주지 전입일: 날짜 선택기 + 오늘 기준 연속 거주 기간 표시. 날짜를 고르면 예전 개월 수 입력은 버린다. */
@Composable
private fun ResidenceSinceField(since: String, legacyMonths: Int, onChange: (String) -> Unit) {
    DateField("현 거주지 전입일", since, onChange)
    val date = AccountPeriod.parse(since)
    val info = when {
        date != null -> "약 ${AccountPeriod.label(AccountPeriod.months(date, LocalDate.now()).coerceAtLeast(0))} 거주 · 오늘 기준. " +
            "판정은 공고일 기준으로 매번 다시 계산합니다."
        legacyMonths >= 0 -> "예전 입력: 거주 ${AccountPeriod.label(legacyMonths)} — 전입일을 넣으면 기간이 자동으로 늘어납니다."
        else -> "주민등록상 전입일을 넣으면 연속 거주 기간을 자동 계산합니다."
    }
    Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
}

/** 날짜 하나 고르는 줄: "제목 yyyy-MM-dd [변경] [지우기]". 미래 날짜는 고를 수 없다. */
@Composable
private fun DateField(title: String, value: String, onChange: (String) -> Unit, minYear: Int = 1930) {
    var picking by remember { mutableStateOf(false) }
    val date = AccountPeriod.parse(value)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (date != null) "$title $date" else "$title 미입력",
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = { picking = true }) { Text(if (date != null) "변경" else "날짜 선택") }
        if (date != null) TextButton(onClick = { onChange("") }) { Text("지우기") }
    }
    if (picking) PastDatePicker(title, date, minYear, onPick = onChange, onDismiss = { picking = false })
}

/** 오늘까지의 날짜만 고를 수 있는 날짜 선택 대화상자. 고르면 "yyyy-MM-dd"로 [onPick]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastDatePicker(title: String, initial: LocalDate?, minYear: Int, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneOffset.UTC // DatePicker는 UTC 자정 millis를 쓴다.
    val todayMillis = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
        yearRange = minYear..LocalDate.now().year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= LocalDate.now().year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
                        if (!d.isAfter(LocalDate.now())) onPick(d.toString())
                    }
                    onDismiss()
                },
            ) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    ) { DatePicker(state = state, title = { Text(title, Modifier.padding(start = 24.dp, top = 16.dp)) }) }
}

/** '내 조건' 탭의 소제목. */
@Composable
internal fun FormSectionTitle(title: String) {
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
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
