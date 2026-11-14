# 오늘의 트랙 · 0.4 로컬 실행 안내

현재 제출 기준은 **0.4.0-submission(5)**, Android Views 흰색 UI와 v2 음악 엔진이다. 실행 코드와 검증된 소스 ZIP은 `submission-v0.4.0` 태그의 `d7d99c4ed6ef6a8d8c24c605475ee6b3723e38aa` 기준이며, main에는 이후 기획서·공개 안내 정리가 포함된다. 예전 0.3 안내는 [과거 기록](history/runbook-0.3.md)에 보존한다.

## 제공 파일

- [apk/MamboCore_TodaysTrack_release.apk](../../apk/MamboCore_TodaysTrack_release.apk): 맘보코어(MamboCore) 제출용 파일명. 기존 `dev.todaystrack.vehicle.submission` 패키지·서명·검증된 바이트를 유지한다.
- [제출 Release](https://github.com/xiuiworld/TodaysTrack/releases/tag/submission-v0.4.0): 같은 APK, `TodaysTrack-source.zip`, `SHA256SUMS.txt`. 소스 ZIP에는 차량·서버·공통 음악 엔진·검토 도구·해당 커밋의 문서·파일별 해시 목록이 포함된다.
- [YouTube 시연 영상](https://youtu.be/bQ5uMmf7UIw): 약 2분 28초의 실제 Pleos 화면과 기기 소리. SDK 합성 주행·생성 대기 단축·별도 재감상 촬영을 표시했다. 버전 고정 전 0.3(4)의 동일 UI·엔진 코드이며 최종 0.4의 신규 생성 촬영으로 표시하지 않는다.
- [검증 결과와 한계](verification.md): 첫 재생 클릭·소스 재현·청취 미평가·후속 범위.

MP4·별도 MP3·원본 기기 증거·DB·키·공식 양식은 공개 Release에 첨부하지 않는다. 과거 문서의 `submission/` 경로는 개발 PC의 로컬 보관본이며 새 clone에는 없다. 새 기기에는 기존 기록·곡이 포함되지 않는다.

## 기존 전용 Pleos에 설치

현재 검증 환경은 Windows/PowerShell 7/JDK 17/Android SDK 35·Build Tools 35/Pleos Connect 2.1.0(API 34)이다. 앱의 Vehicle 2.3.2/FusedLocation 1.1.3 의존성은 Gradle에 고정했다. 전체 에뮬레이터 화면은 **2560×1440, 160dpi**, 시스템·차량 패널을 제외한 앱 영역은 **1682×1210**이다. 기기 해상도나 밀도를 임의로 바꾸지 않는다.

저장소를 clone한 루트에서 실행한다. Git이 필요하며 JDK 17의 `JAVA_HOME`과 설치된 Android SDK의 `ANDROID_HOME`을 준비한다. APK는 저장소에 포함돼 있다. 다운로드만 필요한 경우 위 Release를 이용한다.

```powershell
. ./tools/Use-SubmissionToolchain.ps1
adb devices
adb -s emulator-5554 install -r apk/MamboCore_TodaysTrack_release.apk
adb -s emulator-5554 shell am start --user 10 -W -n dev.todaystrack.vehicle.submission/dev.todaystrack.vehicle.MainActivity
```

위 serial/user는 이 PC의 전용 AVD 기준이다. 다른 환경에서는 실제 대상부터 확인한다. 기존 서명이 다르면 업데이트를 중단하고 기존 앱을 삭제하지 않는다. uninstall/clear/wipe/downgrade로 기록·곡을 지우지 않는다. 서명 지문은56fd311ccd45d0dd255a6241701175f7a530a668cb243d609c51962d310c0490이다.

## 다른 PC에서 소스로 실행

새 전용 Pleos AVD, 위 JDK/SDK와 Python·pip를 준비한다. 소스 ZIP을 압축 해제한 루트 또는 clone한 저장소에서 실행하며 `vehicle`, `server`, `music-core` 폴더를 함께 둔다. 제공 APK의 개발 서버 인증서는 원 검증 PC의 공개 인증서이므로, 다른 PC의 새 TLS 키로 서버를 만들면 그 공개 인증서를 넣어 APK를 다시 빌드해야 한다. private key/DPAPI/기존 사용자 DB는 배포하지 않는다. 초기화 스크립트가 해당 작업 사본의 공개 인증서 파일을 갱신하므로 새 개발 사본에서만 실행한다.

```powershell
. ./tools/Use-SubmissionToolchain.ps1
pwsh -NoProfile -File server/Initialize-LocalServer.ps1
pwsh -NoProfile -File server/Initialize-MusicStorage.ps1
pwsh -NoProfile -File tools/Install-MediaTool.ps1
$env:TODAYSTRACK_FFMPEG = (Get-ChildItem -LiteralPath tools/.local/media/imageio_ffmpeg/binaries -Filter 'ffmpeg-win*.exe' | Select-Object -First 1).FullName
if (-not $env:TODAYSTRACK_FFMPEG) { throw 'FFmpeg executable is required for decoder checks.' }
./server/gradlew.bat -p server test :music-core:test installDist --no-daemon --max-workers=1
pwsh -NoProfile -File tools/Build-SubmissionApk.ps1
```

Build-SubmissionApk 결과는 로컬 `apk/TodaysTrack-release.apk`다. 위 공개 제출 APK를 덮어쓰는 명령이 아니다. 새 작업 환경에서는 새 로컬 서명을 만들므로 이 결과는 **새 전용 AVD에 설치**한다. 기존 설치의 동일 서명 업데이트에는 `-SigningDirectory <기존 submission.p12/password.dpapi 폴더>`를 지정한다. 명시한 폴더에 키가 없으면 대체 키를 만들지 않고 중단한다. 이미 캐시한 의존성만 쓸 때는 `-Offline`을 추가한다. 전체 APK 바이트 재현 여부는 검증 문서의 분리 소스 빌드 결과와 구분한다.

차량 앱의 i→기기 연결 정보에는 공개 등록 정보만 표시된다. 해당 JSON을 로컬 `server/.local/enrollment.json`에 저장한 뒤 아래처럼 명시 등록한다. 개인 키는 가져오거나 채팅에 붙이지 않는다.

```powershell
$env:TODAYSTRACK_SERVER_STATE=(Resolve-Path server/.local).Path
./server/build/install/TodaysTrackServer/bin/TodaysTrackServer.bat enroll server/.local/enrollment.json
pwsh -NoProfile -File server/Start-LocalServer.ps1 -FfmpegExecutable $env:TODAYSTRACK_FFMPEG
```

서버는 localhost127.0.0.1:8443 HTTPS만 열고 앱은 에뮬레이터의10.0.2.2:8443으로 연결한다. StateDirectory 인자는 같은 PC의 기존 상태 폴더를 명시적으로 재사용할 때만 쓴다. Initialize-MusicStorage는 새 결과 암호화 키만 DPAPI로 보관하며, 기존 암호화 결과가 있는데 키가 없으면 새 키로 덮지 않는다. Start-LocalServer는 기존 보호 키를 프로세스 환경에만 읽고 출력하지 않는다.

## 기록·생성·감상

홈에서 새 기록 시작→기록 마감→음악 만들기 확인→생성 상태→저장→보관함의 곡 본문→음악 상세→재생/일시정지→기록 보기 순서다. 새 기록 설정은 다음 기록에 적용되며 마감한 기록의 설정을 덮지 않는다. CarUx 제한·대역 출처·정밀 위치를 제외한 요약 전송 안내는 유지된다. 표지는 고정 장식 이미지이며 실제 주행 사진이 아니다. 재생 바는 실제 위치 표시이고 드래그 탐색 기능은 제공하지 않는다.

기본 실행은 유료 API를 켜지 않는다. 새 v2 입력은 로컬 구성안/서버 입력까지 만들 수 있으나 비용 승인이 없으면 생성 승인 대기로 보존된다. 예전 dispatch.json만으로 v2를 켤 수 없다. 실제 분석·음악 생성에는 별도의 보호된 v2 승인 설정과 보호 저장 API 키, 요청 수·총액·기한 승인이 필요하다. `-EnableMusicKey`는 이미 저장한 키를 읽는 옵션이며 유료 실행 승인을 대신하지 않는다. 별도 유료 가사·자동 충전·자동 재송신은 이번 제출 확인에 포함하지 않는다.

이번 사용자 승인3회/보수적 예약USD0.30은 송신 전 보류1건과 실제 음악2곡으로 소진했다. 추가 호출을 재현 명령에 넣지 않는다. 생성 서버가 없어도 이미 차량에 저장한 곡은 감상할 수 있다. 제공 소스/앱은 사용자 DB와 기존 곡을 미리 심어주는 배포물이 아니며 새 기기에는 새 기록부터 시작한다.

## 제출 경계

공식 양식의 개인정보·동의·서명과 외부 접수는 사용자가 별도로 진행한다. 기획서 두 개·설계v0.2·기획7번 공란·후속 계획을 유지한다. 청취 평가는 유예했다. 실차/장시간/일반 백그라운드·스마트폰/운영 배포를 이 제출물의 검증 완료로 표시하지 않는다. 새 음악 송신 없이 기존 입력·작업·자산 조회/재생으로 확인할 수 있는 항목을 우선 사용한다.
