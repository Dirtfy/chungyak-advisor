# 청약홈 API 활용신청 · 엔드포인트 · 수집기 설계 (사이클 2)

작성일: 2026-09-24
대상: 안드로이드 단독 앱(서버 없음). 기기에서 청약홈 공공 API를 주기 조회 → 신규
수도권 일반공급 공고를 로컬 알림. (오너 확정: 플랫폼 A안 / 앱 로컬 알림 / 수도권 우선 /
일반공급부터.)

이 문서는 사이클 1의 `01_주택청약_리서치_및_MVP제안.md`를 잇는 실행 문서다.

---

## 1. 어떤 API를 쓰나 (확정)

**한국부동산원_청약홈 "분양정보 조회 서비스"** (공공데이터포털, odcloud stage 37000).
이용허락범위 제한 없음(영리 포함). 우리가 실제로 호출하는 실서비스 엔드포인트는
공공데이터포털 상세페이지가 아니라 아래 odcloud 게이트웨이다.

- data.go.kr 데이터 페이지: https://www.data.go.kr/data/15098547/openapi.do
- 실 호출 BASE: `https://api.odcloud.kr/api/ApplyhomeInfoDetailSvc/v1`
- OAS 문서: https://infuser.odcloud.kr/api/stages/37000/api-docs
- (경쟁률/가점: `ApplyhomeInfoCmpetRtSvc/v1` — 이후 확장)

> 참고: 공공데이터포털의 "일반 인증키"는 **Decoding / Encoding 두 형태**로 표시된다.
> Decoding 키는 URL에 붙일 때 1회 인코딩해야 하고, Encoding 키는 그대로 붙인다.
> 앱은 키에 `%XX` 패턴이 있으면 Encoding으로 간주해 그대로 쓰고, 없으면 인코딩한다
> (양쪽 모두 수용).

---

## 2. 활용신청 절차 (오너/사용자가 직접 수행)

⚠️ **이 단계는 사람이 data.go.kr 계정으로 직접 해야 한다.** 나(에이전트)는 오너의
개인 계정으로 회원가입·로그인·신청을 대신할 수 없다(자격증명 없음·정책상 불가). 아래는
그대로 따라 하면 되는 절차다. 키 발급은 개발계정 기준 **즉시 자동승인**이라 몇 분이면 끝난다.

1. https://www.data.go.kr 회원가입 후 로그인.
2. "한국부동산원_청약홈 분양정보 조회 서비스"(데이터 15098547) 페이지에서 **[활용신청]**.
   - 활용목적: 앱/개인 서비스 개발 등 자유 기재.
   - 심의: 개발단계는 자동승인(즉시). 트래픽 개발계정 40,000/일(충분).
3. 승인 후 **마이페이지 → 오픈API → 인증키**에서 "일반 인증키(Decoding)" 값을 복사.
4. 앱을 열고 **설정 → data.go.kr 서비스키**에 붙여넣기 → 저장.
   - 앱은 이 키를 기기 로컬(SharedPreferences)에만 저장한다. 서버로 전송하지 않는다.

### 왜 "사용자별 키" 인가 (설계 결정)
서버가 없는 단독 앱이라, 앱에 공용 키를 하나 심으면 (a) APK에서 키가 추출되고
(b) 모든 사용자의 호출이 한 키의 트래픽 한도를 공유해 금방 막힌다. 그래서 **각
사용자가 본인 키를 발급받아 입력**하는 방식이 표준이자 합법적으로 깔끔하다. 대신 최초
1회 발급 UX 비용이 있어, 설정 화면에 위 절차 링크/안내를 넣는다.
(향후 서버를 두는 방향으로 바뀌면 서버가 단일 키로 대신 수집·푸시하는 구조로 전환 가능.)

---

## 3. 사용하는 엔드포인트 · 파라미터 · 응답 필드

### 3.1 이번 MVP에서 호출 (APT 일반공급)
`GET {BASE}/getAPTLttotPblancDetail`

공통 쿼리 파라미터:
| 파라미터 | 값 | 의미 |
|---|---|---|
| `page` | 1,2,… | 페이지 |
| `perPage` | 100 | 페이지당 건수 |
| `returnType` | `JSON` | 응답 포맷 |
| `cond[RCRIT_PBLANC_DE::GTE]` | `yyyy-MM-dd` | 모집공고일 ≥ 기준일(최근 N일) |
| `serviceKey` | 발급키 | 인증 |

응답 봉투: `{ page, perPage, totalCount, currentCount, matchCount, data: [ {…} ] }`
페이징 종료: 반환 `data` 길이 < `perPage` 이면 마지막 페이지. (totalCount는 필터 미반영
값이라 종료 기준으로 쓰지 않음 — 오픈소스 사례에서 확인된 함정.)

우리가 쓰는 핵심 응답 필드:
| 필드 | 의미 |
|---|---|
| `HOUSE_MANAGE_NO`, `PBLANC_NO` | 공고 고유키(둘을 합쳐 dedupe id) |
| `HOUSE_NM` | 주택명 |
| `SUBSCRPT_AREA_CODE_NM` | 공급지역명(서울/경기/인천 …) — **수도권 필터** |
| `HSSPLY_ADRES` | 공급 주소 |
| `TOT_SUPLY_HSHLDCO` | 총 공급 세대수 |
| `RCRIT_PBLANC_DE` | 모집공고일 |
| `GNRL_RNK1_CRSPAREA_RCPTDE` / `..._ENDDE` | **일반공급 1순위 해당지역 접수 시작/종료** |
| `GNRL_RNK1_ETC_AREA_RCPTDE` / `..._ENDDE` | 일반공급 1순위 기타지역 접수 |
| `GNRL_RNK2_*` | 일반공급 2순위 접수 |
| `SPSPLY_RCEPT_BGNDE` / `_ENDDE` | 특별공급 접수(이후 확장) |
| `PRZWNER_PRESNATN_DE` | 당첨자 발표일 |
| `HOUSE_SECD_NM` / `HOUSE_DTL_SECD_NM` | 주택 구분 |
| `SPECLT_RDN_EARTH_AT` | 투기과열지구 여부(Y/N) |
| `MDAT_TRGET_AREA_SECD` | 조정대상지역 구분 |
| `PBLANC_URL`, `HMPG_ADRES`, `MDHS_TELNO` | 공고 URL/홈페이지/문의 |

