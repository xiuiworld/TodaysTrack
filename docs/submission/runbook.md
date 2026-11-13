# 오늘의 트랙 · 로컬 재현 안내

2026-11-12 · 현재 소스/빌드 기준 release **0.3.0-submission(4)**. 현재 기기 영향 검증과 별도 추출 빌드 결과는 verification.md에 명시한다. 로컬 제출 패키지는 `submission/package/`, 묶음은 `submission/TodaysTrack-submission.zip`이며 포함 버전과 해시는 패키지 manifest.json으로 확인한다. 외부 접수는 실행하지 않았다.

## 환경 준비

- Windows, PowerShell7, JDK17, Android SDK35·Build Tools35, Pleos Connect2.1.0 AVD. Vehicle2.3.2, FusedLocation1.1.3. 제품 minSdk28/target35. 현재 영향 검증은 host GPU 합성 입력이다. software GPU 검은 화면의 인과관계는 미확정이며 동일 장애 재현을 반복하지 않는다.
- 검증 화면2560×1440, API34 Pleos 시스템 이미지이며 일반 Android폰 지원을 뜻하지 않는다. 영상은1920×1080으로 편집했다.
- JAVA_HOME/ANDROID_HOME을 자신의 설치 경로로 설정한다. tools/Use-SubmissionToolchain.ps1은 현재 프로세스의 환경만 바꾼다. Gradle wrapper로 의존성을 내려받는 빌드에는 인터넷이 필요하다.
- server/Initialize-LocalServer.ps1은 로컬 TLS private key와 DPAPI 암호를 server/.local에 만들고 공개 인증서만 vehicle/app/src/main/res/raw에 내보낸다. 다른 PC의 새 인증서에는 APK를 다시 빌드한다. 다른 PC의 private key를 제출물에 넣지 않는다.

## 빌드와 개발 등록

1. 서버: server 디렉터리에서 gradlew.bat test installDist.
2. 차량 release: 프로젝트 루트에서 `pwsh -NoProfile -File tools/Build-SubmissionApk.ps1`. 테스트 후 `apk/TodaysTrack-release.apk`가 생성된다. 서명 키는 이 PC에서 새로 생성되므로 제공 APK와 바이트/서명 동일성을 보장하지 않는다. 기존 다른 서명 앱을 삭제하지 말고 별도 전용 AVD를 사용한다.
3. 앱은 설치별 EC 키를 Android Keystore에 만들고 공개 등록 정보만 보관한다. 제출용 단일 개발 차량/owner 등록은 관리자가 명시적으로 수행하며 CAR 자가 선언을 받는 API는 없다.
4. release 앱 왼쪽 ‘앱 정보’ → ‘공개 등록 정보’에서 **공개 등록 JSON**을 확인해 로컬 `server/.local/enrollment.json`에 저장한다. private key나 음악 키를 추출하지 않는다. release는 run-as 불가가 정상이다.
5. 루트 PowerShell에서 `$env:TODAYSTRACK_SERVER_STATE = (Resolve-Path server/.local).Path` 후 `server/build/install/TodaysTrackServer/bin/TodaysTrackServer.bat enroll server/.local/enrollment.json`을 실행한다. 명시 등록된 설치만 API를 쓸 수 있다. 서버는 127.0.0.1:8443의 HTTPS만 연다. 에뮬레이터는 10.0.2.2:8443으로 접속한다.
6. server/Start-LocalServer.ps1로 대역 서버를 실행한다. 음악 키 옵션 없이 시작할 수 있으며 dispatch.json이 없으면 FIXTURE이다. 다른 실제 기기에 명령하지 않는다.

제공 APK는 새 PC에서 기록 UI를 실행할 수 있지만 생성 서버 인증서가 맞지 않는다. 서버 private key를 배포하지 않기 때문에 새 PC에서는 TLS 초기화와 APK 재빌드가 필요하다. 저장된 개인 데이터/음원을 APK에 미리 심지 않았다. 제공 MP3/영상은 별도 결과물이다.

