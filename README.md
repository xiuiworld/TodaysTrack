# 오늘의 트랙 · Today's Track

**달린 길이 음악이 되다.**

실제 주행 기록으로 주행이 끝난 뒤 AI가 전체 여정의 분위기와 흐름을 담은 곡을 만드는 Pleos Connect 앱 프로젝트입니다. 프로젝트 폴더명은 `TodaysTrack`입니다.

## 문서 구조

```text
TodaysTrack/
├── README.md
├── AGENTS.md
└── docs/
    ├── proposal.md
    ├── product/
    │   └── product-plan.md
    ├── design/
    │   └── deferred-design-notes.md
    ├── research/
    │   └── competitive-analysis-2026-11-12.md
    ├── development/
    │   └── ecc-usage.md
    ├── reviews/
    │   └── 2026-11-12-proposal-review.md
    └── archive/
        └── passenger-gift-proposal.md
```

| 문서 | 용도 |
| --- | --- |
| [오늘의 트랙 기획서](docs/proposal.md) | 선택한 프로젝트의 공모전 소개·심사 자료. 기획 변경은 이 문서에 반영합니다. |
| [내부 제품 기획서](docs/product/product-plan.md) | 핵심 경험·고객·기능·이용 흐름·제품 원칙·검증 과제를 정리한 최신 내부 기준입니다. |
| [설계 단계로 넘길 메모](docs/design/deferred-design-notes.md) | 기존 기술 검토를 분리한 참고 자료입니다. API·데이터 모델·서버·폰 연결의 확정 설계는 아닙니다. |
| [경쟁 서비스 조사](docs/research/competitive-analysis-2026-11-12.md) | 차량 음악·경로 음악화·여행 기록·생성 음악의 선행 사례와 근거, 차별화 제안을 정리합니다. |
| [ECC 사용 안내](docs/development/ecc-usage.md) | 전역 설치 상태, Codex에서의 스킬 사용 예시와 관리 방법입니다. |
| [에이전트 작업 규칙](AGENTS.md) | 프로젝트 맥락, 기존 개발 선호와 ECC·Superpowers 적용 원칙입니다. |
| [심사 검토 및 보완내역](docs/reviews/2026-11-12-proposal-review.md) | 두 후보에 대한 평가와 보완 근거, 아직 검증하지 못한 사항을 기록한 문서입니다. |
| [조수석 선물 기획서](docs/archive/passenger-gift-proposal.md) | 선택하지 않은 후보의 보관본입니다. |

## 현재 진행 상태

- 흰색 시안3 UI를 적용한0.3.0-submission(4), 약89초 v3 영상과 소스·재현 패키지를 준비했습니다. 현재 release의 기록 선택·기억한 설정 수집/마감·대역 저장·기존 실제곡 재실행/오프라인 재생을 확인했습니다. 첫 재생 보류 후 같은 세션 재개라는 제한과 청취 유예는 [검증/한계](docs/submission/verification.md)에 명시했습니다. 실제 새 생성은0.1.0, 전체90초 오프라인 재생은0.1.1의 보존 이력입니다. 추가 음악 유료 호출0·외부 접수 미실행입니다.
- 주행 데이터를 음악에 연결하는 경험이 핵심입니다. 주행 후 생성과 주행 중 생성 모두 가능한 방향이며, 현재 기획서의 기본 개발 범위는 주행 후 생성입니다.
- 앱이 기록에서 여정의 특징·곡 구성안을 만들고, AI가 실제 곡을 생성하는 방향입니다. 기본 악보의 동일성·정밀한 지도 동기화는 보장하지 않습니다.
- 기획과 상세 설계를 분리합니다. 기간·선택 설정·앨범·폰 연계는 주행 후 음악 생성·소장을 돕는 기능으로 다룹니다.
- 참가자 개인정보와 확인되지 않은 성과 수치는 기재하지 않았으며, 두 기획서의 7번 참고자료는 공란으로 유지합니다.

기존 문서는 이전 Codex 작업 폴더에서 이 프로젝트의 `docs/`로 이동했습니다.

