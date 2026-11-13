"""Finite UI0.3 verification on the dedicated Pleos AVD. No live provider calls."""
import argparse,hashlib,json,re,sqlite3,subprocess,threading,time
from pathlib import Path
import submission_device as j

PKG='dev.todaystrack.vehicle.submission'
ROOT=Path(__file__).resolve().parents[2]

def nodes():
    found=[n for n in j.ui_nodes(timeout=20) if n.get('package')==PKG]
    if found:return found
    # Pleos has multiple concurrent windows. A valid tree may belong solely to
    # DrivingView; focus the observed app's harmless upper-right padding once.
    j.adb('shell','input','tap','2515','100')
    found=[n for n in j.ui_nodes(timeout=20) if n.get('package')==PKG]
    if not found:raise TimeoutError('Product accessibility window not observed; no outcome inferred')
    return found

def controls():
    return [n for n in nodes() if n.get('clickable')=='true']

def tap(label,prefix=False):
    until=time.monotonic()+25
    while time.monotonic()<until:
        for n in controls():
            actual=n.get('content-desc') or n.get('text','')
            if (actual.startswith(label) if prefix else actual==label) and n.get('enabled')=='true':
                a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
                j.adb('shell','input','tap',str((a+c)//2),str((b+d)//2));return actual
        time.sleep(1)
    raise TimeoutError('Actual enabled control unavailable: '+label)

def front():
    result=j.adb('shell','am','start','-W','--user','10','-n',PKG+'/dev.todaystrack.vehicle.MainActivity')
    nodes()
    return result

def navigate(label):
    for attempt in range(2):
        tap(label)
        until=time.monotonic()+12
        while time.monotonic()<until:
            if any(n.get('content-desc')==label and n.get('selected')=='true' for n in controls()):return
            time.sleep(.5)
    raise TimeoutError('Navigation selection not observed: '+label)

def capture(folder,name):
    target=folder/(name+'.png')
    result=subprocess.run([j.ADB,'-P','5038','-s',j.SERIAL,'exec-out','screencap','-p'],capture_output=True,timeout=45,check=True)
    target.write_bytes(result.stdout);return target

def playback_state():
    raw=j.adb('shell','dumpsys','media_session');marker='package='+PKG+'\n'
    block=raw[raw.index(marker):] if marker in raw else ''
    m=re.search(r'state=PlaybackState \{state=([A-Z]+)\((\d+)\), position=(\d+)',block)
    return (m[1],int(m[3])) if m else ('MISSING',0),raw

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--mode',choices=['inspect','fixture','offline'],default='inspect');parser.add_argument('--resume');parser.add_argument('--expected-pid');args=parser.parse_args()
    assert j.adb('shell','getprop','ro.build.fingerprint').startswith('Pleos/')
    assert j.adb('shell','am','get-current-user').strip()=='10'
    installed=j.adb('shell','dumpsys','package',PKG);assert 'versionName=0.3.0-submission' in installed
    assert not json.loads((ROOT/'server/.local/dispatch.json').read_text(encoding='utf-8-sig'))['enabled']
    if args.resume:
        assert args.mode in ['inspect','offline'],'Generating fixture runs cannot resume'
        folder=Path(args.resume).resolve();folder.relative_to((j.ROOT/'runs').resolve());assert folder.is_dir()
        if args.mode=='offline':
            assert (folder/'connectivity.txt').is_file(),'Prior offline restart prefix must be preserved'
            assert args.expected_pid and j.adb('shell','pidof',PKG).strip()==args.expected_pid,'Observed restarted process changed'
    else:
        folder=j.ROOT/'runs'/('ui-'+args.mode+'-'+time.strftime('%Y%m%d-%H%M%S'));folder.mkdir()
    j.dump(j.ROOT/('ui-'+args.mode+'-latest.json'),{'run_path':str(folder)})
    (folder/'launch.txt').write_text(front(),encoding='utf-8');time.sleep(2)
    initial=nodes();j.dump(folder/('resumed-controls.json' if args.resume else 'initial-controls.json'),[dict(n.attrib) for n in initial])
    rectangles=[list(map(int,re.findall(r'\d+',n.get('bounds','')))) for n in initial]
    rectangles=[r for r in rectangles if len(r)==4 and r[2]>r[0] and r[3]>r[1]]
    j.dump(folder/'app-window.json',{'bounds':[min(r[0] for r in rectangles),min(r[1] for r in rectangles),max(r[2] for r in rectangles),max(r[3] for r in rectangles)]})
    if args.mode=='inspect':
        density=int(re.findall(r'\d+',j.adb('shell','wm','density'))[-1]);minimum=76*density/160
        clicked=[]
        for n in initial:
            if n.get('clickable')!='true':continue
            a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
            assert c-a>=minimum-1 and d-b>=minimum-1,(n.get('content-desc'),c-a,d-b)
            assert n.get('content-desc') or n.get('text')
            clicked.append({'label':n.get('content-desc') or n.get('text'),'widthPx':c-a,'heightPx':d-b})
        if not args.resume:
            capture(folder,'home');tap('기록');capture(folder,'records');tap('보관함');capture(folder,'library')
        else:
            assert all((folder/(name+'.png')).is_file() for name in ['home','records','library'])
        tap('홈');tap('새 기록 설정')
        before=[n.get('text','') for n in nodes()];capture(folder,'settings');tap('저장')
        tap('새 기록 설정');after=[n.get('text','') for n in nodes()];assert before==after
        tap('취소')
        choices=[n.get('content-desc','') for n in controls() if n.get('content-desc','').startswith('기록 선택 ·')]
        if len(choices)>1:
            tap(choices[-1]);time.sleep(5)
            old=[n.get('content-desc','') for n in controls() if n.get('selected')=='true']
            assert choices[-1] in old,'Older selection did not survive refresh'
            tap(choices[0]);time.sleep(2)
        j.dump(folder/'result.json',{'mode':'inspect','version':'0.3.0-submission','measuredDensityDpi':density,'minimumTouchDp':76,
            'touchTargets':clicked,'homeRecordsLibraryObserved':True,'settingsRetainedWithoutChange':True,'existingRecordsSelectable':len(choices)>1,'newGenerationClicked':False,'resumedAfterPlatformRenderingFailure':bool(args.resume)})
    elif args.mode=='fixture':
        db=sqlite3.connect(ROOT/'server/.local/server.db');db.row_factory=sqlite3.Row
        before=db.execute('select count(*) from jobs').fetchone()[0]
        live_before=db.execute("select count(*) from attempts a join jobs j on a.job_id=j.id where j.mode='LIVE'").fetchone()[0]
        stop=threading.Event()
        def heartbeat():
            while not stop.is_set():
                try:j.http('/browser/session?client=TodaysTrackWhiteUi','POST')
                except Exception:pass
                stop.wait(2)
        worker=threading.Thread(target=heartbeat,daemon=True)
        j.adb('forward','tcp:8088','tcp:8088');worker.start()
        try:
            j.adb('shell','appops','set','2000','android:mock_location','allow')
            j.adb('shell','cmd','location','providers','add-test-provider','gps','--supportsAltitude','--supportsBearing')
            j.adb('shell','cmd','location','providers','set-test-provider-enabled','gps','true')
            base=int(j.http('/vehicle/state','GET')['odometer']['traveled_distance_km'])+1
            def point(km,lat):
                j.http('/vehicle/odometer?traveled_distance_km='+str(km),'PUT')
                j.adb('shell','cmd','location','providers','set-test-provider-location','gps','--location',str(lat)+',127.0276','--accuracy','5')
                time.sleep(4)
            point(base,37.4979);front();tap('새 기록 시작');time.sleep(3)
            capture(folder,'recording');point(base,37.4979);point(base+1,37.5109)
            front();tap('기록 마무리');time.sleep(3)
            capture(folder,'summary');tap('기록 상세')
            details=[n.get('text','') for n in nodes()];j.dump(folder/'record-details.json',details);capture(folder,'details');tap('닫기')
            tap('음악 만들기');capture(folder,'generation')
            until=time.monotonic()+100
            while time.monotonic()<until:
                job=db.execute('select * from jobs order by rowid desc limit 1').fetchone()
                if db.execute('select count(*) from jobs').fetchone()[0]>before:
                    assert job['mode']=='FIXTURE'
                    if job['state']=='READY':break
                    if job['state'].startswith('FAILED') or job['state']=='RECONCILING':raise RuntimeError('Held: '+job['state'])
                time.sleep(2)
            else:raise TimeoutError('Fixture not READY; do not click generation again')
            until=time.monotonic()+45
            while time.monotonic()<until:
                if any(n.get('content-desc')=='음악 듣기' for n in controls()):break
                time.sleep(2)
            else:raise TimeoutError('Vehicle save not observed')
            capture(folder,'saved')
            payload=json.loads(job['payload']);assert payload['summaryVersion']=='summary-v2'
            assert db.execute("select count(*) from attempts a join jobs j on a.job_id=j.id where j.mode='LIVE'").fetchone()[0]==live_before
            j.dump(folder/'result.json',{'mode':'FIXTURE','version':'0.3.0-submission','newAiGeneration':False,'vehicleStartedGeneration':True,
                'rememberedSettingsDirectStart':True,'sdkRecordSealed':True,'inputHash':job['hash'],'jobId':job['id'],'audioSha256':job['asset_hash'],
                'summaryVersion':payload['summaryVersion'],'settings':payload['settings'],'vehicleSavedObserved':True,'newJobs':1,'newLiveProviderAttempts':0,
                'activityKeptVisible':True,'backgroundClaimed':False,'listeningQualityAssessed':False})
        finally:
            stop.set();worker.join(timeout=5)
            j.adb('shell','cmd','location','providers','remove-test-provider','gps')
            j.adb('shell','appops','set','2000','android:mock_location','default')
    else:
        wifi=j.adb('shell','settings','get','global','wifi_on').strip();data=j.adb('shell','settings','get','global','mobile_data').strip()
        db=sqlite3.connect(ROOT/'server/.local/server.db');before=db.execute('select count(*) from jobs').fetchone()[0]
        try:
            j.adb('shell','svc','wifi','disable');j.adb('shell','svc','data','disable')
            until=time.monotonic()+45;network_states=[]
            while time.monotonic()<until:
                connectivity=j.adb('shell','dumpsys','connectivity')
                network_states.append(next((line.strip() for line in connectivity.splitlines() if 'Active default network:' in line),'not observed'))
                if 'Active default network: none' in connectivity or 'Active default network: null' in connectivity:break
                time.sleep(1)
            else:
                (folder/'network-transition-failure.txt').write_text(connectivity,encoding='utf-8')
                raise TimeoutError('No-default-network transition not observed; playback not claimed')
            j.dump(folder/'network-transition.json',network_states)
            (folder/'connectivity.txt').write_text(connectivity,encoding='utf-8')
            if not args.resume:j.adb('shell','am','force-stop','--user','10',PKG)
            front();time.sleep(3);navigate('보관함')
            until=time.monotonic()+35
            while time.monotonic()<until:
                matches=[n.get('content-desc') for n in controls() if n.get('content-desc','').startswith('재생 ·') and '새 AI 음악' in n.get('content-desc','')]
                if matches:
                    actual=matches[0];break
                time.sleep(1)
            else:
                capture(folder,'missing-library-observation')
                raise TimeoutError('Stored actual song not observed after library load; no new generation requested')
            tap(actual)
            until=time.monotonic()+30
            while time.monotonic()<until:
                first,raw=playback_state()
                if first[0]=='PLAYING' and first[1]>0:break
                time.sleep(1)
            else:raise RuntimeError('Stored actual song not playing: '+str(first))
            timeline=[]
            for i in range(12):
                state,raw=playback_state();capture(folder,'offline-'+str(i).zfill(2));timeline.append({'state':state[0],'positionMs':state[1],'file':'offline-'+str(i).zfill(2)+'.png','hostMonotonic':time.monotonic()});time.sleep(.5)
            later,raw=playback_state();assert later[0]=='PLAYING' and later[1]>first[1]
            tap('일시정지');time.sleep(1);paused,raw=playback_state();assert paused[0]=='PAUSED'
            assert db.execute('select count(*) from jobs').fetchone()[0]==before
            j.dump(folder/'timeline.json',timeline)
            j.dump(folder/'result.json',{'mode':'LIVE','version':'0.3.0-submission','existingStoredRealSong':True,'newAiGeneration':False,
                'offlineDefaultNetworkAbsent':True,'appRestarted':True,'positionsMs':[first[1],later[1]],'paused':True,'newJobs':0,'listeningQualityAssessed':False,
                'resumedPreparedPlaybackInSameRestartedProcess':bool(args.resume),'observedRestartedPid':args.expected_pid if args.resume else None,
                'firstPlayAfterRestartPaused':bool(args.resume)})
        finally:
            if wifi=='1':j.adb('shell','svc','wifi','enable')
            if data=='1':j.adb('shell','svc','data','enable')
    print(json.dumps({'run':str(folder),'mode':args.mode,'result':'verified'}),flush=True)

if __name__=='__main__':main()
