"""Read-only artifact verification; writes sanitized evidence only. No builds or device calls."""
from pathlib import Path
import argparse,hashlib,json,re,sqlite3,xml.etree.ElementTree as ET,zipfile

ROOT=Path(__file__).resolve().parents[1]
KNOWN_CERT='56fd311ccd45d0dd255a6241701175f7a530a668cb243d609c51962d310c0490'
PUBLIC_TLS='vehicle/app/src/main/res/raw/development_server.pem'
PRIVATE_EXTENSIONS={'.p12','.pfx','.dpapi','.keystore','.jks','.db','.sqlite','.sqlite3'}
PATTERNS=[re.compile(rb'AIza[0-9A-Za-z_-]{30,}'),re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----')]

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def read_text(path):
    return path.read_text(encoding='utf-16' if path.read_bytes().startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig')
def write(name,value):
    (ROOT/'validation/evidence'/name).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
def local_path(value):
    path=Path(value)
    return path.resolve() if path.is_absolute() else (ROOT/path).resolve()
def public_path(path):
    try:return path.relative_to(ROOT).as_posix()
    except ValueError:return path.name
def code_entry(item):
    return item['path'].startswith(('vehicle/','server/','tools/')) or (item['path'].startswith('validation/') and Path(item['path']).suffix=='.py')
def safe_entry(name):
    path=Path(name)
    assert not path.is_absolute() and '..' not in path.parts,name
    assert '.local' not in path.parts and path.suffix.lower() not in PRIVATE_EXTENSIONS,name
    assert path.name not in {'local.properties','enrollment.json','dispatch.json','server-process.json'},name
    assert not path.name.endswith(('.db-wal','.db-shm')),name
def scan_archive(path):
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None,public_path(path)
        for name in archive.namelist():
            safe_entry(name)
            assert not any(pattern.search(archive.read(name)) for pattern in PATTERNS),'Credential in '+public_path(path)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--expected-apk-sha256',required=True)
    parser.add_argument('--repro-dir',default='submission/repro-v0.2-check')
    parser.add_argument('--package-dir',default='submission/package')
    parser.add_argument('--signature-file',default='validation/evidence/resume-final-apk-signature.txt')
    parser.add_argument('--installed-package-file',default='validation/evidence/resume-final-installed-package.txt')
    parser.add_argument('--repro-release-log',default='submission/repro-v0.2-release-build.log')
    parser.add_argument('--repro-server-log',default='submission/repro-v0.2-server-build.log')
    args=parser.parse_args()
    expected_apk=args.expected_apk_sha256.lower()
    assert re.fullmatch(r'[0-9a-f]{64}',expected_apk),'Expected APK SHA256 must be 64 hex characters'
    gradle=read_text(ROOT/'vehicle/app/build.gradle.kts')
    version=re.search(r'versionName\s*=\s*"([^"]+)"',gradle).group(1)
    version_code=int(re.search(r'versionCode\s*=\s*(\d+)',gradle).group(1))

    profile=json.loads(read_text(ROOT/'tools/workflow-guard/project.profile.json'))
    protected=['AGENTS.md','docs/product/product-plan.md','docs/design/README.md','docs/design/recording-and-journeys.md','docs/design/data-api-and-jobs.md','docs/development/workflow-guard.md','docs/development/implementation-follow-up.md']
    preserved=[]
    for relative in protected:
        digest=sha(ROOT/relative)
        assert digest==profile['sourceHashes'][relative],relative
        preserved.append({'path':relative,'sha256':digest,'matchesPreExecutionBaseline':True})
    for relative in ['docs/proposal.md','docs/archive/passenger-gift-proposal.md']:
        tail=re.split(r'^## 7\..*$',read_text(ROOT/relative),flags=re.M)[1]
        assert not tail.strip(),relative
        preserved.append({'path':relative,'sha256':sha(ROOT/relative),'section7Blank':True})

    apk=ROOT/'apk/TodaysTrack-release.apk'
    assert sha(apk)==expected_apk,'Current APK differs from supplied SHA256'
    signature_path=local_path(args.signature_file)
    signature=read_text(signature_path)
    assert 'Verifies' in signature and KNOWN_CERT in signature,'Release signature evidence mismatch'
    installed_path=local_path(args.installed_package_file)
    installed=read_text(installed_path)
    assert 'dev.todaystrack.vehicle.submission' in installed,'Wrong installed package evidence'
    assert re.search(r'^\s*versionName=(\S+)',installed,re.M).group(1)==version,'Installed version differs from Gradle'
    assert int(re.search(r'^\s*versionCode=(\d+)',installed,re.M).group(1))==version_code,'Installed versionCode differs from Gradle'
    flags=re.search(r'pkgFlags=\[([^\]]+)\]',installed).group(1)
    assert 'DEBUGGABLE' not in flags,'Installed release is debuggable'

    audio=ROOT/'submission/audio/TodaysTrack-8km-Acoustic.mp3'
    assert sha(audio)=='ea0287f39c76dbd32c3d14d46d0289a2091f0e321c39198303359ca571c89712'
    with sqlite3.connect(f'file:{(ROOT/"server/.local/server.db").as_posix()}?mode=ro',uri=True) as database:
        reserved,cap=database.execute('SELECT reserved,cap FROM budget WHERE id=1').fetchone()
        attempts=database.execute("SELECT COUNT(*) FROM attempts a JOIN jobs j ON a.job_id=j.id WHERE j.mode='LIVE'").fetchone()[0]
        assert attempts==1 and reserved==400000 and cap==5000000
        assert database.execute("SELECT COUNT(*) FROM jobs WHERE mode='LIVE' AND state='READY'").fetchone()[0]==1
    assert json.loads(read_text(ROOT/'server/.local/dispatch.json'))['enabled'] is False

    repro=local_path(args.repro_dir)
    original=json.loads(read_text(repro/'source-inventory.json'))
    source_zip=local_path(args.package_dir)/'source/TodaysTrack-source.zip'
    code=[item for item in original['files'] if code_entry(item)]
    with zipfile.ZipFile(source_zip) as archive:
        packaged=json.loads(archive.read('source-inventory.json').decode('utf-8-sig'))
        for item in code:
            safe_entry(item['path'])
            assert hashlib.sha256(archive.read(item['path'])).hexdigest()==item['sha256'],'Packaged code bytes mismatch: '+item['path']
    assert code and code==[item for item in packaged['files'] if code_entry(item)],'Reproduction and packaged code inventories differ'
    revision=hashlib.sha256(''.join(item['path']+'\0'+item['sha256']+'\n' for item in code).encode()).hexdigest()
    assert revision==original['revision']==packaged['revision'],'Code inventory revision mismatch'
    for item in code:
        safe_entry(item['path'])
        assert sha(ROOT/item['path'])==item['sha256'],'Changed source since reproduction build: '+item['path']
        if item['path']!=PUBLIC_TLS:
            assert sha(repro/item['path'])==item['sha256'],'Extracted source mismatch: '+item['path']

    tests={}
    test_inputs=[]
    for name,relative in [('vehicle','vehicle/app/build/test-results/testReleaseUnitTest'),('server','server/build/test-results/test')]:
        cases=[]
        for path in sorted((repro/relative).glob('TEST-*.xml')):
            suite=ET.parse(path).getroot()
            assert int(suite.get('failures','0'))==0 and int(suite.get('errors','0'))==0,public_path(path)
            names=[case.get('classname')+'.'+case.get('name') for case in suite.findall('testcase')]
            assert not suite.findall('.//failure') and not suite.findall('.//error'),public_path(path)
            cases+=names
            test_inputs.append({'target':name,'path':public_path(path),'sha256':sha(path),'testCount':len(names),'reportedSkipped':int(suite.get('skipped','0')),'testNames':names})
        assert cases,(name,'No actual test cases in reproduction XML')
        tests[name]=cases
    build_inputs=[]
    for value in [args.repro_release_log,args.repro_server_log]:
        path=local_path(value)
        assert 'BUILD SUCCESSFUL' in read_text(path),public_path(path)
        build_inputs.append({'path':public_path(path),'sha256':sha(path)})
    scan_archive(apk)
    scan_archive(source_zip)

    write('reproduction-check.json',{
        'archiveExtractedTo':public_path(repro),'separateLocalTlsAndSigning':True,
        'sourceCodeMatchesPackagedRevision':original['revision'],
        'changedDuringSetup':['public TLS certificate only, expected reinitialization'],
        'releaseBuildSucceeded':True,'serverBuildSucceeded':True,'version':version,'versionCode':version_code,
        'tests':tests,'testCounts':{name:len(cases) for name,cases in tests.items()},'testResultInputs':test_inputs,
        'buildLogInputs':build_inputs,'sameApkBytesClaimed':False,'paidCalls':0,'deviceDataChanged':False})
    write('submission-final-checks.json',{
        'protectedDocuments':preserved,'apkSha256':sha(apk),'expectedApkSha256':expected_apk,
        'apkSignatureVerified':True,'signatureEvidence':{'path':public_path(signature_path),'sha256':sha(signature_path)},
        'installedVersion':f'{version}({version_code})','installedDebuggable':False,
        'installedPackageEvidence':{'path':public_path(installed_path),'sha256':sha(installed_path)},
        'historicalMusicGenerationVersion':'0.1.0-submission','historicalOfflinePlaybackVersion':'0.1.1-submission',
        'audioSha256':sha(audio),'musicProviderAttempts':attempts,'reservedUsd':reserved/1e6,'budgetCapUsd':cap/1e6,
        'dispatchEnabled':False,'apkCredentialPatternScan':'pass','sourceArchiveCredentialPatternScan':'pass',
        'sourceCuratedAllowlistScan':'Package-Submission.py private-state exclusions and current inventory checks',
        'rawRuntimeCoordinatesExcluded':True,'syntheticCoordinatesInTestCode':True,
        'listeningQualityAssessed':False,'listeningEvaluationStatus':'deferred_by_user',
        'listeningDeferredBlocksImplementation':False,'externalUploadEmailDeployment':False})
    print(json.dumps({'status':'verified','version':version,'versionCode':version_code,
        'testCounts':{name:len(cases) for name,cases in tests.items()},'apkSha256':sha(apk)}))

if __name__=='__main__':main()