이번 환경의 설치/실행: `adb -P 5038 -s 127.0.0.1:5661 install --user 10 -r apk/TodaysTrack-release.apk`, `adb -P 5038 -s 127.0.0.1:5661 shell am start --user 10 -n dev.todaystrack.vehicle.submission/dev.todaystrack.vehicle.MainActivity`. 자신의 **전용** AVD serial/user를 확인해 바꾼다. Pleos 플랫폼 없는 일반 휴대폰에서 SDK 지원을 보장하지 않는다.

합성 입력 재현 도구는 `validation/journey-probe/`의 유한 Python 스크립트다. submission_device.py의 ADB server/serial/user와 Pleos Virtual Console 8088 전달을 자신의 전용 환경에 맞춘다. `release_journey.py --mode FIXTURE`가 무료 경로이며 LIVE는 기본 재현 절차가 아니다.

다음 두 문단은0.2.0 보존 이력이다. 현재0.3은 차량27개·서버15개와 별도 submission/repro-ui-v0.3 빌드가 통과했으며 설치 APK 해시는 verification.md 참조.

0.2.0의 빌드 증거는 `validation/evidence/resume-server-green.log`의 서버 테스트15개/실행 디렉터리 빌드, `resume-release-build.log`의 차량 테스트22개/release 빌드, `resume-debug-build.log`의 debug 빌드 성공이다. 제출 소스를 `submission/repro-v0.2-final`에 별도 추출하고 새 로컬 TLS/서명을 준비한 차량22개·서버15개 테스트/빌드도 통과했다. 빌드 성공과 실제 기기 관측은 별도 증거다.

최종 release0.2.0(3)은 설치된 APK를 읽어 제공 APK와 SHA256 `52e78c97795da2a5243f4ef40e6d9fc75581a0a51f49f5c60e3cda65210494af` 바이트 일치를 확인했다. 기존에 수집한6km 기록을 중단/재실행 뒤 정상 마감해 summary-v2→대역 서버→차량 저장을 확인했다. 자동 GPS 복원 입력까지 포함한 관측 순서는 A-B-C-B-A-D, 공백3개다. 처음 계획한 합성 왕복만 정확히 재연한 성공으로 표시하지 않는다. 같은 최종 APK의 기존 실제 AI 곡은 재실행/기본 네트워크 없음에서 PLAYING2703→5709ms·일시정지, 새 작업0이었다. 패키지 evidence/current-release-*.json과 verification.md를 함께 확인한다.

## 실제 사용자 흐름

정차/플랫폼 허용 상태 → 필요할 때 ‘새 기록 설정’에서 하루/여행·최소 음악 설정 저장 → ‘새 기록 시작’은 기억된 설정으로 즉시 시작 → SDK 새 입력 수집 → 기록 마무리 → 홈/기록의 목록 또는 좁은 창의 ‘저장된 기록 선택’에서 대상 선택 → 해당 기록의 설정·관측 순서·연속 거리·공백 확인 → 음악 만들기 한 번 → 상태 확인 → 보관함에서 사용자가 재생.

이전 SEALED 기록도 선택할 수 있다. 생성은 선택한 기록의 저장 설정을 사용하며, 이미 고정된 입력과 기존 intent는 이후 설정 변경으로 바꾸지 않는다. 중단된 OPEN 기록이 있으면 이전 기록의 설정을 표시하고 설정 편집을 잠근 채 ‘이전 기록 이어서 수집’을 제공한다. CarUx 제한을 모르거나 제한 상태이면 설정·생성을 보류한다.

기록은 네트워크 없이 차량 Room에 저장된다. 생성에는 PC 로컬 서버와, 실제 AI일 때 제공자 인터넷이 필요하다. 저장이 끝난 곡의 재감상에는 서버/인터넷이 필요 없다. 생성 완료 직후 자동 재생하지 않는다. 사진·메모는 필요 없다.

SDK 원본 위치·절대 누적거리는 차량 내부에 남고, 고정 요약만 서버로 간다. 새 요약의 `positionSegments`는 위치 공백·오류·boot/epoch 변경·미상 시각·시간 역행을 건너 연결하지 않는다. `dwellEvidence`는 같은 known boot/epoch에서 연속으로 받은 정확히 같은 위치의 관측 수·시간 구간이며, 정차·방문·머묾을 입증하지 않는다. 음악 제공자에는 segment별 상대 위치 순서와 반복 위치의 관측 수·구간 시간, 연속 거리 규모·공백·음악 설정을 보내며 epoch/boot/seq 식별자는 보내지 않는다. 기존 flat 요약만 있는 입력은 연속성·반복 위치 시간을 미상으로 취급한다. 첫·마지막 관측 위치가 같다는 사실도 공백을 건넌 연속 귀환을 보장하지 않는다.

