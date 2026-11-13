"""Local public enrollment and remembered settings for the release APK; no private key extraction."""
import json
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
import submission_device as j
import product_capture as p

PKG='dev.todaystrack.vehicle.submission'

def texts():
    j.adb('shell','uiautomator','dump','/data/local/tmp/todaystrack-release.xml')
    raw=j.adb('shell','cat','/data/local/tmp/todaystrack-release.xml')
    return [n.get('text','') for n in ET.fromstring(raw).iter('node')]

def main():
    p.PKG=PKG
    assert j.adb('shell','getprop','ro.build.fingerprint').startswith('Pleos/')
    for permission in ['android.permission.ACCESS_COARSE_LOCATION','android.permission.ACCESS_FINE_LOCATION','pleos.car.permission.FUSED_LOCATION','android.permission.POST_NOTIFICATIONS']:
        j.adb('shell','pm','grant','--user','10',PKG,permission)
    p.tap('로컬 시연 기기 연결 정보')
    public=None
    for t in texts():
        try:
            value=json.loads(t)
            if isinstance(value,dict) and set(value)=={'deviceId','publicKey'}:public=value;break
        except (ValueError,TypeError):pass
    assert public,'Public release registration dialog absent'
    target=Path('server/.local/release-enrollment.json')
    j.dump(target,public)
    p.tap('닫기',focus=False)
    p.tap('하루 기록');p.tap('여행 기록',focus=False)
    p.tap('무가사');p.tap('가사 있는 곡',focus=False)
    p.tap('잔잔한 팝');p.tap('어쿠스틱',focus=False)
    time.sleep(3)  # Let the UI selection event and SharedPreferences.apply finish before forced process death.
    assert '어쿠스틱' in texts()
    j.adb('shell','am','force-stop','--user','10',PKG)
    j.adb('shell','am','start','--user','10','-n',PKG+'/dev.todaystrack.vehicle.MainActivity')
    time.sleep(3)
    j.adb('shell','input','tap','1700','180')
    actual=texts()
    assert all(t in actual for t in ['여행 기록','가사 있는 곡','어쿠스틱']),'Settings not remembered'
    j.dump(j.ROOT/'release-setup.json',{'package':PKG,'publicEnrollmentFile':str(target),'settingsRemembered':['TRIP','VOCAL','어쿠스틱'],
        'privateKeyExtracted':False,'releaseStarted':True})
    print('Release started; only public enrollment exported; TRIP/VOCAL/acoustic settings survived app restart.',flush=True)

if __name__=='__main__':main()
