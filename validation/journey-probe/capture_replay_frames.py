"""Finite host-side screen capture: avoids Android screenrecord GPU/codec interference."""
import json
import sqlite3
import subprocess
import threading
import time
from pathlib import Path
import submission_device as j
import product_capture as p
from offline_playback import state

PKG='dev.todaystrack.vehicle.submission'

def main():
    p.PKG=PKG
    assert j.adb('shell','getprop','ro.build.fingerprint').startswith('Pleos/')
    run=j.ROOT/'runs'/('replay-frames-'+time.strftime('%Y%m%d-%H%M%S'));run.mkdir();frames=run/'frames';frames.mkdir()
    j.dump(j.ROOT/'replay-video-latest.json',{'run_path':str(run)})
    wifi=j.adb('shell','settings','get','global','wifi_on').strip();data=j.adb('shell','settings','get','global','mobile_data').strip()
    db=sqlite3.connect('server/.local/server.db');before=db.execute('select count(*) from jobs').fetchone()[0]
    stop=threading.Event();timeline=[];playback=[];thread=None
    def capture():
        deadline=time.monotonic()+125
        while not stop.is_set() and time.monotonic()<deadline:
            before_capture=time.monotonic()
            result=subprocess.run([j.ADB,'-P','5038','-s',j.SERIAL,'exec-out','screencap','-p'],capture_output=True,timeout=15)
            after=time.monotonic()
            if result.returncode==0 and result.stdout.startswith(b'\x89PNG'):
                name=f'frame-{len(timeline):04d}.png';(frames/name).write_bytes(result.stdout)
                timeline.append({'file':name,'requestedAt':before_capture,'receivedAt':after,'capturedAtEstimate':(before_capture+after)/2})
                j.dump(run/'capture-timeline.json',timeline)
            stop.wait(max(0,1.0-(time.monotonic()-before_capture)))
    try:
        j.adb('shell','svc','wifi','disable');j.adb('shell','svc','data','disable')
        deadline=time.monotonic()+30
        while time.monotonic()<deadline:
            net=j.adb('shell','dumpsys','connectivity');(run/'connectivity.txt').write_text(net,encoding='utf-8')
            if 'Active default network: none' in net or 'Active default network: null' in net:break
            time.sleep(1)
        else:raise RuntimeError('No offline default-network proof')
        j.adb('shell','am','force-stop','--user','10',PKG)
        j.adb('shell','am','start','--user','10','-n',PKG+'/dev.todaystrack.vehicle.MainActivity');time.sleep(3)
        j.adb('shell','input','tap','1700','180')
        text=[n.get('text','') for n in j.ui_nodes()]
        title=next(t for t in text if t.startswith('▶') and '새 AI 음악' in t)
        assert any('일시정지' in t or '재생 대기' in t for t in text)
        thread=threading.Thread(target=capture,daemon=True);thread.start();time.sleep(2)
        p.tap(title)
        deadline=time.monotonic()+108;playing=False;ended=False
        while time.monotonic()<deadline:
            dump=j.adb('shell','dumpsys','media_session');now=time.monotonic();s=state(dump,PKG)
            playback.append({'hostMonotonic':now,'state':s[0],'positionMs':s[1]});j.dump(run/'playback-timeline.json',playback)
            if s[0]=='PLAYING' and s[1]>0:playing=True
            if playing and s[0]=='STOPPED' and s[1]>=90000:
                ended=True;(run/'ended-session.txt').write_text(dump,encoding='utf-8');time.sleep(3);break
            if not playing and len(playback)>=7:raise RuntimeError('No playback after one user request; no focus bypass/repeated retry')
            time.sleep(3)
        assert playing
        assert db.execute('select count(*) from jobs').fetchone()[0]==before
        j.dump(run/'result.json',{'method':'timed host-side ADB screenshots, approximately 1 fps','package':PKG,'offlineDefaultNetworkAbsent':True,
            'appRestarted':True,'existingNewAiTrackReplayed':True,'newProviderCalls':0,'playingObserved':playing,'endObserved':ended,
            'listeningQualityAssessed':False})
        print(json.dumps({'run':str(run),'playingObserved':playing,'endObserved':ended}),flush=True)
    finally:
        stop.set()
        if thread:thread.join(timeout=18)
        if timeline:
            lines=['ffconcat version 1.0']
            for i,entry in enumerate(timeline):
                lines.append("file 'frames/"+entry['file']+"'")
                if i+1<len(timeline):lines.append('duration '+str(timeline[i+1]['capturedAtEstimate']-entry['capturedAtEstimate']))
            (run/'frames.ffconcat').write_text('\n'.join(lines)+'\n',encoding='utf-8')
        if wifi=='1':j.adb('shell','svc','wifi','enable')
        if data=='1':j.adb('shell','svc','data','enable')

if __name__=='__main__':main()
