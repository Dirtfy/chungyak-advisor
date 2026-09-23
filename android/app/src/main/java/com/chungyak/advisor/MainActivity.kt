package com.chungyak.advisor

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chungyak.advisor.data.Notice
import com.chungyak.advisor.data.Settings
import com.chungyak.advisor.ui.NoticeViewModel
import com.chungyak.advisor.ui.theme.ChungyakTheme

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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: NoticeViewModel = viewModel()) {
    val notices by vm.notices.collectAsState()
    val status by vm.status.collectAsState()
    var showSettings by remember { mutableStateOf(!vm.hasKey) }

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
            Spacer(Modifier.height(8.dp))

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
                    items(notices, key = { it.id }) { NoticeRow(it) }
                }
            }
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
        }
    }
}

@Composable
private fun NoticeRow(n: Notice) {
    Card(
        Modifier.fillMaxWidth(),
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
