"""Fresh release SDK journey -> vehicle generation intent. LIVE requires the verified fixture gate and local dispatch control."""
import argparse
import json
import math
import re
import sqlite3
import subprocess
import threading
import time
import xml.etree.ElementTree as ET
from pathlib import Path
import submission_device as j
import product_capture as p

PKG='dev.todaystrack.vehicle.submission'

def ui_text():
    j.adb('shell','input','tap','2515','100')
    return [n.get('text','') for n in j.ui_nodes() if n.get('package')==PKG]

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--mode',choices=['FIXTURE','LIVE'],default='FIXTURE');parser.add_argument('--record',action='store_true');parser.add_argument('--keep-visible',action='store_true',help='Keep the Activity visible; does not prove background collection');args=parser.parse_args()
    p.PKG=PKG
    assert j.adb('shell','getprop','ro.build.fingerprint').startswith('Pleos/')
    assert j.adb('shell','am','get-current-user').strip()=='10'
    if args.mode=='LIVE':
        gate=json.loads(Path('validation/evidence/s3-fixture-gate.json').read_text(encoding='utf-8'))
        control=json.loads(Path('server/.local/dispatch.json').read_text(encoding='utf-8'))
        assert gate['saveAndOfflineReplayVerified'] and control['enabled'] and control['budgetUsd']==5
    run=j.ROOT/'runs'/('release-'+args.mode.lower()+'-'+time.strftime('%Y%m%d-%H%M%S'));run.mkdir()
    j.dump(j.ROOT/('release-'+args.mode.lower()+'-latest.json'),{'run_path':str(run)})
    stop=threading.Event();deadline=time.monotonic()+300
    def heartbeat():
        while not stop.is_set() and time.monotonic()<deadline:
            try:j.http('/browser/session?client=TodaysTrackReleaseJourney','POST')
            except Exception:pass
            stop.wait(.6)
    thread=threading.Thread(target=heartbeat,daemon=True)
    recorder=None
    remote='/sdcard/TodaysTrack-'+run.name+'.mp4'
    db=sqlite3.connect('server/.local/server.db');db.row_factory=sqlite3.Row
    before=db.execute('select count(*) from jobs').fetchone()[0]
    controls=[]
    # Observe real controls before the virtual-console heartbeat starts. Continuous
    # platform property updates can keep uiautomator's idle/root observer busy.
    j.adb('shell','am','start','-W','--user','10','-n',PKG+'/dev.todaystrack.vehicle.MainActivity')
    ready_by=time.monotonic()+60
    while time.monotonic()<ready_by:
        j.adb('shell','input','tap','2515','100')
        try:observed=j.ui_nodes(timeout=10)
        except (TimeoutError,subprocess.TimeoutExpired):continue
        if any(n.get('package')==PKG and n.get('text')=='여정 기록 시작' and n.get('enabled')=='true' for n in observed):break
        time.sleep(1)
    else:raise TimeoutError('Actual release start control not observed; no SDK inputs sent')
    bounds={}
    for label in ['여정 기록 시작','기록 마무리','기록은 계속 · 화면 닫기']:
        node=next(n for n in observed if n.get('package')==PKG and n.get('text')==label)
        bounds[label]=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
        assert len(bounds[label])==4
        if label=='여정 기록 시작':assert node.get('enabled')=='true'
    j.dump(run/'observed-controls.json',{'package':PKG,'bounds':bounds,'method':'actual current accessibility controls, before synthetic-input heartbeat'})
    def tap_observed(label):
        # Pleos may bring its default navigation app forward on synthetic GPS input.
        # Restore the observed product window before touching its cached controls.
        j.adb('shell','am','start','-W','--user','10','-n',PKG+'/dev.todaystrack.vehicle.MainActivity')
        left,top,right,bottom=bounds[label]
        j.adb('shell','input','tap',str((left+right)//2),str((top+bottom)//2))
    try:
        j.adb('forward','tcp:8088','tcp:8088');thread.start();time.sleep(5)
        j.adb('shell','appops','set','2000','android:mock_location','allow')
        j.adb('shell','cmd','location','providers','add-test-provider','gps','--supportsAltitude','--supportsBearing')
        j.adb('shell','cmd','location','providers','set-test-provider-enabled','gps','true')
        base=math.ceil(j.http('/vehicle/state','GET')['odometer']['traveled_distance_km'])
        latitude=37.5432 if args.mode=='LIVE' else 37.4979
        points=[(0,latitude),(2 if args.mode=='LIVE' else 1,latitude+.013),
                (4 if args.mode=='LIVE' else 3,latitude+.027),(4 if args.mode=='LIVE' else 3,latitude+.027),
                (6 if args.mode=='LIVE' else 5,latitude+.013),(8 if args.mode=='LIVE' else 6,latitude)]
        def inject(index,offset,lat):
            baseline=float(j.adb('shell','cat','/proc/uptime').split()[0])*1000
            j.http('/vehicle/odometer?traveled_distance_km='+str(base+offset),'PUT')
            j.adb('shell','cmd','location','providers','set-test-provider-location','gps','--location',f'{lat},127.0276','--accuracy','5')
            controls.append({'index':index,'distanceKm':base+offset,'latitude':lat,'longitude':127.0276,'baselineElapsedMs':baseline})
            j.dump(run/'controls.json',controls)
            time.sleep(4)
        # Prime the baseline before opening an epoch, then inject a fresh A after it starts.
        inject(-1,0,latitude)
        if args.record:
            recorder=subprocess.Popen([j.ADB,'-P','5038','-s',j.SERIAL,'shell','screenrecord','--size','1920x1080','--bit-rate','8000000','--time-limit','180',remote],
                stdout=(run/'screenrecord.stdout.log').open('wb'),stderr=(run/'screenrecord.stderr.log').open('wb'))
        tap_observed('여정 기록 시작');time.sleep(5)
        active=j.adb('shell','dumpsys','activity','services',PKG)
        (run/'capture-start-services.txt').write_text(active,encoding='utf-8')
        assert 'JourneyCaptureService' in active,'Observed start tap did not start product collection'
        for index,(offset,lat) in enumerate(points):
            inject(index,offset,lat)
            if index==1 and not (args.record or args.keep_visible):
                tap_observed('기록은 계속 · 화면 닫기');time.sleep(2)
                (run/'hidden-activities.txt').write_text(j.adb('shell','dumpsys','activity','activities'),encoding='utf-8')
                (run/'hidden-services.txt').write_text(j.adb('shell','dumpsys','activity','services',PKG),encoding='utf-8')
        if args.record or args.keep_visible:
            j.dump(run/'recording-strategy.json',{'uiKeptVisibleDuringCapture':True,
                'reason':'Observed platform black screen after multi-window teardown on software GPU; explicit limited foreground scenario',
                'backgroundEvidence':'Separate finite SDK probe and product S1 non-visible runs; not inferred from this video'})
        j.adb('shell','am','start','--user','10','-n',PKG+'/dev.todaystrack.vehicle.MainActivity')
        tap_observed('기록 마무리');time.sleep(3)
        stop.set();thread.join(timeout=10)
        until=time.monotonic()+30
        while time.monotonic()<until:
            text=ui_text()
            if any('관측 순서' in t for t in text):break
            time.sleep(1)
        j.dump(run/'summary-screen.json',text)
        summary=next(t for t in text if '관측 순서' in t)
        assert 'A → B → C → B → A' in summary, 'Unexpected SDK point order; no generation dispatched'
        expected=8 if args.mode=='LIVE' else 6
        assert f'{expected:.2f} km' in summary, 'Unexpected continuous SDK distance; no generation dispatched'
        j.dump(run/'generation-preflight.json',{'package':PKG,'mode':args.mode,'uiObservedSummary':summary,
            'provider':'Gemini API','model':'lyria-3.5','lyrics':'supplied non-paid template','paidLyricsRequests':0,
            'musicRequestsPerIntent':1 if args.mode=='LIVE' else 0,'reservationUsd':.10 if args.mode=='LIVE' else 0,
            'totalAuthorizedUsd':5,'retryPolicy':'no automatic repost after uncertain acceptance; same job/download only'})
        p.tap('모인 기록으로 음악 만들기')
        until=min(deadline,time.monotonic()+190)
        job=None
        while time.monotonic()<until:
            row=db.execute('select * from jobs order by rowid desc limit 1').fetchone()
            if row and db.execute('select count(*) from jobs').fetchone()[0]>before:
                job=dict(row)
                if job['state']=='READY':break
                if job['state'].startswith('FAILED') or job['state']=='RECONCILING':raise RuntimeError('Generation held: '+job['state'])
            time.sleep(2)
        assert job and job['state']=='READY' and job['mode']==args.mode
        payload=json.loads(job['payload'])
        assert payload['deviceId']==json.loads(Path('server/.local/release-enrollment.json').read_text(encoding='utf-8'))['deviceId']
        assert payload['settings']=={'genre':'어쿠스틱','lyricMode':'VOCAL'}
        assert payload['summary']['returnObserved'] and abs(payload['summary']['continuousDistanceKm']-expected)<.001
        j.dump(run/'server-link.json',{k:job[k] for k in ['id','hash','mode','state','asset_hash','asset_bytes','duration_ms','cost']})
        until=time.monotonic()+45
        while time.monotonic()<until:
            text=ui_text()
            if any('곡이 저장되었습니다' in t for t in text):break
            time.sleep(2)
        else:raise TimeoutError('Vehicle save not confirmed')
        j.dump(run/'saved-screen.json',text)
        j.dump(run/'result.json',{'mode':args.mode,'newAiGeneration':args.mode=='LIVE','freshReleaseSdkJourney':True,'vehicleStartedGeneration':True,
            'relativePointOrder':'A-B-C-C-B-A','continuousDistanceKm':expected,'inputHash':job['hash'],'jobId':job['id'],
            'audioSha256':job['asset_hash'],'fullyDecodedOnServer':True,'vehicleAvailableObserved':True,'activityKeptVisible':args.record or args.keep_visible,'backgroundCollectionProvenByThisRun':False,'listeningQualityAssessed':False})
        print(json.dumps({'run':str(run),'mode':args.mode,'jobId':job['id'],'newAiGeneration':args.mode=='LIVE','vehicleSaved':True}),flush=True)
    finally:
        stop.set();thread.join(timeout=10)
        try:
            if recorder:
                if recorder.poll() is None:
                    try:pids=j.adb('shell','pidof','screenrecord').split()
                    except RuntimeError:pids=[]
                    for pid in pids:
                        command=j.adb('shell','cat','/proc/'+pid+'/cmdline')
                        if remote in command:j.adb('shell','kill','-2',pid)
                try:recorder.wait(timeout=15)
                except subprocess.TimeoutExpired:recorder.terminate()
                j.adb('pull',remote,str(run/'screen.mp4'))
        finally:
            j.adb('shell','cmd','location','providers','remove-test-provider','gps')
            j.adb('shell','appops','set','2000','android:mock_location','default')

if __name__=='__main__':main()
