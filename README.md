# 청약 레이더 (chungyak-advisor)

수도권 **주택청약(아파트) 일반공급** 공고를 기기에서 주기적으로 조회하고, 새 공고가
뜨면 **로컬 알림**으로 알려주는 **안드로이드 단독 앱**(서버 없음).

- 데이터: 한국부동산원 청약홈 **공공 API**(공공데이터포털 / odcloud). 이용허락 제한 없음.
- 동작: 앱이 직접 API를 주기 조회(WorkManager) → 수도권(서울·경기·인천) 일반공급 신규
  공고 감지 → 로컬 알림. 서버·호스팅 없음, 수집·저장·알림 모두 기기 안에서.
- 기능(v0.13.0): 신규 공고 알림, 관심 지역 시·군·구 단위 선택(경기 31개 시·군, [docs/17](docs/17_관심지역_시군.md)), 공고 검색(단지명·주소·지역·사업주체·시공사, [docs/14](docs/14_검색_전입일.md)), 분양가·경쟁률, 내 조건 자격 판정·맞춤 알림, 상세 위치 지도,
  청약 일정 알림(접수 시작일·당첨 발표일 전날, 밴드 전달 — [docs/06](docs/06_밴드_알림_미러링.md)),
  청약 가점(84점) 계산·근거 표시와 '추천순' 정렬([docs/12](docs/12_가점_추천순.md)).
  화면은 하단 탭(공고 / 내 조건 / 설정, [docs/11](docs/11_UI_리디자인.md)).
  첫 실행 때 내 조건을 8단계로 받는 온보딩([docs/13](docs/13_온보딩.md)).
  내 자금(현금·대출 한도·월 상환액 상한)으로 공고·주택형마다 살 수 있는지 판정([docs/15](docs/15_자금_매매가능_판정.md)) — 대출은 LTV·주담대 상한·스트레스 DSR·상환 방식(원리금균등·원금균등·체증식·만기일시·거치) 규정으로 계산([docs/16](docs/16_대출_규정.md)).

## 구성

- **[android/](android/)** — 네이티브 안드로이드 앱(Kotlin + Jetpack Compose).
  디버그 APK 빌드(`./gradlew assembleDebug`). geo-reminder 앱의 빌드 셋업을 재사용.
- **[docs/01_주택청약_리서치_및_MVP제안.md](docs/01_주택청약_리서치_및_MVP제안.md)** —
  도메인 리서치, 데이터 소스, MVP 제안(사이클 1).
- **[docs/02_API_활용신청_및_수집기_설계.md](docs/02_API_활용신청_및_수집기_설계.md)** —
  data.go.kr 활용신청 절차, 엔드포인트/필드, 수집기·백그라운드 설계(사이클 2).

## 상세 화면 위치 지도

공고 상세에 공급위치 지도(OpenStreetMap, 마커)와 [지도 앱에서 열기](geo: 인텐트) 버튼이 있다.
지도·지오코딩 모두 키가 필요 없는 방식(Android Geocoder → Nominatim, 결과 캐시)만 쓴다.
설계·검증·키 필요 공급자 비교: [docs/08_상세_위치지도.md](docs/08_상세_위치지도.md).
지오코딩 라이브 테스트는 `LIVE_GEOCODE=1`일 때만 실행된다.

## 사용 전 준비 (서비스키)

각 사용자가 본인의 data.go.kr 서비스키를 발급받아 앱 설정에 입력한다(서버 없는
단독 앱이라 공용 키를 심지 않음). 절차는 `docs/02_...`의 2장 참고. 키는 기기 로컬에만
저장되고 외부로 전송되지 않는다.

- 필요한 활용신청 두 개: 분양정보(15098547), 경쟁률(15098905, ApplyhomeInfoCmpetRtSvc). 반영까지 최대 1시간.
- **키를 소스·gradle 속성·BuildConfig·리소스·테스트에 넣지 않는다.** `NoEmbeddedKeyTest`가 이를 검사한다.
- 실키 라이브 테스트(`LiveApiTest`)는 환경변수 `ODCLOUD_SERVICE_KEY`가 있을 때만 실행된다. 키는 커밋하지 않는다.

## 빌드

JDK 17 + Android SDK(compileSdk 34) 환경에서:

```bash
cd android
./gradlew assembleDebug   # → app/build/outputs/apk/debug/app-debug.apk
```

- `applicationId`: `com.chungyak.advisor` · `minSdk` 26(Android 8.0) · `targetSdk` 34
- Gradle 8.9, AGP 8.5.2, Kotlin 2.0.20, Compose BOM 2024.09.02

### CI / 릴리스 (GitHub Actions)

- `.github/workflows/ci.yml`: PR·main 푸시 → 단위 테스트 + 디버그 APK. 서명·Secrets 없음.
- `.github/workflows/release.yml`: `v*` 태그 푸시 → 고정 키로 서명한 release APK를 같은 태그의 Release에
  `chungyak-radar-vX.Y.Z.apk`로 첨부. 태그와 `appVersion`(app/build.gradle.kts)이 다르면 실패한다(인앱 업데이트가 태그를 버전으로 읽음).
- 서명: `ANDROID_KEYSTORE_PATH`·`ANDROID_KEYSTORE_PASSWORD`·`ANDROID_KEY_ALIAS`·`ANDROID_KEY_PASSWORD` 환경변수
  (CI는 Secrets `ANDROID_KEYSTORE_BASE64` 등에서 복원). 없으면 저장소 밖 `keystore.properties`, 그것도 없으면 미서명.
  서명 인증서 SHA-256 `e169d6eb…3c61ed` — 바뀌면 기존 설치본 위에 업데이트가 안 된다. 확인: `tools/apk_cert_sha256.py <apk>`.
- 릴리스 절차: `appVersion` 올려 커밋·푸시 → `git tag -a vX.Y.Z` → `git push origin vX.Y.Z`.

## 면책

본 앱은 참고용 정보 제공 도구다. 청약 자격·일정의 최종 확인은 반드시 청약홈 공고문과
법령에서 해야 한다.
