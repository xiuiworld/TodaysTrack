# 음악 핵심 v2 · Android 연동 계약

후속 사용자 지시에 따라 **차량용 앱의 실제 소스 연결**을 진행했다. 최신 범위/증거는 [vehicle-music-integration.md](vehicle-music-integration.md)에 있다. 스마트폰 앱은 만들지 않는다. 아래의 폰 항목은 기존 서버 계약을 보존한 것이다.

이 문서는 사용자가 승인한 음악 핵심 구현의 인수 문서다. 기존 기획서 두 개·설계 v0.2·후속 계획·7번 공란을 대체하지 않는다. Android 앱·MainActivity·UI·권한·수집 서비스·재생·앨범/동기화는 이 변경에 포함하지 않는다.

## 실제 입력의 확인 수준

| 입력 | 현재 확인한 근거 | 앱 담당자가 추가로 확인할 것 | 핵심 엔진 처리 |
|---|---|---|---|
| 위치·시간·누적거리 | 보존된 Pleos SDK 수신 기록. 이번에는 원본 DB를 변경하지 않고 이전 추출 입력을 재사용 | 실기기 권한/수명/재개/단위/boot와 epoch. 현재 재수신 성공으로 간주하지 않음 | 신선도·중복·역순·유효 좌표·단위·같은 boot/epoch 단조 시각 검사 |
| 진행 방향·고도 | 설치된 Fused Location 1.1.3 `LocationData` 필드 `heading`, `altitude`. `confidence`는 미터 정확도가 아님 | 필드별 실제 수신·단위·유효 범위·결측/해제 동작 | 별도 `HEADING`, `ALTITUDE` 관측. 실제 확인된 소스만 `receiptVerified=true` |
| 속도 | Fused Location의 위 필드 목록에 속도 없음. 크루즈 목표 속도는 실제 속도가 아님 | 실제 차량 속도 API/VHAL 지원·권한·수신·단위 확인 | `SPEED`, `m/s`만. 미지원이면 계산하지 않음 |
| 조향 | 설치된 Vehicle 2.3.2 `getCurrentSteeringWheelAngle` 확인 | CAR_DRIVE 권한, 지원 조회, 실제 각도·단위·구독 해제 | `STEERING`, `degrees`. 진행 방향과 분리 |
| 브레이크·주행 모드 | `getCurrentBrakePedalStatus`, `getCurrentDrivingModeState` 시그니처 확인 | 권한/지원/실제 값·상태 enum·해제 | 소스별 상태 순서와 반복 관측 범위. 공격성·감정 추정 없음 |
| 와이퍼·등화 | Wiper 및 Light getter 존재 확인 | 각 필드의 지원/수신/해제. 필드 존재는 통합 증거가 아님 | 실제 상태만. 비·밤·날씨로 치환하지 않음 |
| 기어 | Pleos Gear getter 및 deprecated 표시 확인 | 권장 Google `GEAR_SELECTION` 경로와 권한·실제 값 검증 | 실제 검증된 `GEAR` 상태만 |
| 목적지·경유지·날씨·지역 | 현재 수신 통합 증거 없음 | 사용자 확인 또는 실제 제공자, 원본/수신 시각과 확인 수준 제공 | `ProvidedContext`. 좌표에서 장소·날씨를 채우지 않음 |
| 수동 메모·음성·사진·영상·환경음·흥얼거림 | 순수 Kotlin 계약, 파일 검사와 로컬 합성 디코딩. 분석 요청/응답은 대역 검증 | 수동 선택·권한·기기 파일 해시/길이·동의·취소·보관 UI | 선택된 파일/구간만. 자동 녹음·촬영·주기적 마이크 접근 없음 |

설치 SDK 시그니처 조사: `.local/input-feasibility/api-summary.json` 및 관련 추출 파일. 기존 trip time/distance의 NOT_SUPPORTED 기록은 해소된 것으로 취급하지 않는다. 수집 담당자는 `AVAILABLE / UNSUPPORTED / PERMISSION_DENIED / TEMPORARILY_UNAVAILABLE / UNVERIFIED`와 필드별 실제 수신 근거를 전달해야 한다.

