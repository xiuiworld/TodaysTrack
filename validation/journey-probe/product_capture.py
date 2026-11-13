"""Finite S1 product UI/Room persistence experiment; no generation/provider calls."""
import hashlib
import json
import re
import sqlite3
import subprocess
import threading
import time
import xml.etree.ElementTree as ET
from pathlib import Path
import submission_device as j

PKG = 'dev.todaystrack.vehicle'

def snapshot(run, name):
    folder = run / name
    folder.mkdir()
    for file in ['todaystrack.db', 'todaystrack.db-wal', 'todaystrack.db-shm']:
        result = subprocess.run([j.ADB, '-P','5038','-s',j.SERIAL,'exec-out','run-as',PKG,'--user','10','cat','databases/'+file], capture_output=True, timeout=20)
        if result.returncode == 0:
            (folder / file).write_bytes(result.stdout)
    db = sqlite3.connect(folder / 'todaystrack.db')
    db.row_factory = sqlite3.Row
    assert db.execute('pragma integrity_check').fetchone()[0] == 'ok'
    data = {table:[dict(r) for r in db.execute('SELECT * FROM '+table)] for table in ['sessions','epochs','events','gaps']}
    db.close()
    j.dump(folder / 'records.json', data)
    return data

def tap(text, focus=True):
    deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        # Pleos multi-window can initially give accessibility focus to DrivingView.
        # The observed app window's heading is harmless to focus; wait for its actual control.
        if focus:j.adb('shell','input','tap','2515','100')
        for n in j.ui_nodes():
            if n.get('text') == text and n.get('enabled') == 'true':
                a,b,c,d = map(int,re.findall(r'\d+',n.get('bounds')))
                j.adb('shell','input','tap',str((a+c)//2),str((b+d)//2))
                return
        time.sleep(1)
    raise RuntimeError('UI control unavailable: '+text)

def main():
    assert j.adb('shell','getprop','ro.build.fingerprint').startswith('Pleos/')
    assert j.adb('shell','am','get-current-user').strip() == '10'
    run = j.ROOT / 'runs' / ('s1-product-'+time.strftime('%Y%m%d-%H%M%S'))
    run.mkdir()
    j.dump(j.ROOT / 'product-latest.json', {'run_path':str(run)})
    stop = threading.Event()
    deadline = time.monotonic()+300
    def heartbeat():
        while not stop.is_set() and time.monotonic()<deadline:
            try: j.http('/browser/session?client=TodaysTrackProductS1','POST')
            except Exception: pass
            stop.wait(.6)
    thread = threading.Thread(target=heartbeat,daemon=True)
    controls=[]
    def input_point(km,lat,lon,label):
        response=j.http('/vehicle/odometer?traveled_distance_km='+str(km),'PUT')
        assert response['traveled_distance_km']==km
        j.adb('shell','cmd','location','providers','set-test-provider-location','gps','--location',f'{lat},{lon}','--accuracy','5')
        controls.append({'label':label,'km':km,'lat':lat,'lon':lon,'after_uptime':j.adb('shell','cat','/proc/uptime').split()[0]})
        j.dump(run/'controls.json',controls)
        time.sleep(4)
    try:
        j.adb('forward','tcp:8088','tcp:8088')
        thread.start()
        j.adb('shell','appops','set','2000','android:mock_location','allow')
        j.adb('shell','cmd','location','providers','add-test-provider','gps','--supportsAltitude','--supportsBearing')
        j.adb('shell','cmd','location','providers','set-test-provider-enabled','gps','true')
        j.adb('shell','am','force-stop','--user','10',PKG)
        j.adb('shell','am','start','--user','10','-n',PKG+'/.MainActivity')
        time.sleep(2)
        tap('여정 기록 시작')
        time.sleep(5)
        base=int(j.http('/vehicle/state','GET')['odometer']['traveled_distance_km'])+1
        input_point(base,37.4979,127.0276,'A')
        # Back leaves the native product UI. System evidence decides visibility, not HOME intent.
        tap('기록은 계속 · 화면 닫기')
        time.sleep(2)
        (run/'after-back-activities.txt').write_text(j.adb('shell','dumpsys','activity','activities'),encoding='utf-8')
        (run/'after-back-services.txt').write_text(j.adb('shell','dumpsys','activity','services',PKG),encoding='utf-8')
        input_point(base+1,37.5109,127.0276,'B_AFTER_UI_LEFT')
        input_point(base+3,37.5249,127.0276,'C')
        j.adb('shell','am','force-stop','--user','10',PKG)
        before=snapshot(run,'after-interruption')
        assert any(e['source']=='LOCATION' and e['latitude']==37.5109 and e['quality']=='FRESH' for e in before['events'])
        assert any(e['source']=='ODOMETER' and e['odometerKm']==base+3 for e in before['events'])
        time.sleep(2)
        j.adb('shell','am','start','--user','10','-n',PKG+'/.MainActivity')
        time.sleep(2)
        tap('여정 기록 시작')
        time.sleep(4)
        input_point(base+3,37.5249,127.0276,'C_AFTER_RESTART')
        input_point(base+5,37.5109,127.0276,'B_RETURN')
        input_point(base+6,37.4979,127.0276,'A_RETURN')
        tap('기록 마무리')
        time.sleep(3)
        (run/'after-stop-services.txt').write_text(j.adb('shell','dumpsys','activity','services',PKG),encoding='utf-8')
        j.adb('shell','am','force-stop','--user','10',PKG)
        after=snapshot(run,'after-normal-stop')
        old={e['seq']:e for e in before['events']}
        assert all(next(e for e in after['events'] if e['seq']==seq)==event for seq,event in old.items())
        assert all(e['closed']==1 for e in after['epochs'])
        assert any(g['reason']=='UNCLEAN_EPOCH_END' for g in after['gaps'])
        assert after['sessions'][-1]['state']=='SEALED'
        j.adb('shell','am','start','--user','10','-n',PKG+'/.MainActivity')
        apk=Path('vehicle/app/build/outputs/apk/debug/app-debug.apk')
        j.dump(run/'result.json',{'product_room_integrity':'ok','persisted_prefix':True,'unclean_gap_preserved':True,
            'normal_stop_closed_epochs':True,'new_sdk_points_matched':True,'simulation':True,'event_count':len(after['events']),
            'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'background_product_system_evidence':'after-back-activities.txt; review visibility before claiming',
            'limitations':['No real road/power/reboot/long duration test','This is S1, no music integration yet']})
        print(json.dumps({'run':str(run),'result':'product capture/Room persistence and gap verified','events':len(after['events'])}),flush=True)
    finally:
        stop.set();thread.join(timeout=10)
        j.adb('shell','cmd','location','providers','remove-test-provider','gps')
        j.adb('shell','appops','set','2000','android:mock_location','default')

if __name__=='__main__': main()
