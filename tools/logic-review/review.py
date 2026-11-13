"""Offline Kotlin logic review. Only the explicit `deps` command downloads a public jar.
No product server, emulator, provider call, secret loading or source mutation.
"""
import argparse
import hashlib
import http.server
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
LOCAL = ROOT / '.local/logic-review'
BASE = '117f219'
VEHICLE = 'vehicle/app/src/main/java/dev/todaystrack/vehicle/'
FILES = [VEHICLE + p for p in ['capture/CapturePolicy.kt', 'journey/ObservationFacts.kt',
    'journey/JourneySummaryBuilder.kt', 'journey/SourceCoverage.kt', 'generation/InputSnapshotBuilder.kt',
    'generation/CanonicalSdkNumber.kt', 'generation/GenerationRecoveryPolicy.kt']]

def digest(data):
    return hashlib.sha256(data).hexdigest()

def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False), encoding='utf-8')

def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def java_env():
    env = dict(os.environ)
    jdk = Path(env.get('JAVA_HOME', ''))
    if not (jdk / 'bin/java.exe').is_file():
        jdk = Path.home() / 'Documents/Codex/Tools/Java/jdk-17.0.21+9'
    if not (jdk / 'bin/java.exe').is_file():
        raise RuntimeError('Set JAVA_HOME to JDK 17')
    env['JAVA_HOME'] = str(jdk)
    env['PATH'] = str(jdk / 'bin') + os.pathsep + env['PATH']
    # This harness never reads keys; do not pass music or signing secrets to its children.
    for key in list(env):
        if key.startswith(('GEMINI_', 'TODAYSTRACK_')):
            del env[key]
    return env

def source(path, ref=None):
    if ref:
        return subprocess.check_output(['git', 'show', f'{ref}:{path}'], cwd=ROOT)
    return (ROOT / path).read_bytes()

def paths_at(ref=None):
    if ref:
        return subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', ref], cwd=ROOT).decode().splitlines()
    return [p.relative_to(ROOT).as_posix() for base in ['server/src', 'vehicle/app/src/test', VEHICLE+'generation/v2']
            for p in (ROOT / base).rglob('*.kt')]

def prepare(dest, ref=None):
    all_paths = paths_at(ref)
    main = FILES + [p for p in all_paths if (p.startswith('server/src/main/kotlin/') or p.startswith(VEHICLE+'generation/v2/')) and p.endswith('.kt')]
    tests = [p for p in all_paths if p.endswith('.kt') and (p.startswith('server/src/test/kotlin/') or
        any(p.startswith('vehicle/app/src/test/java/dev/todaystrack/vehicle/' + part + '/')
            for part in ['capture', 'journey', 'generation']))]
    hashes = {}
    for folder, paths in [('main', main), ('test', tests)]:
        for p in paths:
            raw = source(p, ref)
            hashes[p] = digest(raw)
            target = dest / folder / p
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(raw)
    # Host boundary: preserve actual model declarations/constructor defaults, removing ONLY Room annotations/imports.
    model_path = VEHICLE + 'storage/CaptureDatabase.kt'
    raw = source(model_path, ref)
    hashes[model_path] = digest(raw)
    text = raw.decode('utf-8-sig').split('@Database(')[0]
    text = re.sub(r'^import .*\n', '', text, flags=re.M)
    text = re.sub(r'^@Entity\([^\n]*\)\r?\n', '', text, flags=re.M)
    text = re.sub(r'@PrimaryKey(?:\([^)]*\))?\s*', '', text)
    if '@' in text or not all(f'data class {name}(' in text for name in ['CaptureSession','CaptureEpoch','CaptureEvent','CaptureGap']):
        raise RuntimeError('Storage model projection needs review; refusing silent drift')
    (dest / 'main/StorageModels.kt').write_text(text, encoding='utf-8')
    identity_path = VEHICLE + 'auth/DevelopmentIdentity.kt'
    raw = source(identity_path, ref)
    hashes[identity_path] = digest(raw)
    companion = re.search(r'    companion object \{ fun sha256\(bytes: ByteArray\): String = .* \}', raw.decode('utf-8-sig'))
    if not companion:
        raise RuntimeError('Hash boundary changed; inspect before using JVM projection')
    (dest / 'main/DevelopmentIdentity.kt').write_text('package dev.todaystrack.vehicle.auth\nimport java.security.MessageDigest\n'
        + 'class DevelopmentIdentity {\n' + companion.group(0) + '\n}\n', encoding='utf-8')
    return hashes

def tests_summary(build):
    summaries=[]
    for p in (build / 'test-results/test').glob('TEST-*.xml'):
        root=ET.parse(p).getroot()
        summaries.append({k:root.get(k) for k in ['name','tests','failures','errors','skipped']})
    return summaries

