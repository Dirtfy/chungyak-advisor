package com.chungyak.advisor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chungyak.advisor.match.Gajeom
import com.chungyak.advisor.match.NotifyMode
import com.chungyak.advisor.match.Profile
import java.time.LocalDate

/**
 * 첫 실행 온보딩(v0.9.0~, docs/13): 소개 → [Onboarding.steps] 8단계 → 완료.
 * '다음'을 누를 때마다 그 시점까지의 입력을 [onSaveStep]으로 저장한다(중간에 앱을 꺼도 입력이 남는다).
 * '건너뛰기'는 그 단계에서 고친 값만 되돌리고 넘어간다. '나중에 하기'는 저장된 것까지만 두고 닫는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    initial: Profile,
    initialMode: NotifyMode,
    scheduleAlerts: Boolean,
    hasKey: Boolean,
    onScheduleAlerts: (Boolean) -> Unit,
    onSaveStep: (Profile, NotifyMode) -> Unit,
    /** 끝까지 마쳤거나(true) '나중에 하기'로 닫았을 때(false). */
    onClose: (completed: Boolean) -> Unit,
) {
    val steps = Onboarding.steps
    // -1 = 소개, steps.size = 완료.
    var index by rememberSaveable { mutableStateOf(-1) }
    var draft by remember { mutableStateOf(ProfileDraft.of(initial, initialMode)) }
    var stepStart by remember { mutableStateOf(draft) }
    var error by remember { mutableStateOf("") }

    fun go(to: Int) {
        index = to
        stepStart = draft
        error = ""
    }

    BackHandler { if (index <= 0) onClose(false) else go(index - 1) }

    if (index < 0) {
        Intro(onStart = { go(0) }, onLater = { onClose(false) })
        return
    }
    if (index >= steps.size) {
        Done(draft.build(), hasKey, onStart = { onClose(true) })
        return
    }
    val step = steps[index]

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("내 조건 입력 ${index + 1}/${steps.size}") },
                    actions = { TextButton(onClick = { onClose(false) }) { Text("나중에 하기") } },
                )
                LinearProgressIndicator(
                    progress = { (index + 1f) / steps.size },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    drawStopIndicator = {}, // M3 1.3 트랙 끝 점(stop indicator) 끔 — 오너 피드백 v0.10.0
                )
            }
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { go(index - 1) }, enabled = index > 0) { Text("이전") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        draft = Onboarding.skip(draft, stepStart, step)
                        go(index + 1)
                    }) { Text("건너뛰기") }
                    Button(onClick = {
                        error = Onboarding.problems(draft, step, LocalDate.now()).firstOrNull()?.message.orEmpty()
                        if (error.isBlank()) {
                            onSaveStep(draft.build(), draft.mode)
                            go(index + 1)
                        }
                    }) { Text(if (index == steps.lastIndex) "완료" else "다음") }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Text(step.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                step.help,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            )
            Spacer(Modifier.height(4.dp))
            ProfileFields(step, draft) { draft = it; error = "" }
            if (step == FormSection.NOTIFY) {
                Spacer(Modifier.height(6.dp))
                SwitchRow("청약 일정 알림(접수 시작일·당첨 발표일 전날)", scheduleAlerts, onScheduleAlerts)
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Text(
                "모르는 항목은 비워 두세요 — 그 조건이 필요한 판정은 '확인 필요'로 표시됩니다. 나중에 '내 조건' 탭에서 고칠 수 있어요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Intro(onStart: () -> Unit, onLater: () -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Icon(Icons.Outlined.HomeWork, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            Text("청약 레이더에 오신 걸 환영해요", style = MaterialTheme.typography.headlineSmall)
            Text(
                "내 조건을 알려 주시면 신청할 수 있는 공고만 골라 알려 드리고, 청약 가점과 추천 점수도 계산해 드려요.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "· ${Onboarding.steps.size}단계, 2~3분이면 끝나요. 모르는 항목은 건너뛰어도 됩니다.\n" +
                    "· 입력한 정보는 이 폰 안에만 저장되고 어디로도 전송되지 않아요(백업 파일에도 제외).\n" +
                    "· 언제든 '내 조건' 탭에서 고치거나, 설정에서 이 안내를 다시 할 수 있어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("시작하기") }
            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) { Text("나중에 하기") }
        }
    }
}

@Composable
private fun Done(profile: Profile, hasKey: Boolean, onStart: () -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            Text("내 조건을 저장했어요", style = MaterialTheme.typography.headlineSmall)
            if (profile.isSet) GajeomCard(Gajeom.calc(profile, LocalDate.now()), title = "오늘 기준 내 가점")
            else Text(
                "거주 시·도를 건너뛰어서 아직 맞춤 판정은 꺼져 있어요. '내 조건' 탭에서 거주지를 넣으면 켜집니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (hasKey) "이제 공고 목록에서 내 조건 판정과 추천순을 볼 수 있어요."
                else "다음: 설정에서 본인의 data.go.kr 서비스키를 넣으면 공고를 모으기 시작해요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(if (hasKey) "공고 보러 가기" else "서비스키 입력하러 가기")
            }
        }
    }
}
