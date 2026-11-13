# 오늘의 트랙 · 기술 검증 기록

시작: 2026-11-12 KST · 상태: SDK 기록→AI 곡 통합 검증 확인 · 실제 백그라운드/장시간/청취 평가는 남음
현재 제출 구현 실행은 문서 마지막 S1~S4 기록을 우선한다. 과거 문단의 승인 0/미착수는 이력이며 이후 사용자 승인을 취소하지 않는다.
범위: 공식 문서 확인, 개발 환경 설치, Pleos 공식 샘플의 빌드·실행·데이터 수신, AI 생성 검증 준비 및 가능한 실제 호출.
완성 제품·상세 서비스 설계·폰 앱 구현·배포는 이번 작업 범위가 아니다. 제출용 7번은 변경하지 않는다. Windroid 개발은 계속 중단 상태다.

| 요구 | 근거 | 완료 증거 | 상태 |
| --- | --- | --- | --- |
| V1 공식 개발 방법 확인 | 사용자 요청 | 문서·공모전 예외·버전·다운로드 출처 | 확인, 아래 참고 |
| V2 개발 환경 | 사용자 설치 승인 | IDE·JDK·SDK 도구의 실행 버전 | 설치·도구 버전 확인 완료 |
| V3 Pleos 타깃 | 현재 제품의 차량 기록 | Pleos AVD 부팅·ADB·Virtual Console 응답 | Pleos 2.1.0 부팅·ADB 확인, Console HTTP200 |
| V4 SDK 데이터 | FusedLocation·Odometer 활용 계획 | 공식 샘플 또는 작은 probe의 실제 API 응답 | 누적거리 조회·위치 연속 갱신 확인. 구간 시간/거리 API 미지원 |
| V5 AI 곡 생성 | 실제 여정의 AI 작곡 | 명시된 샘플 입력·실제 결과곡·관찰·연동 상태 | Lyria3곡 반환: 가상 입력2곡 + SDK 수집 기록1곡. SDK 곡 전체 디코딩 확인, 청취 품질 별도 |

## 실행 원칙

- 한 명이 순차적으로 진행하고 독립적인 조회만 병렬 도구 호출한다. 추가 에이전트·상주 작업자는 만들지 않는다.
- 제품의 핵심은 실제 기록에서 주행 후 AI가 곡을 만드는 경험이다. 설정·앨범·폰 기능을 이 검증의 선행 구현으로 늘리지 않는다.
- SDK 다운로드·빌드·초기화와 실제 값 수신은 서로 다른 증거다. 일반 Android 부팅을 Pleos 부팅으로 보고하지 않는다.
- 같은 실패가 두 번 반복되면 원인을 조사하거나 접근을 바꾸고 독립 작업을 계속한다.
- 임의 지연·커버리지·합격률을 완료 기준으로 만들지 않는다. 외부 인증·계정·크레딧 의존은 해당 검증에만 표시한다.
- 검증 샘플은 제품 앱과 분리한다. 프로젝트에 Git 저장소는 현재 없다. 기존 제품 브랜치·다른 프로젝트를 변경하지 않는다.

## 초기 확인

- PC: Windows 10 Pro 19045, Intel i5-1340P, RAM 16GB, HypervisorPresent=true.
- GPU: Intel Iris Xe. 공식 Pleos 문서의 최소 외장 GPU 사양에 못 미치므로 실제 부팅·그래픽 동작 확인 필요.
- PATH: Git·Node·Python·winget 있음. Java·adb·emulator·sdkmanager 없음. 기존 Android Studio 설치 항목 없음.
- 공식 Pleos Windows 이미지 목록은 인증 없이 HTTP 200으로 조회됨. 패키지 system-images;android-34;pleos_car_emulator;x86_64, v2.1.0, revision 2, ZIP 약 4.09GB.
- 음악 API 환경변수는 현재 프로세스에 없음. 사용자는 Suno 계정은 있고 API 키는 없다고 답함.
- ECC search-first·verification-loop, 활성 Superpowers executing-plans 및 개인 goal-aligned-development를 읽고 적용. 대상과 무관한 웹 테스트·80% 커버리지는 도입하지 않음.

## 공식 근거