## 공통 모듈

`music-core`는 Android 의존성이 없는 JVM 17/Kotlin 모듈이다. `Contracts.kt`, `ContractJson.kt`가 DTO와 JSON 경계다. `FeatureExtractor`가 기존 순수 Kotlin 요약기를 재사용하고, `CompositionPlanner`, `MusicPromptBuilder`가 구성안과 음악 지시를 만든다. JS로 핵심 계산을 복제하지 않는다.

`prepareLegacy`는 `JourneySummaryBuilder.kt`·`SourceCoverage.kt`를 읽어 **빌드 출력에만 package를 바꿔** 컴파일한다. 원본 Android 파일은 수정하지 않는다. 앱 담당자는 모듈 의존성과 Kotlin 플러그인 연결을 추가하고, 향후 요약기 변경 시 공통 회귀 테스트를 함께 실행한다.

관측은 `seq, source, epochId, bootId, receivedElapsedMs, receivedAtUtcMs, quality`를 갖는다. 위치에는 원본 시각/기준과 좌표, 거리/속도/각도에는 값과 명시적 단위가 필요하다. 선택적으로 요청 시작 단조 시각과 신뢰할 수 있는 `epochStartedUtcMs`를 전달한다. 후자가 있으면 PRE_EPOCH도 다시 검사한다. 없는 경우 기존 capture quality의 PRE_EPOCH 제외는 유지하지만 epoch 시작 시각을 추정하지 않는다. source timestamp와 수신 UTC의 비교가 성립하지 않는 경우에는 FRESH로 승격하지 않는다.

정밀 좌표는 차량에서 `ObservationBatch → JourneyFeatures`로 바꾼다. HTTP에는 `featuresJson(features)`만 전달한다. `confidence`를 위치 정확도나 필터 반경으로 사용하지 않는다. 관측 위치 사이 직선 변위는 도로 길이가 아니다.

- 거리: `continuousDistanceKm`, `gapSpanningDistanceKm`, `unallocatedBoundaryDistanceKm`를 분리한다. 공백 양끝 차이는 규모 정보이며 경로·운전 시간·악절 거리로 배분하지 않는다.
- 위치: 정확히 같은 좌표의 반복과 연속적인 공간 지표를 보존한다. GPS 흔들림을 임의 반경으로 묶어 정차·방문·귀환으로 확정하지 않는다.
- 시간: 관측 범위만 쓴다. 같은 boot/epoch의 단조 시각이 성립하는 구간만 비교한다. 위치와 거리 응답을 임의로 짝짓지 않는다.
- 흐름: 누적거리 소스의 관측 구간을 전·중·후반으로 비교하되 경계를 가로지르는 희소 차분은 `distanceUnallocatedPhaseFraction`에 남긴다. 실제 이동이 그 시점에 일어났다고 보간하지 않는다.
- 선택 여정: `FeatureSelection.combine(ordered coverageRef → features)`로 **원본 소스별 특징**을 결합한다. `coverageRef=deviceId/sessionId`가 근거와 지원 상태에 붙는다. 서로 다른 세션의 동일 seq·A/B 라벨을 같은 장소로 합치지 않고, 구간 사이 경로·귀환·시간은 알 수 없다고 남긴다. 중첩 앨범을 다시 해석하지 말고 원본 소스를 넘긴다.
- 이전 기록 비교: `SpatialComparison.compare`는 선택한 관측점 집합 간 최근접 거리와 정확 일치 비중만 제공한다. 참조 집합이 없으면 계산하지 않는다. 최대 비교 창을 넘으면 명시적으로 분할해야 하며 원본을 자동 삭제/축약하지 않는다.

앨범 선택·보관·월간 범위 결정·동기화 자체는 앱 담당 범위다. 입력 유형이 여행/월간이라는 이유로 핵심 엔진이 미관측 경로를 채우지는 않는다.

