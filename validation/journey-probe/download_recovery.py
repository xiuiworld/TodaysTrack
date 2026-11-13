"""Finite fault injection into DEBUG fixture data; originals backed up/restored.

Leaves the release app and real song untouched. Simulates a persisted interrupted
download (.part) and corrupt cache; does not claim a live network-cut experiment.
"""
from pathlib import Path
import hashlib,json,shutil,sqlite3,subprocess,time,uuid
import submission_device as j
import product_capture as p

def command(*args,data=None,check=True):
    return subprocess.run([j.ADB,'-P','5038','-s',j.SERIAL,*args],input=data,capture_output=True,check=check,timeout=40)
def read_private(path):return command('exec-out','run-as',p.PKG,'--user','10','cat',path).stdout
def write_private(path,data):
    assert path.startswith(('databases/','/data/user/10/'+p.PKG+'/files/tracks/'))
    # ADB exec-out stdin does not deliver EOF reliably here. Use a bounded copy.
    transfer=j.ROOT/'.local';transfer.mkdir(exist_ok=True)
    token='todaystrack-transfer-'+uuid.uuid4().hex
    local=transfer/(token+'.bin');remote='/data/local/tmp/'+token
    local.write_bytes(data)
    try:
        command('push',str(local),remote)
        command('shell','chmod','644',remote)
        command('shell','run-as',p.PKG,'--user','10','cp',remote,path)
    finally:
        command('shell','rm','-f',remote,check=False)
        local.unlink(missing_ok=True)
def db_copy(run,label):
    dest=run/label;dest.mkdir()
    for name in ['todaystrack.db','todaystrack.db-wal','todaystrack.db-shm']:
        result=command('exec-out','run-as',p.PKG,'--user','10','cat','databases/'+name,check=False)
        if result.returncode==0:(dest/name).write_bytes(result.stdout)
    connection=sqlite3.connect(dest/'todaystrack.db');connection.row_factory=sqlite3.Row
    assert connection.execute('pragma integrity_check').fetchone()[0]=='ok'
    return connection
def main():
    subprocess.run([j.ADB,'-P','5038','connect',j.SERIAL],capture_output=True,timeout=15)
    deadline=time.monotonic()+160
    while time.monotonic()<deadline:
        try:
            if j.adb('shell','getprop','sys.boot_completed').strip()=='1':break
        except Exception:pass
        time.sleep(2)
    else:raise TimeoutError('Dedicated AVD did not boot')
    assert j.adb('shell','getprop','ro.build.fingerprint').startswith('Pleos/')
    assert j.adb('shell','am','get-current-user').strip()=='10'
    run=j.ROOT/'runs'/('download-recovery-'+time.strftime('%Y%m%d-%H%M%S'));run.mkdir()
    j.dump(j.ROOT/'download-recovery-latest.json',{'run_path':str(run)})
    server=sqlite3.connect('server/.local/server.db')
    before=(server.execute('select count(*) from jobs').fetchone()[0],server.execute('select count(*) from attempts').fetchone()[0],server.execute('select reserved from budget').fetchone()[0])
    assert json.loads(Path('server/.local/dispatch.json').read_text(encoding='utf-8-sig'))['enabled'] is False
    j.adb('shell','am','force-stop','--user','10',p.PKG)
    db=db_copy(run,'original')
    intent=dict(db.execute('select * from generation_intents order by rowid desc limit 1').fetchone())
    track=dict(db.execute('select * from tracks where jobId=?',(intent['jobId'],)).fetchone())
    assert intent['state']=='AVAILABLE' and intent['mode']=='FIXTURE' and track['provenance']=='FIXTURE'
    original_rows={table:[tuple(r) for r in db.execute('select * from '+table)] for table in ['sessions','epochs','events','gaps','generation_intents','tracks']}
    good_audio=read_private(track['path'])
    assert hashlib.sha256(good_audio).hexdigest()==track['sha256']
    (run/'original-fixture.mp3').write_bytes(good_audio)
    # A standalone backup remains durable even after host SQLite checkpoints WAL.
    backup=sqlite3.connect(run/'original-standalone.db');db.backup(backup);backup.close()
    injected=sqlite3.connect(run/'injected.db');db.backup(injected);db.close()
    injected.execute("update generation_intents set state='READY' where id=?",(intent['id'],));injected.commit();injected.close()
    changed=False
    try:
        changed=True
        for name in ['todaystrack.db-wal','todaystrack.db-shm']:
            command('shell','run-as',p.PKG,'--user','10','rm','-f','databases/'+name)
        write_private('databases/todaystrack.db',(run/'injected.db').read_bytes())
        # Corrupt final cache plus a partial temp file. Neither is considered valid.
        write_private(track['path'],b'fault-injection-corrupt-cache')
        write_private(track['path']+'.part',good_audio[:4096])
        j.dump(run/'injection.json',{'package':p.PKG,'mode':'FIXTURE','jobId':intent['jobId'],'sameExistingInputHash':intent['inputHash'],'partialBytes':4096,'corruptFinalBytes':27,'networkCutClaimed':False,'releaseAppModified':False})
        j.adb('shell','am','start','--user','10','-n',p.PKG+'/.MainActivity')
        time.sleep(4)
        p.tap('모인 기록으로 음악 만들기')
        deadline=time.monotonic()+75
        observed=False
        while time.monotonic()<deadline:
            nodes=j.ui_nodes()
            if any('곡이 저장되었습니다' in n.get('text','') for n in nodes):observed=True;break
            time.sleep(2)
        assert observed,'Existing asset did not recover to AVAILABLE'
        j.adb('shell','am','force-stop','--user','10',p.PKG)
        recovered=read_private(track['path'])
        assert hashlib.sha256(recovered).hexdigest()==track['sha256']
        afterdb=db_copy(run,'recovered')
        for table,rows in original_rows.items():assert [tuple(r) for r in afterdb.execute('select * from '+table)]==rows,'Data changed: '+table
        afterdb.close()
        after=(server.execute('select count(*) from jobs').fetchone()[0],server.execute('select count(*) from attempts').fetchone()[0],server.execute('select reserved from budget').fetchone()[0])
        assert after==before
        j.dump(run/'result.json',{'package':p.PKG,'mode':'FIXTURE','fault':'persisted partial plus corrupt final cache','sameJobRecovered':True,'originalInputAndRecordsPreserved':True,'audioHashMatched':track['sha256'],'newJobs':0,'newProviderAttempts':0,'budgetReservationUnchanged':True,'releaseAppModified':False,'liveNetworkCutClaimed':False})
        print(json.dumps({'run':str(run),'sameAssetRecovered':True,'newProviderAttempts':0}),flush=True)
    finally:
        if changed:
            j.adb('shell','am','force-stop','--user','10',p.PKG)
            for name in ['todaystrack.db-wal','todaystrack.db-shm']:
                command('shell','run-as',p.PKG,'--user','10','rm','-f','databases/'+name)
            write_private('databases/todaystrack.db',(run/'original-standalone.db').read_bytes())
            write_private(track['path'],good_audio)
            command('shell','run-as',p.PKG,'--user','10','rm','-f',track['path']+'.part')
            assert hashlib.sha256(read_private(track['path'])).hexdigest()==track['sha256']
            j.dump(run/'restoration.json',{'originalStandaloneDatabaseRestored':True,'originalFixtureAudioHashRestored':True,'releaseUntouched':True})
        server.close()
if __name__=='__main__':main()