- [공모전 개발 환경](https://appdevchallenge.auto-hmg.io/guide/development)
- [공모전 개발 범위 공지](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008): 공개 SDK는 워크스페이스 준비 중 표시와 별개로 에뮬레이터 시험 가능. Pleos LLM·Gleo 승인 제외. 실제 차량 불필요.
- [에뮬레이터 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator): API34 Pleos 이미지·AVD RAM 4GB·cold boot.
- [환경 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator): GPU 요건, cold boot와 VHAL.
- [Virtual Console](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator): ADB 포트 전달 후 127.0.0.1:8088.
- [Vehicle 샘플](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app): JDK17·Gradle8.10.2·AGP8.8.2·Kotlin2.0.0·SDK35. 샘플 minSdk23은 제품 요구 minSdk28과 구분.
- [Suno 공식 MCP](https://suno.com/mcp): API 키 없이 계정 연결, 기존 크레딧 사용. 실제 연결·크레딧·앱 내장 허용은 미확인.


## 중간 결과

- Android Studio 2026.2.2.8 설치 완료. JDK Temurin 17.0.21+9, Android SDK Platform35·Build Tools35.0.0·Platform Tools37.0.1·Emulator37.2.13 설치 확인.
- emulator -accel-check: WHPX installed and usable. GPU 최소 요건 충족과는 별개다.
- 공식 FusedLocation 샘플: assembleDebug 성공, APK 생성. 타깃 수신은 아직 미검증.
- 공식 Vehicle 샘플 main 959981d960ec71144accb27af5ae5d341e72a644는 SDK 2.3.1을 참조하지만 Maven POM이 404. 문서의 2.3.2는 200. 검증 복사본의 버전 한 줄만 2.3.2로 바꾼 뒤 assembleDebug 성공(BUILD SUCCESSFUL, 35 tasks).
- 공식 Pleos 이미지 4,087,751,964 bytes 다운로드·SHA1 검증 완료. 압축 해제와 설치 완료. 이미지 내부 메타데이터가 android-automotive 태그를 사용하므로 AVD manager의 인식 결과에 맞춰 SDK 내부 설치 경로·package.xml 경로를 정렬했다.
- Suno MCP는 사용자 승인 뒤 연결을 시도했지만 계정이 AI 앱 연결 대상이 아니라는 메시지로 차단됨. 비공식 API로 우회하지 않음. 일반 웹 생성은 사용자가 원하지 않아 중단. 임시 MCP 등록도 정리.
- 사용자가 브라우저 로그인은 Vivaldi를 선호하며, 이 작업은 CLI로 충분하다고 지적. 이후 컴퓨터 제어 중단.
- 사용자는 Lyria3.5 공식 API 최대 2곡 생성 승인. 새 결제수단·구독·충전은 승인하지 않음. 키는 미설정이며 기존 과금 프로젝트 여부는 미확인. 실제 호출은 대기.
- validation/music-probe: 키를 Windows 사용자 암호화로 로컬 저장하는 도구와 API probe 준비. 보컬·무가사 dry-run 성공. 실제 호출·과금·실제 음원 없음.
- API 키 준비가 오래 걸리면 독립 검증을 마친 뒤 멈추고 재개 방법을 안내하라는 사용자 지시를 받음.


## 이번 실행 종료 시점

- 전용 AVD `TodaysTrack_Pleos_21`, RAM4096MB·2 cores·cold boot·headless로 기동.
- 전용 ADB server5038, emulator console5660/adb5661 사용. 다른 연결 기기에는 명령을 보내지 않았다.
- `adb -P 5038 -s 127.0.0.1:5661 shell getprop sys.boot_completed` → `1`.
- fingerprint: `Pleos/crp_avd/crp_avd_x86_64:14/RELEASE.release_connect_v2.1.0/release_connect_v2.1.0:user/test-keys`.
- Vehicle·FusedLocation 두 APK 빌드 성공. Vehicle APK 설치 Success 및 패키지 조회 확인.
- Virtual Console HTTP200 응답 확인. SDK 실제 값 수신과 곡 생성·청취 품질은 아직 확인하지 않았다. 부팅과 APK 빌드를 SDK 연동 완료로 간주하지 않는다.
- API 키가 준비되면 보컬·무가사 각각 한 번, 최대 두 번 호출하는 승인 범위로 재개한다. 새 결제 설정은 하지 않는다.

### 사용자 재개 방법

1. Vivaldi로 https://aistudio.google.com/api-keys 에서 기존 과금 프로젝트의 API 키를 준비한다. 새 과금 설정이 필요하면 진행 전에 알려준다.
2. PowerShell에서 아래 명령을 실행하고 숨겨진 입력란에 키를 붙여 넣은 뒤 Enter. 키는 채팅에 보내지 않는다.

```powershell
& 'C:\Users\xiui\Documents\Projects\TodaysTrack\validation\music-probe\Set-MusicApiKey.ps1'
```

3. `키 설정했어, 검증 이어가줘`라고 요청한다. 키 저장만으로는 생성·과금이 발생하지 않는다.
4. 실제 음악 호출 및 차량 데이터 수신 검증은 아직 남아 있다. 현재 작업을 제품 구현 완료나 기술 검증 전체 완료로 보고하지 않는다.

- 최종 샘플 설치: Vehicle·FusedLocation 모두 install Success. Vehicle MainActivity 시작 명령 성공(화면·SDK 값은 미검증). 전용 AVD와 8088 포트 전달은 종료 시 정리.




## 2026-11-12 키 설정 후 재개 결과

- 사용자 제공 Google 프로젝트 표시명: TodaysTrack, 프로젝트 참조 projects/650390370117, 프로젝트 번호 650390370117. 키의 실제 소속을 별도 관리 API로 검증한 것은 아니다.
- 저장된 DPAPI 키 끝의 개행으로 복호화가 실패해 읽기 시 Trim 처리. 키 재입력 없이 해결했으며, 해당 실패는 네트워크 호출 전이었다.
- 무료 등급 상태에서 첫 생성 요청은 HTTP402 Payment Required로 거절. 오류 본문이 배열인 경우 처리 문제도 확인해 수정. 키로 모델 메타데이터 GET은 HTTP200.
- 사용자가 무료 등급이라고 확인한 뒤 충전 완료 및 재시도를 지시했다. 새 결제 작업은 에이전트가 수행하지 않았다.
- 충전 후 보컬 생성 성공: 응답 36.920초, MP3 90.59초, 44,100Hz·2채널·192kbps, 2,180,348bytes.
- 무가사 생성 성공: 응답 23.794초, MP3 90.88초, 44,100Hz·2채널·192kbps, 2,188,837bytes.
- 총 요청 3회 = 402 거절 1회 + 음원 반환 2회. 승인된 실제 생성곡 최대 2곡을 소진. 추가 생성은 자동으로 실행하지 않는다.
- 공식 단가 기준 두 곡 생성료 추정 $0.16(세금 별도). 실제 청구 내역은 조회하지 않았다. 선불 충전액과 생성료는 별개다.
- 결과: validation/music-probe/results/{vocal,instrumental}/output.mp3, generated-text.txt, request.json, journey.json, result.json. audio-metadata.json은 mutagen으로 읽은 MP3 헤더 정보이며 전체 디코딩/청취 품질 검사는 아니다.
- 입력은 63km·90분·출발→카페/전망대→복귀의 가상 기록이다. 실제 차량 SDK 수집값을 AI에 보낸 것은 아니다.
- 반환 텍스트에서 한국어 가사와 카페·전망대·복귀 소재 확인. 그러나 입력에 없는 '차가운 공기', '우리' 표현이 포함되어 사실 통제가 완전하지 않다. 직접 들은 품질이나 발음·피아노 구성·무가사 준수는 확인하지 않았다.
- 두 호출은 보컬/무가사 형식 검증용이며, 주행 특징별 인과 관계·동일 설정에서 서로 다른 여정의 차이를 입증하는 A/B 시험은 아니다.
- 재생용 사본: Codex outputs/music-validation/TodaysTrackVocal.mp3 및 TodaysTrackInstrumental.mp3.

### SDK 검증 접근 변경

- 기존 공식 Compose 샘플 화면의 접근성 덤프가 null root 또는 지도 창만 반환. SDK 콜백을 확인한 것으로 간주하지 않았다.
- 공식 샘플 복사본에 별도 ValidationProbeActivity를 추가해 SDK 초기화·Odometer read·FusedLocation 콜백을 Logcat/내부 JSONL로 관찰하는 작은 검증만 진행한다. 제품 앱 구현이 아니다.
- 샘플 API는 내려받은 SDK의 공개 타입·메서드 서명 및 공식 샘플 사용법에 맞췄다.
- 에뮬레이터와 Gradle 동시 실행 중 호스트 여유 RAM 약695MB 확인. 전용 AVD를 종료하고 빌드 후 재부팅하는 순서로 바꿨다. 다른 프로젝트 워크로드는 조작하지 않았다.

### SDK 최종 결과 및 검증 범위

| 항목 | 실제 관찰 | 판정 / 한계 |
| --- | --- | --- |
| Vehicle2.3.2 SDK 빌드 | 작은 probe 포함 assembleDebug exit0, 1m41s | 빌드 확인 |
| FusedLocation1.1.3 SDK 빌드 | 작은 probe 포함 assembleDebug exit0, 46s | 빌드 확인 |
| 누적 주행거리 | capability READ, 최초0.0 → 콘솔 입력12345.0 → getCurrentDistanceDriven 콜백12345 | VERIFIED: Pleos 에뮬레이터→SDK 입력 일치 |
| 구간 주행 시간 | getTripCurrentDrivingTime → ERROR_NOT_SUPPORTED_PROPERTY | 현재 SDK/이미지 조합에서 FAILED: 직접 활용 불가 |
| 구간 주행 거리 | getTripCurrentDrivingDistance → ERROR_NOT_SUPPORTED_PROPERTY | 현재 SDK/이미지 조합에서 FAILED: 직접 활용 불가 |
| 위치 지원 | isSupported=true, 초기 캐시 좌표 반환 | 캐시 조회 확인 |
| 위치 연속 갱신 | Android 테스트 GPS (37.4979,127.0276) → (37.501,127.03), 동일 두 좌표가 FusedLocationListener.onChanged로 순차 반환 | VERIFIED: 합성 위치→Pleos SDK 갱신 |
| 실제 주행/곡 전체 연결 | 가상 여정으로 AI 곡, SDK 데이터는 별도 probe | NOT PERFORMED: 실제 주행 기록에서 음원까지 연결한 검증 아님 |
| 청취 품질 | MP3 헤더 44.1kHz/stereo·길이 확인, 두 파일 사용자에게 재생 제공 | NOT ASSESSED: 직접 청취 품질·가창·구조·무가사 준수 미평가 |

- SDK 로그: validation/evidence/vehicle-sdk-callbacks.log, location-software-sdk-callbacks.log, location-sdk-results.jsonl.
- 콘솔 입력 및 응답: odometer-input.json. `/browser/session` 활성화 없이 값을 쓰면 emulator_inactive 응답이므로, 공식 클라이언트가 사용하는 세션 절차를 확인 후 입력했다. 최초 실패를 성공으로 세지 않았다.
- 처음 emulator geo fix는 OK를 반환했지만 플랫폼/SDK 새 좌표까지 전달된 근거는 없었다. 이 방식은 성공으로 보고하지 않는다.
- host GPU 실행이 종료되고 ADB offline 상태가 됐다. Windows 해당 앱 오류 로그에서 원인 확증을 얻지 못했으므로 GPU가 원인이라고 단정하지 않는다.
- 지원되는 `-gpu software` 모드로 재부팅 후, Android `cmd location providers` 테스트 GPS 공급자를 사용했다. 물리 GPS나 실제 차량 주행 검증은 아니다.
- 사용자가 승인한 설치·검증 범위에서만 수행했으며, Windroid나 실제 사용자 차량·다른 연결 기기에 명령을 보내지 않았다.

### 설계에 반영할 확인 사항

- 기본 방향인 주행 후 AI 음원 생성은 실제 공식 API에서 가능했다. 이번 두 샘플의 반환 시간은 약24~37초이며 서비스 전체 지연이나 일반적인 SLA를 입증하지 않는다.
- 기록 수집은 누적거리 스냅샷 차이와 앱의 경과 시간, 위치 갱신으로 구성할 근거가 생겼다. 정차/주행 구분·앱 종료·재부팅·날짜/여행 경계·백그라운드 지속 수집은 아직 검증하지 않았다.
- 구간 시간·거리 API를 필수 의존으로 가정하지 않는다. 시간은 앱에서 기록하고 누적거리 차이를 사용할 경우 실제 의미·초기값·리셋·누락 처리 검증이 필요하다.
- 주행 기록의 사실 추출/가사 작성 단계와 음악 생성 단계를 분리하거나, 제공 가사를 그대로 노래하게 하는 방법은 향후 설계 후보이다. 현재 가사 사실성 문제를 해결한 것으로 보고하지 않는다.
- 이번에는 기존 계획·제출용 문구나 7번 참고자료를 변경하지 않았다. 음원·probe는 제품 완성본이나 제출용 개발 산출물 완성을 의미하지 않는다.
- 새 기능 테스트 스택·커버리지 기준을 만들지 않았다. 실제 API 응답, SDK 콜백, APK 빌드 및 MP3 메타데이터로 이번 작은 검증 범위를 확인했다. 자체 검토이며 독립 리뷰는 수행하지 않았다.

- 종료 정리 완료: 테스트 GPS 공급자 제거, shell mock-location 권한 default 복원, 검증 Activity 중지, 전용 AVD 종료 및 ADB TCP 연결 해제. 설치한 도구·검증 파일·APK는 재사용을 위해 보존.


## SDK 기록 → 곡 통합 검증 (추가 승인)

- 사용자 요청: 남은 검증을 이어서 SDK로 주행 기록을 모으고 그 기록으로 곡 생성까지 확인.
- 추가 음악 승인: 기존 잔액에서 Lyria 추가1곡, 약$0.08(세금 별도), 자동 재시도·충전 없음. 앞선3요청/2곡 원장은 보존하고 전체 상한은4요청/3곡으로만 확장한다.
- E1: 하나의 작은 Android 검증 Activity에서 Vehicle/FusedLocation SDK 값을 타임스탬프와 함께 JSONL로 지속 저장.
- E2: 전용 AVD에서 합성 왕복 경로·누적거리 변화·정체 구간을 입력하고 SDK 기록과 비교. HOME 상태의 짧은 수집과 강제 종료 후 저장파일 복구를 관찰. 프로세스 종료 동안의 누락은 표시.
- E3: SDK 원본만으로 거리 차이·관측 시간·위치 순서·관측 공백을 요약하고 원본 해시를 작곡 입력에 연결. 경유지 이름·날씨·동행자·감정은 추정하지 않는다.
- E4: 새 원본에서 만든 입력으로 승인된1곡만 실제 생성, 로컬 MP3 저장·재생 확인. 실제 차량/장시간 수집/완성 제품 검증과 구분한다.
- ECC verification-loop 및 활성 Superpowers executing-plans, 개인 goal-aligned-development를 사용해 한 명이 순차 실행한다. 제품 설계·구현 확대, 제출용7번 변경, Windroid 개발, 상주 작업자/훅, 자동 개인 메모리 저장은 하지 않는다.

### E1~E4 통합 검증 결과

- 실제 성공 세션: sdk-journey-20261112-071730. APK의 JourneyProbeActivity가 Vehicle/FusedLocation 콜백을 앱 내부 파일에 저장했고 Python이 이를 가져와 요약했다.
- SDK 검사 지점6개 일치: 누적거리20004→20010km, 차이6km. 상대 경로 A→B→C→C→B→A, 관측 범위53.414초. 관측은 가속된 합성 시나리오이며 실제 주행 시간으로 확대하지 않는다.
- 강제 종료 뒤 같은 세션 파일 접두부 보존과 재개 확인. 18.323초의 관측 공백을 요약에 표시했다. 그 동안 계속 수집됐다는 주장은 하지 않는다.
- SDK 원본 해시→여정 요약→실제 전송 본문 해시→생성 MP3 해시의 연결을 검증했다. 정확한 좌표는 로컬 증거에만 두고 외부 입력은 A/B/C와 거리·관측 순서만 사용했다.
- Lyria 추가 승인1곡 성공: 응답27.617초, MP3 99.71초, 44.1kHz/stereo/192kbps, 2,399,150bytes. FFmpeg 전체 디코딩 exit0.
- 기록에 근거한 가사를 템플릿으로 준비해 제공했으며 반환 텍스트9줄 모두 포함. AI 가사 자동 작성이나 실제 가창·청취 품질을 검증한 것은 아니다.
- HOME 및 별도 Settings 실행 뒤 SDK 조회는 관찰했으나 Activity paused/stopped가 관찰되지 않아 실제 백그라운드 상태 검증은 미완료. Activity·프로세스가 종료돼도 계속 수집하는 제품 기능은 만들지 않았다.
- 이번 요청의 SDK 기록→곡 연결은 확인. 제품 전체 검증이나 실제 차량 검증 완료는 아니다. 상용 백그라운드/전원 복구·자동 여정 경계·폰 동기화는 남아 있다.
- 진단: 호스트 대비 기기 wall clock이 약281~542ms 뒤져 처음 새 GPS 이벤트 판정 실패. SDK elapsedRealtime 기준으로 수정. 콘솔의 traveled_distance_must_not_decrease 오류에 맞춰 현재 누적거리 위에서 새 시나리오 시작. 종료 broadcast의 빈 marker 인수만 수정해 완료된 같은 수집을 정상 종료했다.
- SDK 파일/요약: validation/journey-probe/runs/sdk-journey-20261112-071730/. 음악 요청·결과·trace: validation/music-probe/results/sdk-journey/. 최초 부분 실패와 별도 백그라운드 시험도 runs/에 보존.
- 이 시점의 음악 원장: 총4요청=402거절1+생성3. 이번 추가1곡 승인 소진, 추가 호출 없음. 출력 파일과 상세 결과는 Codex outputs/music-validation/SdkJourneyReport.md 및 TodaysTrackSdkJourney.mp3.


- 이번 통합 시험 종료 정리: 테스트 GPS 공급자 제거, shell mock 권한 default 복원, 수집 앱 중지, 콘솔 세션·8088 전달 종료, 전용 AVD 종료 요청 및 ADB TCP 연결 해제. 추가 음악 호출 없음.

## S1~S4 제출 구현 착수 — 2026-11-12

- 이번 채팅의 실제 사용자 실행 지시로 S1~S4의 구현/로컬 빌드/전용 에뮬레이터/영상/패키지 준비 착수. Goal active. inline 한 명, 묶음 자체 검토, Guard 미사용. Windroid 중단 유지.
- 같은 실행에서 추가 사용자 승인: 현재 충전 총 **USD 5 이내 음악 생성**, 호출 횟수 제한 없음, 실패/재시도 비용도 합산. 추가 충전/자동결제/별도 유료 가사 API 금지. 기존 보호 저장 키 재사용, 접수 불명 자동 재송신 금지. 외부 게시/업로드/메일/클라우드 배포 미승인. 상세 승인 원문/적용은 implementation-plan.md 현재 실행 기록.
- 라이브러리/환경/제출 대안은 근거를 남겨 자율 판단하며 사용자 부재 동안 독립 작업 지속. 실제 백그라운드 불가 시 제한 시연을 선택할 수 있으나 제품 요구/후속 항목은 보존.

### S1 작은 실제 비가시 실험

- 기존 JourneyProbeActivity/journey.py/음원과 run 원본 보존. 별도 LifecycleProbeActivity/Service/Journal 및 lifecycle.py 추가. location foreground service가 Vehicle/FusedLocation을 소유하고 Activity는 12초 뒤 종료, 서비스는 최대 120초 뒤 종료하는 유한 실험.
- 빌드: validation/evidence/lifecycle-probe-build.log 및 lifecycle-probe-build-second.log, assembleDebug 성공. 활성 Superpowers executing-plans/TDD 및 개인 goal-aligned-development, ECC Kotlin coroutines/Android guidance 적용. Git 저장소 없음, commit/worktree를 만들어 있다고 주장하지 않음.
- 환경: Pleos 2.1.0 · Vehicle 2.3.2 · FusedLocation 1.1.3 · target/compile35 · JDK17 · AVD TodaysTrack_Pleos_21, software GPU, ADB5038, serial127.0.0.1:5661, Android user10. probe minSdk23과 제품 minSdk28은 별개.
- 첫/세 번째 실행의 거리 입력 HTTP503은 실패로 보존. 두 번째 실행은 이전 서비스가 남아 새 ready가 없었음. 동일 실패 반복 후 console-s1-diagnostic.json의 좁은 진단으로 활성 세션을 유지하면 PUT 성공함을 확인. 3초 세션 만료/활성화 안정화 의존을 유한 heartbeat로 격리, 실험 간 force-stop·앱 내부 정상 stop 경로 적용. 상주 worker가 아님.
- 성공 run: **validation/journey-probe/runs/lifecycle-20261112-101504/**. Activity stopped=855562ms, destroyed=855642ms 뒤 신규 위치=857938ms, 신규 거리2km=858715ms. 같은 PID31237. 입력 controls·SDK raw·외부 activity/service dumps·정상 sdk_released/service_destroyed 연결 확인.
- raw SHA256: 2147b107d692b63dbedbc2ce86c69e1ffd510b637c19ba4c6983ea126e9b16f1. result.json 해시와 마지막 Activity 종료 이벤트까지 담은 raw 파일의 해시는 종료 직후 다시 대조할 것.
- **판정:** 이 유한 합성 환경에서 비가시 후 실제 SDK 신규 입력 지원 확인. 제품 Room 수집 완성·실도로·장시간·전원/재부팅 복구의 증거가 아님. 기존 ‘실제 백그라운드 미검증’의 이 작은 범위만 갱신하며 P0/P1/P6 전체 완료로 승격하지 않음.

### S1 제품 진행 / 미완료

- 새 vehicle/ 프로젝트: 서비스 SDK 소유·제한 Channel→단일 writer→Room 이벤트/epoch/공백 transaction, UI는 읽기, 플랫폼 CarUxRestrictions 확인 전 설정/시작 보류, 하루/여행·가사/장르 기억. 로그에 원본 좌표 출력 안 함, backup 비활성.
- 수집 정책 3개 테스트가 스텁에서 실패한 뒤 구현, s1-product-build.log에서 testDebugUnitTest/assembleDebug 성공. 새 요약/범위 테스트는 s2-summary-red.log에서 3개 실패 확인 후 구현 중. 자체 테스트만으로 실제 사용자 흐름 완료라 하지 않음.
- 첫 제품 실제 UI/Room 시험에서 신규 위치는 저장됐으나 거리 응답은 SecurityException. 실패 원본은 product-latest.json이 가리킨 s1-product run에 보존. 로컬 Vehicle2.3.2 Odometer @RequiresPermission을 확인해 정확한 CAR_DRIVE 선언으로 수정(CAR_MILEAGE만으로 부족). 수정한 APK/동일 사용자 흐름 재검증 필요.

### S1 제품 권한 수정 후 관측 / S2~S3 통합 중

- vehicle 소스의 CAR_DRIVE 수정·수집/요약/범위 총6개 테스트 및 assembleDebug: validation/evidence/s1-s2-build.log 성공. 당시 APK SHA256 3e28a6b5bd8747ee5a87d86063d635eeac042bc946af96d2b67789d9b467a49c.
- 제품 run **validation/journey-probe/runs/s1-product-20261112-102757/**: 실제 UI 시작/마무리, 합성 위치/거리 신규 입력 저장, Room integrity_check=ok, 중단 전 97개까지의 이벤트 접두부 보존, 열린 epoch 복구 gap, 정상 정지 뒤 모든 epoch 닫힘. 결과/외부 입력/DB 복사본/원본 JSON은 로컬 보존. 정확한 좌표는 제출 영상/외부 AI 입력에 넣지 않음.
- 이 run의 BACK 뒤 dumpsys는 MainActivity RESUMED/visible=true였으므로 **제품 비가시 수집 성공으로 계산하지 않는다**. 명시적 ‘기록은 계속·화면 닫기’(finishAndRemoveTask) 경로를 추가해 다음 APK에서 별도 확인한다. 작은 LifecycleProbe의 비가시 성공과 구분한다.
- Room v2에는 원본을 삭제하지 않는 1→2 migration, SDK confidence 원값의 별도 열(거리 정확도라고 해석하지 않음), 고정 생성 intent/보관곡 테이블을 추가했다. 이 migration의 실제 기기 검사 필요.
- S2 서버: Kotlin/Ktor+로컬 SQLite JDBC/WAL/FULL. PostgreSQL/Docker 실행 의존을 제출 선행 조건으로 만들지 않는 제출용 선택은 implementation-plan.md에 근거 기록. 운영 PostgreSQL/다중 worker/완성 로그인은 후속 보존.
- server 실제 DB 멱등/다른 본문 거절·송신 이후 재시작 RECONCILING/자동 재송신 차단·USD5 budget 3개 테스트가 스텁에서 실패한 후 통과(s2-server-red/green.log). 등록 EC 공개키 인증·본문 변조·nonce 재생·가짜 CAR 거절 테스트 추가 후 s2-server-api-build-second.log에서 총4개 통과/실행 디렉터리 빌드. 이는 실제 차량 HTTPS 연결 완료가 아님.
- 로컬 TLS private key/password는 server/.local 보호 저장 및 DPAPI, APK에는 공개 인증서만. 음악 키는 기존 DPAPI만 재사용하며 출력하지 않음. 신규 음악 유료 호출은 아직 **0회**. dispatch.json이 없으면 FIXTURE, 같은 intent는 기존 job을 재사용한다. 기존 소진 probe 원장은 보존.
- 음원 전체 디코딩/영상용 FFmpeg7.1을 imageio-ffmpeg0.6.0 패키지로 tools/.local/media에만 설치. 전역 PATH/상주 worker/훅 변경 없음.
- Media3·유한 WorkManager 전송/다운로드·TLS/기기서명·대역 상태 표시를 통합한 vehicle APK 빌드/6개 로컬 테스트 성공(s2-s3-vehicle-build.log). mode 표시 등의 후속 수정은 s2-s3-vehicle-final-build.log로 다시 확인 중. 대역 저장/재실행·오프라인 재생은 다음 실제 통합 시험으로 남음.

### S1 v2 / S2 실제 HTTPS / S3 대역 저장·오프라인 검증

- s2-s3-vehicle-final-build.log: 현재 v2 APK 6개 테스트/assembleDebug 성공. s2-server-ready-build.log: 서버 4개 테스트/실행 디렉터리 빌드 성공. 서버 key/secret 출력 없음.
- v2 제품 run **s1-product-20261112-105319**: 129개 이벤트, Room integrity 정상·중단 접두부 유지·공백 복구·정상 epoch 종료 확인. v1 run의 97개 기존 이벤트 모든 원래 필드가 v2에 그대로 남음을 독립 Python 대조했다. confidence 신규 열은 원래 값 복사, 기존 열은 보존.
- 명시적 화면 닫기 뒤 after-back-activities.txt에서 제품 Activity는 없음. 그 비가시 입력 구간의 신규 B 위치 event seq115/receivedElapsed3151709ms를 해당 run controls 시간 범위와 대조했고 거리 입력도 새 epoch에 저장됐다. 첫 v2 driver는 재진입 시 접근성 포커스가 DrivingView에 남아 중단됐다. 실제 screenshot/Activity 상태로 앱 crash와 구분 후 앱의 관측된 제목 영역에 포커스를 맞추고 실제 버튼을 기다리는 유한 driver로 변경했다. 새 SDK 입력 없이 성공으로 세지 않았다.
- 실제 HTTPS 인증 없는 GET은 TLS 공개 인증서 신뢰 검사 후 HTTP401(s2-https-unauthorized.json). 전용 AVD public EC key를 명시 등록한 앱은 고정 snapshot→quote→job을 실제 API로 연결했다.
- **fixture-integration-20261112-105720**: 중복 클릭 2회에 작업1개, job **15dc8739-1846-45a2-a2cf-8e71956a4b78**, mode FIXTURE/cost0. 서버 전체 FFmpeg 디코딩→READY→차량 다운로드→AVAILABLE, 입력 hash 및 서버/차량 MP3 SHA256 **44a6b02523a1cbfd6c188df68299eedbc0ddd51222f3a5ed6f0d1d334ba7c48f** 일치. 원래 SDK-journey의 기존 MP3이며 새 AI 생성 아님.
- 첫 오프라인 시험은 비행기 모드가 Bluetooth도 해제해 Pleos host의 오디오 중단 변수를 만들었고 재생은 PAUSED/0초였다. 이 실행을 재생 성공으로 세지 않음. Wi-Fi만 끄면 mobile-data 기본 네트워크가 남음을 확인했다. 네트워크만 끄는 접근으로 바꿨으며 포커스 정책을 우회하지 않았다.
- **offline-playback-20261112-110611**: Wi-Fi+mobile data 끄기, Active default network none 확인, 앱 force-stop/재실행 뒤 저장 대역곡 재생. 대상 package MediaSession의 PLAYING 위치 **2994→6018ms** 증가, 사용자 pause 뒤 PAUSED, 신규 작업0. 네트워크 원상 복구. 실제 내비/전화 공존 및 실제 청취 품질은 미평가.
- 두 독립 실행의 input/audio hash·DB 상태·실제 오프라인 결과를 대조한 **validation/evidence/s3-fixture-gate.json**으로 대역 저장·재감상 선행을 확인했다. 이번 실행의 신규 음악 유료 호출은 여전히 0. 이후 USD5 승인 범위의 새 SDK 입력·새 곡으로 진행 가능하며 대역을 실제 새 곡으로 표시하지 않는다.
- S1/S2 제출 핵심 경로의 묶음 자체 검토. 독립 reviewer/Guard 판정은 없으며 전체 P0~P8·제품 완료가 아니다. release 별도 package/signing 경로는 현재 빌드 중, 설치/실행 검증은 남음.

### S3 release 대역 선행 → 실제 새 AI 음악

- 이전 단락의 ‘신규 음악0/실제 생성 미완료’는 당시 이력이다. 현재 사용자 총 USD5 음악 승인 내에서 아래 **신규 송신1회**를 수행했다. 실패/재시도0, 별도 유료 가사0, 충전/자동결제0. 기존 probe 원장은 수정하지 않았다.
- release 별도 package `dev.todaystrack.vehicle.submission`의 설치별 공개 EC 키를 UI의 공개 등록 dialog로 받아 명시 등록했다. release run-as/private key 추출 없이 등록했고 TRIP/VOCAL/어쿠스틱 설정 재실행 보존을 확인했다.
- `release-fixture-20261112-112450`의 새6km SDK 기록 보존 후 `release-fixture-resume-20261112-114007`에서 같은 요약을 FIXTURE로 연결. job195b2af6-b543-457b-a77f-d46ec8e39502 READY/차량 AVAILABLE. `offline-playback-20261112-114309`에서 앱 재실행·기본 네트워크 none·재생2624→5601ms·pause·신규job0을 확인해 release 대역 선행도 충족했다.
- 제공자 가사 사실 템플릿에 실제 distance/return 조건을 넣고 genre를 명확한 음악 방향으로 연결했다. CompositionPlanTest2개 red→green. 서버 실제 DB/서명 포함 총6개 테스트(s3-composition-green.log), 차량 총6개 테스트 및 release 빌드를 확인했다.
- 실제 새 입력 run: **validation/journey-probe/runs/release-live-20261112-115006/**. 합성 입력6지점 A-B-C-C-B-A, 연속 SDK 누적거리 차이8km, 사진/메모 없음. 관측 seq27,30,33,36,40,43과 관측 범위32178ms를 고정 snapshot에 연결했다. 이 시간은 가속된 관측 범위이며 실제 운전 시간이 아니다. 원본 SDK 좌표/절대 누적거리는 외부에 전송하지 않았다.
- selected observation canonical hash `a7d78de12316714feca4ee4775d1e493ee692faaff8db8471b3082c7b52501a0`, coverage hash `d0377e5c8a65f07ea6bc30207a2f66ec0b92ca1cf6edc7e802a0ec7601529676`. 전체 DB 파일 해시와 혼동하지 않는다.
- 차량 UI에서 새 기록의 첫 생성 시작→Lyria3.5 실제 API→job **2ee78914-a23c-4320-8dd6-558e1a74dd00** READY→차량 다운로드/AVAILABLE. inputHash **32a9ebc8d6ca7a2853ca5a2c5d24e4ff899001a5cf814b728d8077a041715bc3**, requestHash **e97ade362ab5953f1a8006d1795f2a59f5b3eb75450689203f8267f16fe611f5**, MP3 SHA256 **ea0287f39c76dbd32c3d14d46d0289a2091f0e321c39198303359ca571c89712**. 기존 대역 해시와 다름.
- MP3:2174417bytes/90.279초/44.1kHz/stereo/192kbps, FFmpeg 전체 decode exit0. 공급 가사7개 사실 문장(8km/귀환 포함)이 반환 텍스트에 포함됐다. **반환 텍스트는 실제 가창 정확도·음악 품질을 입증하지 않는다. 청취 미평가.** 디코딩 신호는 무음이 아니며 원본 MP3를 보존했다.
- `offline-playback-20261112-115415`: 같은 release 앱 강제 종료/재실행, Wi-Fi+data off 및 default network none, 새 저장곡 PLAYING2887→5893ms, 사용자 pause, 추가 job0. 저장곡 재감상에는 서버를 사용하지 않았다.
- 현재 원장: 기존3곡 보수적 USD0.30+새 attempt USD0.10=**예약 USD0.40**. 신규1곡 공식 기본 가격 추정 USD0.08이며 실제 청구/세금/잔액 조회와 구분한다. READY 후 dispatch.enabled=false로 닫았다. 승인 총액 USD5를 소진하려고 추가 생성하지 않는다.

### S4 최종 APK·영상·분리 소스 재현

- 생성 당시 release0.1.0 APK SHA256 `6331dc06c2bbf7098ad2758decc4acbb253b68133225a68ad16a3af3751942ba`를 `validation/evidence/music-generation-release-v0.1.0.apk`에 보존했다. 최종0.1.1(2)은 재생 위치/상태·기록 없는 생성 버튼·수집 오류 경고 유지 수정이며 SDK/저장/제공자 경로·음원은 동일하다.
- 최종 **apk/TodaysTrack-release.apk** SHA256 **50ec7b72a8b44777d23989fc62c0e3764abad0c56ce57f8cf920f7f51d08b411**. 서명 certificate SHA256 `56fd311ccd45d0dd255a6241701175f7a530a668cb243d609c51962d310c0490`. s4-release-final-build.log:6개 테스트/assembleRelease/lintVital 성공, 설치 업데이트/실행·기존곡 유지·DEBUGGABLE 없음 확인. signing private key/DPAPI는 제출 제외.
- **replay-frames-20261112-122740**에서 최종 APK force-stop/재실행 후 Wi-Fi/data off, default network none, 실제 새 저장곡 버튼1회, PLAYING부터 STOPPED/90289ms까지 관측했다. 음악 신규job0. 수집은 유한 약1fps ADB 화면 캡처이며 네트워크를 finally 복구했다. 이는 청취 평가가 아니다.
- software GPU+Android screenrecord에서 화면 백지(OpenGLRenderer dequeueBuffer timeout/1x1 fallback)와 오디오 포커스 지연으로 PAUSED0ms 사례가 있었다. 해당 실패도 보존했다. ANR/정상재생으로 계산하지 않고 데이터 삭제 없이 재시작으로 UI를 복구했다. 외부 focus grant372ms/no receiver는 [AOSP AudioManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/media/java/android/media/AudioManager.java)의250ms 대기와 부합하는 지연이라는 추론이다. 플랫폼 포커스를 우회하지 않고 **Android 인코더 없는 PC 화면 캡처**로 접근을 바꿔 끝까지 재생했다. 실차/내비/전화 일반 호환 완료가 아님.
- **submission/TodaysTrack-demo.mp4**:1920×1080/H264/AAC,168.94초. 실제 생성 원본 화면과 최종 release 오프라인 재생을 편집했다. 수집1.8배속·대기 컷·약1fps/간격 보정을 표시했다. 소리는 같은 생성 파일 후삽입이며 직접 기기 오디오 녹음이 아니다. 영상만 volume0.65, 원본 MP3 불변. 편집 전/후 JSON checkpoint와 ASS/렌더 코드를 보존했다.
- 영상 전체 FFmpeg decode exit0, 요약/재생 프레임 가독성 확인. 격리 로컬 Chrome 실제 재생66.000376→67.761125초, paused=false/error=null/readyState4 확인(s4-browser-video-playback.json). 자동 검증은 muted이며 인간 청취를 주장하지 않는다. 첫 mux의 무한 audio pad 가능성을 확인해 명시적 영상 길이/600초 subprocess 한도로 변경했다.
- 소스 허용목록169개를 zip으로 만든 뒤 **submission/repro-check/**에 별도 추출했다. 원본과 분리된 TLS/서명 초기화→release 테스트/빌드 **3m17s 성공**, 서버6개 테스트/installDist **57s 성공**. s4 재현 로그는 submission/repro-release-build.log 및 repro-server-build.log. 기존 실제 설치/데이터·키를 교체하지 않았다. 새 서명/인증서이므로 제공 APK 바이트 동일 재현은 주장하지 않는다. Gradle8.10.2/AGP8.8.2/JDK17, SDK XML 도구 경고 및 kapt1.9 fallback은 빌드 성공과 별도로 기록한다.
- 공식 제출 안내/FAQ를 다시 읽고 빈 접수·동의서 DOCX만 내려받았다. 개인정보·개별 동의/서명·실제 팀명·외부 저장소/링크·메일 발송은 미작성/미실행. 기획 원문을 고치지 않고 submission-checklist.md에 로컬 초안을 준비했다. 실제 접수와 패키지 준비는 구분한다.
- 최종 sourceRevision/패키지 해시·원문 보존/비밀정보 검사·분리 재현 증거는 **submission/package/manifest.json**, reproduction-check.json, submission-final-checks.json에 고정한다. Git 저장소가 없어 commit SHA를 만들지 않는다. Guard·자동 에이전트·훅·상주 worker·개인 메모리·Windroid 작업 없음.
- S1/S2 핵심과 S3 실제 새 곡/저장/재감상, S4 로컬 산출물은 확보했다. 남은 **실제 가창·청취/영상 소리 품질 미평가**, 공식 양식의 사용자 정보/동의, 실제 외부 접수는 완료라고 하지 않는다. P0~P8/장시간/실차/전원/내비·전화/폰/앨범/운영 후속은 유지한다. Goal을 자체 테스트만으로 완료 처리하지 않는다.
- 종료 정리: 최종 APK 중지, 테스트 GPS 공급자 제거/mock permission default, 이 프로젝트 MainKt 로컬 서버1개 종료, 전용127.0.0.1:5661 AVD 종료, 로컬 영상 검증 탭 닫음. 다른 기기/프로젝트는 건드리지 않았고 Room/음원/서명/TLS/음악 키를 보존했다(s4-cleanup.json). 음악 dispatch는 꺼짐이다.

### 제출 완료 조건 대조 후 중단 다운로드 추가 검증

- 첫 Goal 실행 turn은 실제 코드/새 곡/영상/분리 소스 빌드/패키지 증거를 만든 progress였다. 전체 완료를 보류한 상태에서 S3의 ‘손상/중단 다운로드는 기존 자산만 다시 받음’에 실제 파일 상태 주입 증거를 추가했다. 전체 suite/유료 생성은 반복하지 않았다.
- 음악 dispatch=false, 음악 키를 읽지 않는 서버와 전용 AVD만 다시 기동. release 앱/실제 새 음악 데이터는 변경하지 않고 기존 debug FIXTURE를 백업했다. 4096bytes .part와 손상 final cache를 주입하고, 기존 jobId/inputHash를 유지한 READY outbox를 실제 UI로 재개했다.
- **validation/journey-probe/runs/download-recovery-20261112-130359/**: 같은 자산 다운로드→원래 MP3 SHA25644a6b02523a1cbfd6c188df68299eedbc0ddd51222f3a5ed6f0d1d334ba7c48f 일치→AVAILABLE. sessions/epochs/events/gaps/intents/tracks 모든 행이 원래 값과 같음. 서버 jobs3/attempts3(대역2+실제1)/예약USD0.40 불변. 신규 제공자 시도0. finally 원본 standalone DB·음원을 복원하고 다시 해시 확인.
- 이 실험은 **남은 부분 파일/손상 캐시 상태의 주입**이며 실제 전송 중 네트워크 절단으로 위장하지 않는다. 이미 검증한 release 저장/재감상 경로와 별도의 debug 동일 downloader 검증이다. snapshot의 원본 좌표/DB/백업은 로컬에만 두고 공개 패키지에는 결과 JSON/재현 Python만 추가했다.
- 첫 helper의 ADB exec-out stdin EOF 대기가 파일쓰기 및 finally 복원에서 각각 timeout이었다. 즉시 원인을 조사해 유한 adb push+run-as cp로 변경, 먼저 원본 DB 해시 복원 후 시험했다. 첫 실패는 download-recovery-130044/s3-download-recovery.log에 보존하며 제품 다운로드 실패로 계산하지 않는다. 원격 임시파일/호스트 임시파일은 제거했다.
- Windows PowerShell5 별도 child는 Microsoft.PowerShell.Security autoload 실패로 서버 기동 불가였다. 현재 정상 PowerShell7.6.9로 변경해 키 없는 서버 기동/실제 복구 성공. 개인 전역 설정을 바꾸지 않았다. 재현 안내의 명령을 실제 확인한 pwsh7로 정정했다.
- 이 후속에서 제품/서버/렌더 소스와 최종 APK/음원/영상 바이트는 바꾸지 않았다. 추가 검증 Python과 안내/증거만 소스 패키지에 반영한다. 기존 분리 빌드/12개 테스트 증거는 동일 제품 코드 revision에 적용된다.
- 최종 completion audit에서 현재 패키지22개 manifest 해시·다운로드 복구·분리 소스 빌드·실제 새 생성/최종 release 재감상·영상 재생 근거를 대조했다. 전용 서버/AVD는 실제 프로세스 조회에서도 종료 상태였다. 앞선 두 Goal turn에서도 유지된 동일 미평가 항목은 **실제 가창·음악/영상 소리 청취 품질**이다. 현재 사용 가능한 도구에는 로컬 음원을 듣고 판단할 기능이 없으며, 외부 업로드 도구를 우회 사용하지 않았다. 파일/신호 검사·반환 텍스트·muted 영상 재생을 청취 결과로 대신하지 않는다. 승인된 독립 작업은 완료했으며 청취 판단 없이는 전체 완료 근거를 더 확보할 수 없다. 추가 유료 생성·테스트 반복·상주 대기는 하지 않는다. 공식 양식 사용자 정보/동의·서명과 외부 접수는 별도 사용자 단계로 남긴다.

### 사용자 재개 지시 이후 0.2.0 제출 경계 보완 — 완료 기록/제한 명시

- 위 청취 대기로 blocked 처리한 판단은 **최신 사용자 지시로 해제**한다. 실제 청취를 deferred로 보존하며 이 항목만으로 개발을 중단하지 않는다. 추가 agent 사용이 허용돼 capture/server/journey 책임을 나눴고 Root가 UI·outbox·기기 통합을 맡았다. 6.1 Sol High 사용, 같은 파일 동시 편집 없음. Guard·훅·상주 작업자·개인 메모리·Windroid/외부 게시 작업 없음.
- 독립 코드 대조에서 실제 누락을 찾고 수정: 지연 위치 STALE/STOP-open 및 늦은 callback 차단; 위치 공백·boot/epoch·품질 경계와 같은 위치 반복 관측 범위; 이전 마감 기록 선택/기록 설정 표시; 앱 시작 미완료 intent 재개; 접수 응답 유실 by-intent 조회; 서버 VALIDATING 로컬 파일/응답 재검증 및 READY 뒤 trace 오류 비치명 처리. 기존 frozen payload/곡/원장 삭제·재생성 없음.
- Root 회귀 로그: resume-vehicle-red.log에서 새 outbox/journey 기대6개 실패, resume-server-summary-red.log에서 신규 요약 계약 기대4개 실패. 두번째 독립 리뷰의 unknown-clock/nonfinite canonical 3개 실패는 resume-clock-canonical-red.log. 이전 capture-v1 미생성 기록의 지연 FRESH도 새 요약 투영에서 보수 재평가하는 ObservationFacts 3개는 cached Kotlin/JUnit 별도 red2→green3을 확인했다. 기존 raw canonical은 원본 events를 계속 사용한다.
- 최종 호환 수정 release0.2.0(3): **차량22개 테스트·assembleRelease/lintVital 성공**, debug assemble 성공(resume-release-build.log/resume-debug-build.log). 서버 **15개 테스트/installDist 성공**(resume-server-green.log). 서버 복구/by-intent 일부는 red를 실제 관측하지 않았으며 테스트부터 작성한 사실과 실행 증거를 구분한다.
- 실제 설치 확인: release SHA256 **ee25a4def6cde84ab2dabd6ad5a3c88716940172fe4706c2b355bacd7d4f4a6f**, version0.2.0(3), DEBUGGABLE 없음, signer56fd311ccd45d0dd255a6241701175f7a530a668cb243d609c51962d310c0490. debug SHA2563a2b69027cf96b78f2b5898fe746371a27d89c7ab6a1d62bae04803ceec4217b는 별도 package/DEBUGGABLE로만 fault injection하며 release 성공을 대신하지 않는다. 설치/서명 원본은 resume-final-install.log/resume-final-installed-package.txt/resume-debug-installed-package.txt/resume-final-apk-signature.txt.
- 첫 복구 run resume-submission-20261112-144232는 AX 관측 실패, 원본 DB/음원 복원 확인. 진단 결과 새 AVD 실행 자체가 동일 AVD 중복 실행 FATAL로 거절됐고 기존 VM의 CarWatchdog/Car 서비스 종료 상태였다. 이전 종료 요청을 실제 프로세스 소멸로 확인하지 못했다. root가 5037/emulator-5660 콘솔에서 **AVD 이름을 검증한 뒤** 종료하고 emulator.exe/qemu-system-x86_64-headless.exe 소멸을 확인했다(resume-avd-actual-exit.json). 다른 AVD/기기/사용자 데이터 삭제 없음.
- 새 VM의 sys.boot_completed1/user10/uptime61.50초/CarUxRestrictionsManagerService를 확인(fresh-boot-verified.json). 두번째 run resume-submission-20261112-150044는 35초 관측에서 intent가 불명/null로 남아 복구 미확인, 원본 복원. 시계차 약2초와 VALIDATED 네트워크는 정상. 이후 같은 앱을 명시 시작해 Displayed+18.172초/PID12010과 WorkManager SUCCESS·실제 새 UI를 관측했으나 이는 원본 복원 뒤 AVAILABLE no-op일 수 있어 fault 복구 성공으로 쓰지 않는다.
- `am start -W`의 Status timeout도 실제 Activity가 뒤늦게 표시된 관측과 구분해야 한다. 새 driver는 같은 시작 요청을 재발행하지 않고 PID/실제 Activity 연결을 확인한 뒤 결과를 관측하도록 수정 중이다. 실제 새 UI·불명 접수 복구·수정 SDK fixture·최종 release 재생의 기기 통합은 **아직 진행 중**이며 과거0.1.1 ZIP/영상 성공으로 대체하지 않는다. 새 패키지/영상/분리 재현은 이 영향 검증 후 갱신한다.
- 음악 신규 송신은 여전히1회, 대역 기존 작업2개와 합쳐 jobs3/attempts3, 예약 USD0.40, dispatch=false. 재개 구현을 검증하려고 유료 곡을 다시 만들지 않았다. 청취 deferred·실차/장시간/폰/운영 후속·실제 접수 미실행 경계 유지.

### 0.2.0 최종 release 영향 검증과 제출 갱신

- 최종 선택 기록 보완을 포함한 release0.2.0(3)의 실제 SHA256은 **52e78c97795da2a5243f4ef40e6d9fc75581a0a51f49f5c60e3cda65210494af**다. 앞의 ee25...는 이전 후보 이력이며 최종이 아니다. 설치된 base.apk를 읽어 현재 apk/TodaysTrack-release.apk와 바이트 일치를 확인(resume-final-installed-apk.json), 서명56fd311ccd45d0dd255a6241701175f7a530a668cb243d609c51962d310c0490/DEBUGGABLE 없음/0.2.0(3)을 재확인했다.
- **resume-submission-20261112-151631**: debug 대역의 접수 불명+jobId 유실 상태 주입 후 생성 버튼 없이 앱 시작만으로 원래 AVAILABLE 작업 회수. inputHash·SDK/Room 원래 행 유지, 작업/attempt/예산 증가0, 이전/최근 기록 선택·원래 설정/요약 확인. finally 원본 Room/음원 복원·release 미수정. 실제 응답 유실은 아니며 byIntentGetIndependentlyObserved=false: 상태 결과만으로 GET 경로를 독립 관측했다고 하지 않는다. 코드·실제 SQLite 테스트는 별도 근거다.
- release-fixture-155552에서 실제 SDK 수집 서비스와 A-B-C-C-B-A 합성 입력은 진행했지만 화면 닫기/재진입 후 마감 탭이 적용되지 않아 기존 기록이 표시됐고 driver가 StopIteration으로 종료됐다. 접근성에는 앱 버튼이 있으나 실제 캡처가 검은 Pleos 화면인 불일치를 보존했다(resume-release-inspect.png). 제품 crash로 단정하지 않는다. 사용자 지시로 같은 문제 재현/전체 SDK 경로 반복은 중단했다. host 메모리16GB 중 가용 약1.7GB도 관측했으나 장애 당시 OOM/인과관계 확인은 아니며 메모리 부족은 가능성으로만 남긴다.
- 정확한 전용 AVD 이름을 검증해 종료/프로세스 소멸을 확인하고 지원되는 swangle 백엔드로 한 번 변경했다. 실제 전체 화면·이전 설정의 OPEN 기록 복구 UI를 관측했고, 기존 기록을 이어받아 정상 마감했다. 왕복 뒤 실제 GPS 복원 입력 D까지 포함해 **A-B-C-B-A-D / 연속6km / 공백3개**가 보존됐다. 마지막 D를 삭제하거나 원래 왕복만 재연한 성공으로 바꾸지 않았다. 현재 버전의 이 추가 시험으로 비가시 수집을 새로 입증하지 않는다. 이전 유한 백그라운드 증거와 제품 요구는 유지한다.
- **current-release-save-20261112-161552**: 새 SDK 경로를 재실행하지 않고 위의 보존된 기록을 실제 non-debug release에서 한 번 생성 버튼으로 전송. transport schemaVersion1 / **summary-v2**와 segmented/unknown 증거→FIXTURE 새 작업2117a434-12e5-4367-993b-e784d6f8f45f→기본 전체 FFmpeg 검사→READY→차량 저장. inputHash570b4995f7242b73ea3fcf207aa67632d8bc87678dc8d3749c786c0dab01add3, 기존 대역 MP3 SHA25644a6b02523a1cbfd6c188df68299eedbc0ddd51222f3a5ed6f0d1d334ba7c48f. 대역1개 추가·새 유료 송신0. 기존 live 곡/고정 입력/원장 보존, 총 jobs4/attempts4(대역3+live1), 예약USD0.40, dispatch=false.
- **offline-playback-20261112-161644**: 같은 최종 release 앱 force-stop/재실행, Wi-Fi/mobile data 차단과 default network none에서 기존 실제 새 AI 곡 재생. 실제 대상 package MediaSession PLAYING **2703→5709ms**·사용자 pause 뒤 PAUSED·신규 작업0. 네트워크 원복. 전체90초 재생은0.1.1 이력이고 현재 확인은 변경 영향에 맞춘 재시작/진행/일시정지다. 같은 MP3·Media3 재생 코드 증거를 재사용하며 불필요한 유료 재생성/끝까지 시험 반복은 하지 않았다.
- 소스 revision **5ecaf302ccc5d3e88221de36063131fa61db5dc6b743d6b4f6a69beefa135126**의 curated ZIP을 submission/repro-v0.2-final에 별도 추출하고 새 TLS/서명을 초기화했다. 재현 release 빌드/실제 XML22개 테스트와 서버 installDist/실제 XML15개 테스트 성공(repro-v0.2-release-build.log / repro-v0.2-server-build.log). 새 인증서/서명 때문에 재현 APK 바이트 동일성을 주장하지 않는다. 공개 TLS만 환경 초기화 예외이며 현재 제품/도구 코드 해시를 대조한다.
- v2 영상 **178.94초,1920×1080,H264/AAC**, SHA256 **2895b542238fce291726df3191d1d70bc71f4fdf3ada13339c55ef46c0870341**.0.1.0 생성·0.1.1 끝까지 재감상 화면을 보존하고0.2.0 실제 UI 정지 캡처10초를 명시 추가했다. project-v2.before/after 및 v1 원본/렌더를 보존했다. 전체 FFmpeg decode exit0·164초 UI 장면 가독성 확인. 연결 도구의 기존 Chrome profile 사용 중 오류 뒤 다른 브라우저를 종료하지 않고 별도 전용 headless profile로 실제 영상 **160.200649→162.361104초**, readyState4/error null을 관측했다. 전용 브라우저는 종료했다. 같은 MP3 후삽입·muted 자동화이며 청취 평가는 deferred다.
- 시험 뒤 전용 AVD/emulator와 이 프로젝트 소유 MainKt 서버만 명령행 경로를 확인하고 종료했다. 타 프로젝트/다른 브라우저/ADB 서버/개인 설정은 건드리지 않았다. 같은 화면 멈춤을 반복 재현하지 않고 확보된 실제 기록과 증거로 제출 완성을 진행한다.
- 최종 묶음 자체 검토에서 S1 실제 SDK/공백·보존, S2 요약/차량 인증 작업·대역, S3 선행 뒤 실제 새 음악 연결·저장/재실행/오프라인, S4 실제 영상 재생·release 설치·분리 재현·비밀정보 제외를 원본 결과/서버 SQLite/설치 APK/영상/ZIP과 대조했다. 기획서 두 개·설계v0.2·후속 계획은 이전 보존 ZIP의 원문 바이트와도 일치했다. 최종 로컬 패키지는 manifest31개 파일, 중첩 소스183개 파일의 해시/CRC를 확인했으며 별도 resume-package-audit.json에 ZIP 해시를 기록한다. 사용자 지시의 청취deferred·화면 멈춤 반복 중단·허용된 제한 시연 범위에서 **S1~S4 구현 및 로컬 제출 패키지 준비 완료**다. 전체 제품/실차·장시간·모든 환경의 백그라운드·청취 평가·외부 접수 완료가 아니다. P0~P8 후속을 삭제하거나 완료 처리하지 않았다.


## 0.3 Porcelain Split UI · 현재 통합 결과

현재 제출 기준은 **0.3.0-submission(4)**, Porcelain Split 흰색 UI입니다. 기존 package ID·서명을 유지한 업데이트 APK의 설치 바이트가 제공 APK와 일치합니다. 차량27개·서버15개 테스트와 별도 추출 소스 빌드를 확인했습니다. 추가 음악 유료 호출은0입니다.

홈·기록·보관함, 실제 기록 선택·설정 유지, 기억한 설정으로 바로 수집/마감, summary-v2 대역 생성·차량 저장을 현재 release에서 확인했습니다. 76dp 이상 클릭 영역을 실측했고 실제 앱 너비841dp에서 기록 선택 창을 확인했습니다. 표지3종은 고정 흑백 장식 이미지이며 실제 주행 사진이 아닙니다.

기존 실제 생성곡은 재실행·기본 네트워크 없음에서 PLAYING2826→14836ms와 일시정지를 확인했습니다. **첫 재생이 PAUSED/0초에서 보류됐고, 같은 재실행 프로세스에서 준비된 곡을 다시 눌러 재개했습니다.** 첫 클릭 성공이나 현재 버전의90초 전체 재생으로 확대하지 않습니다. 전체 재생은0.1.1의 보존 증거입니다.

새 영상 v3는 약89초,1920×1080 H264/AAC입니다. 현재0.3 실제 화면 캡처·오프라인 진행 장면과 보존된0.1.0 실제 새 AI 생성 촬영을 구분합니다. 약1fps 실제 화면의 간격 편집과 같은 MP3 후삽입이며 직접 차량 오디오 녹음·사람의 청취 평가가 아닙니다. 청취는 유예합니다.

software GPU에서 플랫폼 전체 검은 화면을 관측한 뒤 반복 재현을 중단하고 Intel Iris Xe host GPU로 변경해 남은 검증을 진행했습니다. 메모리 부족과의 인과관계는 미확정입니다. 이번 수집은 화면 유지형 유한 시연이며 일반 백그라운드·실차·장시간·폰·운영 검증을 대신하지 않습니다. 기존 요구와 후속 계획은 보존합니다. 외부 업로드·이메일·접수·배포는 실행하지 않았습니다.

설치 APK SHA256 `d493ab515b66e7c2c9f72e2b2f84ef2df3d9dbd49f6ae2a3bca505b065b9671e`. ui-inspect-174842 / ui-fixture-180858 / ui-offline-182252 원본 결과와 실패 이력을 보존한다. 전용 AVD·자체 서버 종료/표시 설정 원복은 ui-v3-cleanup.json에 남겼다. 보호 문서·기획7번·후속 계획·기존 실제 생성 이력은 유지하며 묶음별 자체 검토는 승인 범위/회귀/실제 증거를 대조한다. 새 유료0, Guard/상주 작업자/훅/개인 메모리/에이전트 추가0이다.

패키지 최종 승격·전체 영상 재생·소스 재현 대조 결과는 최종 audit에 기록한다. 이 문단만으로 패키지 완료를 주장하지 않는다.


### 0.3 로컬 제출물 검증 완료

최종0.3(4) APK의 설치 바이트/서명·non-debug 확인, 새 UI 실제 기록/대역 저장/오프라인 진행, 분리 소스 차량27개·서버15개 테스트 및 빌드 대조를 마쳤다. 컴파일 뒤 달라진 보조 Python3파일은 별도 source-refresh 증거로 명시했고 vehicle/server/빌드 입력64파일은 불변이다. 최종 소스 revision은 `9ab8ca428fdde368b36888eca71ddc7516df7a5648fb7d11598c82d3eb4f0b03`이다.

v3 영상89.01초·1920×1080, SHA256 `247d8b063b0fd7aaf98a01d2ee4235b605103cbaf443245cb465c8e9ba978adc`. 전체 decode exit0, 별도 로컬 Chrome 실제 재생72.000262→74.261049초·readyState4·mediaError=null이며 전용 브라우저를 종료했다.12/37/76초 프레임에서 UI·확대한 상세·오프라인 진행의 가독성과 사실 표시를 검토했다. 첫 재생 보류/동일 세션 재개와 청취 유예는 유지한다.

후보 ZIP39파일·소스228파일의 manifest 해시/CRC/키 제외·보호 원문을 실제 대조했다. 결과는 validation/evidence/ui-v3-package-audit.json. 검증한 package-next/ZIP을 최종 package/TodaysTrack-submission.zip으로 교체하고 기존0.2 APK/영상/ZIP/패키지는 history/ui-refresh-0.2에 보존한다. 이는 승인된 UI 개편과 로컬 제출 패키지 준비 완료이며 외부 접수·청취·실차/장시간/일반 백그라운드 전체 완료가 아니다.
