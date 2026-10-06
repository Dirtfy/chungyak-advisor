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
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chungyak.advisor.match.Gajeom

/** 청약 가점 카드: 총점 + 항목별 점수 막대 + 계산 근거. 내 조건 탭(오늘 기준)과 상세(공고일 기준)에서 쓴다. */
@Composable
fun GajeomCard(g: Gajeom.Result, title: String = "내 청약 가점") {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    g.total?.let { "${it}점" } ?: "${g.known}점+",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(" / ${Gajeom.MAX}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            g.parts.forEach { part ->
                Column(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(part.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            (part.score?.let { "${it}점" } ?: "미계산") + " / ${part.max}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (part.score ?: 0).toFloat() / part.max },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        part.basis + if (part.estimated) " (추정)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "${g.asOf} 기준 · 주택공급에 관한 규칙 별표 1. 민영주택 일반공급 가점제에 쓰이며(국민주택은 납입 횟수·금액 순), " +
                    "참고용입니다 — 최종 점수는 청약홈 가점 계산기와 모집공고문으로 확인하세요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
