#!/usr/bin/env python3
"""v0.13.3 업데이트 설치 화면 한 번만 + 설치 전 APK 검사: 보고용 PPT형 PDF (v013_report.py·v0131_report.py의 그리기 도구 재사용).
사용: python3 tools/v0133_report.py [release_note]
"""
import sys
from PIL import Image, ImageDraw
import v013_report as R
from v013_report import f, bullets, table, BLUE, INK, SUB, GREEN, RED, SW, SH, ROOT
from v0131_report import flow


def slide(title, n):
    im = Image.new("RGB", (SW, SH), "white")
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, SW, 12], fill=BLUE)
    d.text((70, 70), title, font=f(48), fill=INK)
    d.text((SW - 70, SH - 40), f"청약 레이더 v0.13.3 · {n}", font=f(20), fill=SUB, anchor="rm")
    return im, d


def build(release_note):
    pages = []
    im, d = slide("업데이트 설치 화면 한 번만 + 받은 APK 검사 — v0.13.3", 1)
    bullets(d, 80, 180, [
        "점검 (fadeout-gallery v0.2.16 기준): 설치 화면 이중 실행은 부분 해당, 설치 전 검사 누락은 해당 → 둘 다 수정.",
        "이중 실행: 알림이 설치 화면을 직접 열고, 앱도 받기 완료 때 설치 화면을 엶. 두 경로의 판단 기준이 달라 겹칠 수 있었음.",
        "검사 누락: 파일 크기만 확인(크기 모르면 그것도 생략). APK로 읽히는지·패키지명·버전코드는 안 봄.",
        "수정: 설치 화면은 앱의 한 곳에서만, 같은 파일은 3초에 한 번. 넘기기 전에 검사, 불량이면 버리고 [다시 시도]로 새로 받기.",
        release_note,
        "주의: 효과는 v0.13.3 설치 뒤의 업데이트부터. v0.13.2 이하에서 v0.13.3을 받을 때는 옛 코드가 동작. 실기기 미검증.",
    ], size=28, gap=18)
    pages.append(im)

    im, d = slide("근거 — 파일/라인 (v0.13.2)", 2)
    table(d, 80, 160, ["위치", "v0.13.2 (AS-IS)", "v0.13.3 (TO-BE)"], [
        ["UpdateDownloads.notifyReady", "알림 탭 → 설치 화면 직접", "알림 탭 → 앱(ACTION_INSTALL)"],
        ["UpdateDownloadReceiver", "isForeground()면 알림 생략", "그대로"],
        ["UpdateViewModel.poll", "ON_START~ON_STOP 동안 완료 시 설치", "그대로 → install() 경유"],
        ["UpdateViewModel.install", "검사 없이 바로 설치 화면", "3초 가드 + verify() + discard()"],
        ["UpdateDownloads.sizeOk", "크기만(모르면 통과)", "+ APK·패키지·versionCode"],
        ["MainActivity", "(알림 인텐트 처리 없음)", "알림 인텐트 처리(최근 앱 재실행 무시)"],
    ], [460, 500, 620], size=21, rh=56)
    bullets(d, 80, 570, [
        "겹치는 순간: 화면은 보이지만 맨 앞이 아닐 때(알림창 내림·분할 화면). 앱은 설치 화면을 열고 수신기는 알림을 남김 → 알림 탭 시 같은 APK로 설치 화면 한 번 더.",
        "fadeout처럼 '알림 탭 + 앱 복귀'가 동시에 여는 구조는 아니었음(앱 복귀는 [설치] 팝업만) → '부분 해당'.",
    ], size=25, gap=14)
    pages.append(im)

    im, d = slide("AS-IS vs TO-BE", 3)
    d.text((80, 150), "AS-IS (v0.13.2)", font=f(32), fill=SUB)
    d.text((880, 150), "TO-BE (v0.13.3)", font=f(32), fill=BLUE)
    flow(d, 80, 210, [
        ("다운로드 완료", "n"),
        ("앱: 설치 화면 열기 / 수신기: 알림", "n"),
        ("알림 탭 → 설치 화면 또 열림", "bad"),
        ("파일 검사 없음(크기만)", "bad"),
        ("\"앱 파일에 문제가 있습니다\"", "bad"),
    ])
    flow(d, 880, 210, [
        ("다운로드 완료", "n"),
        ("모든 경로 → install() 한 곳", "ok"),
        ("같은 파일 3초 안이면 건너뜀", "ok"),
        ("검사: 크기·APK·패키지·버전코드", "ok"),
        ("통과 → 설치 / 불량 → 버리고 다시 받기", "ok"),
    ])
    pages.append(im)

    im, d = slide("장단점", 4)
    d.text((80, 160), "장점", font=f(36), fill=GREEN)
    bullets(d, 80, 220, [
        "설치 화면이 겹쳐 뜨는 경우를 구조적으로 막음(진입점 하나).",
        "잘린 파일·오류 페이지·다른 앱·이전 버전 APK를 설치 화면에 넘기지 않음.",
        "불량 파일은 자동으로 버림 → [다시 시도]가 처음부터 새로 받음.",
        "fadeout-gallery와 같은 방식 — 앱 간 동작 일치.",
        "추가 권한·라이브러리 없음, 저장된 설정·기록 영향 없음.",
    ], size=26, gap=12, width=680)
    d.text((840, 160), "단점·한계", font=f(36), fill=RED)
    bullets(d, 840, 220, [
        "알림 탭이 앱을 한 번 거친 뒤 설치 화면이 뜸(전환 한 번 추가).",
        "설치 화면을 닫고 3초 안에 [설치]를 다시 누르면 무시됨 → 잠시 후 다시.",
        "검사 때문에 설치 직전 APK를 한 번 읽음(헤더만, 짧음).",
        "효과는 v0.13.3 설치 뒤 업데이트부터(이미 깔린 옛 버전은 옛 동작).",
        "실기기 미검증(CI 빌드·단위 테스트만).",
    ], size=26, gap=12, width=680)
    pages.append(im)

    im, d = slide("폰에서 확인할 것 (실기기 검증은 오너)", 5)
    bullets(d, 80, 170, [
        "v0.13.3은 앱 안 업데이트로 받을 수 있습니다(이번 받기·설치는 옛 코드로 동작).",
        "다음 릴리스가 나오면:",
        "1) [업데이트] → 홈으로 나가기 → '업데이트 준비됨' 알림 탭 → 앱이 잠깐 열리고 설치 화면이 한 번만 뜨는지.",
        "2) 받는 중 앱에 머물러 있다가 완료 → 설치 화면이 한 번만 뜨는지(알림이 남지 않는지).",
        "3) 설치 화면을 취소하고 앱으로 돌아오면 [설치] 팝업이 보이고, 누르면 다시 열리는지.",
        "4) 최근 앱에서 앱을 다시 열 때 설치 화면이 저절로 뜨지 않는지.",
    ], size=28, gap=14)
    pages.append(im)

    out = ROOT / "report_v0.13.3.pdf"
    pages[0].save(out, save_all=True, append_images=pages[1:], resolution=150)
    print(out)


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "릴리스: (CI 결과 대기)")
