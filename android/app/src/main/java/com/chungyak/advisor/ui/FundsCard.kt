package com.chungyak.advisor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chungyak.advisor.Caption
import com.chungyak.advisor.Pill
import com.chungyak.advisor.data.HouseModel
import com.chungyak.advisor.data.Notice
import com.chungyak.advisor.match.Affordability
import com.chungyak.advisor.match.LoanRules
import com.chungyak.advisor.match.Profile

/** 자금 판정 색: 가능 = 파랑(브랜드), 빠듯 = 주황, 불가 = 빨강. */
@Composable
internal fun fundsColor(v: Affordability.Verdict) = when (v) {
    Affordability.Verdict.OK -> MaterialTheme.colorScheme.primary
    Affordability.Verdict.TIGHT -> MaterialTheme.colorScheme.tertiary
    Affordability.Verdict.NO -> MaterialTheme.colorScheme.error
}

@Composable
private fun FundsPill(v: Affordability.Verdict) {
    val c = fundsColor(v)
    Pill(v.label, c.copy(alpha = 0.14f), c)
}

/** 목록: 내 자금 판정 한 줄(내 조건 판정 줄과 같은 모양). 색은 가장 유리한 주택형 기준. */
@Composable
internal fun FundsLine(r: Affordability.NoticeResult) {
    val c = fundsColor(r.best.verdict)
    Text(
        "내 자금: ${FundsFormat.summary(r)}",
        style = MaterialTheme.typography.labelMedium,
        color = c,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .background(c.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** 개인 대출 한도 요약(내 조건 → 자금 묶음에서 입력하는 대로 바로 계산). 현금 미입력이면 안내만. */
@Composable
internal fun BudgetSummary(p: Profile) {
    val b = Affordability.budget(p)
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (b == null) {
            Text("보유 현금을 넣으면 공고마다 LTV·DSR·주담대 상한을 따져 살 수 있는지 보여 드려요.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(FundsFormat.budget(b), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(FundsFormat.loanBasis(b, p), style = MaterialTheme.typography.bodySmall)
            Text("가정: ${FundsFormat.assumptions(p)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(FundsFormat.RULES_NOTE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 상세: "내 자금으로 살 수 있나" 카드. 공고 지역(규제지역 여부)과 주택형별 분양가(최고가)마다 가능/빠듯/불가,
 * 대출 최대액과 그걸 정한 제약(예: "DSR 40%에 걸림"), 월 상환액·총이자, 입주 전 현금.
 * 자금을 입력하지 않았으면 판정 대신 입력 안내([onEdit]).
 */
@Composable
internal fun FundsCard(n: Notice, models: List<HouseModel>, profile: Profile, onEdit: () -> Unit) {
    val area = LoanRules.area(n)
    val b = Affordability.budget(profile, area)
    val result = Affordability.evaluate(profile, n, models)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("내 자금으로 살 수 있나", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (result != null) FundsPill(result.best.verdict)
            }
            Spacer(Modifier.height(6.dp))
            when {
                b == null -> {
                    Caption("보유 현금·연소득을 넣으면 주택형마다 살 수 있는지(가능/빠듯/불가), 대출이 얼마나 나오는지(LTV·DSR·주담대 상한), 월 상환액과 총이자를 보여 드려요.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onEdit) { Text("내 자금 입력하기") }
                }
                result == null -> {
                    Caption(FundsFormat.areaLine(area))
                    Caption("분양가 정보가 아직 없어 판정할 수 없습니다. '지금 확인' 후 다시 열어보세요.")
                }
                else -> {
                    Text(FundsFormat.areaLine(area), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Caption("현금 ${FundsFormat.amount(b.cash)} · 가정: ${FundsFormat.assumptions(profile)}")
                    Spacer(Modifier.height(4.dp))
                    val priced = models.filter { it.priceManwon > 0 }
                    if (priced.isNotEmpty()) {
                        Caption("주택형별 · 최고가 기준")
                        priced.forEachIndexed { i, m ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Affordability.check(b, profile, m.priceManwon, area)?.let { FundsRow(m.houseType.ifBlank { "-" }, it, profile) }
                        }
                    } else {
                        // 주택형별 정보를 아직 못 받았으면 공고의 최저·최고 분양가로.
                        Caption("공고 분양가 범위 기준(주택형별 정보는 아직 못 받음)")
                        result.checks.sortedBy { it.price }.forEachIndexed { i, c ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            FundsRow(if (c.price == n.priceMinManwon && result.checks.size > 1) "최저가" else "최고가", c, profile)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                FundsFormat.RULES_NOTE + " 담보가치는 분양가로 보고, 대출은 입주 때 잔금대출 기준(LTV·DSR·주담대 상한 중 가장 작은 값)입니다. " +
                    "중도금 집단대출(분양가 ${LoanRules.MID_PAYMENT_PCT}%까지, LTV 이내)은 DSR이 적용되지 않지만 잔금대출로 바꿀 때 위 한도로 줄어듭니다. " +
                    "빠듯 = 입주 전 현금(계약금 ${profile.downPaymentPct}% + 중도금 중 대출 안 되는 몫)이 모자라거나, 남는 돈이 분양가의 ${Affordability.TIGHT_MARGIN_PCT}% 미만" +
                    "(취득세·등기 등 부대비용). 정책대출(디딤돌·보금자리론·신생아 특례)·2금융권·옵션·발코니 확장비는 반영하지 않습니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 주택형 한 줄: 왼쪽 주택형·분양가, 오른쪽 판정 배지, 아래 근거·대출 한도·상환·입주 전 현금. */
@Composable
private fun FundsRow(label: String, c: Affordability.Check, p: Profile) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Caption("분양가 ${PriceFormat.full(c.price)}")
            }
            FundsPill(c.verdict)
        }
        Spacer(Modifier.height(4.dp))
        Text(FundsFormat.detail(c), style = MaterialTheme.typography.bodySmall, color = fundsColor(c.verdict))
        Text(FundsFormat.loanLine(c, p), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Caption(FundsFormat.limits(c))
        FundsFormat.repaymentLine(c, p).takeIf { it.isNotEmpty() }?.let { Caption(it) }
        Caption(FundsFormat.buildLine(c))
    }
}