위치 source 시각이 수신보다10초 넘게 오래되면 `STALE`로 원본을 보존하고 현재 관측에서 제외한다. 이는 관측된 SDK 지연을 바탕으로 정한 앱의 기술 정책이며, SDK 보장·사용자 합격 기준·운전 시간 측정이 아니다. 감정/날씨/방문 지명을 측정했다고 하지 않는다.

## 미완료 작업과 저장 재개

앱 시작 시 미완료 intent를 유한하게 조회해 WorkManager에 다시 등록한다. 조회·다운로드 재시도는 유한하며, 이후에도 남은 작업은 선택한 기록의 ‘기존 작업·저장 다시 확인’으로 확인한다. 이미 받은 jobId로 결과 상태와 같은 자산을 다시 받으며 음악 제공자에게 새 생성을 요청하지 않는다.

jobId를 저장하지 못했으면 인증된 `GET /v1/generation-jobs/by-intent/{localIntentId}`로 원래 작업부터 조회한다. 이 조회는 owner+intent가 정확히 일치하는 기존 작업만 반환하며 새 quote·예산 여유·attempt를 요구하지 않는다. 신규 `LOCAL_PENDING`/`PREPARING`만 기존 작업 없음이 확인된 뒤 첫 접수를 진행한다. 신규 접수 직전에는 `ACCEPTANCE_UNCERTAIN`을 먼저 저장한다. 이 상태와 이전 버전의 `SENDING` 등 접수 여부가 불명확한 상태는 조회 전용이며, 원래 작업이404여도 자동 재접수하지 않는다.

서버가 `VALIDATING` 도중 중단되면 저장된 final/part/제공자 response만으로 로컬 검사와 READY 확정을 재개한다. 로컬 결과가 없거나 불명확하면 `RECONCILING`에 보류하며 제공자를 다시 호출하지 않는다. READY 확정 뒤 trace 기록 실패도 이미 저장한 곡의 READY를 취소하지 않는다. 서버 복구는 실제 SQLite 회귀 테스트로 확인했다. debug 대역의 jobId 유실/접수 불명 상태 주입 후 앱 시작만으로 기존 작업을 회수했고 기록·입력·원장 증가0과 원본 복원을 확인했다. 실제 응답 유실 실험이나 GET 경로의 독립 access-log 관측으로 확대하지 않는다.

## 대역과 실제 AI

대역은 기존 MP3를 새로운 작업 결과에 연결하는 저장·재생 검사다. UI/trace에 FIXTURE/새 AI 생성 아님을 남긴다. 실제 새 생성 증거와 바꾸어 쓰지 않는다. 현재 앱 기본 프로필은 합성 환경이며 실도로 검증 앱이라고 설명하지 않는다.

현재 사용자 승인은 총 USD5 이내의 음악 생성이며 별도 유료 가사 API/충전/자동결제는 금지다. 기존 소진 원장은 그대로 두고 서버 원장에는 기존 3곡에 대한 보수적 USD0.30과 신규 시도별 USD0.10 예약을 유지한다. 접수 불명은 RECONCILING으로 보존하고 자동 재송신하지 않는다. 다운로드 실패는 같은 자산만 다시 받는다. 현재 실제 청구/잔액을 관리 API로 검증한 것은 아니다.

이번 제품 실제 음악 송신은 여전히 **1회**, 재시도0이다. 기존분 포함 보수적 예약 USD0.40, 신규1곡 공식 기본 단가 추정 USD0.08(실제 청구/세금 미확인). dispatch는 꺼져 있으며0.2.0 구현을 위해 실제 음악을 재생성하지 않았다. 실제 제공자는 Lyria3.5, 가사는 비과금 사실 템플릿이다. **역사적 기기 증거**는0.1.0의 새 SDK→새 곡→저장과0.1.1의 같은 곡 재실행·오프라인 끝까지 재생이다. 반환 텍스트 포함·전체 디코딩과 청취 판단은 구분한다. 실제 가창/음악 품질 청취는 최신 사용자 지시에 따라 **유예(deferred)**하며, 그 이유만으로 구현 작업이나 전체 Goal을 blocked로 두지 않는다.