## 음악이 달라지는 근거

여정은 전개, 선택 재료는 이야기, 사용자 취향은 사운드에 영향을 준다. 고정 출발→고조→귀환 틀과 고정 가사를 새 요청에서 제거했다.

구성안에는 장르·편성·보컬 취향·주제의 출처·악절 순서/관측 비중·반복/변화/회상 지시·사용/제외한 소재·근거가 있다. `controls`는 관측 반복 비중, 방향 변화, 직접 속도 분포, 시작/끝 근접도 등의 **음악적 해석 값**이다. 감정 측정치가 아니다. AUTO는 허용 장르 안에서 최근 장르/편성 반복을 피한다. 고정 선택은 기존 장르 선택을 존중한다.

같은 frozen 입력·변주 식별자·버전으로 구성안과 요청을 재현한다. 의도적인 새 곡은 새 `variationId`와 입력/intent를 사용한다. 음악 AI 자체의 음원 바이트까지 결정적으로 같다고 약속하지 않는다. 길이는 생성 지시이며 실제 길이/가창/구성 준수는 별도 검증이다.

`VOCAL`은 음악 AI가 자연스러운 지정 언어 가사와 곡을 함께 작성하도록 한다. `INSTRUMENTAL`은 보컬·발화·허밍·보컬 샘플 금지다. 운전/도로 이야기로 주제를 제한하지 않고, 메모가 없으면 창작 주제를 명시한다. USER / PROVIDED_CONTEXT / MEDIA_INFERENCE / CREATIVE를 구분한다.

## 미디어와 개인정보

등록 DTO는 `MediaSelection(kind, sha256, mimeType, bytes, durationMs?, fromMs?, toMs?, consent)`다. `consent`는 revision·사용자 선택·외부 분석 허용·가사 노출 허용·사진 직접 음악 입력·음성 포함·철회 상태를 분리한다.

지원 포맷은 사진 JPEG/PNG, 영상 MP4, 수동 음성/환경음/흥얼거림 WAV/MP3/M4A다. 다른 포맷은 명시적으로 변환 안내를 해야 한다. 파일 이름·로컬 경로·원격 URL은 API로 받지 않는다. 원본 해시/크기를 검사하고 실제 선택 내용의 디코딩을 검사한다.

- 사진: 최대 16MiB/20M 픽셀. 재인코딩하며 EXIF/GPS를 복사하지 않고 긴 변을 최대 2048px로 만든다.
- 음성/영상: 원본 최대 64MiB, 선택 구간 최대 10분. 실제 선택 구간의 디코딩 길이를 확인한다. 원본 전체의 모든 구간이 검증됐다는 의미는 아니다.
- 분석용 음성 파생본: 24kHz/mono/96kbps MP3. 영상: 최대 640×360, 2fps. 영상 음성은 `includeSpeech=true`일 때만 포함한다. 원본은 변환/삭제하지 않는다.
- 서버 네이티브 디코딩의 일시적인 평문 파일은 서버 전용 scratch에서 소유자 접근만 허용하고 finally에서 지운다. 정상 시작 시 알려진 잔여 scratch만 정리한다. 내구 저장한 선택 파생본과 복구 응답은 AES-256-GCM으로 암호화한다. 키는 서버의 보호된 환경에서 공급하며 앱/로그/채팅에 넣지 않는다.
- 분석 후 직접 음악 이미지로 쓰지 않을 파생본을 정리한다. 직접 사진도 음악 처리 후 정리한다. 재생성에서 직접 사진이 다시 필요하면 동일 원본을 재업로드하고 해시를 대조한다. 파생본이 달라졌으면 새 frozen 버전을 만들어야 한다.
- 분석 캐시는 계정·원본/파생 내용·선택 구간/동의 revision·분석/전처리 버전을 분리한다. 철회는 이전 입력과 실행 중 결과의 유효성을 막는다. 완료한 곡 삭제는 별도 입력 삭제 동작이다.
- 외부 요청은 `store=false`이며 Files API 원격 파일을 만들지 않는다. 반환 interaction ID가 있으면 삭제 API를 요청하고 상태를 기록한다. 삭제 API 확인과 제공자의 모든 보관/백업 즉시 삭제는 같은 의미가 아니다.
- 파일 속 말·자막·문장·URL은 데이터다. 외부 링크/명령/도구를 실행하지 않는다. 얼굴·목소리로 신원·관계·감정을 추정하라고 요청하지 않는다. 흥얼거림은 특징 설명만 사용하며 같은 선율/목소리 복제/원음 삽입을 약속하지 않는다.

