package com.chungyak.advisor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.chungyak.advisor.match.Regions

/**
 * 관심 지역 고르기(v0.13.0~). 시·도마다 'OO 전체' 체크(전체/일부/없음 3단계)와 펼치면 시·군·구 칩.
 * 값 규칙은 [Regions] — 전부 고르면 시·도 하나("경기")로 합쳐 저장한다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegionPicker(selected: Set<String>, onChange: (Set<String>) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "지역 — 지금: ${Regions.summary(selected)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Regions.SIDO.forEach { sido ->
            val all = Regions.SIGUNGU.getValue(sido)
            val picked = Regions.picked(selected, sido)
            val state = when {
                Regions.isAll(selected, sido) -> ToggleableState.On
                picked.isEmpty() -> ToggleableState.Off
                else -> ToggleableState.Indeterminate
            }
            // 일부만 고른 시·도는 펼친 채로 연다(무엇을 골랐는지 바로 보이게).
            var open by rememberSaveable(sido) { mutableStateOf(state == ToggleableState.Indeterminate) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TriStateCheckbox(state = state, onClick = { onChange(Regions.toggleAll(selected, sido)) })
                Text("$sido 전체", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { open = !open }) {
                    Text("${Regions.unitName(sido)} ${picked.size}/${all.size} " + if (open) "▲" else "▼")
                }
            }
            if (open) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    all.forEach { sg ->
                        FilterChip(
                            selected = sg in picked,
                            onClick = { onChange(Regions.toggle(selected, sido, sg)) },
                            label = { Text(sg) },
                        )
                    }
                }
            }
        }
        Text(
            "하나도 안 고르면 모든 지역. 주소에서 시·군·구를 못 읽은 공고는 그 시·도에서 하나라도 골랐으면 포함하고 상세에 표시해요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
