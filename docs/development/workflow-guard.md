# 오늘의 트랙 · 실행 방식과 Workflow Guard

2026-11-12 · 설정 v0.3 · 상태: 설정/오프라인 검사만, 제품/Guard run 미착수

11월 15일까지는 **inline 실행과 네 묶음 단위 자체 검토**를 기본으로 한다. Guard를 기본 실행하거나 Guard 검증을 제출 선행 조건으로 만들지 않는다. 전체 제품 방향/설계 v0.2는 유지하고 [제출용 구현 계획](implementation-plan.md) S1~S4를 우선한다. [후속 계획](implementation-follow-up.md)의 P0~P8은 보존한 상세 기술 분류이며 제출용 묶음의 자동 선행 단계가 아니다.

제출 마감은 **2026-11-15 23:59 KST**다. 일별 묶음 배정·단계별 소요시간 추정 없이 선행 조건이 충족되는 대로 승인 작업을 진행한다. 도구의 timeout은 실행 한도이며 개발 일정 추정이 아니다.

## 현재 결정과 실행 권한

- 프로필 execution.defaultMode는 inline, guardEnabledByDefault는 false다. 현재 승인된 구현 단계·유료 호출은 없다. 설정을 읽거나 검사해도 코드/AVD/SDK/모델/Guard run을 시작하지 않는다.
- 실제 착수 지시가 오면 그 승인 범위만 한 명이 이어서 진행한다. 기존 승인을 매번 다시 묻지 않으며 미승인 선행/후속 묶음으로 확장하지 않는다.
- Guard는 승인된 작업의 격리/독립 리뷰에 분명한 이점이 있을 때만 선택한다. 사용 여부 판단은 이번에 사용자에게 위임받았다. Guard를 선택해도 제품/기기/유료 권한이 새로 생기지 않는다.
- 유료 API는 음악/가사별 호출 수·총액·통화·실제 요청 내용을 구체화한 별도 승인이 필요하다. 현재 잔여 승인 0, live dispatch 기본 꺼짐이다. 키를 채팅에 요청/출력하거나 계약/로그에 넣지 않는다.
- 외부 저장소 공개·업로드·이메일 발송·배포는 S4 자료 준비 승인에 포함되지 않는다. 전역 Guard·Windroid·훅·상주 작업자·개인 메모리는 변경하지 않는다.

## 설정 파일과 작업 구분

| 파일 | 역할 |
| --- | --- |
| [project.profile.json](../../tools/workflow-guard/project.profile.json) | inline 기본값·제출 네 묶음·후속 기술 분류·공통 제약·문서 해시. 실행 spec이 아님 |
| [project.mjs](../../tools/workflow-guard/project.mjs) | check는 순수 설정/설치 스키마 검사. prepare는 실제 승인·범위·명시적 Guard opt-in이 있어야 격리 계약 준비. start 기능 없음 |
| [project-run-contract.md](../../tools/workflow-guard/project-run-contract.md) | Guard를 선택한 경우의 승인·부분/전체 판정·검사·통합 규칙 |
| [p0.mjs](../../tools/workflow-guard/p0.mjs), [p0.profile.json](../../tools/workflow-guard/p0.profile.json) | S1 안의 작은 probe 또는 후속 P0 실험에만 쓰는 선택 레시피 |
| [p0-run-contract.md](../../tools/workflow-guard/p0-run-contract.md), check-driver.mjs, check-evidence.mjs | 추후 probe의 caller 통합 검사·실제 비가시 증거/정리 계약 |
| [config.test.mjs](../../tools/workflow-guard/config.test.mjs) | 승인·기본 비활성·묶음 격리·후속 보존·증거 일관성의 오프라인 검사 |

| 제출 ID | 범위 | 전체 묶음 판정의 선행 | 유료 검사 |
| --- | --- | --- | --- |
| S1 | 수집·작은 실제 백그라운드 시험·저장/재실행 | 별도 착수 지시 | 불가 |
| S2 | 고정 입력·개발 자격·서버 작업의 대역 연결 | S1의 관련 증거 | 불가 |
| S3 | 대역 저장/재생 확인 후 실제 AI 연결 | S2 + 대역 저장/재생 증거 + 별도 유료 승인 | 별도 한도 승인 시만 |
| S4 | 영상·release APK·소스/실행 자료 준비 | 핵심 완성 판정은 S1~S3 실제 증거 | 불가 |

프로필에는 후속 P0~P8도 follow-up-reference로 보존한다. S 계약에는 승인된 S 작업만 넣으며 P를 암묵적으로 추가하지 않는다. 후속 P 작업은 그 기술 범위를 명시한 별도 착수 지시 후에만 선택한다. 같은 코드를 다뤄도 S full과 P 전체 완료를 구분한다. partial은 독립 작업 진행용이며 전체 선행 증거가 생겼다는 뜻이 아니다.

## 지금 허용된 오프라인 검사

프로젝트 루트에서 다음만 실행해 설정을 확인할 수 있다. Node v24.18 이상과 설치 Guard 1.2.0의 순수 validateSpec 함수를 사용한다. 이 명령은 Guard run·모델·Gradle·ADB·음악 API를 호출하지 않는다.

