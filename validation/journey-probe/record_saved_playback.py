"""Local replay footage of the already-generated track. No generation or paid requests."""
import json
import sqlite3
import subprocess
import threading
import time
import submission_device as j
import product_capture as p
from offline_playback import state

PKG='dev.todaystrack.vehicle.submission'

def main():
    p.PKG=PKG
    assert j.adb('shell','getprop','ro.build.fingerprint').startswith('Pleos/')
    run=j.ROOT/'runs'/('offline-replay-video-'+time.strftime('%Y%m%d-%H%M%S'));run.mkdir()
    j.dump(j.ROOT/'replay-video-latest.json',{'run_path':str(run)})
    db=sqlite3.connect('server/.local/server.db');before=db.execute('select count(*) from jobs').fetchone()[0]
    wifi=j.adb('shell','settings','get','global','wifi_on').strip();data=j.adb('shell','settings','get','global','mobile_data').strip()
    remote='/sdcard/TodaysTrack-'+run.name+'.mp4';recorder=None;observations=[]
    try:
        j.adb('shell','svc','wifi','disable');j.adb('shell','svc','data','disable')
        # Radio disable completes asynchronously. Observe the actual default network instead of assuming a fixed delay.
        until=time.monotonic()+30
        while time.monotonic()<until:
            net=j.adb('shell','dumpsys','connectivity')
            (run/'connectivity.txt').write_text(net,encoding='utf-8')
            if 'Active default network: none' in net or 'Active default network: null' in net:break
            time.sleep(1)
        else:raise RuntimeError('Offline network state not observed; recording not started')
        j.adb('shell','am','force-stop','--user','10',PKG)
        j.adb('shell','am','start','--user','10','-n',PKG+'/dev.todaystrack.vehicle.MainActivity');time.sleep(3)
        j.adb('shell','input','tap','1700','180')
        texts=[n.get('text','') for n in j.ui_nodes()]
        title=next(t for t in texts if t.startswith('▶') and '새 AI 음악' in t)
        started=time.monotonic()
        recorder=subprocess.Popen([j.ADB,'-P','5038','-s',j.SERIAL,'shell','screenrecord','--size','1920x1080','--bit-rate','8000000','--time-limit','125',remote],
            stdout=(run/'screenrecord.stdout.log').open('wb'),stderr=(run/'screenrecord.stderr.log').open('wb'))
        time.sleep(7)
        p.tap(title)
        until=time.monotonic()+110
        playing_seen=False
        while time.monotonic()<until:
            dump=j.adb('shell','dumpsys','media_session')
            playback=state(dump,PKG)
            entry={'recordingRelativeSeconds':round(time.monotonic()-started,3),'state':playback[0],'positionMs':playback[1]}
            observations.append(entry);j.dump(run/'playback-timeline.json',observations)
            if playback[0]=='PLAYING' and playback[1]>0:playing_seen=True
            if playing_seen and playback[0]=='STOPPED' and playback[1]>=90000:
                (run/'ended-session.txt').write_text(dump,encoding='utf-8');break
            time.sleep(3)
        assert playing_seen
        text=[n.get('text','') for n in j.ui_nodes()];j.dump(run/'final-screen.json',text)
        assert any('재생을 마쳤습니다' in t or '재생 중' in t for t in text)
        assert db.execute('select count(*) from jobs').fetchone()[0]==before
        j.dump(run/'result.json',{'package':PKG,'offlineDefaultNetworkAbsent':True,'appRestartedBeforeRecording':True,
            'replayedExistingLiveTrack':True,'newProviderCalls':0,'playbackStateVisible':True,'playingObserved':True,
            'endObserved':observations[-1]['state']=='STOPPED','audioCapture':'screenrecord video only; same generated audio will be inserted in edit',
            'listeningQualityAssessed':False})
        print(json.dumps({'run':str(run),'offlineReplayFootage':True,'endObserved':observations[-1]['state']=='STOPPED'}),flush=True)
    finally:
        try:
            if recorder:
                if recorder.poll() is None:
                    try:pids=j.adb('shell','pidof','screenrecord').split()
                    except RuntimeError:pids=[]
                    for pid in pids:
                        if remote in j.adb('shell','cat','/proc/'+pid+'/cmdline'):j.adb('shell','kill','-2',pid)
                try:recorder.wait(timeout=15)
                except subprocess.TimeoutExpired:recorder.terminate()
                j.adb('pull',remote,str(run/'screen.mp4'))
        finally:
            if wifi=='1':j.adb('shell','svc','wifi','enable')
            if data=='1':j.adb('shell','svc','data','enable')

if __name__=='__main__':main()
