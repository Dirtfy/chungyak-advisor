#!/usr/bin/env python3
"""v0.13.1 인앱 업데이트 백그라운드 다운로드: 보고용 PPT형 PDF (v013_report.py의 그리기 도구 재사용).
사용: python3 tools/v0131_report.py [release_note]
"""
import sys
from PIL import Image, ImageDraw
import v013_report as R
from v013_report import f, bullets, table, BLUE, BLUE_BG, INK, SUB, LINE, CARD, RED, GREEN, SW, SH, ROOT


def slide(title, n):
    im = Image.new("RGB", (SW, SH), "white")
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, SW, 12], fill=BLUE)
    d.text((70, 70), title, font=f(48), fill=INK)
    d.text((SW - 70, SH - 40), f"청약 레이더 v0.13.1 · {n}", font=f(20), fill=SUB, anchor="rm")
    return im, d


def flow(d, x, y, steps, w=640, h=78, gap=34):
    """세로 흐름도. steps: [(문구, 색)]."""
    for i, (s, col) in enumerate(steps):
        bg = {"ok": (230, 246, 238), "bad": (252, 232, 232), "n": CARD}[col]
        fg = {"ok": GREEN, "bad": RED, "n": INK}[col]
        d.rounded_rectangle([x, y, x + w, y + h], 14, fill=bg, outline=LINE, width=2)
        d.text((x + w / 2, y + h / 2), s, font=f(24), fill=fg, anchor="mm")
        if i < len(steps) - 1:
            cx = x + w / 2
            d.line([cx, y + h, cx, y + h + gap - 6], fill=SUB, width=3)
            d.polygon([(cx - 8, y + h + gap - 12), (cx + 8, y + h + gap - 12), (cx, y + h + gap - 2)], fill=SUB)
        y += h + gap
    return y


