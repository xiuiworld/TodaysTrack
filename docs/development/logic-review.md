# 핵심 로직 검토 시작 — 2026-11-14

기준: `117f219`의 `0.3.0-submission(4)`. 브랜치 `logic-review`, 전용 `.local/worktrees/logic-review`.
목표는 사용자가 같은 입력의 중간·최종 출력을 보고 제품 로직을 판단하는 것이다. 과거 제출 완료/테스트 통과는 제품 판단의 확정 근거가 아니다.
기획서 두 개·설계 v0.2·7번 공란·후속 계획·기존 검증 한계는 그대로 보존한다.

## 현재 코드의 실제 흐름

| 단계 | 실제 코드 | 결과와 확인 경계 |
| --- | --- | --- |
| SDK 입력 | `capture/PleosCaptureAdapter.kt`, `JourneyCaptureService.kt` | 위치/누적거리 비동기 수신, UTC/단조 시각·boot/epoch 보존. SDK·서비스는 이번에 실행하지 않음 |
| 관측·공백 | `CapturePolicy.kt`, `storage/CaptureRepository.kt` | 위치의 FRESH/STALE/중복/역순, 거리 VALID/RESET, 소스별 gap 저장. 신규 요약의 지연 위치 재투영은 `journey/ObservationFacts.kt` |
| 여정 요약 | `journey/JourneySummaryBuilder.kt` | seq 순서의 위치 라벨·연속성 구간·미확정 경계, 정확히 같은 좌표 반복 관측 범위, 연속 누적거리 차분. 감정/장소/방문을 측정하지 않음 |
| 고정 입력 | `generation/InputSnapshotBuilder.kt`, `GenerationIntentOutbox.kt` | SEALED 기록→좌표 없는 summary-v2·coverage/raw hash·설정. 기존 session의 intent가 있으면 그대로 재사용 |
| 음악 요청 | 서버 `GenerationWorker.kt`의 `compositionRequest` | 구간 순서·반복 관측·거리·귀환 조건→음악 지시문과 템플릿 가사. 별도 orderedSections/relativeWeights 구성안 모델은 아직 없음 |
| 작업 | 차량 `GenerationRecoveryPolicy`/`GenerationOutboxWorker`, 서버 `Main.kt`/`JobStore.kt` | 등록 차량 인증·snapshot/quote/intent·비용 예약, SUBMITTING durable 저장, 불명 접수는 조회/RECONCILING. 새 POST 자동 재발행 금지 |
| 저장·재생 | `GenerationApiClient.download`, `GenerationOutboxWorker`, `playback/TrackLibraryService.kt` | 같은 자산 다운로드→해시/크기→Android metadata→Track/AVAILABLE→Media3. 서버 READY는 차량 저장·첫 클릭 재생 성공과 다름 |

파일 경로는 별도 표기가 없으면 `vehicle/app/src/main/java/dev/todaystrack/vehicle/` 아래다.

## 검토 우선순위

| 분류 | 입력·현상 | 처리 |
| --- | --- | --- |
| 구현 결함 / 이번 수정 | 같은 boot/epoch, 계기 100→108→109km, 수신 단조 2000→1000→3000ms. 기존 연속거리 9km | 역행/음수 시각의 쌍은 제외하고 이후 유효 108→109만 1km로 합산. 원본은 유지 |
| 구현 결함 / 이번 수정 | ODOMETER gap의 양끝 100→108km가 연속거리 8km로 합산 | 거리 소스 gap을 가로지르는 쌍은 연속거리에서 제외. 위치만 gap이면 정상 거리 합산은 유지. 열린/복합 소스 gap도 회귀 검사 |
| 제품 판단 / 변경 안 함 | A→B→출발점과 아주 가까운 다른 좌표는 A→B→C, 귀환 false | 좌표 동등성·공간 근접·최소 반복/시간·정확도 정책은 입력 예시를 보고 사용자와 결정. 임의 반경/머묾 임계값 도입 안 함 |
| 제품 판단 / 변경 안 함 | A→B→C→C→B→A와 같은 관측에서 C 반복 범위는 남지만 곡은 약 90초·템플릿 가사 중심 | 구간 비중과 대비를 어떤 음악 요청으로 표현할지 결정. 반복 관측을 실제 정차/방문으로 바꾸지 않으며 곡의 실제 준수는 별도 청취 |
| 후속 / 계약 제안 필요 | 공백 양끝 거리 규모 정보와 구간별 비중을 별도 전달하려면 현재 summary 외 표현 필요 | `gapSpanningDelta` 등 별도 필드·version/호환과 UI 설명 제안을 먼저 정리. 연속거리로 합치거나 공백에 경로/시점을 배분하지 않음 |
| 기기 검증 / 이번 미실행 | 현재 0.3 첫 재생이 PAUSED/0에서 보류된 기존 이력 | 코드 검토·로컬 정책 출력은 첫 클릭 재생 검증을 대신하지 않음. 앱/에뮬레이터 실행은 사용자 요청 전 금지 |

