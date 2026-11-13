"""S2/S3 bounded real vehicle HTTPS + existing-audio fixture save/offline playback."""
import hashlib
import json
import sqlite3
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
import submission_device as j
import product_capture as p

def ui_text():
    j.adb('shell','input','tap','1700','180')
    j.adb('shell','uiautomator','dump','/data/local/tmp/todaystrack-fixture.xml')
    raw=j.adb('shell','cat','/data/local/tmp/todaystrack-fixture.xml')
    return [n.get('text','') for n in ET.fromstring(raw).iter('node') if n.get('package')==p.PKG]

def main():
    run=j.ROOT/'runs'/('fixture-integration-'+time.strftime('%Y%m%d-%H%M%S'))
    run.mkdir()
    j.dump(j.ROOT/'fixture-latest.json',{'run_path':str(run)})
    db=sqlite3.connect('server/.local/server.db');db.row_factory=sqlite3.Row
    before=db.execute('SELECT COUNT(*) FROM jobs').fetchone()[0]
    p.tap('모인 기록으로 음악 만들기')
    p.tap('모인 기록으로 음악 만들기')
    deadline=time.monotonic()+100
    job=None
    while time.monotonic()<deadline:
        row=db.execute('SELECT * FROM jobs ORDER BY rowid DESC LIMIT 1').fetchone()
        if row:
            job=dict(row)
            if job['state']=='READY':break
            if job['state'].startswith('FAILED') or job['state']=='RECONCILING':raise RuntimeError('Job held: '+job['state'])
        time.sleep(1)
    assert job and job['state']=='READY', 'No ready fixture job'
    assert job['mode']=='FIXTURE' and job['cost']==0
    assert db.execute('SELECT COUNT(*) FROM jobs').fetchone()[0]==before+1, 'Duplicate click created more than one job'
    until=time.monotonic()+45
    while time.monotonic()<until:
        text=ui_text()
        if any('곡이 저장되었습니다' in s for s in text):break
        time.sleep(2)
    else:raise TimeoutError('Vehicle download not available: '+' | '.join(text))
    j.adb('shell','am','force-stop','--user','10',p.PKG)
    p.snapshot(run,'after-save')
    device=sqlite3.connect(run/'after-save'/'todaystrack.db');device.row_factory=sqlite3.Row
    intent=dict(device.execute('SELECT * FROM generation_intents ORDER BY rowid DESC LIMIT 1').fetchone())
    track=dict(device.execute('SELECT * FROM tracks ORDER BY rowid DESC LIMIT 1').fetchone())
    assert intent['state']=='AVAILABLE' and intent['jobId']==job['id'] and intent['inputHash']==job['hash']
    assert track['sha256']==job['asset_hash'] and track['provenance']=='FIXTURE'
    audio=subprocess.run([j.ADB,'-P','5038','-s',j.SERIAL,'exec-out','run-as',p.PKG,'--user','10','cat',track['path']],capture_output=True,check=True,timeout=20).stdout
    assert hashlib.sha256(audio).hexdigest()==track['sha256']
    (run/'device-fixture.mp3').write_bytes(audio)
    j.dump(run/'connection.json',{'jobId':job['id'],'intentId':intent['id'],'inputHash':intent['inputHash'],
        'audioSha256':track['sha256'],'mode':'FIXTURE','newAiGeneration':False,'duplicateClickSingleJob':True,'snapshotPayload':json.loads(intent['payload'])})
    wifi=j.adb('shell','settings','get','global','wifi_on').strip()
    airplane=j.adb('shell','settings','get','global','airplane_mode_on').strip()
    try:
        j.adb('shell','cmd','connectivity','airplane-mode','enable')
        j.adb('shell','svc','wifi','disable')
        time.sleep(3)
        network=j.adb('shell','dumpsys','connectivity')
        (run/'offline-connectivity.txt').write_text(network,encoding='utf-8')
        assert 'Active default network: none' in network or 'Active default network: null' in network, 'No proof of offline default network'
        j.adb('shell','am','start','--user','10','-n',p.PKG+'/.MainActivity')
        time.sleep(2)
        p.tap(f"▶ {track['title']} · {track['durationMs']//1000}초")
        time.sleep(2)
        first=j.adb('shell','dumpsys','media_session')
        (run/'offline-playback-first.txt').write_text(first,encoding='utf-8')
        time.sleep(4)
        second=j.adb('shell','dumpsys','media_session')
        (run/'offline-playback-second.txt').write_text(second,encoding='utf-8')
        assert p.PKG in first and 'state=3' in first, 'No playing session'
        p.tap('재생 일시정지')
        (run/'offline-paused.txt').write_text(j.adb('shell','dumpsys','media_session'),encoding='utf-8')
    finally:
        if airplane=='0':j.adb('shell','cmd','connectivity','airplane-mode','disable')
        if wifi=='1':j.adb('shell','svc','wifi','enable')
    j.dump(run/'result.json',{'mode':'FIXTURE','newAiGeneration':False,'vehicleHttpsJob':True,'frozenInputHashLinked':True,
        'duplicateClickSingleJob':True,'downloadHashMatched':True,'appRestarted':True,'offlineDefaultNetworkAbsent':True,
        'mediaSessionPlaying':True,'listeningQualityAssessed':False,'remaining':'review playback position progression and paused state dumps'})
    print(json.dumps({'run':str(run),'result':'fixture vehicle job, save/restart/offline playback observed','newAiGeneration':False}),flush=True)

if __name__=='__main__':main()
