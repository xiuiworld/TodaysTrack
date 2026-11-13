"""Portable finite product-verification helpers; legacy journey probe remains unchanged."""
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import time
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parent
SDK=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path(os.environ['LOCALAPPDATA'])/'Android'/'Sdk')
ADB=str(SDK/'platform-tools'/'adb.exe')
SERIAL='127.0.0.1:5661'
BASE='http://127.0.0.1:8088'

def dump(path,value):
    Path(path).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def adb(*args):
    result=subprocess.run([ADB,'-P','5038','-s',SERIAL,*args],capture_output=True,timeout=35)
    if result.returncode:
        raise RuntimeError('Dedicated ADB command failed: '+result.stderr.decode('utf-8',errors='replace')[:500])
    return result.stdout.decode('utf-8',errors='strict').replace('\r\n','\n')

def http(path,method):
    with urllib.request.urlopen(urllib.request.Request(BASE+path,method=method),timeout=10) as response:
        return json.load(response)

def ui_nodes(timeout=30):
    """Pleos uiautomator can exit zero without a root/file. Wait for the artifact, not exit code alone."""
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        path='/data/local/tmp/todaystrack-ui-'+str(time.monotonic_ns())+'.xml'
        try:
            adb('shell','uiautomator','dump',path)
            raw=adb('shell','cat',path)
            return list(ET.fromstring(raw).iter('node'))
        except (RuntimeError,ET.ParseError):
            time.sleep(1)
    raise TimeoutError('No valid accessibility root artifact; product outcome not inferred')
