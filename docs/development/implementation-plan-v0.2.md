# 오늘의 트랙 · 단계별 구현 계획

작성·리뷰: 2026-11-12 KST · 계획 v0.2 · 기준: 설계 v0.2 유지 · 상태: 계획 리뷰 반영, 모든 구현/실행 단계 미착수

> **후속 실행자:** 별도 구현 착수 지시를 받은 뒤 활성 Superpowers `executing-plans`를 사용해 한 명이 순서대로 진행한다. 자동 에이전트 분업·상주 에이전트·훅 설치는 사용하지 않는다. 이 문서의 체크박스·명령은 실행 승인이 아니다. 단계마다 계속할지 묻지 않고 승인된 범위 안에서 진행하며, 유료 호출은 그 범위가 별도로 승인된 경우에만 실행한다.

**목표:** 먼저 실제 백그라운드 수집 구조의 가능성을 작은 probe로 확인하고, 차량에서 기록→주행 후 요약→서버 AI 생성→음원 저장→차량 재감상으로 이어지는 핵심 흐름을 만든다.

**구조:** 차량이 원본·공백·묶음과 고정 입력을 관리하고 서버가 생성 작업·비용·음원을 관리한다. 폰 연결 없이 차량 핵심 흐름을 먼저 검증한다. 작은 probe의 코드를 제품 앱으로 그대로 옮기지 않는다.

**기술 기준:** Kotlin/Android·Room·서비스 소유 coroutine·Media3, Kotlin/Ktor·PostgreSQL·비공개 객체 저장. Lyria는 첫 실제 어댑터 후보이며 최종 선정은 미확정이다. 버전은 구현 착수 시 호환성을 확인해 고정하고 SDK/OS/target/minimum을 구별한다.

