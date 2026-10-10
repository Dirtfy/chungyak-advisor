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

/** 구매 가능 자금 요약(내 조건 → 자금 묶음에서 입력하는 대로 바로 계산). 현금 미입력이면 안내만. */
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
            Text("보유 현금을 넣으면 구매 가능 자금을 계산하고, 공고마다 살 수 있는지 보여 드려요.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(FundsFormat.budget(b), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(FundsFormat.loanBasis(b, p), style = MaterialTheme.typography.bodySmall)
            Text("가정: ${FundsFormat.assumptions(p)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 상세: "내 자금으로 살 수 있나" 카드. 주택형별 분양가(최고가)마다 가능/빠듯/불가와 필요한 대출·월 상환액·부족액.
 * 자금을 입력하지 않았으면 판정 대신 입력 안내([onEdit]).
 */
@Composable
internal fun FundsCard(n: Notice, models: List<HouseModel>, profile: Profile, onEdit: () -> Unit) {
    val b = Affordability.budget(profile)
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
                    Caption("보유 현금·대출 한도·월 상환액 상한을 넣으면 주택형마다 살 수 있는지(가능/빠듯/불가), 부족액과 예상 월 상환액을 보여 드려요.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onEdit) { Text("내 자금 입력하기") }
                }
                result == null -> {
                    Text(FundsFormat.budget(b), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Caption("분양가 정보가 아직 없어 판정할 수 없습니다. '지금 확인' 후 다시 열어보세요.")
                }
                else -> {
                    Text(FundsFormat.budget(b), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Caption(FundsFormat.loanBasis(b, profile))
                    Spacer(Modifier.height(4.dp))
                    val priced = models.filter { it.priceManwon > 0 }
                    if (priced.isNotEmpty()) {
                        Caption("주택형별 · 최고가 기준")
                        priced.forEachIndexed { i, m ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Affordability.check(b, profile, m.priceManwon)?.let { FundsRow(m.houseType.ifBlank { "-" }, it) }
                        }
                    } else {
                        // 주택형별 정보를 아직 못 받았으면 공고의 최저·최고 분양가로.
                        Caption("공고 분양가 범위 기준(주택형별 정보는 아직 못 받음)")
                        result.checks.sortedBy { it.price }.forEachIndexed { i, c ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            FundsRow(if (c.price == n.priceMinManwon && result.checks.size > 1) "최저가" else "최고가", c)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "사용자가 입력한 값으로 계산한 추정이며 실제 대출 심사(LTV·DSR·소득·규제지역·중도금 집단대출 조건)와 다를 수 있습니다. " +
                    "계약금(${profile.downPaymentPct}%)은 현금, 나머지는 현금+대출로 낸다고 가정합니다. " +
                    "빠듯 = 계약금을 현금으로 못 내거나, 남는 돈이 분양가의 ${Affordability.TIGHT_MARGIN_PCT}% 미만(취득세·등기 등 부대비용). " +
                    "옵션·발코니 확장비 제외.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 주택형 한 줄: 왼쪽 주택형·분양가, 오른쪽 판정 배지, 아래 근거. */
@Composable
private fun FundsRow(label: String, c: Affordability.Check) {
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
    }
}