미디어 실패/불명/불충분 결과는 조용히 제외하지 않는다. `MATERIAL_REVIEW_REQUIRED` 또는 `RECONCILING`으로 반환한다. 앱이 `/materials`로 분석 결과/불확실성을 보여주고, 사용자가 제외를 선택하면 `excludedMediaIds`를 포함한 새 입력을 만든다.

## API와 인증

기존 `/v1` frozen 요청·원장·음원 경로는 보존한다. 새 경계는 `/v2/music`다. 이번 작업에서 HTTPS/앱을 실행하지 않았다.

인증 헤더는 `X-Device-Id, X-Timestamp, X-Nonce, X-Signature`. 서버에 등록된 EC 공개키로 다음 UTF-8 문자열의 SHA256withECDSA 서명을 확인한다.

```text
METHOD\nPATH\nDEVICE_ID\nTIMESTAMP_MS\nNONCE\nSHA256(EXACT_BODY_BYTES)
```

서버 시각 ±120초, nonce 일회성. 쿼리 파라미터를 허용하지 않는다. GET/DELETE의 본문은 비어 있어야 한다. owner/role을 요청 JSON이나 임의 헤더에서 받지 않는다. `enroll-account-device`는 **검증된 계정/기기를 연결하는 신뢰된 운영자 경로**이며 공개 회원가입 API가 아니다. 계정 로그인·기기 키 발급·Android Keystore 연동은 앱/계정 시스템에서 실제로 연결하고 검증해야 한다. 기존 `enroll`은 기존 CAR 호환용이다. 폰은 v1 인증으로 승격되지 않는다.

| 메서드·경로 | 본문/결과 |
|---|---|
| GET `/capabilities` | 버전·모델·지원 포맷/한도·현재 전송 허용 상태 |
| POST `/media` | `MediaSelection` → `mediaId,state,revision,cleanupState` |
| PUT `/media/{id}/content` | 등록한 정확한 파일 바이트. 최대 64MiB, 본문 전체 서명 |
| GET / DELETE `/media/{id}` | 상태 / 동의 철회·서버 파생본 정리 |
| POST `/inputs` | `intentId,operation,parentInputId?,coverage?,features?,preferences,materials,mediaIds,excludedMediaIds?,context?` |
| POST `/inputs/import-legacy` | `snapshotId,intentId`. 같은 계정의 기존 LIVE/READY 범위만 읽어서 참조. 원본 해시/가사/음원/원장 불변 |
| GET `/inputs/{id}` | frozen 입력·inputHash |
| GET `/inputs/{id}/materials` | 분석 소재·출처·불확실성·대기/제외 상태. 구성안의 길이 한도를 넘어도 소재 검토 가능 |
| GET `/inputs/{id}/plan` | feature/plan/request hash·구성안·텍스트 요청·직접 사진 manifest. 원본 이미지 base64는 전문 추적에 저장하지 않음 |
| DELETE `/inputs/{id}` | 새 v2 입력/곡의 삭제 tombstone·겹치는 source 범위 재생성 차단. 기존 v1 원본은 삭제/재작성하지 않음 |
| POST `/quotes` | `inputId,analysisOnly?` → 견적·최대 요청 수·최대 비용·승인 상태·5분 유효기간 |
| POST `/jobs` | `inputId,quoteId,intentId` → 작업 상태 |
| GET `/jobs/by-intent/{intent}` | 응답 유실 후 원래 작업 회수 |
| GET `/jobs/{id}` | 상태/revision/attempt/비용 상한/제공자 정리 상태·성공한 반환 가사/구조/음원 참조 |
| POST `/jobs/{id}/cancel` | `{}`. 미송신 예약만 해제. 이미 접수됐을 수 있는 비용은 보수적으로 보존 |
| POST `/jobs/{id}/resume-local-result` | `{}`. 내구 저장된 응답만 로컬 재검증. 저장 응답이 없으면 재송신하지 않음 |
| GET `/jobs/{id}/audio` | 같은 계정의 READY만. 해시/길이 확인 후 private/no-store MP3 |

