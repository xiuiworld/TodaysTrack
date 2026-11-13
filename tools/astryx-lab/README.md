# Astryx 사용 준비 · 오늘의 트랙

Astryx는 Meta의 React 웹 디자인 시스템이다. 컴포넌트·테마·템플릿을 제공하며 CLI가 문서/예제 검색과 작업 준비를 돕는다. AI 음악 생성기나 Android Views 라이브러리가 아니다. 오늘의 트랙에서는 웹으로 디자인을 검토할 때 쓰고, 방향 확인 후 Android Views로 따로 옮긴다. 기존 4173 검토실이나 실제 제품에 자동 적용하지 않는다.

## 준비한 환경

`ui-review` worktree의 이 폴더에만 설치했다. 공식 npm 배포판 기준 Core/CLI/Neutral `0.7.15`, React/React DOM `19.3.1`, StyleX `0.19.0`, esbuild `0.28.2`를 package.json과 lockfile에 고정했다. Node 요구는 `>=22.13.0`; 준비 시 로컬 `v24.18.0`을 확인했다. GitHub main의 버전과 npm 배포판은 다를 수 있다.

설치는 `--ignore-scripts`로 했으며 전역 설치·공통 AGENTS.md 변경·개인 스킬/메모리 저장·훅을 하지 않았다. `init --features agents --agent codex --agent-docs-path AGENTS.md`는 **이 하위 폴더의 AGENTS.md만** 생성했다. 명령마다 `NO_UPDATE_NOTIFIER=1`을 적용해 업데이트 조회용 백그라운드 실행도 끈다. 버전은 자동 갱신하지 않는다.

## 가장 쉬운 사용법

이 채팅에 “Astryx로 기록 목록을 간결하게 바꾼 안을 보여줘”, “밝은 테마와 어두운 테마로 같은 화면을 비교해줘”처럼 요청한다. 에이전트가 CLI로 문서와 템플릿을 읽고 검토안을 만든다. 버튼 이름·배치·색조·간격에 대한 피드백을 받는다. 실제 앱 반영은 기존 승인 흐름을 따른다. CarUx·대역/전송 안내·기존 데이터 보호를 유지하고, 핵심 로직/API 변경은 먼저 제안한다.

기본 Astryx는 일반 웹 크기다. 차량용 최소 터치 프레임과 기기 확인을 별도로 적용해야 한다. 이 예제 갤러리는 그 프레임을 구현한 제품 시안이 아니다.

## 직접 실행

아래 명령은 **ui-review worktree 루트**에서 실행한다. 전체 경로는 `C:\Users\xiui\Documents\Projects\TodaysTrack\.local\worktrees\ui-review`다.

```powershell
# 컴포넌트 검색 / 속성·예제 확인
npm --prefix tools/astryx-lab run astryx -- search button
npm --prefix tools/astryx-lab run astryx -- component Button

# 템플릿, 색·간격·타이포 토큰, 테마 사용법
npm --prefix tools/astryx-lab run astryx -- template --list
npm --prefix tools/astryx-lab run astryx -- docs tokens
npm --prefix tools/astryx-lab run astryx -- docs use-a-theme

# 페이지를 만들 때: 먼저 템플릿 후보를 읽고, 선택한 뒤 별도 경로에 생성
npm --prefix tools/astryx-lab run astryx -- build "music library"
```

`build "..."`는 레이아웃/템플릿 후보와 작업 절차를 설명한다. 이 명령 하나로 완성 UI를 생성하거나 음악을 만들지 않는다. `template <이름> <경로>`는 실제 소스를 쓰는 명령이다. `init`은 agent 문서를 쓰고, `theme add/use`, `upgrade --apply`, `swizzle`도 파일을 바꿀 수 있으므로 작업 범위 안에서 사용한다. 이 프로젝트에서는 출력이 제안하더라도 글로벌 설치나 상주 `--watch`를 실행하지 않는다.

설치하지 않은 폴더에서 `npx astryx`를 실행하면 동명의 다른 npm 패키지를 받을 수 있다. 여기서는 위의 고정 runner를 사용한다. 일반 일회성 실행이 필요할 때 공식 패키지명은 `npx @astryxdesign/cli@0.7.15 ...`다.

## 실습 화면

```powershell
npm --prefix tools/astryx-lab run build
npm --prefix tools/astryx-lab run preview
```

<http://127.0.0.1:4174>에서 밝은/어두운 Neutral 테마, 클릭 횟수, 비동기 로딩 표시, 비활성 버튼을 확인한다. 원본 ThemeShowcase 템플릿을 가져와 동일 frame/gap/padding을 유지하고 내용과 테마를 바꿨다. 모든 동작은 메모리 안의 예시이며 API·Room·오디오·실제 기록과 연결되지 않는다. 외부 폰트나 CDN도 불러오지 않는다. 별도 정적 localhost 서버만 사용하고 종료는 Ctrl+C. 자동 기동·감시 기능은 없다.

기존 오늘의 트랙 UI 검토 화면은 <http://127.0.0.1:4173>이며 그대로 유지한다. 이 갤러리는 기존 검토실의 “적용” 이력에 등록한 디자인 변경안이 아니다.

## 복원과 확인

```powershell
# 다른 체크아웃에서 복원 시에만 필요
npm --prefix tools/astryx-lab ci --ignore-scripts
npm --prefix tools/astryx-lab run build
npm --prefix tools/astryx-lab run check
```

node_modules, npm 캐시, 빌드 결과는 Git에 넣지 않는다. 배포나 push는 수행하지 않았다. 준비 중 sandbox가 esbuild의 자식 프로세스를 차단해 빌드 명령에 필요한 실행 권한을 사용했다.

설치 직후 `doctor --built .local/dist`는 실패 0, 경고 1이었다. Windows의 `node_modules/.bin/astryx.cmd`를 다른 프로그램으로 판정하는 경고다. shim 내용이 실제 `@astryxdesign/cli/.../astryx.mjs`를 가리키고 직접 `--version`도 0.7.15를 반환함을 확인했다. 전역 PATH 변경 없이 npm runner에서 정확한 JS entry를 호출한다. 진단의 build 확인은 번들의 Astryx 사용 여부이며 제품 전체 품질이나 Android 검증이 아니다.

준비 후 브라우저에서 밝기 전환/원복·밝고 어두운 카드 구분·클릭 반응·두 예제의 독립 상태·로딩 중 중복 활성화 차단·단일 완료·비활성 상태·가로 넘침 없음의 9개 항목을 확인했다. 콘솔 오류/경고 없음, 요청은 4174 localhost의 HTML/CSS/JS 3개뿐이었다. 현재 Neutral 폰트 지정은 시스템 폰트로 대체될 수 있으며 웹폰트 파일을 별도로 내려받지 않았다.

공식 자료: [저장소](https://github.com/meta/astryx), [CLI 사용법](https://github.com/meta/astryx/blob/main/packages/cli/README.md), [컴포넌트 시작하기](https://github.com/meta/astryx/blob/main/packages/core/README.md), [Storybook](https://astryx.atmeta.com/storybook/).
