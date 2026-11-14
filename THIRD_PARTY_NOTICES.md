# 제3자 고지와 자산 출처

## 공개 목적과 적용 범위

오늘의 트랙 소스는 심사·검토를 위해 공개합니다. 직접 작성한 코드에 MIT 등 별도 오픈소스 라이선스를 부여하지 않습니다. 이 안내는 아래 제3자 구성 요소의 라이선스나 제공자 이용 조건을 대체하지 않습니다.

## 코드와 의존성

| 구성 요소 | 사용 범위·고지 |
| --- | --- |
| Astryx | 브라우저 UI 검토 도구에서 사용. Meta의 ThemeShowcase를 바탕으로 한 코드의 MIT 전문은 [기존 고지](tools/astryx-lab/THIRD_PARTY_NOTICES.md)에 보존 |
| React · React DOM · StyleX · esbuild | 브라우저 검토 도구의 npm 의존성. 버전은 [package.json](tools/astryx-lab/package.json)과 lockfile에 고정하며 각 패키지의 라이선스 적용 |
| Kotlin · Ktor · kotlinx · AndroidX(Room·WorkManager·Media3 등) | 차량·서버의 Gradle 의존성. 각 프로젝트의 고지와 이용 조건 적용. [차량 의존성](vehicle/app/build.gradle.kts) · [서버 의존성](server/build.gradle.kts) |
| Pleos Vehicle · FusedLocation SDK | 차량 SDK 의존성. [Pleos 개발 문서](https://document.pleos.ai/)와 제공자 조건 적용. 별도 SDK·에뮬레이터 설치 파일이나 소유권을 배포하는 저장소가 아님 |
| Gradle Wrapper | 포함된 wrapper JAR·스크립트의 기존 고지와 Apache License 2.0 적용 |

## 이미지와 화면

- 오늘의 트랙 로고는 이 프로젝트에서 작성한 트랙 형태의 벡터 심볼입니다.
- 앱의 흑백 기본 커버 3종은 image_gen으로 만든 고정 장식 이미지입니다. 실제 주행 장소·사진·음원 파형을 표현하지 않습니다. 생성 설명은 [자산 기록](docs/design/ui-refresh-reference.md)에 보존합니다.
- README 화면은 실제 Pleos Connect 에뮬레이터의 Android 앱 캡처입니다. 차량·시스템 UI는 실행 환경의 표시이며 프로젝트가 해당 브랜드나 시스템 자산의 권리를 주장하지 않습니다. [캡처 출처](docs/assets/README.md)
- README의 버전·기술 배지는 Shields.io 이미지 서비스를 사용합니다. 테스트 자동 실행이나 CI 상태를 나타내지 않습니다.

## 음원과 시연 영상

- [reference.mp3](server/fixtures/README.md)는 이전 검증에서 만든 AI 음원의 대역입니다. 신규 기록에 사용해도 새 AI 생성으로 표시하지 않습니다.
- 공개 시연은 [사용자가 제출한 YouTube 영상](https://youtu.be/bQ5uMmf7UIw)으로 제공합니다. 이번 Release에 MP4나 별도 MP3를 첨부하지 않습니다.
- AI 출력의 독점권·저작권 성립이나 감상 품질을 보증하지 않습니다. 생성 제공자의 이용 조건은 별도로 적용되며, 사람이 듣는 품질·유사성 평가는 유예한 상태입니다. 이전 이용 조건 확인 기록은 [제출 체크리스트](docs/submission/submission-checklist.md)에 남깁니다.
