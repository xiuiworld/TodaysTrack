# 오늘의 트랙 핵심 로직 검토

앱·Pleos·음악 생성 서버를 켜지 않고 저장 이벤트 경계부터 실제 Kotlin을 실행한다.
브라우저 JS는 결과 표시·차이 비교·JSON 편집만 한다. 추가 유료 호출은 0이다.

현재 사용자 지시는 **채팅과 로컬 산출물로만 검토**다. 사이트/브라우저/검토 HTTP 서버를 열지 않는다.
새 음악 핵심은 `docs/development/music-core-handoff.md`와 아래의 파일 전용 명령으로 검토한다.

```powershell
. ./tools/Use-SubmissionToolchain.ps1
./server/gradlew.bat -p server --offline --no-daemon :musicReview `
  '-PreviewInput=<cases.json 또는 보존 initial-report.json 절대 경로>' `
  '-PreviewOutput=<새 결과 JSON 절대 경로>'
```

이 명령은 실제 Kotlin 핵심을 실행하고 입력·제외·특징·구성안·가사 없는 전송 예정 요청·장르/메모/변주 비교를 파일로 쓴다. Ktor/모델 전송을 실행하지 않는다. 보존 report를 주면 그 파일의 기존 baseline 결과도 나란히 보존하며, 새 baseline 재실행이라고 주장하지 않는다.

## 실행

전용 `logic-review` worktree에서 Python 3·JDK 17과 기존 Gradle 캐시를 사용한다.

```powershell
python tools/logic-review/review.py deps
python tools/logic-review/review.py build --baseline-ref 117f219
```

`deps`만 Maven의 공개 `org.json:json:20240303`을 받는다. Maven SHA1을 대조하고 SHA256을 로컬에 고정한다.
그 뒤 Gradle은 `--offline --no-daemon`으로 실행된다. 캐시가 없으면 실패하며 앱 빌드/실행으로 대체하지 않는다.
`serve` 명령은 과거 검토 도구로만 보존한다. 현재 지시에서는 실행하지 않는다. 별도 사용자 요청 시에만 사용한다.

`build`는 기준 commit과 현재 코드를 각각 준비·컴파일하고 기존 차량 순수 로직 테스트 및 서버 테스트를 실행한다.
테스트 실패도 로그/XML과 함께 남기며 배포용 APK·영상을 만들지 않는다.
입력·출력·테스트·소스 SHA256·실행 로그는 `.local/logic-review/run-*`에 누적하고 기존 실행을 덮어쓰지 않는다.
이후 `--baseline-run <이전 run 절대 경로>`로 같은 commit의 이미 컴파일한 기준 실행기를 재사용할 수 있다.
현재 코드 변경 뒤에는 다시 build하고 새 run으로 serve한다. 화면의 재실행은 컴파일 당시 코드를 쓰므로 소스가 바뀌면 거절한다.

차량의 새 `integration/` 테스트는 Android/Room 클래스가 실제 앱 classpath에 있어야 하므로 `:app:testDebugUnitTest`에서 실행한다. 이 호스트 검토기는 기존 순수 capture/journey/generation 테스트와 서버 테스트 및 Android-free v2 조정 코드만 포함한다. 앱 테스트를 실행한 것으로 대신 표시하지 않는다.

## 입력과 기존 근거

`cases.json`은 `make_cases.py`로 만드는 합성 사례 8개다. 재현 조건의 출처와 제품 질문을 각 사례에 넣었다.
원본 SDK 증거는 Git에 추가하지 않는다. 보존된 standalone Room DB가 있을 때만 읽기 전용으로 사본을 추출한다.

```powershell
python tools/logic-review/make_cases.py --evidence-db <보존된-standalone.db> --output .local/logic-review/cases-with-sdk.json
python tools/logic-review/review.py build --input .local/logic-review/cases-with-sdk.json --baseline-run <기준-run>
```

DB는 `mode=ro&immutable=1`로 열고 전후 SHA256을 대조한다. WAL이 분리되지 않은 live DB에는 사용하지 않는다.
sealed session의 events/gaps/epoch 시각과 frozen 입력을 읽는다. 원본 frozen payload는 표시만 하며 갱신하지 않는다.
검토용 snapshot의 device ID는 `review-device`다. 보존 frozen 입력과 동일한 서명·inputHash라고 주장하지 않는다.

브라우저에서 사례의 JSON을 편집한 뒤 실행하면 두 JVM이 같은 입력을 받는다. 실패는 오류로 표시하고 원본 입력을 보존한다.
원본 `quality`는 저장된 값이고 `summaryQuality`는 실제 `toSummaryFact()` 투영이다.
`capturePolicyDiagnostic`은 정책 함수 호출 결과이며 서비스 전체의 SDK→이벤트→gap 생성 재현이 아니다.
거리 기여는 실제 요약기를 seq 접두 범위마다 실행한 누적값의 차이로 보인다. JS/Python으로 거리 해석을 복제하지 않는다.

## 실제로 실행하는 것과 경계

- **실제 Kotlin:** CapturePolicy, ObservationFacts, JourneySummaryBuilder, SourceCoverage, InputSnapshotBuilder,
  CanonicalSdkNumber, GenerationRecoveryPolicy, 서버 compositionRequest/validateSnapshot/JobStore 및 기존 단위 테스트.
- **호스트 변환:** CaptureDatabase 파일에서 데이터 클래스 선언을 추출하고 Room annotation/import만 제거한다.
  DevelopmentIdentity에서 기존 SHA256 본문만 추출한다. 실제 SDK·인증·Room 구현을 대역 성공으로 세지 않는다.
- **JSON 차이:** JVM의 공개 org.json 구현을 사용한다. Android JSONObject의 직렬화 순서/바이트 및 frozen 해시 동일성은 검증하지 않는다.
  호스트의 동일 입력·동일 JSON 라이브러리에서 변경 전후 의미와 전송 내용을 비교한다.
- **작업:** 화면용 합성 작업은 새 SQLite 메모리 DB에서 중복 intent·다른 입력 거절·불명 접수 재송신 차단만 실행한다.
  기존 서버 테스트는 임시 SQLite 파일 복구·인증 거절도 검사한다. 기존 제품 DB·예산 원장·실제 음원을 열거나 바꾸지 않는다.
- **미실행:** SDK 콜백/서비스 수명, Room/WorkManager, 실제 HTTPS/Keystore, 음악 API·음원 검증, 다운로드·Media3/첫 클릭 재생.
  MainActivity·ui/·테마·화면 자산·제품 API 계약은 변경하지 않는다.

화면은 로컬에서만 열고 외부 리소스를 로드하지 않는다. 서버는 정해진 파일만 제공하고 프로젝트 디렉터리를 공개하지 않는다.
POST는 loopback Host/Origin과 크기·사례 수를 제한한다. 명령/경로/URL은 입력에서 받지 않는다.
