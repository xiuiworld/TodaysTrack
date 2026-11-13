"""Bounded offline replay without toggling Bluetooth or creating generation jobs."""
import argparse
import json
import re
import sqlite3
import time
import xml.etree.ElementTree as ET
import submission_device as j
import product_capture as p

def state(dump, package):
    marker='package='+package+'\n'
    block=dump[dump.index(marker):] if marker in dump else ''
    m=re.search(r'state=PlaybackState \{state=([A-Z]+)\((\d+)\), position=(\d+)',block)
    return (m[1],int(m[3])) if m else ('MISSING',0)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--package',default=p.PKG);parser.add_argument('--live',action='store_true');args=parser.parse_args()
    assert args.package in ['dev.todaystrack.vehicle','dev.todaystrack.vehicle.submission']
    p.PKG=args.package
    assert j.adb('shell','getprop','ro.build.fingerprint').startswith('Pleos/')
    run=j.ROOT/'runs'/('offline-playback-'+time.strftime('%Y%m%d-%H%M%S'));run.mkdir()
    j.dump(j.ROOT/'offline-latest.json',{'run_path':str(run)})
    wifi=j.adb('shell','settings','get','global','wifi_on').strip()
    data=j.adb('shell','settings','get','global','mobile_data').strip()
    db=sqlite3.connect('server/.local/server.db');before=db.execute('select count(*) from jobs').fetchone()[0]
    try:
        j.adb('shell','svc','wifi','disable');j.adb('shell','svc','data','disable')
        time.sleep(5)
        network=j.adb('shell','dumpsys','connectivity')
        (run/'connectivity.txt').write_text(network,encoding='utf-8')
        assert 'Active default network: none' in network or 'Active default network: null' in network
        j.adb('shell','am','force-stop','--user','10',p.PKG)
        j.adb('shell','am','start','--user','10','-n',p.PKG+'/dev.todaystrack.vehicle.MainActivity')
        time.sleep(3)
        title=None
        until=time.monotonic()+30
        while time.monotonic()<until:
            j.adb('shell','input','tap','2515','100')
            j.adb('shell','uiautomator','dump','/data/local/tmp/todaystrack-offline.xml')
            raw=j.adb('shell','cat','/data/local/tmp/todaystrack-offline.xml')
            texts=[n.get('text','') for n in ET.fromstring(raw).iter('node')]
            matches=[t for t in texts if t.startswith('▶') and ('새 AI 음악' if args.live else '기존 음원') in t]
            if matches:title=matches[0];break
        assert title,'Saved track absent after offline restart'
        p.tap(title)
        until=time.monotonic()+35
        while time.monotonic()<until:
            dump=j.adb('shell','dumpsys','media_session')
            current=state(dump,p.PKG)
            if current[0]=='PLAYING' and current[1]>0:break
            time.sleep(1)
        else:raise RuntimeError('Playback not progressing: '+str(current))
        (run/'playing-first.txt').write_text(dump,encoding='utf-8')
        time.sleep(4)
        second=j.adb('shell','dumpsys','media_session');(run/'playing-second.txt').write_text(second,encoding='utf-8')
        later=state(second,p.PKG)
        assert later[0]=='PLAYING' and later[1]>current[1]
        p.tap('재생 일시정지');time.sleep(1)
        paused=j.adb('shell','dumpsys','media_session');(run/'paused.txt').write_text(paused,encoding='utf-8')
        assert state(paused,p.PKG)[0]=='PAUSED'
        assert db.execute('select count(*) from jobs').fetchone()[0]==before
        j.dump(run/'result.json',{'package':p.PKG,'mode':'LIVE' if args.live else 'FIXTURE','newAiGeneration':False,
            'existingStoredTrackReplayed':True,'offlineDefaultNetworkAbsent':True,'appRestarted':True,'playingPositionsMs':[current[1],later[1]],
            'paused':True,'newGenerationJobs':0,'listeningQualityAssessed':False})
        print(json.dumps({'run':str(run),'result':'offline restart, advancing playback and pause verified','positionsMs':[current[1],later[1]]}),flush=True)
    finally:
        if wifi=='1':j.adb('shell','svc','wifi','enable')
        if data=='1':j.adb('shell','svc','data','enable')

if __name__=='__main__':main()
