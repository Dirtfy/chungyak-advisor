package com.chungyak.advisor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import com.chungyak.advisor.match.Eligibility
import com.chungyak.advisor.match.NotifyMode
import com.chungyak.advisor.match.Profile
import java.time.LocalDate

/**
 * 내 청약 조건 입력 화면(하단 '내 조건' 탭). 저장 값은 기기 안(ProfileStore)에만 남는다.
 * [onBack]이 있으면 닫기 버튼을 보여 준다(탭 안에서는 없음 — 뒤로 가기는 탭 화면이 처리).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    initial: Profile,
    initialMode: NotifyMode,
    onSave: (Profile, NotifyMode) -> Unit,
    onClear: () -> Unit,
    onBack: (() -> Unit)? = null,
    /** 이 묶음으로 바로 스크롤(공고 목록·상세의 '내 자금 입력' 안내에서 들어올 때). 스크롤한 뒤 [onFocused]. */
    focus: FormSection? = null,
    onFocused: () -> Unit = {},
) {
    if (onBack != null) BackHandler(onBack = onBack)
    // 저장·지우기로 프로필이 바뀌면 입력칸도 새 값으로. 입력칸·검증은 온보딩과 같은 것(ProfileFields, ProfileDraft).
    var draft by remember(initial, initialMode) { mutableStateOf(ProfileDraft.of(initial, initialMode)) }
    var error by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    var focusY by remember { mutableStateOf(-1) }
    LaunchedEffect(focus, focusY) {
        if (focus != null && focusY >= 0) {
            scroll.animateScrollTo(focusY)
            onFocused()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("내 청약 조건") },
                navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("〈 닫기") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "입력한 조건과 공고의 자격 요건을 비교해 맞는 공고만 알려 드립니다. " +
                    "이 정보는 이 폰 안에만 저장되고 어디로도 전송되지 않습니다(자동 백업·백업 파일에도 제외).",
                style = MaterialTheme.typography.bodySmall,
            )

            TAB_ORDER.forEach { section ->
                Box(if (section == focus) Modifier.onGloballyPositioned { focusY = it.positionInParent().y.toInt() } else Modifier) {
                    FormSectionTitle(section.title)
                }
                if (section == FormSection.FUNDS) Text(section.help, style = MaterialTheme.typography.bodySmall)
                ProfileFields(section, draft) { draft = it }
            }

            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    error = draft.problems(LocalDate.now()).firstOrNull()?.message.orEmpty()
                    if (error.isBlank()) onSave(draft.build(), draft.mode)
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

/** '내 조건' 탭의 묶음 순서(v0.8.0까지와 같음 — 온보딩 단계 순서와는 다르다). */
private val TAB_ORDER = listOf(
    FormSection.NOTIFY, FormSection.RESIDENCE, FormSection.HOUSEHOLD, FormSection.FAMILY,
    FormSection.ACCOUNT, FormSection.GAJEOM, FormSection.INCOME, FormSection.INTEREST, FormSection.FUNDS,
)