def build_one(run, name, ref, jar):
    prepared=run / name / 'prepared'
    hashes=prepare(prepared, ref)
    build=run / name / 'build'
    command=[str(ROOT / 'server/gradlew.bat'), '-p', str(HERE), '--offline', '--no-daemon', '--console=plain',
        '-Pprepared='+str(prepared), '-PreviewBuild='+str(build), '-PjsonJar='+str(jar),
        '-Dorg.gradle.java.installations.auto-download=false', 'test', 'installDist', '--continue']
    print(f'Building {name}: actual Kotlin sources / local tests only', flush=True)
    with (run / f'{name}-build.log').open('w', encoding='utf-8') as log:
        status=subprocess.run(command,cwd=ROOT,env=java_env(),stdout=log,stderr=subprocess.STDOUT).returncode
    metadata={'ref':ref or 'logic-review working tree','sourceHashes':hashes,'gradleExit':status,'tests':tests_summary(build)}
    write_json(run / name / 'metadata.json',metadata)
    libs=build / 'install/TodaysTrackLogicReview/lib'
    if not libs.is_dir():
        raise RuntimeError(f'{name} compilation failed: {run / (name+"-build.log")}')
    return run / name

def evaluate(distribution, input_path, output):
    java=Path(java_env()['JAVA_HOME']) / 'bin/java.exe'
    libs=distribution / 'build/install/TodaysTrackLogicReview/lib'
    result=subprocess.run([str(java),'-Xmx256m','-cp',str(libs / '*'),'dev.todaystrack.review.ReviewMainKt',
        str(input_path),str(output)],env=java_env(),capture_output=True,text=True,encoding='utf-8',timeout=45)
    if result.returncode:
        raise RuntimeError(result.stderr[-3000:])
    return read_json(output)

def runner_fingerprint(distribution):
    """Compare the actual compiled host harness when reusing an older baseline."""
    lib=distribution / 'build/install/TodaysTrackLogicReview/lib'
    jar=next(lib.glob('TodaysTrackLogicReview*.jar'))
    with zipfile.ZipFile(jar) as archive:
        classes=sorted(n for n in archive.namelist() if n.startswith('dev/todaystrack/review/') and n.endswith('.class'))
        return digest(b''.join(n.encode()+archive.read(n) for n in classes))

def report(run, baseline, input_path, prefix='initial'):
    if runner_fingerprint(baseline)!=runner_fingerprint(run / 'current'):
        raise RuntimeError('Compiled host harness differs between versions. Rebuild baseline; do not compare tool drift as product drift.')
    before=evaluate(baseline,input_path,run / f'{prefix}-baseline.json')
    after=evaluate(run / 'current',input_path,run / f'{prefix}-current.json')
    result={'input':read_json(input_path),'baseline':before,'current':after,
        'baselineMetadata':read_json(baseline / 'metadata.json'),
        'currentMetadata':read_json(run / 'current/metadata.json'),
        'limits':['로컬 Kotlin·SQLite 실행 / SDK 수신·Room 저장·Media3 재생은 실행하지 않음',
            '전송 payload는 생성만 함 / 음악 생성·네트워크 송신·추가 과금 0',
            '저장 모델에서 Room annotation만 제거, 실제 SHA-256 본문만 추출; 인증 성공의 증거 아님',
            '실제 위치 입력으로 정차·방문·운전자 감정을 확정하지 않음',
            'capturePolicyDiagnostic은 정책 함수 진단; 서비스 수명·gap 생성 재실행 아님',
            'JVM org.json 사용: 두 호스트 버전의 의미·출력 비교이며 Android 직렬화 바이트·frozen 해시 동일성 검증 아님']}
    write_json(run / f'{prefix}-report.json',result)
    return result

def install_deps():
    folder=LOCAL / 'deps';folder.mkdir(parents=True,exist_ok=True)
    url='https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar'
    data=urllib.request.urlopen(url,timeout=30).read()
    checksum=urllib.request.urlopen(url+'.sha1',timeout=30).read().decode().strip().split()[0]
    if hashlib.sha1(data).hexdigest()!=checksum:
        raise RuntimeError('Public JSON jar checksum mismatch')
    (folder / 'json-20240303.jar').write_bytes(data)
    write_json(folder / 'provenance.json',{'url':url,'sha1':checksum,'sha256':digest(data),'purpose':'actual org.json implementation on JVM'})
    print('Downloaded public org.json dependency, Maven SHA1 verified and local SHA256 pinned. No music API calls.')