## 실행과 산출물

- 차량 앱: `vehicle/` — Kotlin/Android, Room, 서비스 소유 SDK 수집, 고정 outbox, Media3. release package는 `dev.todaystrack.vehicle.submission`, 개발 앱과 별도로 설치해 기존 기록을 보존합니다.
- 로컬 서버: `server/` — Kotlin/Ktor, 제출용 단일 SQLite 내구 작업 원장, HTTPS·명시 기기 등록·서명/재생 공격 방지·멱등 작업·비용 한도. 운영 배포/일반 로그인 완료를 뜻하지 않습니다.
- 실제 빌드: `tools/Build-SubmissionApk.ps1`, 결과 `apk/TodaysTrack-release.apk`. JDK17·SDK35가 필요합니다. 서명 private key/암호는 `.local` 및 Windows DPAPI로 보호하며 제출하지 않습니다.
- [재현 안내](docs/submission/runbook.md), [검증/한계](docs/submission/verification.md), [기술 증거](docs/development/technical-validation.md), [현재 구현 계획](docs/development/implementation-plan.md).
- `server/fixtures/reference.mp3`는 기존 음원의 대역이며 **새 생성이 아닙니다**. 실제 새 곡·영상·소스 묶음은 `submission/`에서 별도 관리합니다. 외부 게시/업로드/이메일/접수는 실행하지 않았습니다.

로컬 묶음: `submission/TodaysTrack-submission.zip`, 내용: `submission/package/`. manifest.json에 APK/영상/음원/소스 해시와 Git 대신 소스 트리 SHA256을 기록합니다. [제출 체크리스트](docs/submission/submission-checklist.md)에 공식 빈 양식과 보내지 않은 이메일 초안을 연결했습니다. 테스트 화면은 2560×1440, 최종 영상은1920×1080입니다.

처음 재현할 때는 `tools/Use-SubmissionToolchain.ps1` → `server/Initialize-LocalServer.ps1` → `tools/Install-MediaTool.ps1` → 서버/차량 빌드 순서로 준비합니다. 새 로컬 TLS 인증서를 만든 PC에서는 APK도 다시 빌드합니다. 기본 서버는 FIXTURE이며 유료 재현을 자동으로 시작하지 않습니다. 보호 키와 실제 현재 사용자 승인·예산 없이는 음악 dispatch를 켜지 않습니다.

생성에는 PC 서버·제공자 인터넷이 필요합니다. 저장곡 재감상에는 서버/인터넷이 필요 없습니다. 정확한 위치는 차량 로컬에 남고, 음악 제공자에는 상대 순서·거리 규모·공백·음악 설정만 전송합니다. 기획서/설계 원문과 제출용 7번 참고자료 공란은 유지합니다.

## 버전 관리

현재 소스는 `xiuiworld/TodaysTrack` 비공개 GitHub 저장소의 `main`에서 관리합니다. 첫 커밋은0.3.0-submission(4) 상태의 기준점이며, 이전 개발 과정을 Git 이력으로 복원한 것은 아닙니다. 제출 당시 소스 트리 해시와 로컬0.2 보관본은 그대로 유지합니다.

프로젝트 계정은 `.project-identity`의 `xiuiworld`이며 Git 작성자와 SSH 설정은 저장소 로컬 설정을 사용합니다. 상위 폴더의 개인 식별 가이드는 커밋하지 않습니다.

APK·영상·제출 ZIP, 원본 기기 증거/좌표·로컬 DB·키·서명 정보·빌드 캐시는 Git에서 제외합니다. `submission/`, `apk/`, `validation/evidence/`의 기존 파일은 이 PC에 보존되어 있으며 새 clone에 포함되지 않습니다. 별도 upstream SDK 샘플 checkout인 `validation/vehicle-sample/`, `validation/fusedlocation-sample/` 및 도구 checkout `work/`도 제외합니다. 차량/서버 제품 소스, 테스트, 검증 스크립트와 실행 안내는 포함합니다.