실제 음악은 **대역 저장/재실행·오프라인 재생 확인 뒤**에만 승인 기록을 가진 로컬 dispatch 설정과 기존 보호 저장 키로 켠다. 키는 채팅·APK·소스·로그에 넣지 않는다. 다른 사용자의 키를 새로 입력해야 한다면 로컬 숨김 입력 도구를 사용한다. 유료 호출을 재현 명령에 자동 포함하지 않는다.

## 보존과 제출 경계

기존 검증 APK/기록은 제거하지 않는다. release 서명 차이로 업데이트되지 않으면 기존 앱을 삭제하는 대신 별도 package ID의 제출 APK로 확인한다. 서명 private key와 .local·DPAPI·서버 DB·원본 좌표·인증 자료는 소스 패키지에서 제외한다.

외부 게시/업로드·이메일·클라우드 배포·실제 접수는 별도 사용자 승인 없이는 실행하지 않는다. 후속 제품 기능은 implementation-follow-up.md에 보존한다.

## 영상과 청취 확인

TodaysTrack-demo.mp4의 v2 편집은 1920×1080/H.264/AAC 약179초이며0.1.0 생성 화면·0.1.1 재감상 증거와0.2.0 실제 UI 정지 캡처10초를 구분한다. 실제 생성 대기를 줄였고,0.1.1 APK 오프라인 재생은 약1fps 호스트 화면 수집을 MediaSession 위치에 맞춰 편집했다. 소리는 같은 생성 MP3 후삽입이며 Android 직접 오디오 녹음이 아니다. 정지 캡처를0.2.0 전체 동작의 영상 증거로 대체하지 않는다. audio/TodaysTrack-8km-Acoustic.mp3의 실제 들리는 내용·발음·음악 품질은 청취 유예 상태이며, 파일 검사나 muted 영상 재생으로 평가를 대신하지 않는다.

화면 닫기 뒤 Pleos 전체 검은 화면이 관측됐다. 메모리 부족 가능성은 미확정이며 같은 문제 재현을 반복하지 않는다. 새 그래픽 백엔드 swangle에서 실제 UI를 확인하고 기존 기록을 보존·마감했다. 현재 버전의 이 추가 시험으로 비가시 수집을 새로 입증했다고 하지 않는다. 백그라운드 제품 요구는 후속 문서에 남아 있다.

software GPU+Android screenrecord 부하에서 화면 백지/포커스 지연이 발생했다. 데이터 삭제 없이 앱 재실행으로 렌더가 복구됐고, 최종 영상은 Android 인코더 없이 PC 유한 캡처를 사용했다. 포커스를 우회하지 않았다. 실제 내비/전화·실차·장시간/전원은 후속 검증이다.


## 0.3 UI와 재현 증거

선택 기록의 설정은 읽기 전용이고 새 기록 설정은 다음 기록에만 적용됩니다. OPEN은 원래 설정으로 이어갑니다. 보관함은 실제 저장곡 전체를 표시하며 재생/일시정지만 제공합니다. 화면 너비960dp 미만에서는 목록이 선택 창으로 접힙니다. 버튼 시각 영역과76dp 이상 터치 frame은 별개입니다.

유한 UI 검증 도구는 `ui_refresh.py --mode inspect|fixture|offline`입니다. fixture는 새 SDK 합성 입력/무료 대역 작업을 만듭니다. 제공된 `--resume`은 기존 관측 세션 보완용이며 offline에는 실제 관측 PID를 요구합니다. 첫 재생 보류 이력을 삭제하거나 무조건 성공으로 재해석하지 않습니다. 전용 AVD/네트워크/서버 준비는 자신의 환경에 맞게 명시적으로 수행합니다. 신규 LIVE 실행은 재현 절차에 포함하지 않습니다.
