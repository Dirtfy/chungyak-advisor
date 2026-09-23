# 청약 레이더 (chungyak-advisor)

수도권 **주택청약(아파트) 일반공급** 공고를 기기에서 주기적으로 조회하고, 새 공고가
뜨면 **로컬 알림**으로 알려주는 **안드로이드 단독 앱**(서버 없음).

- 데이터: 한국부동산원 청약홈 **공공 API**(공공데이터포털 / odcloud). 이용허락 제한 없음.
- 동작: 앱이 직접 API를 주기 조회(WorkManager) → 수도권(서울·경기·인천) 일반공급 신규
  공고 감지 → 로컬 알림. 서버·호스팅 없음, 수집·저장·알림 모두 기기 안에서.
- 범위(현재): 추천 이전 단계인 **수집 + 신규 공고 알림 PoC**. 가점 계산·자금/자격
  매칭·분양가·경쟁률은 다음 단계(설계는 `docs/` 참고).

## 구성

- **[android/](android/)** — 네이티브 안드로이드 앱(Kotlin + Jetpack Compose).
  디버그 APK 빌드(`./gradlew assembleDebug`). geo-reminder 앱의 빌드 셋업을 재사용.
- **[docs/01_주택청약_리서치_및_MVP제안.md](docs/01_주택청약_리서치_및_MVP제안.md)** —
  도메인 리서치, 데이터 소스, MVP 제안(사이클 1).
- **[docs/02_API_활용신청_및_수집기_설계.md](docs/02_API_활용신청_및_수집기_설계.md)** —
  data.go.kr 활용신청 절차, 엔드포인트/필드, 수집기·백그라운드 설계(사이클 2).

## 사용 전 준비 (서비스키)

각 사용자가 본인의 data.go.kr 서비스키를 발급받아 앱 설정에 입력한다(서버 없는
단독 앱이라 공용 키를 심지 않음). 절차는 `docs/02_...`의 2장 참고. 키는 기기 로컬에만
저장되고 외부로 전송되지 않는다.

## 빌드

JDK 17 + Android SDK(compileSdk 34) 환경에서:

```bash
cd android
./gradlew assembleDebug   # → app/build/outputs/apk/debug/app-debug.apk
```

- `applicationId`: `com.chungyak.advisor` · `minSdk` 26(Android 8.0) · `targetSdk` 34
- Gradle 8.9, AGP 8.5.2, Kotlin 2.0.20, Compose BOM 2024.09.02

## 면책

본 앱은 참고용 정보 제공 도구다. 청약 자격·일정의 최종 확인은 반드시 청약홈 공고문과
법령에서 해야 한다.
