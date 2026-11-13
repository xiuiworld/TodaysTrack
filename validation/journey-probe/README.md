# SDK 여정 통합 검증

제품 앱과 분리한 전용 AVD/공식 샘플 검증 코드다. 실제 차나 실제 사용자의 위치가 아니다.

1. Vehicle 샘플의 JourneyProbeActivity APK를 빌드하고 전용 Pleos AVD를 software 렌더러로 부팅한다.
2. ADB server5038, 전용 serial127.0.0.1:5661로만 설치/조작한다.
3. `python -u validation/journey-probe/journey.py capture`는 유한한 합성 왕복 경로를 입력한다. 앱은 SDK 콜백과 수신 시각을 자체 저장한다.
4. `runs/<session>/raw-events.jsonl`은 앱 내부 JSONL을 가져온 원본이다. controls.json은 에뮬레이터 입력, journey.json은 SDK 원본에서 만든 요약이다. 두 파일을 혼동하지 않는다.
5. HOME 후 SDK 콜백, 강제 종료 후 파일 접두부 보존과 재시작을 관찰한다. 종료 동안의 수집 공백은 메운 것으로 처리하지 않는다.
6. 별도 승인을 받은 음악1곡만 `Invoke-MusicProbe.ps1 -Sample sdk-journey -JourneyFile <journey.json> -Generate`로 만든다. 기본 실행은 dry-run이다. 기존 호출 원장을 보존한다.

요약은 누적거리 차이·실제 관측 시간·좌표 순서에서만 계산한다. 관측 시간은 실제 주행 시간 API가 아니며 프로세스 중단 시간을 포함한다. 시뮬레이터에서는 가속된 테스트 경로를 사용한다.

공식 서비스의 상용 조건·장시간 백그라운드·차량 전원 종료·차량 재부팅·서비스 재시작 복구·폰 동기화·앨범·실제 차 검증은 이 작은 probe의 완료 범위가 아니다.

정리는 테스트 GPS 공급자 제거, shell mock 권한 복원, 검증 전용 AVD 종료로 한다. 다른 기기나 AVD에는 명령을 보내지 않는다.
