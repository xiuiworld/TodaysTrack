# 차량 앱의 음악 핵심 연결

실행 지시: “그럼 그것도 너가 해야지”, “goal로 하렴”. 최신 보정: **스마트폰 앱은 만들지 않는다.**

목표는 기존 차량용 Pleos 앱의 생성 동작을 새 music-core/v2 경계로 연결하고 상태·복구·저장·기존 재생까지 이어주는 것이다. 음악 해석 정책을 청취 전에 추가 튜닝하지 않는다. 화면 디자인·ui/·테마·자산·스마트폰 앱은 범위 밖이다. MainActivity가 필요하면 기존 화면 구조를 유지한 바인딩/상태 설명만 수정한다.

고정 제한: logic-review worktree, main 직접 수정 없음. 추가 유료 호출0, 별도 사용자 요청 전 앱/Pleos/음악 서버 실행 없음, 브라우저 열지 않음. 기존 frozen 입력·작업·SDK 원본·곡·비용 원장 및 보호 문서/7번 공란 보존. 자동 push/배포/업로드·Guard/훅/상주 에이전트/추가 에이전트/개인 메모리 저장/Windroid 재개 없음.

연결 전 앱은 v1 outbox만 사용했다. 새 frozen 입력은 v2, 이미 존재하는 frozen 입력은 기존 v1 경로를 유지한다. v2의 서버 입력 해시는 로컬 동결 해시와 의미가 다르므로 별도 수신 기록에 보관한다. Room은 기존 행을 재작성하지 않는 추가 테이블 migration으로 확장한다. 실제 Android 수명/SDK/HTTPS/재생은 앱 실행 제한 때문에 로컬 검증과 구분해 남긴다.

## 반영한 연결

- 차량 Gradle이 실제 `music-core` 모듈을 사용한다. Android-free v2 조정 코드를 실제 앱과 JVM 계약 테스트가 함께 컴파일한다. Gradle 저장소는 각 settings의 중앙 선언을 사용한다.
- 기존 생성 버튼은 처음 동결하는 마감 기록에 `VehicleMusicProjection → VehicleMusicInput → v2 outbox`를 사용한다. 현재 수집하는 LOCATION/ODOMETER와 epoch/gap을 넘기며 좌표는 차량 안에서 특징으로 바꾼다. 추가 SDK 소스를 수신했다고 표시하지 않는다.
- SDK 원문 오류/알 수 없는 품질 문자열은 차량 원본에 보존하고, 전송에는 표준 코드만 사용한다. confidence를 미터 정확도로 해석하지 않는다.
- 기존 v1 frozen 입력은 다시 만들지 않는다. 이미 있는 intent를 그대로 반환하고 v1 작업/다운로드 경로로 복구한다. 기존 `InputSnapshotBuilder`, `GenerationRecoveryPolicy`, 원본 이벤트·기존 곡은 재작성하지 않는다.
- 새 v2 서버 입력 ID/hash, 접수 불명 여부, 서버 상태 revision, 구성안, 반환 가사·구조·검사 결과를 추가 Room 테이블 `music_v2_receipts`에 보관한다. 로컬 frozen 해시와 서버 정규화 입력 해시는 별개다.
- 서버가 `dispatchAllowed=false`면 입력/구성안만 보관하고 **작업 접수 POST를 보내지 않는다**. 생성 접수 직전에 불명 상태를 내구 저장하고, 응답 유실 뒤에는 원래 intent/job 조회만 한다. 새로운 작업을 만들지 않는다.
- 입력/구성안 해시·원격 job ID·revision·음원 경로/hash/bytes·LIVE/LOCAL_TEST 출처를 확인한다. 결과가 다른 입력에 속하면 다운로드 전에 보류한다.
- 새 음원은 `files/tracks/v2/<jobId>.mp3`에 저장하고 기존 Media3 보관함에 연결한다. 파일과 메타데이터 검사 후 트랙/AVAILABLE을 Room 트랜잭션으로 함께 확정한다. 중단 다운로드는 같은 작업의 같은 자산으로 재개한다.
- 서버 job 조회에 실제 저장된 구성안/hash를 추가했다. 차량에는 반환 가사와 구조를 별도로 저장한다. LOCAL_TEST를 실제 AI 생성으로 표시하지 않는다.

## 화면 경계

`ui/`, 테마, 자산, Manifest는 수정하지 않았다. MainActivity의 기존 화면 구조 안에서 생성 방향 미리보기·상태/가사 설명·설정의 장르 선택지·대역 표시만 연결했다. 기존 기본 장르/기록 설정과 운전 중 UX 제한을 유지한다. 비동기 설명을 읽는 사이 기록 선택이 바뀌면 이전 결과를 표시하지 않는다.