class ReviewHandler(http.server.BaseHTTPRequestHandler):
    run=None
    baseline=None
    latest=None
    def log_message(self, *args):
        pass
    def respond(self, status, data, mime='application/json; charset=utf-8'):
        self.send_response(status)
        self.send_header('Content-Type',mime)
        self.send_header('Cache-Control','no-store')
        self.send_header('X-Content-Type-Options','nosniff')
        self.send_header('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; frame-ancestors 'none'")
        self.end_headers();self.wfile.write(data)
    def valid_host(self):
        return self.headers.get('Host')==f'127.0.0.1:{self.server.server_port}'
    def do_GET(self):
        if not self.valid_host():
            return self.respond(403,b'{}')
        name=self.path.split('?')[0]
        if name=='/report.json':
            return self.respond(200,json.dumps(self.latest,ensure_ascii=False).encode())
        static={'/':('index.html','text/html'),'/review.js':('review.js','text/javascript'),'/review.css':('review.css','text/css')}
        if name not in static:
            return self.respond(404,b'{}')
        filename,mime=static[name]
        self.respond(200,(HERE / filename).read_bytes(),mime+'; charset=utf-8')
    def do_POST(self):
        if not self.valid_host() or self.path!='/evaluate' or self.headers.get('Origin')!=f'http://127.0.0.1:{self.server.server_port}':
            return self.respond(403,b'{}')
        try:
            hashes=read_json(self.run / 'current/metadata.json')['sourceHashes']
            if any(digest((ROOT / p).read_bytes())!=h for p,h in hashes.items()):
                raise ValueError('Source changed after compilation. Rebuild the review before evaluating.')
            size=int(self.headers.get('Content-Length','0'))
            if not 0<size<=1_000_000:
                raise ValueError('Input must be <= 1 MB')
            data=json.loads(self.rfile.read(size))
            if not isinstance(data.get('cases'),list) or not 1<=len(data['cases'])<=30:
                raise ValueError('Use 1..30 cases per review')
            if sum(len(c.get('events',[])) for c in data['cases'])>1000:
                raise ValueError('Use <= 1000 events per review')
            token='edited-'+time.strftime('%Y%m%d-%H%M%S')+'-'+str(time.time_ns())
            path=self.run / f'{token}-input.json';write_json(path,data)
            # Append artifacts, never overwrite prior runs or frozen product evidence.
            self.__class__.latest=report(self.run,self.baseline,path,token)
            self.respond(200,json.dumps(self.latest,ensure_ascii=False).encode())
        except Exception as exc:
            self.respond(400,json.dumps({'error':str(exc)},ensure_ascii=False).encode())

def main():
    parser=argparse.ArgumentParser()
    sub=parser.add_subparsers(dest='command',required=True)
    sub.add_parser('deps')
    build=sub.add_parser('build')
    build.add_argument('--baseline-ref',default=BASE)
    build.add_argument('--baseline-run',type=Path)
    build.add_argument('--input',type=Path,default=HERE / 'cases.json')
    serve=sub.add_parser('serve')
    serve.add_argument('--run',type=Path)
    serve.add_argument('--port',type=int,default=8765)
    args=parser.parse_args()
    LOCAL.mkdir(parents=True,exist_ok=True)
    if args.command=='deps':
        return install_deps()
    if args.command=='build':
        jar=LOCAL / 'deps/json-20240303.jar'
        if not jar.is_file():
            raise RuntimeError('Run review.py deps once (public JSON dependency only)')
        provenance=read_json(LOCAL / 'deps/provenance.json')
        if digest(jar.read_bytes())!=provenance['sha256']:
            raise RuntimeError('JSON dependency integrity mismatch')
        run=LOCAL / ('run-'+time.strftime('%Y%m%d-%H%M%S'))
        run.mkdir(exist_ok=False)
        shutil.copyfile(args.input,run / 'input.json')
        ref=subprocess.check_output(['git','rev-parse',args.baseline_ref],cwd=ROOT,text=True).strip()
        baseline=args.baseline_run.resolve() / 'baseline' if args.baseline_run else build_one(run,'baseline',ref,jar)
        if read_json(baseline / 'metadata.json')['ref']!=ref:
            raise RuntimeError('Reused baseline revision differs')
        current=build_one(run,'current',None,jar)
        if runner_fingerprint(baseline)!=runner_fingerprint(current):
            print('Host harness changed; rebuilding baseline to keep comparison equivalent.',flush=True)
            baseline=build_one(run,'baseline',ref,jar)
        result=report(run,baseline,run / 'input.json')
        write_json(run / 'run.json',{'baseline':str(baseline),'inputHash':digest((run / 'input.json').read_bytes())})
        write_json(LOCAL / 'latest.json',{'run':str(run)})
        print(json.dumps({'run':str(run),'cases':len(result['current']['cases']),
            'baselineTests':result['baselineMetadata']['tests'],'currentTests':result['currentMetadata']['tests']},ensure_ascii=False))
        return 1 if result['currentMetadata']['gradleExit'] else 0
    run=args.run.resolve() if args.run else Path(read_json(LOCAL / 'latest.json')['run'])
    ReviewHandler.run=run
    ReviewHandler.baseline=Path(read_json(run / 'run.json')['baseline'])
    ReviewHandler.latest=read_json(run / 'initial-report.json')
    with http.server.HTTPServer(('127.0.0.1',args.port),ReviewHandler) as server:
        print(f'Logic review only: http://127.0.0.1:{args.port} / Ctrl+C to stop',flush=True)
        server.serve_forever()

if __name__=='__main__':
    sys.exit(main())