단일 소스 coverage는 `{deviceId,sessionId,fromSeq,toSeqExclusive}` 배열이다. 복수 소스의 근거에는 해당 `coverageRef`가 필요하다. CAR 최초 생성의 coverage device는 서명한 CAR와 같아야 한다. PHONE은 `operation=REGENERATE`와 같은 계정 parent만 주며 features/coverage를 새로 보내지 않는다. 그 범위의 **실제 LIVE/READY**가 없으면 거절한다. 테스트용 성공은 production 자격이 아니다.

예시 CAR 본문:

```json
{
  "intentId": "trip-original",
  "operation": "INITIAL",
  "coverage": [{"deviceId":"registered-car","sessionId":"trip-session","fromSeq":1,"toSeqExclusive":10}],
  "features": "이 위치에는 featuresJson(actualFeatures)의 JSON 객체를 넣는다",
  "preferences": {"genre":"FUNK","lyricMode":"VOCAL","language":"ko","durationSeconds":90,"variationId":"original","vocalStyle":["편안한 중음 보컬"]},
  "materials": [{"id":"memo-1","origin":"USER","text":"미뤄둔 일을 끝낸 날을 기억하고 싶다","allowedInLyrics":true}],
  "mediaIds": [],
  "excludedMediaIds": []
}
```

위 `features` 문자열은 문서 표시용이다. 객체가 포함된 실제 합성 예시는 `examples/music-v2-initial-input.json`, 폰 예시는 `examples/music-v2-phone-regeneration.json`에 있다. 등록 기기와 parent ID는 실제 승인된 값으로 바꾸고 자신의 소스 범위를 대조한다. 입력에 임의 좌표 필드를 추가하면 거절한다.

폰 본문은 `operation=REGENERATE,parentInputId=<성공 범위의 v2 inputId>`와 새 취향/소재/`variationId`를 보낸다. `features,coverage`를 제외한다. `materials`는 USER 메모만 직접 제출할 수 있고, 외부 맥락은 출처/원본·수신 시각/확인 수준이 있는 `context`로 전달한다. 미디어 추정의 출처는 서버가 선택 미디어 종류로 정한다.

오류는 JSON `{error,automaticResubmission:false}`. 인증 실패 401, 없는/다른 계정 객체 404, 충돌/보류 409, 형식/단위 오류 400, 한도 초과 본문 413, 보호 키/디코더 미설정 503이다. 주요 이유: `CAR_FIRST_GENERATION_REQUIRED`, `FIRST_SUCCESS_REQUIRED`, `IDEMPOTENCY_CONFLICT`, `MEDIA_CONSENT_CHANGED`, `MEDIA_DERIVATIVE_CHANGED_CREATE_NEW_VERSION`, `MATERIAL_REVIEW_REQUIRED`, `MUSIC_CONTEXT_LIMIT_SELECT_MATERIALS`, `PAID_EXECUTION_DISABLED`. 통신 실패 후 새 intent로 무작정 다시 보내지 말고 원래 intent 조회를 먼저 한다.

## 작업·비용·버전

`QUEUED → SUBMITTING(ANALYSIS) → PREPARING`을 소재별로 진행한다. 분석 전용 작업은 `PLAN_READY`에서 끝난다. 이후 구성안 검토 뒤 음악 견적/작업을 만들면 캐시 분석을 재사용한다. 음악은 `SUBMITTING(MUSIC) → PREPARING → VALIDATING → READY`다.

