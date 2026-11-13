"""Curated local source/package; never traverse private state or upstream work/."""
from pathlib import Path
import hashlib, json, re, shutil, sys, zipfile

ROOT=Path(__file__).resolve().parents[1]
PACKAGE=ROOT/'submission/package-next'
PACKAGE.mkdir(parents=True,exist_ok=True)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()

gradle=(ROOT/'vehicle/app/build.gradle.kts').read_text(encoding='utf-8-sig')
version_match=re.search(r'versionName\s*=\s*"([^"]+)"',gradle)
assert version_match,'Gradle versionName missing'
version=version_match.group(1)
current_ui=ROOT/'validation/evidence/ui-v3-release-current-ui.png'

def ui_run(mode):
    pointer=json.loads((ROOT/('validation/journey-probe/ui-'+mode+'-latest.json')).read_text(encoding='utf-8-sig'))
    path=Path(pointer['run_path']);path=path.resolve() if path.is_absolute() else (ROOT/path).resolve()
    path.relative_to((ROOT/'validation/journey-probe/runs').resolve())
    result=json.loads((path/'result.json').read_text(encoding='utf-8-sig'))
    assert result['version']==version,(mode,'Evidence version mismatch')
    return path

ui_runs={}
for mode in ['inspect','fixture','offline']:
    pointer=ROOT/('validation/journey-probe/ui-'+mode+'-latest.json')
    if '--source-only' in sys.argv:
        if not pointer.is_file():continue
        value=Path(json.loads(pointer.read_text(encoding='utf-8-sig'))['run_path'])
        path=value if value.is_absolute() else ROOT/value
        if not (path/'result.json').is_file():continue
    ui_runs[mode]=ui_run(mode)

def code_entry(item):
    return item['path'].startswith(('vehicle/','server/','tools/')) or (item['path'].startswith('validation/') and Path(item['path']).suffix=='.py')
def files_under(relative):
    return [p for p in (ROOT/relative).rglob('*') if p.is_file() and not any(x in {'.local','.gradle','.kotlin','build','__pycache__','.idea','ui-concepts'} for x in p.relative_to(ROOT).parts)]
selected=[]
for directory in ('vehicle','server','docs'):selected+=files_under(directory)
selected += [ROOT/x for x in ['README.md','.gitignore','tools/Use-SubmissionToolchain.ps1','tools/Build-SubmissionApk.ps1','tools/Install-MediaTool.ps1','tools/Render-SubmissionVideo.py','tools/Package-Submission.py','validation/Finalize-Submission.py']]
for name in ['submission_device.py','product_capture.py','offline_playback.py','release_setup.py','release_journey.py','capture_replay_frames.py','download_recovery.py','resume_submission.py','ui_refresh.py','journey.py']:
    selected.append(ROOT/'validation/journey-probe'/name)
live=ROOT/'validation/journey-probe/runs/release-live-20261112-115006'
replay=ROOT/'validation/journey-probe/runs/replay-frames-20261112-122740'
selected += [live/'screen.mp4',replay/'capture-timeline.json',replay/'playback-timeline.json',replay/'result.json',ROOT/'submission/audio/TodaysTrack-8km-Acoustic.mp3']
selected += list((replay/'frames').glob('*.png'))
if current_ui.is_file():selected.append(current_ui)
selected.append(ROOT/'docs/design/ui-concepts/2026-11-12/v3-white/03-porcelain-split.png')
for run in ui_runs.values():
    selected+=list(run.glob('*.png'))
    selected+=[run/name for name in ['app-window.json','result.json','timeline.json'] if (run/name).is_file()]
