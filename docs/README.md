# 오늘의 트랙 문서

현재 제출 기준은 **0.4.0-submission(5)**입니다. 처음 살펴본다면 아래 심사용 안내부터 읽어 주세요. 설계·계획·과거 기록의 작성 시점과 실제 구현 범위는 구분합니다.

## 심사용 안내

| 문서·자료 | 내용 |
| --- | --- |
| [프로젝트 소개](../README.md) | 실제 화면, 핵심 흐름, 구현 범위, 다운로드 |
| [오늘의 트랙 기획서](proposal.md) | 현재 공모전 소개·심사 자료. 7번 참고자료는 공란 유지 |
| [YouTube 시연](https://youtu.be/bQ5uMmf7UIw) | 실제 Pleos 앱 화면·기기 재생 소리. SDK 합성 입력·대기 단축·별도 재감상 촬영 표시 |
| [제출 Release](https://github.com/xiuiworld/TodaysTrack/releases/tag/submission-v0.4.0) | 검증된 APK·소스 ZIP·SHA256 목록 |
| [설치·실행 안내](submission/runbook.md) | 다른 PC의 준비·빌드·기기 연결, 기존 데이터 보존, 비용 없는 기본 실행 |
| [검증 결과와 한계](submission/verification.md) | 실제 생성·저장·재생, 최종 앱 확인, 소스 재현 결과와 미검증 범위 |
| [제출 체크리스트](submission/submission-checklist.md) | 공개 자료 경로와 사용자가 처리하는 공식 양식·접수의 경계 |

## 현재 구현과 검증

| 문서 | 읽는 방법 |
| --- | --- |
| [기술 검증 기록](development/technical-validation.md) | 시점별 SDK·통합 검증 기록. 최종 제출 판정은 위 검증 문서 기준 |
| [UI 개선 기록](development/ui-refresh-progress.md) | 브라우저 비교부터 Android Views 반영·Pleos 실측까지의 이력 |
| [차량·음악 통합](development/vehicle-music-integration.md) | 기록과 음악 입력·작업·저장 연결 |
| [음악 엔진 구현](development/music-core-implementation.md) · [리뷰](development/music-core-review.md) | 공통 음악 엔진과 서버 통합의 구현·검토 기록 |
| [브라우저 UI 비교 도구](../tools/ui-review/README.md) | 가상 상태·무음 모의 재생 도구. 실제 Android 실행·음악 생성과 구분 |
| [화면 캡처 출처](assets/README.md) · [제3자 고지](../THIRD_PARTY_NOTICES.md) | README에 공개한 실제 캡처와 자산 출처 |

## 설계와 후속 계획

| 문서 | 상태 |
| --- | --- |
| [내부 제품 기획](product/product-plan.md) | 제품 방향·고객·경험·검증 과제. 계획된 전체 범위를 의미 |
| [시스템 설계 v0.2](design/README.md) | 보존한 설계 원문. 앱 구현 전 제안·후속 확장이 포함되며 현재 구현 목록이 아님 |
| [개발 계획](development/implementation-plan.md) | 단계별 계획·범위와 기존 의사결정 |
| [후속 계획](development/implementation-follow-up.md) | 실차·장시간·폰 연계·운영 등 남은 검증과 확장 |
| [경쟁 서비스 조사](research/competitive-analysis-2026-11-12.md) | 조사 시점의 선행 사례·근거. 실적이나 최초성의 입증으로 사용하지 않음 |

## 과거 기록과 내부 도구

- [0.3 실행 안내](submission/history/runbook-0.3.md), [0.3 검증](submission/history/verification-0.3.md)은 과거 버전 기록입니다.
- [기획 리뷰](reviews/2026-11-12-proposal-review.md)와 [조수석 선물 기획서](archive/passenger-gift-proposal.md)는 이전 검토·후보 보관본입니다. 두 기획서와 7번 공란을 보존합니다.
- [초기 UI 참고](design/ui-refresh-reference.md), [촬영 안내](submission/manual-filming-guide.md), [스토리보드](submission/storyboard.md)는 제작 당시 기록입니다. 현재 화면·영상·실행 정보는 위 심사용 안내를 우선합니다.
- [ECC 안내](development/ecc-usage.md), [Workflow Guard 안내](development/workflow-guard.md)는 개발 도구 기록입니다. Guard는 기본 비활성 상태이며 제품 실행에 필요하지 않습니다.

## 공개 자료와 로컬 보관 자료

이 목차에서 링크한 소스·문서·선정 화면 캡처는 저장소에 포함합니다. APK·검증된 소스 ZIP은 GitHub Release, 영상은 YouTube에서 제공합니다.

과거 문서에 등장하는 `validation/**/runs`, `validation/**/results`, `.local`, `apk`, `submission` 경로는 개발 PC에 보존한 **로컬 자료**입니다. 새 clone에 포함되거나 GitHub에서 열리는 링크가 아닙니다. 원본 기기 증거·정밀 좌표·사용자 DB·키·접수 개인정보를 공개하지 않습니다.

기존 커밋 이력을 유지하므로 현재 목록에서 제외한 문서 윤문 자료와 개발 계정 설정도 이전 커밋에는 남아 있습니다. 이 설정은 개발 계정 식별 정보이며 API 키·개인키가 아닙니다. 직접 작성한 코드의 새 오픈소스 이용 허락은 부여하지 않습니다.