응답이 불명확하면 `RECONCILING`, 소재 판단 필요 시 `MATERIAL_REVIEW_REQUIRED`, 비용/승인 문제는 `BILLING_HELD`, 파일 검증 실패는 `FAILED_VALIDATION`이다. 새 intent로 동일 입력을 보내도 기존 작업을 돌려주고, 계정별 분석/음악 송신 fence가 접수 불명 재송신을 막는다. 확실히 저장된 응답은 승인 만료 후에도 로컬에서 복구할 수 있다.

입력/특징/구성안/요청은 버전과 해시가 있다. 직접 사진이 포함된 실제 요청 해시는 텍스트 요청 해시와 분리된다. 로그/원장에는 새 원본 미디어와 base64 요청 전문을 복제하지 않는다. 반환 가사·구조·음원과 원래 반환 텍스트를 분리 보존한다. MP3 디코딩 성공과 가창/구성 준수/청취 평가는 별도 필드다.

**이번 승인에서 유료 송신은 0이다.** v2는 legacy `dispatch.json`으로 켜지지 않는다. 별도 보호된 `music-v2-authorization.json`의 실제 사용자 승인 문구·모델별 상한·요청 수·총액·만료가 필요하며 현재 파일을 생성하지 않았다. 각 유료 송신 전에 attempt/요청 해시/상한을 WAL+FULL 트랜잭션으로 저장한다. 테스트 정책은 실제 전송 어댑터가 키 조회 전에 거절한다.

가격 스냅샷은 `google-standard-2026-11-14`. 분석은 모델 최대 입력/출력 전체 기준의 보수적 상한(실제 청구 예상치가 아님), 음악은 곡당 기준이다. 기존 전역 $5 원장 상한도 함께 적용한다. 상한이 과도해지면 조용히 소재를 누락하지 않고 선택 조정을 요구한다. 가격 스냅샷 유효기간을 넘는 승인은 거절하며 재조사해야 한다. 미관측 실제 청구/미사용 예약을 임의 환불 처리하지 않는다.

## 검증 경계와 인수 항목

이번 증거는 Kotlin/JVM·임시 SQLite·대역 모델·로컬 합성 파일 디코딩이다. 실제 Android/SDK 신규 수신, 계정 로그인/Keystore/권한 화면, HTTPS 왕복, 외부 미디어 분석, Lyria 생성/가창, 저장 후 차량/폰 재생·첫 클릭, 앨범/동기화 통합은 확인하지 않았다. 기존 유예/한계도 그대로다.

앱 담당자는 (1) 필드별 수신 확인표, (2) 원본 동의/선택 구간과 해시, (3) 계약 JSON/서명·취소/재개·원래 intent 조회, (4) 실제 CAR 첫 생성 및 PHONE 재생성 범위, (5) MP3 다운로드·해시·재생·첫 클릭을 실제 승인 환경에서 연결한다. 핵심 테스트 통과가 앱/전체 제품 완료를 대신하지 않는다.

공식 근거 확인(2026-11-14): [Gemini 3.8 Flash](https://ai.google.dev/gemini-api/docs/models/gemini-3.8-flash), [Lyria 3.5](https://ai.google.dev/gemini-api/docs/models/lyria-3.5), [음악 입력/반환](https://ai.google.dev/gemini-api/docs/music-generation), [구조화 분석](https://ai.google.dev/gemini-api/docs/structured-output), [오디오 입력](https://ai.google.dev/gemini-api/docs/audio), [영상 입력](https://ai.google.dev/gemini-api/docs/video-understanding), [Interactions 보관/삭제](https://ai.google.dev/gemini-api/docs/interactions), [가격](https://ai.google.dev/gemini-api/docs/pricing). 모델 문서 확인은 유료 통합 호출의 성공 증거가 아니다.
