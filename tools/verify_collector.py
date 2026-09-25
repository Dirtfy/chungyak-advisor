#!/usr/bin/env python3
"""
청약 레이더 수집기 E2E 검증 스크립트 (라이브 데이터).

안드로이드 앱 android/app/src/main/java/com/chungyak/advisor/api/ApplyHomeClient.kt
와 동일한 요청/파싱 경로를 그대로 재현해, 발급받은 data.go.kr 서비스키로 실제
수도권 일반공급 APT 분양정보가 수집되는지 확인한다.

보안: 서비스키는 **환경변수 ODCLOUD_SERVICE_KEY** 로만 받는다. 절대 소스/커밋/로그에
키를 하드코딩하지 말 것. (data.go.kr '일반 인증키'는 Decoding/Encoding 어느 형태든 됨.)

사용:
  export ODCLOUD_SERVICE_KEY='...'          # 발급키(원문 그대로)
  python3 tools/verify_collector.py [lookback_days ...]
  # 예) python3 tools/verify_collector.py 30 90 180
"""
import datetime as dt
import json
import os
import re
import sys
from urllib import request as urlreq, parse as urlparse, error as urlerr

BASE = "https://api.odcloud.kr/api/ApplyhomeInfoDetailSvc/v1"
ENDPOINT = "getAPTLttotPblancDetail"
PER_PAGE = 100
MAX_PAGES = 10
REGIONS = ["서울", "경기", "인천"]  # 수도권


def key_param(k):
    # ApplyHomeClient.kt 와 동일: 이미 %XX 인코딩된 키면 그대로, 아니면 인코딩.
    return k if re.search(r"%[0-9A-Fa-f]{2}", k) else urlparse.quote(k, safe="")


def fetch_page(key, page, since):
    cond = urlparse.quote("cond[RCRIT_PBLANC_DE::GTE]", safe="")
    url = (f"{BASE}/{ENDPOINT}?page={page}&perPage={PER_PAGE}&returnType=JSON"
           f"&{cond}={since}&serviceKey={key_param(key)}")
    req = urlreq.Request(url, headers={"Accept": "application/json"})
    try:
        with urlreq.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode("utf-8")), r.status, None
    except urlerr.HTTPError as e:
        return None, e.code, e.read().decode("utf-8", "ignore")[:200]


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


def run(key, lookback_days):
    since = (dt.date.today() - dt.timedelta(days=lookback_days)).strftime("%Y-%m-%d")
    print(f"\n===== lookback {lookback_days}일 (RCRIT_PBLANC_DE >= {since}) =====")
    all_rows, metro, page = [], [], 1
    while page <= MAX_PAGES:
        js, code, err = fetch_page(key, page, since)
        if js is None:
            print(f"  HTTP {code} 오류: {err}")
            return
        if "data" not in js:
            print(f"  응답에 data 없음: {json.dumps(js, ensure_ascii=False)[:200]}")
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
    for r in metro[:5]:
        print(f"    - [{r.get('SUBSCRPT_AREA_CODE_NM','')}] {r.get('HOUSE_NM','')} · "
              f"{r.get('TOT_SUPLY_HSHLDCO','')}세대 · 공고 {iso(r.get('RCRIT_PBLANC_DE'))} · "
              f"일반1순위 {iso(r.get('GNRL_RNK1_CRSPAREA_RCPTDE'))}~"
              f"{iso(r.get('GNRL_RNK1_CRSPAREA_ENDDE'))} · 발표 {iso(r.get('PRZWNER_PRESNATN_DE'))}")


if __name__ == "__main__":
    key = os.environ.get("ODCLOUD_SERVICE_KEY", "").strip()
    if not key:
        print("ERROR: 환경변수 ODCLOUD_SERVICE_KEY 를 설정하세요.", file=sys.stderr)
        sys.exit(2)
    days = [int(x) for x in sys.argv[1:]] or [30, 90, 180]
    print("청약 레이더 수집기 E2E 검증 (키는 환경변수에서만 로드)")
    for d in days:
        run(key, d)
