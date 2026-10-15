#!/usr/bin/env python3
"""v0.13.2 업데이트 팝업 닫기 동작: 보고용 PPT형 PDF (v013_report.py·v0131_report.py의 그리기 도구 재사용).
사용: python3 tools/v0132_report.py [release_note]
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
    d.text((SW - 70, SH - 40), f"청약 레이더 v0.13.2 · {n}", font=f(20), fill=SUB, anchor="rm")
    return im, d


def build(release_note):
    pages = []
    im, d = slide("업데이트 팝업 닫기 동작 맞추기 — v0.13.2", 1)
    bullets(d, 80, 180, [
        "(1) 바깥 탭·뒤로 가기: 해당 있음 → 수정함. v0.13.1까지는 [나중에]와 같게 처리돼 그 버전을 다시 묻지 않았음.",
        "     이제 바깥 탭·뒤로 가기 = 이번만 닫기(다음 자동 확인 때 같은 버전 다시 뜸). [나중에] 버튼만 그 버전을 미룸.",
        "(2) onCreate 때만 확인하던 버그: 해당 없음. v0.4.4부터 화면에 나올 때마다(ON_START) 확인, 30분 간격으로 중복 요청 방지.",
        "테스트 3개 추가(UpdateDismissTest), GitHub CI 통과. 로컬 빌드 안 함.",
        release_note,
        "실기기 미검증 — 오너 폰에서 확인 필요(6쪽).",
    ], size=29, gap=18)
    pages.append(im)

    im, d = slide("근거 — 파일/라인", 2)
    table(d, 80, 160, ["위치", "v0.13.1 (AS-IS)", "v0.13.2 (TO-BE)"], [
        ["UpdateDialog.kt:56", "onDismissRequest → vm.later()", "onDismissRequest → vm.dismiss()"],
        ["UpdateDialog.kt:121", "[나중에] → vm.later()", "그대로"],
        ["UpdateViewModel.kt later()", "Session.dismissed = 버전", "그대로 ([나중에] 전용)"],
        ["UpdateViewModel.kt dismiss()", "(다운로드 [숨기기]만 사용)", "바깥 탭·뒤로 가기도 사용"],
        ["UpdateDialog.kt:28-40", "ON_START → onForeground()", "그대로 (v0.4.4부터)"],
        ["UpdateViewModel.kt:60-64", "30분 간격 검사", "autoCheckDue()로 분리·테스트"],
    ], [500, 520, 520], size=23, rh=56)
    bullets(d, 80, 570, [
        "(2) 근거: 확인은 Activity onCreate가 아니라 Compose 수명주기 관찰자(ON_START)에서 함 → 최근 앱에서 복귀해도 매번 실행.",
        "중복 요청 방지: 마지막 확인 후 30분 안이면 건너뜀(GitHub 비인증 한도 60회/시간). 확인 중·다운로드 중이면 다시 요청하지 않음.",
        "다운로드 중에는 바깥 탭으로 닫히지 않음(기존 그대로).",
    ], size=26, gap=14)
    pages.append(im)

    im, d = slide("AS-IS vs TO-BE — 팝업 바깥 탭", 3)
    d.text((80, 150), "AS-IS (v0.13.1)", font=f(32), fill=SUB)
    d.text((880, 150), "TO-BE (v0.13.2)", font=f(32), fill=BLUE)
    flow(d, 80, 210, [
        ("새 버전 팝업", "n"),
        ("바깥 탭 / 뒤로 가기", "n"),
        ("[나중에]와 같게: 그 버전 미룸", "bad"),
        ("앱 복귀해도 팝업 안 뜸", "bad"),
        ("설정 [업데이트 확인]으로만 다시 봄", "bad"),
    ])
    flow(d, 880, 210, [
        ("새 버전 팝업", "n"),
        ("바깥 탭 / 뒤로 가기", "n"),
        ("이번만 닫기", "ok"),
        ("다음 자동 확인(복귀, 30분 뒤) 때 다시 뜸", "ok"),
        ("[나중에]만 그 버전을 미룸", "ok"),
    ])
    pages.append(im)

    im, d = slide("상황별 동작 (v0.13.2)", 4)
    table(d, 80, 160, ["상황", "동작"], [
        ["팝업 바깥 탭 / 뒤로 가기", "이번만 닫음 → 다음 자동 확인 때 같은 버전 다시 뜸"],
        ["[나중에] 버튼", "그 버전은 이번 실행 동안 자동으로 다시 묻지 않음"],
        ["설정 [업데이트 확인]", "미룬 버전도 항상 팝업"],
        ["다운로드 중 바깥 탭", "닫히지 않음([숨기기]로 숨김, 다운로드는 계속)"],
        ["최근 앱에서 복귀", "확인함(마지막 확인 30분 지났을 때)"],
        ["30분 안에 다시 복귀", "요청 안 함(중복 방지)"],
        ["앱이 종료됐다 다시 뜸", "새로 확인([나중에] 기록도 초기화 — 기존과 같음)"],
        ["저장된 설정·기록", "변경 없음(메모리 상태만 바뀜)"],
    ], [560, 940], size=24, rh=58)
    pages.append(im)

    im, d = slide("장단점", 5)
    d.text((80, 160), "장점", font=f(36), fill=GREEN)
    bullets(d, 80, 220, [
        "실수로 바깥을 눌러도 업데이트를 놓치지 않음.",
        "'미루기'는 명시적인 [나중에]로만 → 의도가 분명함.",
        "fadeout-gallery 등 다른 앱과 같은 동작.",
        "변경이 작음(한 줄 + 함수 분리), 저장 데이터 영향 없음.",
        "복귀 확인 간격 로직을 함수로 분리해 테스트로 고정.",
    ], size=26, gap=12, width=680)
    d.text((840, 160), "단점·한계", font=f(36), fill=RED)
    bullets(d, 840, 220, [
        "바깥 탭으로 닫은 뒤 30분 안에는 복귀해도 다시 안 뜸(요청 간격 때문).",
        "업데이트를 원치 않는 사용자는 팝업을 더 자주 볼 수 있음 → [나중에] 사용.",
        "[나중에]는 앱 프로세스가 끝나면 초기화(기존과 같음, 영구 저장 안 함).",
        "실기기 미검증(CI 빌드·단위 테스트만).",
    ], size=26, gap=12, width=680)
    pages.append(im)

    im, d = slide("폰에서 확인할 것 (실기기 검증은 오너)", 6)
    bullets(d, 80, 170, [
        "v0.13.2는 앱 안 업데이트로 받을 수 있습니다(설정 → [업데이트 확인]).",
        "새 동작은 v0.13.2 다음 릴리스의 팝업부터 보입니다. 다음 릴리스가 나오면:",
        "1) 팝업 바깥을 탭해 닫기 → 30분 뒤 앱을 다시 열거나 복귀하면 같은 팝업이 다시 뜨는지.",
        "2) [나중에]로 닫기 → 앱을 완전히 종료하기 전까지는 복귀해도 다시 안 뜨는지.",
        "3) 설정 [업데이트 확인]을 누르면 [나중에]로 미룬 버전도 뜨는지.",
        "4) 다운로드 중 바깥 탭으로는 닫히지 않는지.",
    ], size=28, gap=14)
    pages.append(im)

    out = ROOT / "report_v0.13.2.pdf"
    pages[0].save(out, save_all=True, append_images=pages[1:], resolution=150)
    print(out)


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "릴리스: (CI 결과 대기)")
