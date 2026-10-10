#!/usr/bin/env python3
"""v0.13.0 관심 지역 시·군 선택: 화면 목업(PNG) + 보고용 PPT형 PDF (PIL + NanumGothic).

실기기 화면이 아니라 목업이다(빌드를 서버에서 돌리지 않음). 색·배치는 앱 테마(파랑 #2F6BFF)와 RegionPicker.kt를 따른다.
사용: python3 tools/v013_report.py [release_note]
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONT = str(Path.home() / ".fonts/NanumGothic.ttf")
BLUE = (47, 107, 255)
BLUE_BG = (226, 234, 255)
INK = (28, 28, 34)
SUB = (100, 104, 116)
LINE = (214, 218, 228)
CARD = (247, 248, 252)
RED = (200, 60, 60)
GREEN = (30, 140, 90)

GG = ["수원시", "성남시", "의정부시", "안양시", "부천시", "광명시", "평택시", "동두천시", "안산시", "고양시",
      "과천시", "구리시", "남양주시", "오산시", "시흥시", "군포시", "의왕시", "하남시", "용인시", "파주시",
      "이천시", "안성시", "김포시", "화성시", "광주시", "양주시", "포천시", "여주시", "연천군", "가평군", "양평군"]


def f(size):
    return ImageFont.truetype(FONT, size)


def text_w(d, s, font):
    return d.textlength(s, font=font)


def checkbox(d, x, y, state, s=34):
    if state == "off":
        d.rounded_rectangle([x, y, x + s, y + s], 6, outline=SUB, width=3)
    else:
        d.rounded_rectangle([x, y, x + s, y + s], 6, fill=BLUE)
        if state == "on":
            d.line([x + 8, y + 18, x + 15, y + 25, x + 27, y + 10], fill="white", width=4)
        else:
            d.line([x + 8, y + s / 2, x + s - 8, y + s / 2], fill="white", width=4)


def chip(d, x, y, label, on, font, h=46):
    w = text_w(d, label, font) + (64 if on else 36)
    if on:
        d.rounded_rectangle([x, y, x + w, y + h], 10, fill=BLUE_BG)
        d.line([x + 14, y + 23, x + 20, y + 30, x + 31, y + 16], fill=BLUE, width=3)
        d.text((x + 42, y + h / 2), label, font=font, fill=INK, anchor="lm")
    else:
        d.rounded_rectangle([x, y, x + w, y + h], 10, outline=LINE, width=2)
        d.text((x + 18, y + h / 2), label, font=font, fill=INK, anchor="lm")
    return w


def phone(w, h, title):
    im = Image.new("RGB", (w, h), "white")
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, w, 96], fill="white")
    d.text((32, 50), title, font=f(36), fill=INK, anchor="lm")
    d.line([0, 96, w, 96], fill=LINE, width=2)
    return im, d


def mock_tobe(path):
    W, H = 720, 1320
    im, d = phone(W, H, "내 조건")
    y = 124
    d.text((32, y), "관심 조건(선택 — 비우면 전체)", font=f(30), fill=INK)
    y += 50
    d.text((32, y), "지역 — 지금: 서울 전체, 경기 수원시·용인시·화성시", font=f(24), fill=INK)
    y += 50
    sel = {"수원시", "용인시", "화성시"}
    rows = [("서울", "on", "구 25/25 ▼", None), ("경기", "mid", "시·군 3/31 ▲", sel), ("인천", "off", "구·군 0/11 ▼", None)]
    cf = f(24)
    for sido, st, btn, picked in rows:
        checkbox(d, 32, y + 6, st)
        d.text((84, y + 23), f"{sido} 전체", font=f(28), fill=INK, anchor="lm")
        d.text((W - 32, y + 23), btn, font=f(24), fill=BLUE, anchor="rm")
        y += 64
        if picked is not None:
            x = 56
            for name in GG:
                cw = text_w(d, name, cf) + (64 if name in picked else 36)
                if x + cw > W - 24:
                    x = 56
                    y += 56
                chip(d, x, y, name, name in picked, cf)
                x += cw + 10
            y += 72
    d.text((32, y), "하나도 안 고르면 모든 지역. 주소에서 시·군·구를 못 읽은 공고는", font=f(21), fill=SUB)
    d.text((32, y + 30), "그 시·도에서 하나라도 골랐으면 포함하고 상세에 표시해요.", font=f(21), fill=SUB)
    y += 90
    for label in ["분양가 상한(만원)", "전용 최소(m2)          전용 최대(m2)"]:
        d.rounded_rectangle([32, y, W - 32, y + 70], 8, outline=LINE, width=2)
        d.text((52, y + 35), label, font=f(24), fill=SUB, anchor="lm")
        y += 90
    d.rounded_rectangle([32, y + 10, W - 32, y + 150], 14, fill=CARD)
    d.text((52, y + 40), "목록 배지", font=f(22), fill=SUB, anchor="lm")
    bx = 52
    for b in ["경기 수원시", "조정대상", "접수 D-3"]:
        bw = text_w(d, b, f(22)) + 28
        d.rounded_rectangle([bx, y + 66, bx + bw, y + 106], 20, fill=BLUE_BG)
        d.text((bx + 14, y + 86), b, font=f(22), fill=BLUE, anchor="lm")
        bx += bw + 10
    d.text((52, y + 128), "예전: \"경기\" → 이제: \"경기 수원시\"", font=f(20), fill=SUB, anchor="lm")
    im = im.crop((0, 0, W, min(H, y + 180)))
    im.save(path)
    return im


def mock_asis(path):
    W = 720
    im, d = phone(W, 520, "내 조건")
    y = 124
    d.text((32, y), "관심 조건(선택 — 비우면 전체)", font=f(30), fill=INK)
    y += 60
    x = 32
    for s, on in [("서울", True), ("경기", True), ("인천", False)]:
        x += chip(d, x, y, s, on, f(26), 54) + 12
    y += 90
    d.text((32, y), "경기 = 31개 시·군 전부. 수원만 보고 싶어도", font=f(24), fill=SUB)
    d.text((32, y + 36), "포천·연천·가평 공고까지 모두 알림이 옵니다.", font=f(24), fill=SUB)
    y += 110
    d.rounded_rectangle([32, y, W - 32, y + 70], 8, outline=LINE, width=2)
    d.text((52, y + 35), "분양가 상한(만원)", font=f(24), fill=SUB, anchor="lm")
    im.save(path)
    return im


# ── slides ──
SW, SH = 1600, 900


def slide(title, n):
    im = Image.new("RGB", (SW, SH), "white")
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, SW, 12], fill=BLUE)
    d.text((70, 70), title, font=f(48), fill=INK)
    d.text((SW - 70, SH - 40), f"청약 레이더 v0.13.0 · {n}", font=f(20), fill=SUB, anchor="rm")
    return im, d


def bullets(d, x, y, items, size=28, gap=16, width=None, color=INK):
    font = f(size)
    for it in items:
        sub = it.startswith("  ")
        s = it.strip()
        xx = x + (34 if sub else 0)
        d.text((xx, y), "–" if sub else "•", font=font, fill=SUB if sub else BLUE)
        # 간단 줄바꿈
        line, lines = "", []
        maxw = (width or SW - xx - 80) - 30
        for ch in s:
            if text_w(d, line + ch, font) > maxw:
                lines.append(line)
                line = ch
            else:
                line += ch
        lines.append(line)
        for ln in lines:
            d.text((xx + 30, y), ln, font=font, fill=color)
            y += size + 10
        y += gap
    return y


def table(d, x, y, cols, rows, widths, size=24, rh=52):
    font = f(size)
    d.rectangle([x, y, x + sum(widths), y + rh], fill=BLUE_BG)
    cx = x
    for c, w in zip(cols, widths):
        d.text((cx + 14, y + rh / 2), c, font=f(size), fill=BLUE, anchor="lm")
        cx += w
    y += rh
    for r in rows:
        cx = x
        for c, w in zip(r, widths):
            col = INK
            if c.startswith("○"):
                col = GREEN
            elif c.startswith("×"):
                col = RED
            d.text((cx + 14, y + rh / 2), c, font=font, fill=col, anchor="lm")
            cx += w
        d.line([x, y + rh, x + sum(widths), y + rh], fill=LINE, width=1)
        y += rh
    return y


def paste_fit(im, src, box):
    x0, y0, x1, y1 = box
    s = min((x1 - x0) / src.width, (y1 - y0) / src.height)
    r = src.resize((int(src.width * s), int(src.height * s)), Image.LANCZOS)
    im.paste(r, (x0 + ((x1 - x0) - r.width) // 2, y0))
    ImageDraw.Draw(im).rectangle([x0 + ((x1 - x0) - r.width) // 2 - 1, y0 - 1,
                                  x0 + ((x1 - x0) + r.width) // 2, y0 + r.height], outline=LINE, width=2)


def build(release_note):
    img = ROOT / "docs/img"
    tobe = mock_tobe(img / "v013_region_picker.png")
    asis = mock_asis(img / "v013_region_asis.png")
    pages = []

    im, d = slide("관심 지역을 시·군 단위로 — v0.13.0", 1)
    bullets(d, 80, 180, [
        "오너 요청: \"관심조건에 지역을 더 세분화하자. 경기는 범위가 너무 커 시 단위로 체크하자.\"",
        "경기 31개 시·군을 체크로 고릅니다. '경기 전체' 체크 하나로 모두 선택/해제.",
        "서울 25개 구, 인천 11개 구·군도 같은 방식(접혀 있어 안 쓰면 예전과 같음).",
        "공고마다 공급위치 주소에서 시·군을 읽어 거릅니다. 못 읽은 공고는 버리지 않고 포함 + 표시.",
        "예전에 '경기'를 골라 둔 사용자는 업데이트 후에도 경기 공고를 하나도 잃지 않습니다.",
        "DB 변경 없음. 빌드·테스트는 GitHub Actions에서만(서버 빌드 없음).",
        release_note,
    ], size=30, gap=20)
    pages.append(im)

    im, d = slide("AS-IS vs TO-BE", 2)
    d.text((80, 160), "AS-IS (v0.12.0)", font=f(32), fill=SUB)
    d.text((840, 160), "TO-BE (v0.13.0)", font=f(32), fill=BLUE)
    paste_fit(im, asis, (80, 210, 760, 520))
    bullets(d, 80, 560, [
        "시·도 칩 3개(서울/경기/인천)",
        "공급지역 글자만 비교",
        "배지: \"경기\"",
    ], size=26, gap=8, width=680)
    paste_fit(im, tobe, (840, 210, 1540, 840))
    pages.append(im)

    im, d = slide("새 지역 선택 화면 (목업)", 3)
    paste_fit(im, tobe, (80, 150, 700, 860))
    bullets(d, 760, 170, [
        "'경기 전체' 체크박스는 3단계: 전체(■) / 일부(▣) / 없음(□).",
        "'시·군 3/31 ▼'를 누르면 31개 칩이 펼쳐집니다. 일부만 고른 시·도는 펼친 채로 열림.",
        "31개를 모두 켜면 자동으로 '경기 전체'로 합쳐집니다(앞으로 생길 시·군도 포함).",
        "전체 상태에서 하나를 끄면 나머지 30개가 개별 선택으로 남습니다.",
        "맨 위에 \"지금: 서울 전체, 경기 수원시·용인시·화성시\" 요약.",
        "목록·상세 배지가 \"경기 수원시\"처럼 시·군까지 보입니다.",
        "위치: 내 조건 탭 → 관심 조건 (온보딩 7단계도 같은 화면).",
        "※ 실기기 캡처가 아닌 그림입니다(서버 빌드 금지 규칙).",
    ], size=26, gap=12, width=780)
    pages.append(im)

    im, d = slide("공고 → 시·군 판별과 매칭 규칙", 4)
    y = table(d, 80, 160, ["공급위치(HSSPLY_ADRES) 예", "판별"], [
        ["경기도 수원시 권선구 서둔동 212-1번지 일원", "경기 수원시"],
        ["경기도 화성특례시 만세구 향남읍 하길리", "경기 화성시 (특례시 표기)"],
        ["경기도 평택시 고덕동 일원(평택고덕국제화계획지구)", "경기 평택시 (괄호 무시)"],
        ["인천광역시 계양구 … 서울특별시 강서구 …", "인천 계양구 (공고 시·도 구간만)"],
        ["인천광역시 중구 중산동 (2026.7 개편 전 이름)", "인천 영종구 + 메모"],
        ["경기도 ○○공공주택지구 일원", "경기, 시·군 미확인"],
    ], [820, 620], size=23, rh=50)
    table(d, 80, y + 40, ["공고", "내 선택", "결과"], [
        ["경기 수원시", "경기 전체", "○ 포함"],
        ["경기 화성시", "경기 수원시", "× 제외 (관심 지역 아님)"],
        ["경기, 시·군 미확인", "경기에서 하나라도", "○ 포함 + 상세에 '시·군 미확인' 메모"],
        ["경기, 시·군 미확인", "서울 강남구만", "× 제외"],
    ], [420, 420, 600], size=23, rh=50)
    pages.append(im)

    im, d = slide("저장·마이그레이션 — 설정과 알림을 잃지 않게", 5)
    bullets(d, 80, 170, [
        "저장 위치는 그대로(프로필 prefs JSON profile_v1). DB(Room) 변경 없음.",
        "새 값 interestRegions = {\"경기\"(전체) | \"경기 수원시\"(개별)}.",
        "옛 값(v0.12.0 이하 interestSido)을 그대로 읽음: \"경기\" = 경기 전체 = 31개 시·군 + 시·군 미확인 공고 + 새 시·군.",
        "  변환이 '항등'이라 실패할 여지가 없음. 서울·인천도 같음.",
        "저장할 때 옛 키 interestSido에도 시·도를 함께 기록 → 옛 버전으로 내려 설치해도 공고가 빠지지 않음(넓게 받음).",
        "알림 범위·일정 알림·보낸 알림 기록·수집 범위 설정은 건드리지 않음.",
        "테스트: RegionsTest(실제 주소 형식별 판별·토글·매칭·옛 '경기'→31개 전부), ProfileStoreTest(v0.12.0 JSON 이전·왕복), EligibilityTest(시·군 필터·미확인 메모).",
    ], size=28, gap=18)
    pages.append(im)

    im, d = slide("장단점", 6)
    d.text((80, 160), "장점", font=f(36), fill=GREEN)
    bullets(d, 80, 220, [
        "원하는 시·군만 알림·추천 → 알림 소음 감소(예: 수원·용인만).",
        "전체 체크 하나로 예전처럼 쓸 수 있어 학습 부담 없음.",
        "못 읽은 공고도 버리지 않음(포함 + 표시) → 놓치는 공고 없음.",
        "기존 설정 자동 유지, 내려 설치해도 안전.",
        "서울·인천도 같은 판별기로 추가 비용 없이 구 단위.",
        "2026.7 인천 개편 반영 + 옛 이름 공고도 새 구로.",
    ], size=26, gap=10, width=680)
    d.text((840, 160), "단점·한계", font=f(36), fill=RED)
    bullets(d, 840, 220, [
        "주소에 시·군 이름이 없는 공고는 '미확인'으로 넓게 포함 → 가끔 관심 밖 공고가 섞일 수 있음.",
        "인천 옛 '서구' → 검단/서해 구분은 동 이름 목록 기반(경계 동은 틀릴 수 있어 메모 표시).",
        "한 공고가 두 시·군에 걸치면 둘 다로 봄(배지는 첫 번째만).",
        "경기 일반구(권선구 등)까지는 내리지 않음.",
        "수집 범위(설정)는 여전히 시·도 단위 — 데이터 사용량은 같음.",
        "행정구역이 또 바뀌면 표(Regions.kt)를 고쳐야 함.",
    ], size=26, gap=10, width=680)
    pages.append(im)

    im, d = slide("범위 결정 · 폰에서 확인할 것", 7)
    bullets(d, 80, 170, [
        "범위 결정: 앱은 수도권만 수집하므로 '넓은 도'는 경기뿐 → 경기 31개 시·군. 다른 도(충남 등)는 수집 대상이 아니라 제외.",
        "서울 25구·인천 11구군은 같은 판별기라 함께 넣음(접힌 상태가 기본).",
        "경기 일반구는 해당지역 단위가 아니고 칩이 너무 많아져 시·군에서 멈춤.",
    ], size=27, gap=14)
    bullets(d, 80, 470, [
        "1) 업데이트 후 내 조건 → 관심 조건에서 예전 선택(예: 경기)이 '경기 전체'가 체크된 상태로 보이는지.",
        "2) '시·군 ▼'를 펼쳐 수원시만 남기고 저장 → 공고 목록에서 다른 경기 공고가 '관심 조건 제외'가 되는지.",
        "3) 목록 배지가 \"경기 수원시\"처럼 시·군까지 나오는지.",
        "4) 31개를 다 켜면 '경기 전체'로 바뀌는지.",
    ], size=27, gap=10)
    pages.append(im)

    for i, p in enumerate(pages, 1):
        p.save(f"/tmp/v013_slide{i}.png")
    out = ROOT / "report_v0.13.0.pdf"
    pages[0].save(out, save_all=True, append_images=pages[1:], resolution=150)
    print(out)


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "릴리스: (CI 결과 대기)")