```powershell
node tools/workflow-guard/project.mjs check
node --test tools/workflow-guard/config.test.mjs
```

check는 각 S/P 분류의 메모리 내 테스트 계약을 설치 스키마에 대조한다. 제품 실행기나 전체 제품 검증이 아니다. 고정 문서가 바뀌면 검토 후 sourceHashes를 갱신한다. 이미 등록한 실행 계약은 몰래 바꾸지 않는다. 설치 패키지 검사만으로 이미 로드된 MCP 버전까지 확인했다고 주장하지 않는다.

## 선택적으로 Guard를 쓸 때만

실행 에이전트는 실제 대화 원문으로 approval manifest와 scope를 작성한다. 원문 출처/의미는 실제 대화와 대조하며 테스트/예시/설정 요청을 승인으로 쓰지 않는다.

- approval: schemaVersion 1, kind execution, userInstruction 원문, approvedStages와 각 stageEvidence 인용문. permissions.deviceExecution은 승인 작업에 포함된 기기 검사만, paidApi는 기본 null이다.
- paidApi가 별도 승인됐다면 원문의 quote·양의 정수 maxRequests·양의 maxCost·currency를 넣는다. 문자열 검사나 숫자 필드가 사용자 인증/실제 과금 차단을 대신하지 않는다. 서버 원장·dispatch/중복 통제가 필요하다.
- scope: schemaVersion 1, units의 키는 approvedStages와 정확히 일치한다. 단위마다 completion partial/full·instruction·필요 paths/artifacts·requirements·고정 checks를 둔다.
- check는 argv·effect(local/device/paid-api)·execution(sandbox/caller)·유한 timeout을 고정한다. 외부 영향은 caller로 수행한다. S2는 대역만, 실전송은 S3에서 대역 재감상 검증과 별도 승인 후 수행한다.
- dependsOn은 함께 승인된 task만 참조한다. 전체 선행이 별도 완료됐다면 evidence의 stage/path/sha256으로 고정하고 reviewer가 코드/환경 적용성을 확인한다. 해시만으로 실제 성공이 증명되지는 않는다.
- readOnlyPaths는 필요한 추가 텍스트만 context/extra로 복사한다. allowedRoots는 쓰기 상한이며 실제 단위는 필요한 파일만 선택한다. 기획/설계·전역 설정을 수정하지 않는다.

미래 준비 명령은 다음과 같다. **현재 실행하지 않는다.**

```powershell
node tools/workflow-guard/project.mjs prepare --approval-file <실제승인.json> --scope-file <작업범위.json> --confirm APPROVED_STAGES_ONLY --enable-guard true
```

opt-in을 생략하면 prepare를 거부한다. 있어도 Guard를 시작하지 않고 .workflow-guard/runs/<고유ID>/workspace와 stage.spec.json만 만든다. 승인된 소스/읽기 문맥만 복사하며 기존 run·음악 probe·비밀키·work/·빌드 캐시는 기본 제외한다. 실제 시작은 검토한 spec 객체로만 하며 넓은 workspace+request 자동 계약은 사용하지 않는다. 준비/시작 모두 실제 작업 승인이 먼저 있어야 한다.

선택한 Guard run만 한 구현 lane과 controller 독립 reviewer를 사용한다. 바깥 도구로 live workspace를 동시에 편집하지 않는다. pending caller ticket은 기존 Codex 권한 정책 아래 수행하고 continue한다. 사후 경로 검사/문자열 승인은 악성 명령을 차단하는 OS 보안 경계가 아니다.

## 작은 probe와 종료/통합

S1에 p0Unit({ withinSubmission: true })를 쓰면 **부분 단위**로만 준비한다. probe 완료를 S1 전체/제품 수집 완료로 표시하지 않는다. 후속 P0 단독 전체 실험에만 기존 full 레시피와 scope 생략 기능을 유지한다. probe를 포함하면 실제 검증한 세션 파일도 필요하다.

전용 AVD·serial·Android user·ADB 포트·실제 Pleos fingerprint·JDK17/SDK를 실행 시 다시 확인한다. session에는 verified, 절대 adbPath/javaHome/sdkRoot, adbServerPort, serial, androidUser, fingerprint 및 선택 python을 넣는다. 키는 넣지 않는다. 설정 검사는 AVD를 켜지 않는다. 고정 probe driver는 최대 300초(내부 280초)의 유한 검사이며 시간 한도는 제품 SLA/합격 기준이 아니다.

supported/unsupported/inconclusive와 실제 비가시/소스별 신선한 이벤트·종료/복원을 구별한다. 미지원 실험 완료를 백그라운드 지원으로 바꾸지 않는다. runner 원본/caller/SDK/시스템 증거를 리뷰한다. 한도/필수 증거 부족은 needs_attention으로 남긴다.

run 종료 후에만 diff·original-baseline.json·원본/문서 해시·새 파일 충돌을 대조해 승인 파일을 원본에 반영한다. 자동 copy-back/공개/배포는 없다. 기술 검증 문서에는 실제 결과/한계를 기록한다. Guard completed·원본 통합·실기 성공·제출 묶음 완료·전체 제품 완료·접수 완료를 구분한다.
