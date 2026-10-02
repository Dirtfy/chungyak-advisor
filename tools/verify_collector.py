#!/usr/bin/env python3
"""
청약 레이더 수집기 E2E 검증 스크립트 (라이브 데이터).

안드로이드 앱 android/app/src/main/java/com/chungyak/advisor/api/ApplyHomeClient.kt
와 동일한 요청/파싱 경로를 재현해, 발급받은 data.go.kr 서비스키로 실제 수도권
일반공급 APT 분양정보 + 분양가(주택형별) + 경쟁률이 수집되는지 확인한다.

  - 목록  getAPTLttotPblancDetail        (수도권 일반공급 공고)
  - 분양가 getAPTLttotPblancMdl           (LTTOT_TOP_AMOUNT, 만원)  [DetailSvc]
  - 경쟁률 getAPTLttotPblancCmpet         (접수 마감된 공고만 값 존재) [CmpetRtSvc]

보안: 서비스키는 **환경변수 ODCLOUD_SERVICE_KEY** 로만 받는다. 절대 소스/커밋/로그에
키를 하드코딩하지 말 것. (data.go.kr '일반 인증키'는 Decoding/Encoding 어느 형태든 됨.)

사용:
  export ODCLOUD_SERVICE_KEY='...'
  python3 tools/verify_collector.py [lookback_days ...]   # 예) ... 30 90 180
"""
import datetime as dt
import json
import os
import re
import sys
from urllib import request as urlreq, parse as urlparse, error as urlerr

DETAIL = "https://api.odcloud.kr/api/ApplyhomeInfoDetailSvc/v1"
CMPET = "https://api.odcloud.kr/api/ApplyhomeInfoCmpetRtSvc/v1"
PER_PAGE = 100
MAX_PAGES = 10
REGIONS = ["서울", "경기", "인천"]  # 수도권


def key_param(k):
    # ApplyHomeClient.kt 와 동일: 이미 %XX 인코딩된 키면 그대로, 아니면 인코딩.
    return k if re.search(r"%[0-9A-Fa-f]{2}", k) else urlparse.quote(k, safe="")


def get(key, base, ep, params):
    qs = "&".join(f"{urlparse.quote(k, safe='')}={urlparse.quote(str(v), safe='')}"
                  for k, v in params.items())
    url = f"{base}/{ep}?{qs}&serviceKey={key_param(key)}"
    try:
        with urlreq.urlopen(urlreq.Request(url, headers={"Accept": "application/json"}), timeout=20) as r:
            return json.loads(r.read().decode("utf-8")), None
    except urlerr.HTTPError as e:
        return None, f"HTTP {e.code}: {e.read().decode('utf-8', 'ignore')[:200]}"


def matches_region(row):
    area = str(row.get("SUBSCRPT_AREA_CODE_NM", ""))
    addr = str(row.get("HSSPLY_ADRES", ""))
    return any(t in area or t in addr for t in REGIONS)


def iso(v):
    t = str(v or "").strip()
    if re.match(r"^\d{4}-\d{2}-\d{2}", t):
        return t[:10]
    if re.match(r"^\d{8}$", t):
        return f"{t[:4]}-{t[4:6]}-{t[6:8]}"
    return t


def manwon(v):
    d = re.sub(r"[^0-9]", "", str(v or ""))
    return int(d) if d else None


def price_range(key, h, p):
    """getAPTLttotPblancMdl 에서 LTTOT_TOP_AMOUNT(만원) 최소/최대."""
    js, err = get(key, DETAIL, "getAPTLttotPblancMdl",
                  {"page": 1, "perPage": PER_PAGE, "returnType": "JSON",
                   "cond[HOUSE_MANAGE_NO::EQ]": h, "cond[PBLANC_NO::EQ]": p})
    if err or not js or "data" not in js:
        return None, err, []
    rows = js["data"]
    prices = [m for m in (manwon(r.get("LTTOT_TOP_AMOUNT")) for r in rows) if m]
    return (min(prices), max(prices)) if prices else None, None, rows


def competition(key, h, p):
    """getAPTLttotPblancCmpet — 접수 후 공고만 값이 있음. 필드는 공식 OAS 기준."""
    js, err = get(key, CMPET, "getAPTLttotPblancCmpet",
                  {"page": 1, "perPage": PER_PAGE, "returnType": "JSON",
                   "cond[HOUSE_MANAGE_NO::EQ]": h, "cond[PBLANC_NO::EQ]": p})
    if err or not js or "data" not in js:
        return None, err
    return js["data"], None