이번 goal은 **기존 차량 수집·생성·보관함 흐름을 새 엔진에 연결하는 범위**다. 파일 선택/분석 동의 화면, 새 버전 선택 화면, 앨범/동기화 화면을 추가하지 않았다. 공통/서버의 수동 재료·변주 계약은 유지하며 후속 UI가 연결할 수 있도록 freeze 입력에 취향/메모/등록 미디어 참조 인자를 둔다. 스마트폰 앱은 만들지 않는다.

현재 화면의 한 기록/한 생성 intent 동작과 기존 unique 제약을 유지한다. 기존 곡을 덮어쓰거나 생성 버튼을 몰래 새 버전/추가 과금 동작으로 바꾸지 않는다.

## 검증과 한계

- `red-flow.log`: 입력 투영·승인 차단·접수 불명 연결 미구현 실패 확인. `red-recovery-migration.log`: 입력 투영 통과 후 작업 복구·추가 테이블 부재 실패 확인. XML은 `red/`에 보존한다.
- `green-flow1.log`: 실제 Android-free 차량 코드와 실제 MusicService/SQLite를 연결한 5개 검증 통과. 외부 모델/HTTP는 대역이다.
- `server-regression.log`: 기존 서버/핵심 검증과 차량 계약을 함께 확인했다. 접수 응답 유실+중단 다운로드 뒤 동일 작업 회수, 잘못된 입력 결과 거절이 포함된다. 실제 SDK·서명 네트워크·MP3 가창/재생의 성공 증거가 아니다.
- `android-build1.log`: 차량 단위 테스트31개 및 debug 빌드 성공. 추가 검사 `android-final.log`는 코드 오류가 아니라 offline lint용 공개 Android 라이브러리 2개 캐시 누락으로 실패했다. 공개 의존성을 받은 `android-final2.log`, 이후 `android-verified.log`에서 빌드/단위 테스트/lint 성공을 확인했다.
- 최종 SDK 원문 개인정보 경계 테스트의 실패를 `red-sdk-privacy.log`에 재현하고 표준 코드 투영으로 보완했다. 최종 증거는 `android-privacy-verified.log`와 `verification.json`이다.
- 보존 SDK DB의 **새 사본**에 실제 migration SQL을 적용했다. sessions2/epochs7/events129/gaps13/intents1/tracks1의 기존 행 해시가 모두 유지됐다. 새 테이블의 PRAGMA schema가 실제 Room kapt 생성 DDL과 일치했다. 원본 DB SHA256도 그대로다. `migration-verification.json`에 사본 경로/해시/한계를 기록했다.
- 이 migration 검증은 호스트 SQLite의 실제 SQL 검증이다. Android Room 실행/identity 갱신/기기 업그레이드를 실행한 것으로 표시하지 않는다.

최종 자체 검토: 차량 **32개**, 서버 **44개**, 공통 핵심 **18개**, 합계 **94개** 실패/오류/건너뜀0. 공통 알고리즘의 기존 유효한 통과 증거는 Gradle up-to-date 판정과 함께 재사용했다. 최종 debug 빌드 성공, lint 오류0/경고19. SDK XML 버전 차이·kapt의1.9 fallback·기존 화면 API deprecation 경고는 성공과 별도로 보존한다. 보호된 문서·ui/·res/·Manifest의 diff 없음과 `git diff --check`를 확인했다. `verification.json`은 실제 XML 집계, 소스 SHA256, 설치하지 않은 debug APK SHA256, 원본 SDK 보존을 기록한다.

**이 goal의 차량 기존 흐름 → 새 음악 핵심 연결 소스와 로컬 검증을 완료했다.** 스마트폰·새 미디어 선택 UI·앨범/동기화·실제 기기 통합 완료를 포함하지 않는다. 음악 해석 규칙은 실제 결과를 듣기 전에 추가 튜닝하지 않았다. 코어 단계의 과거 “Android 미수정” 기록은 그 단계의 이력이고, 이번 차량 바인딩은 사용자 후속 지시로 별도 진행한 것이다.

앱·Pleos·음악 서버를 설치/실행하지 않았고 추가 유료 호출0이다. 새 v2의 실제 HTTPS·SDK 수명·차량 저장 후 Media3 첫 클릭/포커스·외부 AI 분석/음악/가창·청취는 미검증이다. 특히 기존 0.3의 첫 클릭 보류 이력을 이번 컴파일로 해결했다고 주장하지 않는다. 설치된 기존 앱/제출 APK도 교체하지 않았다.

인수 근거: [Room의 수동 migration](https://developer.android.com/training/data-storage/room/migrating-db-versions), [Gradle 저장소 중앙 관리](https://docs.gradle.org/current/userguide/centralizing_repositories.html). 프로젝트의 고정된 Room2.6.1/Gradle8.10.2 API와 실제 생성 schema로 대조했으며 문서 확인을 런타임 통합 증거로 쓰지 않는다.