빌드 출발점은 검증 샘플에서 확인한 JDK17·compileSdk/targetSdk35이며, 제품 minSdk는 [기술 검증의 공식 근거](technical-validation.md#공식-근거)에 기록된 28을 따른다. 검증 샘플의 minSdk23은 제품에 계승하지 않는다. AGP/Kotlin/Gradle은 기존 성공 조합을 먼저 확인하고, Room/Media3/Ktor 추가에 호환성 변경이 필요한 경우 이유와 영향만 기록해 조정한다.

**설계/근거:** [시스템 설계](../design/README.md), [기록과 여정](../design/recording-and-journeys.md), [데이터/API/작업](../design/data-api-and-jobs.md), [내부 기획서](../product/product-plan.md), [최신 기술 검증](technical-validation.md#e1e4-통합-검증-결과). 원문 전체를 읽고 실행한다. 설계 v0.2 기준 SHA-256 접두부는 각각 `2a18e049b3ff`, `c3f60771e4d6`, `5f6c2cd3ead2`이며, 설계 변경 시 영향받는 계획/증거도 갱신한다.

## 1. 공통 제약과 현재 상태

아래는 이번/이전 사용자 지시의 원문을 보존한 공통 제약이다. 모든 단계에 적용한다. 단계만 떼어 다른 실행자/리뷰어에게 인계할 경우 이 절과 최신 사용자 보정을 **그대로 함께 포함**한다.

- “아직 제품 코드 구현이나 유료 API 호출은 시작하지 마.” — **현재 요청의 범위**다. 이번 산출물은 이 계획 문서뿐이며 아래 코드·빌드·설치·실험은 실행하지 않는다.
- “아직 코드 구현이나 유료 API 호출은 시작하지 마.” — 최신 리뷰 요청의 보정이다. P0의 검증 코드도 지금 작성/실행하지 않는다.
- “AI가 실제 곡을 작곡·생성한다. 규칙 기반 음표 작곡 엔진을 만드는 방향이 아니다.”
- “새 주행 기록의 첫 곡은 차량에서 생성한다.”
- “오래된 위치와 수집 공백을 실제 관측으로 채우지 않아야 해.”
- “사진·메모는 선택 입력이며 없어도 곡을 만들 수 있어야 한다.”
- “전체 제품 기능을 임의로 삭제하지 말고, 개발 순서나 공모전 구현 범위가 필요하면 구분해서 제안해줘.”
- “제출용 7번 참고자료는 계속 공란으로 유지해.”
- “기존 검증 코드를 완성 제품 코드로 간주하지 마.”
- “기본은 한 명이 이어서 작업하는 방식이며 자동 에이전트 분업·상주 작업자·훅 설치·개인 메모리 저장은 하지 마.”
- “Windroid 개발 중단 상태도 유지해.”
- “추가 유료 API 호출이나 충전은 별도 승인 없이 하지 마. 키를 출력하거나 채팅에 요청하지 마.”

현재 확인된 것은 Pleos 2.1.0/Vehicle 2.3.2/FusedLocation 1.1.3의 합성 SDK 기록과 호스트 Python을 거친 실제 음원 연결이다. 누적거리 차이 6km·관측 범위 53.414초·공백 18.323초·응답 27.617초·음원 99.71초는 해당 시험값이다. 실제 도로 주행·서비스 SLA·청취 품질·장시간 안정성의 완료 기준으로 옮겨 쓰지 않는다. 구간 시간/거리 API는 미지원이므로 필수 의존에서 제외한다.

기존 생성 원장은 총 4요청(402 거절 1회, 생성곡 3개), 추가 승인 잔여량 0이다. PC는 JDK17/SDK35, 에뮬레이터는 software GPU 검증 환경이다. 에뮬레이터 전용 ADB/AVD 식별자는 실행 시 다시 확인하고 다른 기기/프로젝트에 명령을 보내지 않는다. RAM 부담이 있으면 빌드와 AVD 실행을 순차화한다.

## 2. 우선순위·의존성과 완료의 의미

| 순서 | 단계와 사용자 결과 | 선행 | 설계 대응 | 이번 상태 |
| --- | --- | --- | --- | --- |
| 1 | P0 작은 서비스 probe: 실제 화면 비활성 중 SDK 수신 확인 | 후속 검증 착수 지시 | V1 | 미착수 |
| 2 | P1 제품 수집/저장: 중단 후 기록 복구·공백 표시 | P0의 실행 조건. 저장소/대역 검사만은 독립 가능 | V2 | 미착수 |
| 3 | P2 여정/요약: 하루·여행 범위를 로컬 생성 초안으로 고정 | P1 저장 계약. fixture 해석은 SDK 실행과 독립 | V2·V3 | 미착수 |
| 4 | P3 서버: 로그인·작업·유료 단계 통제·복구 | 고정 입력 계약, SDK 실행과 독립 진행 가능 | V3 | 미착수 |
| 5 | P4 차량 통합: 한 번 누르기→서버 작업→다운로드→저장곡 재생 | P1~P3 + P4의 파일검사/저장 구현, 유료 호출 불필요 | V3 | 미착수 |
| 6 | P5 실제 AI 통합: 새 SDK 기록으로 실제 곡 생성·재감상 | 대역/정적 검사는 독립 가능. 신규 호출은 P4 핵심 경로·별도 승인·제공자 확인 뒤 | V3·V4 | 승인/착수 전 |
| 별도 수행 | P6 지속성: 장시간·전원·여러 날 기록의 관측 범위 확장 | 저장 지속성은 P1, 날짜/여행은 P2, 생성/재생 간섭은 P4 뒤 | V2·V6 | 미착수 |
| 후속 | P7 폰·선택 입력·앨범 / P8 자동 생성·운영 | 차량 핵심 흐름 계약 | V5·V6 | 후속 범위 보존 |

기본 실행 순서는 P0→P1→P2→P3→P4→P5다. P3의 대역 기반 작업/비용 로직은 P0의 플랫폼 답변이나 P6 장시간 시험이 지연돼도 독립적으로 진행할 수 있다. 이는 병렬 에이전트 지시가 아니라 한 명이 막히지 않은 일을 이어서 선택하는 기준이다. P6는 P1 구현 뒤 시작하며 P4/P5의 선행 장벽으로 만들지 않는다. 반대로 P4/P5 성공을 P6 성공으로 대체하지도 않는다.

**의존성 판정:** P0 실패 중에도 저장/해석/서버 대역 작업은 가능하지만 실제 백그라운드 동작이 필요한 P1 경로와 M1의 그 항목은 완료로 닫지 않는다. P3는 설계 v0.2의 고정 입력 예제로 먼저 진행할 수 있고 P2의 실제 출력과 일치하는 계약 검사는 P4 진입 전에 마친다. ‘독립 진행 가능’은 해당 단계 전체 성공을 추정하거나 현재 착수 금지를 해제한다는 뜻이 아니다.

| 검토 지점 | 확인할 결과 | 주장할 수 없는 것 |
| --- | --- | --- |
| M1: P0~P2 | 수집 실행 조건·복구·고정 요약을 원본/상태와 대조 | 음악 생성·실차/장시간 안정성 |
| M2: P3~P4 | 제품 차량/서버 경로와 장애 처리, 기존 음원 재생. 미디어 host/포커스 실검증 결과는 항목별 기재 | 그 입력으로 새 AI 곡을 만들었다는 주장·대역만 쓴 내비/전화 공존의 실검증 |
| M3: P5 | 승인된 신규 요청과 새 SDK 기록·음원의 연결, 차량 재감상 | 청취 가치 전체·폰/앨범/운영/배포 완료 |
| 지속성 검토: P6 | 실제 관측한 길이·중단/전원 시나리오와 손실/복구 | 관측하지 않은 기간·차종·자동 수집의 보장 |

각 지점은 요구·도입 결함·회귀에 대한 자체 검토다. 독립 리뷰/매 단계 승인 절차를 자동 추가하지 않는다. 동일 외부 실패가 두 번 반복되면 증거를 조사하고 접근을 바꾸거나 해당 의존성만 격리한다. 통과하지 못한 항목은 미완료/미검증으로 유지한다.

## 3. 파일 배치와 검증 실행 원칙

아래는 **추후 생성할 경로**다. 지금 폴더/코드를 만들지 않는다. 기존 `validation/vehicle-sample`은 P0의 격리된 probe만 추가하고 이전 증거·JourneyProbeActivity는 보존한다.

| 위치 | 역할/주요 파일 | 담당 단계 |
| --- | --- | --- |
| `validation/vehicle-sample/app/src/main/java/ai/pleos/playground/sample/vehicle/` | 새 `LifecycleProbeActivity.kt`, `LifecycleProbeService.kt`, `LifecycleProbeJournal.kt` | P0만. 제품 namespace로 복사하지 않음 |
| `validation/journey-probe/lifecycle.py` | 새 유한 실행기. 대상 ADB/serial/Android user/run을 명시하고 lifecycle probe만 조작 | P0. 기존 `journey.py capture`와 별개 |
| `validation/journey-probe/continuity.py` | 새 유한 전후 제어기. 제품 앱 종료/재부팅 사이 증거를 보존하고 기기 검사를 재실행 | P6. P0에 미리 만들지 않음 |
| `vehicle/` | 새 Gradle wrapper/settings/catalog, `app/build.gradle.kts`, app manifest | P1에서 필요한 최소 Android 프로젝트 |
| `vehicle/app/src/main/java/dev/todaystrack/vehicle/` | `capture/`, `storage/`, `journey/`, `generation/`, `playback/`, `ui/`, `auth/`의 책임별 파일 | P1~P4 |
| `vehicle/app/src/test/java/dev/todaystrack/vehicle/` | 시간/거리·범위/해시·발신 대기 등 순수 로직 테스트 | P1~P4 |
| `vehicle/app/src/androidTest/java/dev/todaystrack/vehicle/` | Room·수명주기·기기 저장/미디어 연동 검사 | P1·P4 |
| `server/` | 새 Gradle 프로젝트, `src/main/kotlin/dev/todaystrack/server/`, `src/main/resources/db/migration/` | P3~P5 |
| `server/src/test/kotlin/dev/todaystrack/server/` | 실제 PostgreSQL 경합 검사·제공자 대역·권한/작업 API 검사 | P3~P5 |
| `docs/development/technical-validation.md` | 실행 뒤 단계·환경·증거 위치·남은 한계를 추가 | 후속 실행 시 |

namespace는 개발용 제안이며 최종 배포 ID 소유를 주장하지 않는다. 의존성은 단계가 필요로 할 때 추가한다. 최초부터 폰 프로젝트·별도 큐·다수 Gradle 모듈·운영 배포를 만들지 않는다. Git 저장소가 현재 없으므로 커밋/브랜치 완료를 가정하지 않는다. 후속 버전 관리 방식을 정한 경우에만 실제 revision을 검증 기록에 연결한다.

명령은 프로젝트 루트 PowerShell 기준이며 파일이 생긴 **후속 실행 시** 사용한다. 처음에는 wrapper/JDK/SDK 인식을 확인하고 특정 테스트→영향받는 통합 검사 순으로 수행한다. 존재하지 않는 테스트를 이번에 통과했다고 기록하지 않는다.

```powershell
# P0: 현재 존재하는 검증 프로젝트의 wrapper 사용
& .\validation\vehicle-sample\gradlew.bat -p .\validation\vehicle-sample :app:assembleDebug
# P1 이후: 새 차량 프로젝트가 생긴 뒤
& .\vehicle\gradlew.bat -p .\vehicle :app:testDebugUnitTest
& .\vehicle\gradlew.bat -p .\vehicle :app:connectedDebugAndroidTest
# P3 이후: 새 서버 프로젝트와 격리된 테스트 PostgreSQL을 준비한 뒤
& .\server\gradlew.bat -p .\server test
```

`connectedDebugAndroidTest`는 대상 AVD만 보이는 전용 ADB 환경을 확인한 뒤 실행한다. PostgreSQL 경합은 단순 메모리 DB로 대체해 통과시키지 않는다. 웹/Playwright·임의 80% 커버리지·성능 SLA를 도입하지 않는다. 코딩 단계에서는 핵심 위험을 드러내는 실패 사례를 먼저 만들고 최소 구현 뒤 같은 사례가 해결됐는지 확인한다. SDK/미디어 같은 플랫폼 동작은 자체 단위 테스트로 대체하지 않는다.

## 4. P0 — 실제 백그라운드 수집 구조의 작은 검증

**결과:** Activity 밖 수집 소유자가 실제로 가능한지 판단한다. 로그인·Room·서버·앨범·음악 호출은 만들지 않는다. 이 단계는 제품 구현과 분리된 작은 검증 코드이며 착수 지시 전에는 실행하지 않는다.

**파일/계약:** 위 probe 3개 파일을 추가하고 검증 앱 manifest에 probe Activity/서비스·필요 권한만 추가한다. Activity는 시작/정지와 lifecycle 기록, Service는 SDK 초기화/해제·단일 listener·유한 수집, Journal은 앱 전용 JSONL 저장을 맡는다. `runId, bootId, pid, elapsedMs, component, event, sourceTimestamp, quality, acquisition, environment`를 공통 이벤트로 연결한다. 좌표 원본은 로컬 파일에만 두고 Logcat에는 좌표/키/메모를 출력하지 않는다.

**실행기 구분:** `validation/journey-probe/journey.py capture`는 기존 JourneyProbeActivity를 실행하고 Android user 10·과거 포트/serial을 고정 사용하며 HOME/force-stop 시나리오를 포함한다. 그대로 실행하면 새 서비스 검증이 되지 않는다. 새 `lifecycle.py`는 명시한 대상과 새 Activity만 제어하고, SDK 수집 주체는 계속 Android Service다. PC 실행기는 입력/증거 회수만 담당하며 값을 SDK 대신 수집하지 않는다. 기존 runner/음악 probe를 자동 연결하지 않는다.

Journal에는 `sourceTimestampKind`와 원본 SDK 시각(있을 때만), 수신 elapsed, 정상 중지의 경계/마지막 저장 seq도 둔다. 서로 다른 시각 기준을 직접 빼지 않는다. 서비스는 Activity bind가 풀려도 유지되는 시작 서비스로 검토하고 단순 bound service의 UI 생존 효과를 백그라운드 성공으로 세지 않는다. 외부 노출이 불필요한 서비스/제어 receiver는 비공개로 두고, 시험 진입점은 검증 APK의 명시적 경로로 한정한다.

- [ ] 현재 이미지/OS·target/compile/minSdk·SDK 버전·권한 상태를 기록한다. 표본 minSdk23을 제품 요건으로 복사하지 않고, 설치 SDK35를 에뮬레이터 OS로 표시하지 않는다.
- [ ] 정차 중 보이는 Activity에서 사용자 시작 FGS 후보를 실행한다. 서비스 시작 허용, Pleos SDK 권한 허용, 신선한 콜백 지속을 별도 기록한다. 일반 Android의 location 타입/위치 권한과 Pleos 전용 권한 관계를 실제 오류·응답으로 확인한다.
- [ ] 전경에서 알려진 합성 경로·누적거리 입력을 주고 저장 이벤트를 비교한다. 이어 다른 화면/window 전환을 수행해 Activity의 실제 pause/stop과 서비스의 계속된 SDK 관측을 같은 단조 시각으로 대조한다.
- [ ] UI 재진입/재생성 후 중복 listener가 생기지 않는지, 정상 중지의 최종 닫힘 뒤 새 SDK 이벤트가 더 저장되지 않는지 확인한다. 위치 권한 철회/SDK 오류는 수집 성공으로 표시하지 않는다.
- [ ] 프로세스 종료와 사용자 force-stop을 별도 관찰한다. PID 소멸을 확인하지 못한 종료 명령은 kill 성공으로 세지 않는다. 중단 중 계속 수집했다고 주장하지 않고 마지막 저장~재개 간격을 기록한다.

**소규모 시험 순서:** 아래 입력은 비교용 합성 데이터이며 제품 성능/거리 임계값이 아니다. 실제 좌표는 로컬 controls에만 기록한다. 누적거리 D0는 시험 직전 계기값보다 큰 기준을 선택해 감소 오류를 피한다.

| 구간 | 실행·증거 | 판정 포인트 |
| --- | --- | --- |
| 전경 기준 | Activity에서 시작, 유효 위치 A/누적거리 D0 입력→SDK/Journal 비교 | SDK·권한·파일 기록이 먼저 정상인지 확인 |
| 실제 비가시 | 다른 화면/창 배치로 검증 앱의 모든 Activity가 보이지 않게 하고 `onStop`·시스템 Activity/window 상태를 보존 | `onPause`만 발생하거나 앱 창이 보이면 ‘일시정지/다중 창’ 시험일 뿐 백그라운드 판정 보류 |
| 비가시 뒤 새 입력 | 위 상태를 확인한 **뒤** 새 위치 B→C와 증가한 누적거리 입력. Service PID/수신 elapsed·값·원본 시각 의미를 연결 | 전경에서 지연된 콜백·캐시가 아니라 비가시 기간의 입력을 받았는지 소스별 확인 |
| 재진입/정상 중지 | 같은 run 재진입, 중복 listener 확인→명시 정지→소스 listener 해제·writer drain/닫기→최종 seq 기록 | 정지 요청 전 in-flight 이벤트는 경계까지 정리 가능. 최종 닫힘 뒤 추가 SDK 이벤트 저장은 없어야 함 |
| 중단 진단 | 별도 하위 run에서 종료/force-stop·재실행, PID/저장 파일·공백 관찰 | Room/자동 복구의 제품 성공 판정은 P1. 종료 동안 수집했다고 주장 안 함 |

**완료 조건/방법:** 전경 입력 일치, 실제 비가시 상태, 그 뒤 주입한 입력의 새 SDK 이벤트, 정상 종료/자원 해제 증거가 같은 run에 연결돼야 ‘해당 조건의 백그라운드 수신 확인’으로 판정한다. SDK별로 성공/미지원/오류/미확정을 따로 기록한다. 위치가 끊기고 거리만 계속되면 둘 다 계속 수신한 성공으로 보고하지 않는다. HOME 명령 성공·알림·프로세스 생존·캐시 응답만으로 통과시키지 않는다. 비가시 증거가 없으면 미확정이고, 확인된 비가시에서 수신이 끊기면 해당 경로 실패다. 중단 진단 미실행이 기본 lifecycle 결과를 지우지는 않지만 P0 전체 체크리스트와 P1 복구는 미확인으로 남긴다. 짧은 시험은 장시간/전원 복구 통과가 아니다.

`onPause`와 비가시 상태의 구별은 [Android Activity 수명주기](https://developer.android.com/guide/components/activities/activity-lifecycle)에 근거한다. Pleos 다중 창에서 실제 전환되는지는 이번 시험으로 확인해야 하며 문서만으로 성공 판정하지 않는다.

**실패 시:** 무관한 FGS 타입·기기 권한 우회·끝없는 폴링으로 숨기지 않는다. 권한/시작 제한/SDK 서비스 제한을 분리해 허용 경로를 조사한다. P1의 저장 로직·P2의 fixture 해석·P3 서버 작업은 진행할 수 있으나 백그라운드 제품 요구는 열어 둔다.

근거: [Pleos 위치 권한 경계](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions), [Android foreground service 타입](https://developer.android.com/develop/background-work/services/fgs/service-types#location). 일반 Android 문서가 Pleos 성공 증거를 대신하지 않는다.

## 5. P1 — 제품 수집·저장·중단 복구

**결과:** 음악/네트워크 없이 기록을 남기고 다시 실행해 읽는다. P0에서 확인한 실행 조건을 제품 구조로 별도 구현한다.

**파일 기준 디렉터리:** `vehicle/app/src/main/java/dev/todaystrack/vehicle/`. 이 아래 `capture/JourneyCaptureService.kt`, `capture/PleosCaptureAdapter.kt`, `capture/CapturePolicy.kt`, `storage/CaptureDatabase.kt`, `storage/CaptureDao.kt`, `storage/CaptureRepository.kt`, `ui/RecordingScreen.kt`를 만든다. manifest/backup 규칙은 `vehicle/app/src/main/AndroidManifest.xml`, `vehicle/app/src/main/res/xml/backup_rules.xml`, `vehicle/app/src/main/res/xml/data_extraction_rules.xml`에 둔다. 테스트는 3절의 test source 아래 `capture/CaptureQualityTest.kt`, `capture/CaptureTimeTest.kt`, androidTest source 아래 `storage/CaptureRecoveryTest.kt`다.

**계약:** 서비스→어댑터의 시작/중지, 어댑터→Observation, 저장소→커밋된 session/epoch/seq와 관측 Flow. 소유 범위는 `(deviceId, localProfileScope)`이고 UI가 SDK listener를 소유하지 않는다. `receivedElapsedMs/bootId`와 SDK 원본 시각을 분리한다.

- [ ] 최소 앱·SDK 어댑터·수집 UI를 만들고 capability와 실제 오류를 분리한다. 필수 입력은 누적거리와 위치의 실제 가용성에 따르며 구간 시간/거리 미지원을 0으로 바꾸지 않는다.
- [ ] 제한 buffer→단일 writer→Room transaction으로 이벤트/마지막 커밋/상태를 함께 저장한다. seq 재사용·중복 시작을 방지하고 UI는 커밋 결과와 마지막 관측 시각을 표시한다.
- [ ] 소스별 freshness/중복/역순·누적거리 리셋·read 시각 불확실성·gap을 구현한다. 시작값 0을 무조건 오류로 버리거나 nullable을 0으로 바꾸지 않는다.
- [ ] 정상 정지/비정상 종료/force-stop/재부팅 후 열린 epoch를 복구한다. 다른 boot의 elapsed 차이를 계산하지 않는다. 알 수 없는 사망 시각은 누락 가능 범위로 표시한다.
- [ ] 저장 실패/버퍼 포화/권한 철회 시 상태를 낮추고 기록 누락을 드러낸다. 원본 백업/공유 저장소/진단 유출을 막고 로컬 프로필 전환 시 이전 수집을 닫는다.

**완료 조건:** 커밋 성공한 기록이 재실행 뒤 보존되고, 중단이 새 epoch/gap으로 나타나며, 중복 listener·조용한 drop·공백 메우기 없이 다음 수집이 이어진다. 프로그램이 죽어 있던 동안의 기록 지속은 완료 조건이 아니다. 권한/지원 부족도 정확한 제한 상태로 끝나야 한다.

**검증 방법:** 같은 boot 시계 역행·다른 boot·오래된 위치·거리 감소·거리만/위치만 관측을 단위 fixture로 확인한다. Room transaction/마이그레이션·쓰기 실패·재실행 복구는 실제 Android 저장소에서 확인한다. force-stop과 전원 재시작 시험은 전용 AVD만 대상으로 실행하고 전/후 DB·PID·boot 증거를 남긴다. P0 결과는 실행 조건의 참고이며 새 제품 Service의 lifecycle 확인을 생략하는 근거가 아니다.

종료/재부팅 전후 비교는 앱 외부의 유한 실행 기록으로 연결한다. 종료 대상과 같은 프로세스의 instrumentation이 force-stop/재부팅 후에도 살아서 검사한다고 가정하지 않는다. 기기 테스트는 준비/복구 후 검사를 별도 실행하고, 호스트는 그 사이 실제 종료·부팅·데이터 보존 증거를 회수한다. 구현 전의 테스트 이름만으로 자동 복구 검증이 생겼다고 보지 않는다.

## 6. P2 — 주행 구간·하루/여행·고정 생성 입력

**결과:** 지원 기록을 사용자가 이해할 요약과 음악 생성 재료로 만든다. 사진/메모 없이 한 번 누를 수 있는 범위를 준비한다.

**파일 기준 디렉터리:** `vehicle/app/src/main/java/dev/todaystrack/vehicle/`. 이 아래 `journey/JourneyGrouping.kt`, `journey/JourneySummaryBuilder.kt`, `journey/SourceCoverage.kt`, `generation/InputSnapshotBuilder.kt`, `generation/GenerationIntentOutbox.kt`, `ui/JourneySummaryScreen.kt`, `ui/InitialSettingsScreen.kt`를 만든다. unit test source에는 `journey/JourneyGroupingTest.kt`, `journey/JourneySummaryBuilderTest.kt`, `journey/SourceCoverageTest.kt`, `generation/InputSnapshotBuilderTest.kt`, `generation/GenerationIntentOutboxTest.kt`를 둔다.

**계약:** 저장된 Observation/Gap→DriveLeg/GroupSlice→JourneySummary→고정 local intent 초안. 범위는 `[fromSeq,toSeqExclusive)`, 최종 범위 해시는 owner/device/session의 정규화 합집합, 최종 입력 해시는 summary/settings/attachment revision을 포함한다. groupId·장르·요약 버전이 범위 정체성을 바꾸지 않는다.

**로그인 전/후 경계:** P2는 아직 서버 ownerId가 없는 기록을 다룰 수 있어야 한다. `localIntentId`, `localProfileScope`, 변하지 않는 device/session/seq 범위·요약/설정 revision·초안 본문 해시를 고정한다. 이를 ownerId를 가진 서버 snapshot/최종 sourceCoverageHash라고 부르지 않는다. P3의 귀속 계약과 P4 로그인에서 사용자가 선택한 로컬 범위를 인증된 ownerId/ownerEpoch에 한 번 연결한 뒤 최종 snapshot/hash를 만든다. 기존 초안은 수정하지 않고 귀속 결과를 별도 저장한다. 서버 응답 유실 후 같은 owner/localIntentId 재전송은 같은 귀속/snapshot으로 돌아오며, 다른 계정으로 로그인하면 기존 의도를 자동 이관하지 않고 새 확인/의도를 요구한다. SDK 사실과 설정은 이 과정에서도 다시 읽어 섞지 않는다.

- [ ] 초기 하루/여행 선택과 음악 설정 기억을 만들고, 사용자 시작/마무리로 구간과 묶음을 확정한다. 자동 경계는 검증 전 후보로만 남긴다.
- [ ] 여러 leg·중간 정차·자정·시간대 변경·여러 날 여행을 원본 참조로 묶는다. 자정/공백을 가로지른 거리 차분은 비례 배분하지 않고 미배분으로 둔다.
- [ ] 순서·관측 규모·출발 시간대·머묾/귀환 근거·unknown/gap을 요약한다. 장소/관계/감정/날씨를 추정 사실로 추가하지 않는다.
- [ ] ‘기록 모임’ 알림/카드→정차 중 한 번 누르기→고정 범위/설정/localIntentId/허용 비용·만료의 발신 대기를 저장한다. 로컬 초안 또는 로그인 뒤 최종 snapshot 확정 후 새 관측·설정 변경이 입력에 섞이지 않게 한다. 미로그인 의도는 ‘로그인/귀속 대기’이며 서버 접수나 과금 동의 완료가 아니다.
- [ ] 추가 주행은 새 groupRevision, 기존 음원/입력은 불변으로 처리한다. 선택 입력 필드/출처는 마련하되 업로드 화면·사진 분석이 없다는 이유로 기본 생성을 막지 않는다.

**완료 조건:** 사람이 지정한 기록 범위와 로컬 생성 초안이 일치하고 공백/거리/순서의 근거를 추적할 수 있다. 초안 동결 후 변동이 없어야 하며, 무관측/소재 부족을 허위 방문/운전 시간으로 채우지 않는다. 하루·여행 설정을 초기부터 선택/변경할 수 있어야 하되 완전 자동 경계 성공을 주장하지 않는다. 최종 서버 snapshot의 귀속/해시 일치는 P3/P4 검증 항목이며 P2만으로 성공 처리하지 않는다.

**검증 방법:** 기존 SDK 세션의 A→B→C→C→B→A를 입력 fixture로 사용한다. 전체 범위 거리 6km와 약18초 gap을 보존하고 53.414초를 실제 주행 시간으로 라벨링하지 않는지 비교한다. 겹친 slice 합집합, 동일 범위 재요약, 새 이벤트 추가, 자정 차분, 원본 삭제, 늦은 콜백을 별도 fixture로 확인한다. 기존 fixture 통과만으로 새 SDK 수집까지 확인했다고 보고하지 않는다.

구형 probe fixture의 총 차이 6km는 양끝 누적거리 규모의 증거다. v0.2가 요구하는 연속 차분/gap 횡단 차분/미배분 거리 구별을 생략하거나 기존 journey.json의 모든 구간 해석을 제품 정답으로 복사하지 않는다. 입력 factSet/출처·범위/해시를 별도 기대값과 대조한다.

## 7. P3 — 서버 작업·권한·비용을 대역으로 구현

**결과:** 차량 요청 하나를 복구 가능한 서버 작업 하나로 다룬다. 이 단계에는 실제 음악/가사 AI 호출이 없다.

**파일 기준 디렉터리:** `server/src/main/kotlin/dev/todaystrack/server/`. 이 아래 `auth/DeviceEnrollment.kt`, `auth/OwnerAccess.kt`, `generation/InputAdoption.kt`, `generation/GenerationApi.kt`, `generation/GenerationWorker.kt`, `generation/AttemptRepository.kt`, `generation/CostReservation.kt`, `generation/MusicGenerationProvider.kt`, `generation/LyricsProvider.kt`, `generation/CompositionPlanBuilder.kt`, `generation/GroundedLyricsBuilder.kt`, `assets/AudioAssetStore.kt`, `sync/OwnerChangeLog.kt`, `deletion/DeletionFence.kt`를 만든다. migration은 `server/src/main/resources/db/migration/V001__generation_core.sql`이다. 서버 test source에는 `InputAdoptionTest.kt`, `GenerationJobTest.kt`, `PaidAttemptRecoveryTest.kt`, `CompositionGroundingTest.kt`, `OwnerCursorTest.kt`, `DeletionFenceTest.kt`와 제공자/저장소 대역을 둔다.

**계약:** 설계의 `/v1/input-snapshots`, quotes/jobs/status/cancel, 생성 범위 업로드, 로그인/차량 자격, 다운로드 권한을 우선 구현한다. 폰·앨범 API는 P7로 남긴다. `INITIAL/REGENERATE`, lineageEpoch, inputHash, stage별 attempt, job stateRevision, cost reservation, owner commit cursor가 권한/중복 경계다.

P3에서는 인증된 owner와 localIntentId를 기준으로 원본 범위를 귀속하는 로직, 고정 factSet→CompositionPlan/선택 템플릿 LyricsDraft를 **외부 호출 없이** 구현한다. 저장소/음원 검사 의존은 계약과 대역으로 주입한다. 가사 AI 확장은 P5 이후지만 사실/창작 경계·구성안의 순서는 P4 전에 검증할 수 있어야 한다. 테스트용 모델/가격 profile은 격리된 대역 원장용이며 실제 승인·가격 근거로 쓰지 않는다. P3의 다운로드 API 계약은 P4에서 실제 파일 검사/저장소와 연결한 뒤에야 음원 통합으로 판정한다.

- [ ] 작은 Ktor/실제 PostgreSQL 구조와 소유권·기기 등록을 만든다. 차량 로그인과 폰 앱 연결은 분리한다. 개발 차량은 명시 등록한 기기만 허용하고 `kind=CAR` 자가 선언은 거절한다. 서비스 배포용 차량 등록 근거가 미검증이면 그 한계를 기록한다.
- [ ] 입력/quote/idempotency·활성 intent 합류·예산 예약·job/outbox 생성을 transaction으로 묶는다. 조회/취소/권한 오류의 화면 계약을 만든다.
- [ ] 로컬 초안의 일회 귀속·동일 요청 재전송과 고정 factSet→순서/비중 구성안·선택 템플릿 가사를 구현한다. 반환/요청 가사 텍스트와 실제 가창 판정 필드를 분리한다. 저장소와 검사기는 대역 주입 상태임을 표시한다.
- [ ] INLINE/ASYNC 대역으로 stage별 attempt·lease/fence·실행 결과 보존·READY/FAILED/RECONCILING/UNRECOVERABLE을 구현한다. 가사 AI도 유료 단계로 계산하고 이 단계의 기본은 외부 전송이 없는 대역이다.
- [ ] 취소가 attempt 전송 가능 경계를 앞서는 경우와 뒤서는 경우를 나눈다. 결과 불명 시 새 POST 금지·비용 예약 유지, 회수 불능 이후 새 intent도 별도 고지/예약하도록 한다.
- [ ] 입력/곡 삭제와 늦은 결과·오래된 outbox의 경합을 막는다. owner cursor를 커밋 순서로 관리해 늦은 transaction의 변경이 건너뛰어지지 않게 한다. 새 계정/삭제 lineage가 이전 권한을 승계하지 않게 한다.

**완료 조건/검증 방법:** 아래를 테스트 API·DB row/원장·대역 호출 횟수로 함께 확인한다. 같은 구현을 따라 쓰는 스키마 검사만으로 통과시키지 않는다.

| 구체 시나리오 | 기대 결과 |
| --- | --- |
| 같은 key/본문 두 번, 같은 key/다른 본문 | 같은 job 반환 / `409`, 외부 대역 추가 송신 없음 |
| worker가 가사 또는 음악 송신 직후 종료 | stage attempt로 복구, 불명 접수 재송신 없음, 비용 미결 유지 |
| 제공자가 조회/멱등/취소를 지원하지 않음 | 지원한다고 가정하지 않음, 회수 불능·비용 UNKNOWN을 별도 표시 |
| 동시에 예산을 소비하는 작업 두 개 | 승인 상한 초과 예약 없음, 거절 작업의 송신 없음 |
| 취소/입력 삭제 후 성공 결과 도착 | 곡 READY/다운로드 권한/공개 이벤트 부활 없음, 미사용 예약과 미결 비용 분리. 실제 앨범 참조 검사는 P7 |
| 입력 삭제 후 재요약/옛 outbox, 곡만 삭제 후 재생성 | 삭제 fence 우선 / 보존 입력·lineage 자격은 별도 확인 |
| cursor 낮은 transaction이 늦게 끝나는 경합 | 커밋 순서 직렬화, 클라이언트가 변경을 놓치지 않음 |
| 다른 소유자·폰의 INITIAL·자가 선언 차량 | 거절. 등록된 개발 차량의 INITIAL만 허용 |
| 미로그인 초안 귀속 뒤 응답 유실·같은 owner 재전송·다른 계정 로그인 | 같은 귀속/snapshot 재사용 / 자동 이관 거절. 입력 사실·설정 불변 |
| 다른 설정을 넣은 같은 localIntentId, 클라이언트 계약 불일치 | 멱등 본문 충돌 또는 업그레이드 필요로 접수 보류. 외부 송신 없음 |
| 입력의 순서/미관측 사실·템플릿/AI 가사 구별 | 구성안이 사실 출처와 순서를 보존, 미관측 방문/감정/날씨 추가 없음 |

저장소/비밀키/제공자 config는 환경 분리하고 실제 유료 어댑터의 dispatch는 비활성으로 둔다. 호스팅/운영 계정 구매·클라우드 배포는 이 단계의 필수 작업으로 넣지 않는다.

## 8. P4 — 차량 요청→음원 저장·재생까지 통합

**결과:** 실제 차량 앱과 서버에서 생성 요청·완료·다운로드·재감상의 사용자 흐름을 연결한다. 음원은 기존 파일/제공자 대역이므로 **새 입력의 AI 생성 성공으로 세지 않는다.**

**파일 기준 디렉터리:** `vehicle/app/src/main/java/dev/todaystrack/vehicle/`. 이 아래 `auth/VehicleSignIn.kt`, `generation/GenerationApiClient.kt`, `generation/GenerationOutboxWorker.kt`, `generation/AssetDownloader.kt`, `storage/TrackRepository.kt`, `playback/TrackLibraryService.kt`, `playback/PlaybackController.kt`, `ui/GenerationStatusScreen.kt`, `ui/LibraryScreen.kt`를 만든다. 서버 main source에는 `assets/PrivateAssetStore.kt`, `assets/DownloadGrantApi.kt`, `assets/AudioValidator.kt`를 둔다. 차량 unit test의 `generation/GenerationOutboxWorkerTest.kt`, `generation/AssetDownloaderTest.kt`, androidTest의 `playback/TrackPlaybackTest.kt`, 서버 test의 `DownloadGrantTest.kt`, `AudioValidatorTest.kt`로 아래 사례를 확인한다.

**계약:** 차량 local intent→서버 snapshot/quote/job→`READY(versionId,assetId,hash)`→기기 `DOWNLOADING/AVAILABLE`. 서버 READY와 기기 가용 상태를 분리한다. MediaLibraryService/Session은 저장된 파일을 사용하고 수집 Service와 독립한다.

- [ ] 브라우저 기반 차량 로그인·처리 동의 후 정차 상태에서 범위 확인과 한 번 누르기를 연결한다. 폰 앱이 없어도 된다. 주행 제한 상태를 알 수 없으면 생성/편집 UI를 보류한다.
- [ ] P2 초안→인증 계정의 최종 snapshot/범위 해시·허용 비용/만료를 확인해 P3 API 계약에 연결한다. Android 에뮬레이터의 loopback을 PC 서버 주소로 가정하지 않고 전용 연결 경로·인증·TLS/개발 신뢰 설정을 확인한다. 개발 예외는 검증 구성에만 한정하고 실제 배포의 인증/TLS 완료로 보고하지 않는다.
- [ ] 네트워크 단절/응답 유실/기기 재시작에도 고정 intent로 접수/상태를 회수한다. 최신 설정을 섞거나 첫 생성 대기 건을 폰 승인으로 전환하지 않는다.
- [ ] 기존 SDK 통합 음원을 대역 결과로 전달하고 **서버에서도** 형식/크기/전체 디코딩/길이/해시 검사→비공개 객체 확정→DB READY를 만든다. 차량은 검사→임시 파일→원자적 확정→DB AVAILABLE로 처리한다. 서버 DB 커밋 실패의 객체 회수, 다운로드 중단·손상·저장 부족은 기존 결과 재다운로드/명시 실패로 처리하고 음악 재호출하지 않는다.
- [ ] Media3 라이브러리/세션·최근 곡·재생/일시정지·오프라인 재감상과 오디오 포커스 대응을 연결한다. 완료 즉시 다른 미디어를 끊거나 자동 재생하지 않는다.
- [ ] 차량 꺼짐/앱 종료 뒤 서버 작업 회수, 로컬 캐시/입력/곡 삭제와 로그/backup 노출 여부를 확인한다. 음악 재생이 수집 시작/중지 조건이 되지 않게 한다.

**완료 조건/방법:** 같은 intent가 중복 작업 없이 서버 완료와 해당 음원 다운로드까지 이어지고, 앱 재실행·인터넷 차단 뒤에도 저장곡을 재생한다. 파일 손상은 가용곡으로 표시하지 않는다. SDK 신선도·품질은 P1/P2 증거를 연결한다. Pleos 미디어 host 탐색·명령 수신·포커스 손실/회복을 직접 관찰한다. 실제 내비/전화 상황이 없어서 포커스 대역으로만 확인한 경우 그 한계를 남긴다.

판정은 ‘차량/서버 핵심 저장·재생 경로’, ‘Pleos host 연동’, ‘실제 내비/전화 공존’으로 나눠 완료/대역 확인/미검증을 표시한다. 실제 차량 앱의 허용된 재생 경로와 포커스 대역 대응이 확인되면 P5의 핵심 음악 통합을 진행할 수 있지만, 대역만 쓴 내비/전화 검증은 계속 열어 둔다. P4 전체 플랫폼 연동 완료나 출시 적합성을 선언하지 않는다. 키/좌표 노출·인증 우회·허용되지 않은 차량 동작은 부분 완료를 이유로 넘기지 않는다.

대역 실행은 격리된 계정/데이터와 `providerMode=FIXTURE` 표지를 사용한다. 기존 MP3의 실제 과거 입력 해시와 현재 테스트 입력 해시를 구분하고 ‘연동 검증용 기존 음원’으로 표시한다. 테스트 READY/lineage를 실서비스 생성 이력/폰 재생성 자격으로 승격하지 않는다. 파일이 재생된 사실로 새 입력에 대한 AI 작곡을 주장하지 않는다.

근거: [Android 차량 미디어 구조](https://developer.android.com/media/implement/surfaces/cars). Pleos host 호환성은 이 단계의 실제 검증이다.

## 9. P5 — 별도 승인 후 실제 AI 연결

**결과:** 새로 수집한 SDK 기록이 실제 AI 요청·새 음원·차량 재감상에 연결된다. P4가 끝나도 승인이 없으면 이 단계의 실제 호출은 대기한다.

**파일 기준 디렉터리:** `server/src/main/kotlin/dev/todaystrack/server/`. 새 `generation/LyriaMusicProvider.kt`와 필요한 경우 가사 AI용 `generation/AiLyricsProvider.kt`를 만든다. P3의 구성안/템플릿 가사 로직과 P4의 `assets/AudioValidator.kt`를 재사용한다. 서버 test source의 `LyriaResponseTest.kt`, `CompositionGroundingTest.kt`, `AudioValidatorTest.kt`와 stage attempt 검사를 갱신한다. 공급자를 바꾸면 어댑터 파일과 profile만 교체하고 클라이언트/저장 음원 계약은 유지한다.

이 단계의 **대역 응답 파싱·어댑터 구현·공식 조건 확인**은 신규 유료 승인 전에도 승인된 개발 범위에서 가능하다. 실제 전송만 P4 핵심 경로 확인과 U3에 종속된다. 템플릿 가사로 음악 연결을 확인한 경우 ‘실제 음악 생성 경로 완료 / 가사 AI 자동 작성 미검증’으로 분리한다. 청취/가창 평가는 파일 성공과 별개로 기록하며 평가를 생략한 채 음악 품질까지 완료로 처리하지 않는다.

- [ ] 기존 3곡의 실제 청취·요청 가사/반환 텍스트/가창·무가사·전개 반영을 구분해 기록한다. 이 평가는 P0~P4를 기다리지 않고 가능한 독립 작업이다. 직접 듣지 않았으면 평가 완료로 세지 않는다.
- [ ] 제공자의 공식 통합/검증 이용 조건·모델/출력 제약·요청당 비용 상한·데이터 처리·조회/멱등/취소 지원을 확인한다. 최종 서비스 선정이나 배포권 확보로 확대 해석하지 않는다.
- [ ] 새 합성 SDK run을 제품 수집 구조로 기록하고 P2의 요약과 P3의 구성안을 만든다. 정확 좌표·누적거리 절대값·외부 필요 없는 원본 manifest는 음악 요청에서 제외한다.
- [ ] 실행 전에 실제 전송할 요약/구성안·모델·음악/가사별 최대 호출 수·예상 총액 상한·재시도 유무를 구체화해 U3 승인을 받는다. 키는 서버의 보호 저장에서 읽고 채팅으로 요청/출력하지 않는다. 기존 승인을 재사용하지 않는다.
- [ ] 승인 범위에서만 외부 dispatch를 켜고 stage별 attempt/예약을 사용한다. 음악만 승인됐다면 사실 템플릿 가사를 사용할 수 있지만 가사 AI 자동 작성 검증으로 세지 않는다. 가사 AI도 호출하려면 그 비용/횟수가 승인에 포함돼야 한다.
- [ ] 원본→요약/고정 입력→최종 요청→제공자 결과→검사된 음원→차량 파일을 해시/ID로 연결하고 차량에서 전체 음원 재생·앱 재실행 후 재감상을 확인한다. 승인 소진 뒤 dispatch를 다시 비활성으로 둔다.

**완료 조건:** 호스트 Python probe의 수동 파일 전달 없이 제품 차량 앱/서버 경로로 새 SDK 기록을 처리해 새 AI 음원을 저장·재감상한다. 기록 환경이 합성이면 표시한다. 전송/파일 검사·차량 재생·실제 가창/구성 준수의 결과는 각각 기록한다. 반환 가사 텍스트 일치가 실제 가창을 입증하지 않으며 한 곡 성공이 일반 지연/품질·전체 제품 완료를 입증하지 않는다.

**실패 시:** 유료 자동 반복 생성으로 해결하지 않는다. 결과 회수/다운로드/대역 검사는 계속할 수 있지만 새 음악/가사 호출은 남은 명시 승인과 비용 예약이 있을 때만 한다. 권리/계정/승인 대기는 해당 실제 호출에만 붙이고 P6·P7의 비과금 작업을 막지 않는다.

## 10. P6 — 장시간·전원·여러 날 지속성

**결과:** P1 이후 실제 구현의 관측 범위를 늘리고 하루/여행 보관·복구의 한계를 드러낸다. 저장 구조를 만들기 전에 긴 시험부터 시작하지 않는다.

**파일:** P1/P2의 서비스/저장/묶음 코드 중 확인된 결함만 수정하고 `vehicle/app/src/androidTest/java/dev/todaystrack/vehicle/capture/CaptureContinuityTest.kt`, `vehicle/app/src/androidTest/java/dev/todaystrack/vehicle/storage/PowerRecoveryTest.kt`와 외부 유한 실행기 `validation/journey-probe/continuity.py`를 추가한다. 기기 검사는 준비/재개 후 assertion, 외부 실행기는 실제 중단/재부팅과 전후 증거 연결을 맡는다. 실행 자료는 기존 `technical-validation.md`에서 run 경로로 연결한다. 상주 에이전트·영구 watcher·자동 작업 예약은 만들지 않는다.

| P6 하위 범위 | 최소 선행 | 독립 판정 |
| --- | --- | --- |
| 저장 지속성·중단/전원 복구 | P1의 실제 수집/Room | 보존/누락/새 epoch·자원 관측. 날짜/여행 판정은 포함 안 함 |
| 자정·여러 날 여행 귀속 | P1+P2 | 같은 원본 범위 중복/오귀속 없음, 공백 표시. 실제 시간 관측과 시계 fixture를 구별 |
| 수집 중 생성·다운로드·재생 간섭 | P1+P4 | 대역 음원으로도 가능. 수집 중단·listener 중복·메모리/저장 압박 확인, 새 AI 호출 불필요 |

- [ ] 실행 전 대상 버전·시나리오·실제 관측 예정 기간·저장/메모리 관측 방법·중지/정리 방법을 정해 기록한다. 임의 ‘몇 시간 무손실이면 합격’ 기준을 새로 만들지 않는다.
- [ ] 여러 번의 이동/정차·앱 창 전환·오프라인·다음 날 재개·여행 유지 시나리오를 지속 수집 구조에서 관찰한다. 시계만 넘긴 fixture 결과와 실제 오랜 시간 수집한 결과를 구별한다.
- [ ] 전원/재부팅·프로세스 종료·force-stop·권한 철회·저장 부족을 구별하고, 마지막 커밋/새 boot/epoch·소스별 gap과 자동 재개 허용 여부를 확인한다.
- [ ] 실제 기록량·DB 증가·큐 포화/손실·메모리·재시작·가능한 자원/발열 자료를 보고한다. 에뮬레이터가 측정할 수 없는 실차 소비전력/발열은 미검증으로 남긴다.

**완료 조건:** 실행한 하위 범위마다 선언한 실제 시험 구간에서 저장 성공한 기록이 복구되고, 못 모은 구간/미확정 시각이 gap으로 남아야 한다. 묶음 검사는 P2 뒤에만 날짜/여행 중복·오귀속 없음으로 판정한다. 불허된 자동 재시작은 재개 안내로 처리하고 성공처럼 숨기지 않는다. 측정하지 못한 하위 범위는 미검증으로 남기고 P6 전체 완료로 묶지 않는다. 새 결함을 고친 경우 영향받는 사례만 다시 실행하고 이전 증거의 적용 범위를 기록한다.

P6 결과는 보관 정책 U2와 자동 경계 수치의 근거가 된다. 기술 수치는 관측 근거로 정하고 사용자에게 임의 튜닝 선택을 넘기지 않는다. P6 실패를 숨긴 채 ‘장시간 안정성 확인’이라고 보고하지 않되, 독립적인 핵심 음악 흐름까지 전부 미완료로 뭉뚱그리지 않는다.

## 11. 후속 제품 범위와 검증 인계

아래는 전체 기능을 보존한 후속 단위다. 첫 차량 흐름의 선행 개발로 끌어오지 않는다. P7/P8 착수 시 v0.2와 그때의 실제 계약을 기준으로 해당 작업만 세분화한다.

| 단계 | 범위·선행 결정 | 사용자 완료 조건·검증 |
| --- | --- | --- |
| P7 폰/소장 | U1 폰 플랫폼 후 선택 클라이언트, 기존곡 감상·보관·다운로드·공유·제목/커버·기존곡 재생성, 선택 메모/사진, 월간/여행 앨범 | 폰 INITIAL 거절·기존 lineage 재생성 허용, 선택 입력 없이도 생성. 기존 날짜곡 재사용/앨범 버전 고정, 동시 편집/오프라인 sync·해제 상태 확인/사본 삭제·외부 파일 전달을 실제 기기로 확인 |
| P8 자동화/운영 | 자동 생성 opt-in·비용 상한/끄기·기간 선택/날짜별 배치·보관/삭제/계약·서비스 운영 | 오래된 intent보다 정책 폐기 우선, 부분 성공 child 보존, 삭제 재업로드 차단·backup/제공자 만료, 실제 운영 비용 범위 확인. 배포는 별도 요청 범위 |
| 선택 확장 | 실시간 음악·검증된 와이퍼 질감·정밀 구간 대응 | 별도 사용가치/플랫폼/비용 증거가 필요. 주행 후 생성의 완료 조건으로 넣지 않음 |

폰 플랫폼 U1·자동 보관 성향 U2·추가 유료 범위 U3 외의 현재 필수 인터뷰는 없다. 미확정 장르/가사/길이 기본값은 기존 곡 평가 뒤 권고하고 기억/변경 기능을 유지한다. 공모전 일정이 없으므로 출시일·기간·인원·완료율을 만들어 약속하지 않는다.

## 12. 단계별 증거와 계획 자체 검토

후속 실행의 evidence는 기존 `docs/development/technical-validation.md`에 추가한다. 개발용 run 파일은 예를 들어 `validation/journey-probe/runs/<run-id>/`에 보존하되 기존 run을 덮어쓰지 않는다. 최종 계획 파일에 run 결과를 장황하게 복제하지 않는다.

| 항목 | 기록할 내용 |
| --- | --- |
| 대상 | P번호·검증한 파일/revision/hash·설계 버전·OS/SDK/target/GPU·기기 식별 |
| 입력/행동 | 합성/실제 구분·입력 지점·실제 lifecycle·중단/복구 행동·허용 호출 범위 |
| 관찰 | source/receive 시각·seq/epoch/gap·API job/attempt·비용 상태·음원 해시/검사·기기 재생 |
| 판정 | 완료/부분 확인/미검증/특정 의존성 대기, 주장 가능한 범위와 다음 행동 |
| 정리 | 수집·테스트 공급자·전용 AVD/연결·임시 서버·유료 dispatch 종료, 설치물/증거 보존 범위 |

**현재 증거:** 이 문서 작성과 자체 검토만 수행했다. 코드 작성·Gradle/SDK/미디어/DB 시험·AVD 기동·유료 호출·충전·배포는 실행하지 않았다. 단계 체크박스는 모두 미실행이다. 기존 설계/기획/기술 검증 문서와 샘플 코드의 해시 보존을 확인하고, 이 계획의 문서 링크·표·코드 블록·요구 대응을 점검한다.

| 기준 | 대응 단계 | 완료 주장에 필요한 한계 표시 |
| --- | --- | --- |
| 백그라운드 구조를 작은 검증부터 확인 | P0 | Activity 비활성 증거 없는 시험은 미확정 |
| 복구/누락/시간·거리 의미 | P1·P2·P6 | 실제 주행시간/죽어 있는 동안 수집으로 과장 금지 |
| 차량 첫 생성·AI 실제 작곡 | P2~P5 | FIXTURE와 신규 실생성 구별, P5는 별도 유료 승인 |
| 비밀키·중복/비용·취소/삭제 | P3~P5 | 모든 유료 단계 attempt, 불명 비용과 회수 불능 구별 |
| 저장·재생·재감상 | P4·P5 | 서버 READY/기기 AVAILABLE/청취 품질 구별 |
| 전체 제품 기능·사용자 결정 보존 | P7·P8, 설계 U1~U3 | 첫 차량 흐름을 전체 출시/폰/앨범 완료로 부르지 않음 |

계획 완성은 구현 착수 허가나 단계 완료가 아니다. 다음 실행이 요청되면 승인된 범위의 P0부터 시작하고, 구현 승인과 추가 유료 호출 승인을 서로 대신 사용하지 않는다.

## 13. 계획 v0.2 리뷰와 보완

기준 설계는 계속 v0.2이며 변경하지 않았다. 이 기록은 계획의 의존성과 판정 방법을 보완한 자체 리뷰다. 실제 기능의 통과 결과는 아니다.

| ID | 발견한 누락/모순 | 보완·검증 위치 |
| --- | --- | --- |
| PR1 | pause만으로 백그라운드 오인 가능, 기존 runner가 다른 Activity를 실행 | P0: 모든 Activity 비가시+시스템 상태, 그 뒤 입력 주입·소스별 SDK 수신, 새 전용 runner |
| PR2 | 검증 APK 교체·고정 user/포트·원본/테스트 설정 보존 절차 부재 | 14절: 대상/Android user 재확인, 데이터 삭제 없는 설치 경로·복원·유한 종료 |
| PR3 | P2의 owner 포함 해시가 P4 로그인보다 먼저 필요 | P2 로컬 초안/P3 귀속 계약/P4 최종 snapshot 분리, 같은 owner 재전송·다른 계정 차단 사례 |
| PR4 | 구성안/가사와 음원 검사기가 P5에 있어 P4 저장 완료를 독립 구현 못함 | 구성안/템플릿은 P3, 실제 AudioValidator/저장소는 P4로 이동. P5는 실제 제공자 어댑터 확장 |
| PR5 | P6는 P1 뒤 시작인데 날짜/여행 검사에는 P2가 필요, 종료될 앱 안의 테스트만으로 복구 검증 불가 | P6 하위 범위별 선행 분리, P1/P6의 외부 전후 제어·두 차례 기기 검사 |
| PR6 | P4가 대역 포커스 검사만 해도 실제 내비/전화 공존 완료로 읽힐 여지 | P4/M2의 핵심 재생·host·실제 공존 판정 분리, 미검증은 유지하며 독립 P5 수행 가능 |
| PR7 | 구형 fixture의 모든 해석을 정답으로 복사할 여지, P3에서 아직 없는 앨범 기능을 검증하는 모순 | P2 사실/차분 출처 대조, P3 공개/버전 상태와 P7 앨범 참조 검사 구별 |
| PR8 | P5의 무과금 준비와 실제 유료 전송이 한 승인 의존성으로 섞임 | P5 어댑터/공식 조건/대역은 독립, 실제 전송만 P4 핵심 경로+U3에 종속 |

리뷰 뒤 의존성·현재 상태·검사 범위·파일 배치를 다시 대조하고 문서 링크/형식 및 기준 문서/기존 코드의 해시 보존을 확인한다. P0~P8의 미착수 상태와 현재 코드/유료 호출 금지를 유지한다.

## 14. P0 착수에 필요한 사항만

**현재 사용자 쪽에 남은 것은 P0를 시작하라는 명시적 지시다.** 이번 요청은 ‘아직 코드 구현을 시작하지 말라’고 했으므로 검증 코드도 작성/실행하지 않는다. 다음 P0 지시의 범위는 아래 작은 probe 추가·빌드/설치·전용 AVD의 합성 입력·증거 회수·정리까지이며 P1 제품 구현·유료 호출을 포함하지 않는다. 지금 추가 확인 질문을 요구하지 않는다.

| 준비 항목 | 착수 지시 뒤 실행자가 확인/준비할 내용 | P0를 막는 실제 조건 |
| --- | --- | --- |
| 대상과 환경 | JDK17·SDK/Gradle 인식, Pleos 2.1.0/software GPU, Vehicle2.3.2/FusedLocation1.1.3 확인. 전용 AVD fingerprint·ADB server/serial·실제 Android user·8088 전달 대상과 포트 충돌 확인 | 도구/이미지/대상 식별 불가. 과거 5038/5661/user10 값을 확인 없이 사용하지 않음 |
| 기존 증거 보존 | 기존 JourneyProbeActivity/runner/run 파일은 기준으로 보존. 앱 내부 기록을 보호하고 새 runId/파일명 사용. APK 갱신은 서명 호환/데이터 보존 경로 확인 | 데이터 삭제/앱 제거가 필요한 설치 실패는 조용히 우회하지 않고 격리 설치안을 먼저 검토. `pm clear`/재설치로 원본 삭제 금지 |
| 작은 변경 범위 | P0의 새 Kotlin 3파일·manifest와 `lifecycle.py`만 작업. 실제 필요가 드러난 빌드 설정 변경은 원인/차이를 기록. 제품 폴더·서버·DB·미디어·음악 probe는 만들거나 실행하지 않음 | 기존 runner를 새 서비스용이라고 오인하거나 다른 프로젝트/기기를 조작해야만 실행 가능한 상태 |
| 시작/관측 절차 | 보이는 화면의 사용자 시작 FGS 후보·권한 요청 선언, 전경→실제 비가시→새 B/C 입력→재진입/정지 순서를 정함. 후보 권한은 확인하되 백그라운드 성공을 착수 전제에 넣지 않음 | 권한 요청/시스템 상태 증거를 얻을 경로가 없음. 실제 권한/SDK 허용 여부 자체는 P0의 검증 결과 |
| 유한 종료·복원 | 실행 구간/timeout·명시 정지·비정상 종료 후 정리 경로를 정하고 시작 전 mock-location/AppOps/포트 상태 기록. 이번에 만든 테스트 공급자·연결만 제거하고 이전 상태로 복원 | 종료/정리 대상을 구별할 수 없음. 상주 에이전트/예약 작업을 만들어 해결하지 않음 |
| 증거와 판정 | 새 `validation/journey-probe/runs/lifecycle-<run-id>/`에 controls·SDK Journal·Activity/window·PID/boot·권한 상태·결과/한계를 보존하고 기술 검증 문서에 연결 | 화면 비가시/새 입력/SDK 수신을 연결할 수 없으면 성공 판정은 보류, 부족한 증거를 명시 |

실행기는 각 대기/실행 구간에 종료 조건을 두고 로그 조회·중단이 가능해야 한다. 작은 run의 구체 입력/관측 기간은 실행 전에 기록하며 제품의 지연·성공률 임계값으로 바꾸지 않는다. 기존 자동 권한 grant나 mock AppOps 설정은 시험 도구용이라는 사실을 남기고 제품의 사용자 권한 흐름 검증과 혼동하지 않는다.

P0에는 **폰 플랫폼·기록 자동 보관 기간·AI 제공자 최종 선정·API 키·크레딧/추가 결제·장르 기본값·장시간 시험 완료가 필요하지 않다.** 소유자 귀속·서버/미디어 의존성의 보완은 이후 단계용이며 P0에 구현하지 않는다. Windroid 중단·제출용 7번 공란·개인 메모리 미저장도 그대로 유지한다.