def run(key, lookback_days, deep=True):
    since = (dt.date.today() - dt.timedelta(days=lookback_days)).strftime("%Y-%m-%d")
    print(f"\n===== lookback {lookback_days}일 (RCRIT_PBLANC_DE >= {since}) =====")
    all_rows, metro, page = [], [], 1
    while page <= MAX_PAGES:
        js, err = get(key, DETAIL, "getAPTLttotPblancDetail",
                      {"page": page, "perPage": PER_PAGE, "returnType": "JSON",
                       "cond[RCRIT_PBLANC_DE::GTE]": since})
        if err:
            print("  목록 오류:", err)
            return
        if not js or "data" not in js:
            print("  응답에 data 없음:", json.dumps(js, ensure_ascii=False)[:200])
            return
        rows = js["data"]
        if page == 1:
            print(f"  totalCount={js.get('totalCount')} matchCount={js.get('matchCount')}")
        all_rows.extend(rows)
        metro.extend([r for r in rows if matches_region(r)])
        if len(rows) < PER_PAGE:
            break
        page += 1
    print(f"  전국 APT {len(all_rows)}건 / 수도권 {len(metro)}건 (페이지 {page})")
    dist = {}
    for r in metro:
        a = str(r.get("SUBSCRPT_AREA_CODE_NM", "?"))
        dist[a] = dist.get(a, 0) + 1
    if dist:
        print("  지역 분포:", ", ".join(f"{k} {v}" for k, v in sorted(dist.items())))

    if not deep:
        return
    # 최신 공고 몇 건: 분양가 결합 확인
    print("  -- 분양가 결합(주택형별 상세) 상위 5건 --")
    for r in metro[:5]:
        pr, err, mdl = price_range(key, r.get("HOUSE_MANAGE_NO"), r.get("PBLANC_NO"))
        ptxt = f"{pr[0]:,}~{pr[1]:,}만원" if pr else (f"오류 {err}" if err else "분양가 미확인")
        print(f"    - [{r.get('SUBSCRPT_AREA_CODE_NM')}] {r.get('HOUSE_NM')} · "
              f"{r.get('TOT_SUPLY_HSHLDCO')}세대 · 공고 {iso(r.get('RCRIT_PBLANC_DE'))} · "
              f"일반1순위 {iso(r.get('GNRL_RNK1_CRSPAREA_RCPTDE'))} · 분양가 {ptxt} "
              f"(주택형 {len(mdl)}종)")
    # 오래된 공고: 경쟁률 필드 발견/검증 (접수 마감분)
    print("  -- 경쟁률 확인(오래된 공고 우선, 접수 마감분만 값 존재) --")
    older = sorted(metro, key=lambda r: str(r.get("RCRIT_PBLANC_DE", "")))
    shown = 0
    for r in older:
        if shown >= 3:
            break
        cmp_, err = competition(key, r.get("HOUSE_MANAGE_NO"), r.get("PBLANC_NO"))
        if err:
            print(f"    - {r.get('HOUSE_NM')}: 오류 {err}")
            if "401" in str(err):
                print("      → 경쟁률 서비스(data.go.kr 15098905) 활용신청 필요. 앱은 '활용신청 필요'로 안내하고 계속 동작.")
                return
            shown += 1
            continue
        if not cmp_:
            continue  # 아직 경쟁률 미공개
        print(f"    - [{r.get('SUBSCRPT_AREA_CODE_NM')}] {r.get('HOUSE_NM')} "
              f"(공고 {iso(r.get('RCRIT_PBLANC_DE'))}) · 경쟁률 rows={len(cmp_)}")
        # 앱(CompetitionParser.kt)이 읽는 공식 OAS 필드
        for row in cmp_[:6]:
            print(f"       · {row.get('HOUSE_TY')} {row.get('SUBSCRPT_RANK_CODE')}순위 "
                  f"{row.get('RESIDE_SENM') or row.get('RESIDE_SECD')} · 공급 {row.get('SUPLY_HSHLDCO')} "
                  f"· 접수 {row.get('REQ_CNT')} · 경쟁률 {row.get('CMPET_RATE')}")
        shown += 1
    if shown == 0:
        print("    (조회 구간에 접수 마감된 공고가 없어 경쟁률 값이 아직 없음 — 정상)")


if __name__ == "__main__":
    key = os.environ.get("ODCLOUD_SERVICE_KEY", "").strip()
    if not key:
        print("ERROR: 환경변수 ODCLOUD_SERVICE_KEY 를 설정하세요.", file=sys.stderr)
        sys.exit(2)
    days = [int(x) for x in sys.argv[1:]] or [30, 180]
    print("청약 레이더 수집기 E2E 검증 (목록+분양가+경쟁률, 키는 환경변수에서만 로드)")
    for i, d in enumerate(days):
        run(key, d, deep=(i == 0 or d >= 180))