for mode in ui_runs:selected.append(ROOT/('validation/journey-probe/ui-'+mode+'-latest.json'))
selected=sorted(set(selected))
forbidden_ext={'.p12','.pfx','.dpapi','.db','.sqlite','.keystore','.jks'}
patterns=[re.compile(rb'AIza[0-9A-Za-z_-]{30,}'),re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----')]
inventory=[]
for p in selected:
    rel=p.relative_to(ROOT).as_posix()
    assert p.is_file(),rel
    assert p.suffix.lower() not in forbidden_ext,rel
    assert p.name not in {'local.properties','enrollment.json','dispatch.json','server-process.json'},rel
    assert not any(pattern.search(p.read_bytes()) for pattern in patterns),f'Credential pattern in {rel}'
    inventory.append({'path':rel,'bytes':p.stat().st_size,'sha256':sha(p)})
# Revision covers source + Gradle configuration, not mutable build outputs/docs.
code=[x for x in inventory if code_entry(x)]
revision=hashlib.sha256(''.join(x['path']+'\0'+x['sha256']+'\n' for x in code).encode()).hexdigest()
source=PACKAGE/'source/TodaysTrack-source.zip';source.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for p in selected:z.write(p,p.relative_to(ROOT).as_posix())
    z.writestr('source-inventory.json',json.dumps({'revisionKind':'SHA256 curated source tree; no Git repository','revision':revision,'files':inventory},ensure_ascii=False,indent=2))
if '--source-only' in sys.argv:
    print(json.dumps({'source':str(source),'revision':revision,'files':len(selected)}));sys.exit(0)

copies={'apk/TodaysTrack-release.apk':'apk/TodaysTrack-release.apk','TodaysTrack-demo.mp4':'submission/TodaysTrack-demo.mp4','audio/TodaysTrack-8km-Acoustic.mp3':'submission/audio/TodaysTrack-8km-Acoustic.mp3'}
for name in ['runbook.md','verification.md','storyboard.md','submission-checklist.md']:copies['docs/'+name]='docs/submission/'+name
copies['forms/official-application-consent-blank.docx']='submission/forms/official-application-consent-blank.docx'
for name in ['music-trace.json','returned-lyrics.txt']:copies['evidence/'+name]='submission/evidence/'+name
for name in ['s4-browser-video-playback.json','reproduction-check.json','submission-final-checks.json']:
    copies['evidence/'+name]='validation/evidence/'+name
copies['evidence/ui-v3-source-refresh.json']='validation/evidence/ui-v3-source-refresh.json'
copies['evidence/s4-cleanup.json']='validation/evidence/ui-v3-cleanup.json'
copies['evidence/current-installed-apk.json']='validation/evidence/ui-v3-installed-apk.json'
for mode,run in ui_runs.items():copies['evidence/ui-v3-'+mode+'.json']=(run/'result.json').relative_to(ROOT).as_posix()
for name in ['home','records','library']:copies['evidence/ui-v3-'+name+'.png']=(ui_runs['inspect']/(name+'.png')).relative_to(ROOT).as_posix()
copies['evidence/offline-final-apk.json']=str((replay/'result.json').relative_to(ROOT))
copies['evidence/offline-playback-timeline.json']=str((replay/'playback-timeline.json').relative_to(ROOT))
recovery=json.loads((ROOT/'validation/journey-probe/download-recovery-latest.json').read_text())
copies['evidence/download-recovery.json']=str((Path(recovery['run_path'])/'result.json').relative_to(ROOT))
copies['evidence/new-sdk-to-music.json']=str((live/'result.json').relative_to(ROOT))
for name in ['project-v1.before.json','project-v1.after.json']:copies['editing/'+name]='submission/editing/'+name
for name in ['project-v2.before.json','project-v2.after.json']:copies['editing/'+name]='submission/editing/'+name
for name in ['project-v3.before.json','project-v3.after.json']:copies['editing/'+name]='submission/editing/'+name
if current_ui.is_file():copies['evidence/current-release-ui.png']=current_ui.relative_to(ROOT).as_posix()
video_project=json.loads((ROOT/'submission/editing/project-v3.after.json').read_text(encoding='utf-8-sig'))
assert video_project['version']==3 and video_project['currentUiAppVersion']==version,'Video UI version differs from current package'
assert current_ui.is_file() and video_project['sources']['currentUiSha256']==sha(current_ui),'Video does not use the current UI capture'
assert video_project['sha256']==sha(ROOT/'submission/TodaysTrack-demo.mp4'),'Video differs from the v3 rendered checkpoint'

# A pointer is created before the experiment starts. Its existence alone is not success.
resume_pointer=ROOT/'validation/journey-probe/resume-submission-latest.json'
resume_evidence=None
resume_copies={
    'evidence/resume-submission-result.json':'result.json',
    'evidence/resume-submission-startup.json':'startup-recovery-result.json',
    'evidence/resume-submission-restoration.json':'restoration.json',
}
if resume_pointer.is_file():
    pointer=json.loads(resume_pointer.read_text(encoding='utf-8-sig'))
    resume_run=Path(pointer['run_path'])
    if not resume_run.is_absolute():resume_run=ROOT/resume_run
    resume_run=resume_run.resolve()
    resume_run.relative_to((ROOT/'validation/journey-probe/runs').resolve())
    result_path=resume_run/'result.json'
    restoration_path=resume_run/'restoration.json'
    if result_path.is_file() and restoration_path.is_file() and not (resume_run/'restoration-failure.json').exists():
        result=json.loads(result_path.read_text(encoding='utf-8-sig'))
        restoration=json.loads(restoration_path.read_text(encoding='utf-8-sig'))
        success=(result.get('mode')=='FIXTURE' and result.get('faultInjection') is True
            and result.get('startupRecoveredExistingJob') is True and result.get('sameInputHash') is True
            and result.get('sdkOriginalRowsUnchanged') is True and result.get('newJobs')==0
            and result.get('newAttempts')==0 and result.get('budgetReservationUnchanged') is True
            and result.get('generationButtonClicked') is False and result.get('releaseAppModified') is False
            and result.get('selector',{}).get('status')=='verified'
            and restoration.get('originalRoomRowsRestored') is True
            and restoration.get('originalAudioRestored') is True and restoration.get('releaseUntouched') is True)
        if success:
            for dest,name in resume_copies.items():copies[dest]=(resume_run/name).relative_to(ROOT).as_posix()
            resume_evidence={'result':result_path.relative_to(ROOT).as_posix(),
                'byIntentGetIndependentlyObserved':result.get('byIntentGetIndependentlyObserved') is True,
                'limitations':result.get('limitations',[])}
# Never carry forward optional evidence from a prior successful run after a new failure.
for dest in [*resume_copies,'evidence/resume-release-current-ui.png']:
    if dest not in copies:
        target=PACKAGE/dest
        if target.is_file():target.unlink()
for dest,src in copies.items():
    target=PACKAGE/dest;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(ROOT/src,target)
readme=f'''# 오늘의 트랙 · 로컬 제출 패키지

현재 {version} 흰색 UI release APK, 새 UI 시연 영상, 실제 새 AI 곡, 소스와 재현 안내입니다.
docs/runbook.md → docs/verification.md → manifest.json 순서로 확인하세요.

- 실제 새 음악은0.1.0에서 SDK 기록→8km 요약→차량 시작 생성→저장했으며0.1.1에서 재실행/오프라인 끝까지 재생했습니다. 이 이력을 현재 APK의 새 기기 검증으로 바꾸어 쓰지 않습니다.
- 현재 버전은 선택한 흰색 시안3을 적용했습니다. 기록 선택·기억한 설정으로 수집·대역 생성/저장·기존 실제곡 오프라인 재생은 현재 release에서 확인했습니다. 영상은 새 UI 실제 캡처와 보존된 실제 새 생성 촬영을 명시적으로 구분합니다.
- 음악은 실제 Lyria3.5 새 생성입니다. server/fixtures의 기존 대역과 다릅니다.
- 사람이 듣는 가창·청취 품질은 사용자 지시로 deferred이며 assessed=false입니다. 파일/재생 상태 검사를 청취로 대신하지 않으며 청취 유예만으로 구현·전체 Goal을 blocked로 두지 않습니다.
- 생성에는 PC 로컬 서버가 필요합니다. 다른 PC는 TLS 초기화 후 APK를 재빌드합니다.
- 키·인증 private key·서버 DB·원본 좌표는 포함하지 않습니다. 음원은 별도 산출물이며 APK에 심지 않았습니다.
- 영상 음원은 같은 MP3 후삽입입니다. 편집 프로젝트/실제 공개 가능한 화면은 소스 zip에 보존했습니다.
- 외부 게시/업로드/접수는 실행하지 않았습니다. 개인 정보·제출 양식 최종 입력은 사용자가 확인해야 합니다.
'''
(PACKAGE/'README.md').write_text(readme,encoding='utf-8')
entries=[]
for p in sorted(PACKAGE.rglob('*')):
    if p.is_file() and p.name!='manifest.json':
        rel=p.relative_to(PACKAGE)
        assert '.local' not in rel.parts and p.suffix.lower() not in forbidden_ext,rel
        assert p.name not in {'local.properties','enrollment.json','dispatch.json','server-process.json'},rel
        assert not any(pattern.search(p.read_bytes()) for pattern in patterns),f'Credential pattern in {rel}'
        entries.append({'path':rel.as_posix(),'bytes':p.stat().st_size,'sha256':sha(p)})
manifest={'product':'오늘의 트랙 / Today’s Track','version':version,'deadline':'2026-11-15T23:59:00+09:00','sourceRevision':revision,'sourceRevisionKind':'curated source tree SHA256, no Git repository',
 'historicalMusicGenerationVersion':'0.1.0-submission','historicalOfflinePlaybackVersion':'0.1.1-submission',
 'historicalEvidenceVersions':{'evidence/new-sdk-to-music.json':'0.1.0-submission','evidence/offline-final-apk.json':'0.1.1-submission','evidence/offline-playback-timeline.json':'0.1.1-submission'},
 'historicalDebugRecoveryEvidence':resume_evidence,'currentDeviceIntegrationEvidence':{'version':version,'results':['evidence/ui-v3-'+mode+'.json' for mode in ui_runs]},'currentUiStillIncluded':current_ui.is_file(),
 'newMusicProviderCalls':1,'newMusicRetryCalls':0,'additionalMusicProviderCallsForUiRefresh':0,'reservedUsdIncludingHistorical':0.4,'actualBillingVerified':False,'liveDispatchEnabled':False,
 'listeningQualityAssessed':False,'listeningEvaluationStatus':'deferred_by_user','listeningDeferredBlocksImplementation':False,
 'externalSubmissionCompleted':False,'files':entries}
(PACKAGE/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
bundle=ROOT/'submission/TodaysTrack-submission-next.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for p in sorted(PACKAGE.rglob('*')):
        if p.is_file():z.write(p,p.relative_to(PACKAGE).as_posix())
print(json.dumps({'bundle':str(bundle),'sha256':sha(bundle),'sourceRevision':revision,'bytes':bundle.stat().st_size}))