def build(release_note):
    pages = []
    im, d = slide("업데이트 다운로드가 앱을 나가면 끊기던 문제 — v0.13.1", 1)
    bullets(d, 80, 180, [
        "재현: 예(코드 분석으로 판단 — 실기기·에뮬레이터 없음). 받는 중 앱을 나가면 1분 안팎에 네트워크 오류로 실패.",
        "원인: APK를 앱 프로세스 안의 코루틴(HttpURLConnection, 읽기 제한 60초)으로 직접 받음. 백그라운드가 되면 OS가 네트워크·CPU를 막음.",
        "수정: 다운로드를 안드로이드 시스템 '다운로드 관리자'(DownloadManager)에 맡김 → 앱을 나가도, 앱이 종료돼도 계속.",
        "완료 시: 앱 밖이면 \"업데이트 준비됨 — 탭하면 설치\" 알림 / 앱 안이면 바로 설치 화면 + [설치] 팝업.",
        "돌아오면 진행률 이어서 표시. 이미 받은 APK는 다시 받지 않고 [설치]. 알림 권한이 없어도 앱 안 팝업은 동작.",
        release_note,
        "주의: 효과는 v0.13.1 다음 업데이트부터. v0.13.0→v0.13.1은 옛 코드로 받으니 받는 동안 앱을 켜 두세요.",
    ], size=29, gap=18)
    pages.append(im)

    im, d = slide("원인 — 근거 코드 (v0.13.0)", 2)
    table(d, 80, 160, ["위치", "내용", "영향"], [
        ["UpdateViewModel.kt:87-98", "viewModelScope.launch { download() }", "앱 프로세스에 묶임"],
        ["UpdateClient.kt:216-249", "HttpURLConnection 읽기 루프", "시스템 도움 없음"],
        ["UpdateClient.kt:220", "readTimeout = 60_000", "멈추면 60초 뒤 예외"],
        ["UpdateViewModel.kt:97", "onFailure → Failed(메시지)", "\"timeout\" 등 오류 팝업"],
        ["(없음)", "포그라운드 서비스·WakeLock·WorkManager", "백그라운드 보호 없음"],
    ], [470, 620, 420], size=23, rh=56)
    bullets(d, 80, 540, [
        "앱을 나가면 프로세스가 'cached' 상태 → Doze·앱 대기 네트워크 차단, 절전 모드 백그라운드 데이터 제한, Android 14+ 프로세스 동결(수 초 뒤 CPU 정지).",
        "소켓 읽기가 멈춤 → 60초 뒤 SocketTimeoutException / 호스트 확인 실패 → \"업데이트를 받지 못했습니다: timeout\" (오너가 본 '인터넷 연결 오류').",
        "메모리가 부족해 프로세스가 정리되면 받던 파일(.part)과 진행 상태가 함께 사라짐 → 처음부터 다시.",
    ], size=26, gap=14)
    pages.append(im)

    im, d = slide("AS-IS vs TO-BE", 3)
    d.text((80, 150), "AS-IS (v0.13.0)", font=f(32), fill=SUB)
    d.text((880, 150), "TO-BE (v0.13.1)", font=f(32), fill=BLUE)
    flow(d, 80, 210, [
        ("[업데이트] → 앱 코루틴이 직접 받기", "n"),
        ("홈 버튼 / 다른 앱으로 전환", "n"),
        ("OS가 앱 네트워크·CPU 제한", "bad"),
        ("60초 뒤 timeout → 실패 팝업", "bad"),
        ("[다시 시도] → 처음부터 다시 받기", "bad"),
    ])
    flow(d, 880, 210, [
        ("[업데이트] → 시스템 다운로드 관리자", "n"),
        ("앱 나가도 계속(끊기면 대기 후 이어받기)", "ok"),
        ("알림창에 시스템 진행 표시", "ok"),
        ("앱 밖 완료: '업데이트 준비됨' 알림 → 탭=설치", "ok"),
        ("앱 안 완료/복귀: 설치 화면 + [설치] 팝업", "ok"),
    ])
    pages.append(im)

    im, d = slide("바뀐 동작 — 상황별", 4)
    table(d, 80, 160, ["상황", "v0.13.1 동작"], [
        ["받는 중 앱을 나감", "○ 계속 받음(시스템 진행 알림)"],
        ["받는 중 네트워크 끊김", "○ '네트워크 연결을 기다리는 중' → 연결되면 이어받기"],
        ["받는 중 앱으로 돌아옴", "○ 팝업에 진행률 이어서(앱이 종료됐다 다시 떠도)"],
        ["앱 밖에서 완료", "○ '업데이트 준비됨 v…' 알림, 탭하면 설치 화면"],
        ["앱 안에서 완료", "○ 바로 설치 화면 + 팝업 [설치]"],
        ["알림 권한 없음", "○ 알림만 생략, 앱 열면 [설치] 팝업"],
        ["이미 다 받은 APK 있음", "○ 다시 받지 않고 [설치]"],
        ["설치 권한(알 수 없는 앱) 없음", "○ 알림 탭 → 앱 → [권한 설정]/[설치] 안내"],
        ["다운로드 중 팝업이 거슬림", "○ [숨기기] (다운로드는 계속)"],
        ["다운로드 관리자 앱이 꺼진 기기", "× 시작 실패 → [웹에서 받기]로 우회"],
    ], [560, 940], size=24, rh=56)
    pages.append(im)

    im, d = slide("장단점", 5)
    d.text((80, 160), "장점", font=f(36), fill=GREEN)
    bullets(d, 80, 220, [
        "앱 상태(백그라운드·종료)와 무관하게 다운로드 유지, 네트워크 끊김 자동 재시도.",
        "추가 권한·라이브러리·포그라운드 서비스 없음 → 코드 단순, Play 정책 이슈 없음.",
        "진행 알림은 시스템이 표시 → 앱 알림 권한이 없어도 진행이 보임.",
        "받은 APK 재사용, 설치 후 자동 정리.",
        "사용자 API 키 등 개인 데이터와 무관(공개 GitHub 릴리스만 받음).",
    ], size=26, gap=12, width=680)
    d.text((840, 160), "단점·한계", font=f(36), fill=RED)
    bullets(d, 840, 220, [
        "일부 기기에서 '다운로드 관리자' 시스템 앱을 꺼 두면 시작 불가(→ [웹에서 받기]).",
        "진행 알림 모양·문구는 시스템이 정함.",
        "완료 알림은 앱 알림 권한이 있어야 뜸(없으면 앱 안 팝업만).",
        "효과는 v0.13.1 설치 이후의 업데이트부터(이번 업데이트는 옛 방식).",
        "실기기 검증 전(빌드·테스트는 CI만) — 오너 폰 확인 필요.",
    ], size=26, gap=12, width=680)
    pages.append(im)

    im, d = slide("폰에서 확인할 것 (실기기 검증은 오너)", 6)
    bullets(d, 80, 170, [
        "0) 이번 v0.13.1 업데이트는 옛 코드로 받습니다 — 받는 동안 앱을 켜 두세요(또는 [웹에서 받기]).",
        "다음 릴리스(v0.13.2 이상)가 나오면:",
        "1) [업데이트] → 바로 홈으로 나가기 → 알림창에 '청약 레이더 v…' 시스템 다운로드 진행이 보이는지.",
        "2) 몇 분 뒤 '업데이트 준비됨' 알림이 오고, 탭하면 설치 화면이 뜨는지.",
        "3) 받는 중 앱으로 돌아오면 팝업이 진행률을 이어서 보여 주는지.",
        "4) 다 받은 뒤 알림을 무시하고 앱을 열면 [설치] 팝업이 뜨는지(다시 받지 않음).",
        "5) 받는 중 비행기 모드를 잠깐 켜면 '네트워크 연결을 기다리는 중' → 끄면 이어지는지.",
        "자세한 내용: docs/18_업데이트_백그라운드_다운로드.md",
    ], size=28, gap=14)
    pages.append(im)

    out = ROOT / "report_v0.13.1.pdf"
    pages[0].save(out, save_all=True, append_images=pages[1:], resolution=150)
    print(out)


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "릴리스: (CI 결과 대기)")