> **날짜 필드 함정**: 접수일 필드명이 엔드포인트/건별로 다르다. APT 일반은
> `GNRL_RNK1_*`/`RCEPT_*`, 무순위는 `SUBSCRPT_RCEPT_*`, 드물게 `GNRL_RCEPT_*`만 채워짐.
> 파서는 다중 키를 순차 시도해야 한다.
> **분양가 함정**: `getAPTLttotPblancDetail`에는 분양가가 없다. 주택형별 상세
> `getAPTLttotPblancMdl`(키: `HOUSE_MANAGE_NO`,`PBLANC_NO`)의 `LTTOT_TOP_AMOUNT`
> (만원)에 있다 → 상세가격은 2차 호출로 조인. (MVP는 목록/일정까지, 가격은 다음 단계.)

### 3.2 이후 확장 엔드포인트 (동일 BASE, 지금은 미호출)
| 엔드포인트 | 용도 |
|---|---|
| `getRemndrLttotPblancDetail` | 무순위/잔여세대(줍줍) |
| `getUrbtyOfctlLttotPblancDetail` | 오피스텔/도시형/생숙 |
| `getPblPvtRentLttotPblancDetail` | 공공지원 민간임대 |
| `getOPTLttotPblancDetail` | 임의공급 |
| `getAPTLttotPblancMdl` | 주택형별(면적·세대수·분양가) |
| `ApplyhomeInfoCmpetRtSvc/v1/getAPTLttotPblancCmpet` | 경쟁률 |
| `.../getAptLttotPblancScore` | 당첨 가점(컷) |

수도권 지역코드(경쟁률/통계 API용 `SUBSCRPT_AREA_CODE`): 서울 100 · 인천 400 · 경기 410.
(분양정보 목록 필터는 코드가 아니라 `SUBSCRPT_AREA_CODE_NM` 문자열 매칭으로 처리.)

---

## 4. 수집기 PoC 설계 (앱 내 동작)

파이프라인: **WorkManager(주기) → CheckWorker → ApplyHomeClient.fetch → 수도권 필터
→ Room dedupe → 신규만 로컬 알림**.

- `api/ApplyHomeClient.kt` — `HttpURLConnection` + `org.json`(무의존). 페이징 병합,
  키 Encoding/Decoding 자동 처리, 지역 필터(`SUBSCRPT_AREA_CODE_NM`/주소 contains).
- `work/CheckWorker.kt` — `CoroutineWorker`. 조회→기존 id 집합과 차집합→신규 Room 저장
  →`Notifier.notifyNew`→`notified` 마킹. 실패는 `Result.retry()`(백오프).
- `data/`(Room) — `notices` 테이블. PK = `HOUSE_MANAGE_NO:PBLANC_NO` → 재조회해도
  같은 공고는 1회만 알림.
- `notify/Notifier.kt` — `IMPORTANCE_HIGH` 채널, 공고별 알림 + 다건 요약.
- `work/Scheduler.kt` — `PeriodicWorkRequest` 기본 6시간, 네트워크 연결 제약.
  수동 "지금 확인"은 `OneTimeWorkRequest`.

### 4.1 안드로이드 백그라운드 실행 OS 제약 (중요)
- **WorkManager 주기 최소 간격 15분**. 그보다 짧게는 불가.
- **Doze/앱 대기(App Standby)**: 화면 꺼진 유휴 상태에선 주기 작업이 **배치·지연**된다.
  즉 "정확히 N분마다"가 아니라 "대략 그 즈음, OS가 몰아서" 실행 — 정시성 보장 없음.
- **제조사 배터리 최적화**(삼성 등)가 백그라운드 작업을 더 조일 수 있음.
- **정시성이 필요 없다**: 청약 접수 창은 보통 수일이고, 공고 등장 후 수 시간 내에만
  잡으면 충분하므로 6시간 주기 + best-effort로 요건을 만족한다. (분 단위 정확 알림이
  필요한 성격이 아님.)
- 정시성이 꼭 필요해지면 대안: `AlarmManager setExactAndAllowWhileIdle`(정확 알람,
  배터리·권한 비용) 또는 서버+FCM 푸시(A안 이탈). 현재는 채택하지 않음.
- 재부팅 시 주기 작업이 사라지므로 `BootReceiver`로 재등록.

### 4.2 한계 / 다음 단계
- 아직 "추천"이 아니라 "수도권 일반공급 신규 = 알림". 가점 계산·자금/자격 매칭은
  사이클 1 문서의 입력 스펙대로 다음 단계에서 얹는다.
- 분양가·경쟁률·가점컷은 2차 호출(Mdl/Cmpet)로 확장.
- 특별공급 트랙, LH 국민주택 소스는 이후.
- 실데이터 검증은 **유효한 서비스키가 있어야** 가능(키는 사람이 발급). 키 없이도 앱은
  빌드·설치·구동되며, 설정에서 키 입력 시 즉시 수집 시작.