MainActivity·화면 상태·API 계약 변경은 이번에 없으며 필요한 변경안은 별도 사용자 검토 대상이다.

## 검토 환경과 사례

실행 방법과 정확한 호스트 경계는 [`tools/logic-review/README.md`](../../tools/logic-review/README.md).
합성 8사례: 왕복/반복 위치, 과거 FRESH 지연 위치, 위치 공백, 거리 공백, 시각 역행, 리셋/unknown boot, GPS 흔들림, 기존 순서 테스트.
기존 검증 사례는 `resume-submission-20261112-151631/original-standalone.db`의 SEALED session 2개(97/32 events)를 읽기 전용으로 추출했다.
이는 과거 SDK+SIMULATED 수집 기록이며 현재 버전의 새 SDK 통합 성공/실제 도로 기록이 아니다. DB의 경로와 SHA256, 기존 frozen payload는 로컬 보고서에서 확인한다.

브라우저는 JSON 입력 편집과 Kotlin 출력 표시/차이 비교만 한다. 실제 핵심 로직을 JS/Python으로 재구현하지 않는다.
Room 모델 annotation 제거·SHA256 함수 추출·JVM org.json이라는 호스트 경계와 Android 직렬화 바이트의 미검증을 화면에 명시한다.
새 검토용 payload는 기존 frozen 입력과 별개이며 source/event/기존 작업/음원/비용 원장을 덮어쓰지 않는다.

## 검증 기록

- 기준 코드: 재사용한 기존 차량 순수 로직 22개 + 서버 15개 = 37개 로컬 테스트 통과.
- 수정 전 회귀: 신규 2개가 실제 실패(39개 중 2개 실패). `.local/logic-review/run-20261114-010243/current-build.log`와 XML 보존.
- 동일 기준 실행기 결과: 시각 역행 9km, 거리 gap 8km, 위치 전용 gap 2km. 새 SDK 입력 없이 보존 기록도 실행 가능.
- 수정 후 결과와 테스트·소스 해시·제한은 최종 run의 metadata/report에 보존한다. 이 문서의 과거 통과 수로 미실행 기기 경로를 완료 처리하지 않는다.

최종 로컬 실행: `.local/logic-review/run-20261114-011013-matched/`.
표시용 진단 실행기의 수정도 기준/현재에 동일하게 적용했으며 컴파일된 review 클래스의 fingerprint 일치를 검사한다.
현재 코드 검증은 같은 소스의 `run-20261114-010756/current`에서 통과한 39개 테스트·컴파일 증거를 재사용했다.
시각 역행 9→1km, 거리 공백 8→0km, 위치 전용 공백 2→2km. 나머지 합성/보존 SDK 사례는 동일 결과이며 10사례 모두 서버 입력의 로컬 검증을 통과했다.
이 숫자는 사례별 연속 관측 거리이며 총 주행거리/실제 도로 이동의 입증이 아니다.

앱 내 브라우저에서 입력 편집 후 실제 두 Kotlin 실행 결과 10→2km와 초기 9→1km 복원을 확인했다.
POST 완료 대기의 첫 locator가 약 3초 시점에 끊겼지만 다시 누르지 않고 화면 상태를 조회해 완료를 확인했다.
그 실행의 입력/결과는 최종 run의 `edited-*`에 별도로 보존했다. Chrome 프로필 충돌은 다른 브라우저를 종료하지 않고 앱 내 브라우저로 우회했다.
HTTP 결과 10사례, 디렉터리/임의 경로 비공개, Origin 없는 POST 거절을 확인했다.
Python 구문 검사·JS 구문 검사·`git diff --check` 통과. 자체 검토이며 독립 에이전트 리뷰는 하지 않았다.

추가 유료 API 호출 0. 앱·Pleos·음악 생성 서버·영상·패키지·외부 업로드·배포·push 실행 없음.
Guard·훅·상주 작업자·추가 에이전트·개인 메모리 저장 없음. Windroid 중단 유지.
