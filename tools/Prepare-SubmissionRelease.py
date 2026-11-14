"""Package an immutable Git source revision and verified local artifacts. Never upload or call an API."""
import argparse,hashlib,io,json,pathlib,re,shutil,subprocess,zipfile

parser=argparse.ArgumentParser()
parser.add_argument('--workspace-root',type=pathlib.Path,default=pathlib.Path(__file__).resolve().parents[1])
parser.add_argument('--revision',required=True)
parser.add_argument('--apk',type=pathlib.Path,required=True)
parser.add_argument('--verification',type=pathlib.Path,required=True)
parser.add_argument('--video',type=pathlib.Path)
parser.add_argument('--audio',type=pathlib.Path)
parser.add_argument('--source-only',action='store_true')
parser.add_argument('--update-local-files',action='store_true')
args=parser.parse_args();root=args.workspace_root.resolve()
def git(*parts):return subprocess.check_output(['git','-C',str(root),*parts])
revision=git('rev-parse',args.revision+'^{commit}').decode().strip()
def sha(data):return hashlib.sha256(data).hexdigest()
def file_sha(path):return sha(path.read_bytes())
gradle=git('show',revision+':vehicle/app/build.gradle.kts').decode()
version=re.search(r'versionName\s*=\s*"([\w.-]+)"',gradle)[1]
code=int(re.search(r'versionCode\s*=\s*(\d+)',gradle)[1])
release=root/'submission/releases'/version;release.mkdir(parents=True,exist_ok=True)
package=release/'package';package.mkdir(exist_ok=True)
for folder in ['apk','source','docs','evidence']:(package/folder).mkdir(exist_ok=True)
private_ext={'.p12','.pfx','.dpapi','.key','.jks','.keystore','.db','.sqlite','.sqlite3','.apk','.aab','.hprof'}
patterns=[re.compile(rb'AIza[0-9A-Za-z_-]{30,}'),re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----\r?\n[A-Za-z0-9+/]{20}')]
tool_files={'Use-SubmissionToolchain.ps1','Build-SubmissionApk.ps1','Install-MediaTool.ps1','Prepare-SubmissionRelease.py'}
def selected(path):
    p=pathlib.PurePosixPath(path)
    if p.is_absolute() or '..' in p.parts or ':' in path:raise ValueError('Unsafe source path')
    if any(x in {'.local','.gradle','.kotlin','build','node_modules','__pycache__','.git','work'} for x in p.parts):return False
    if p.name in {'local.properties','enrollment.json','dispatch.json','music-v2-authorization.json','server-process.json'} or p.suffix.lower() in private_ext:return False
    return path in {'README.md','.gitignore'} or path.startswith(('vehicle/','server/','music-core/','docs/','tools/ui-review/','tools/astryx-lab/')) or (p.parent.as_posix()=='tools' and p.name in tool_files)
entries=[]
for record in git('ls-tree','-rz',revision).split(b'\0'):
    if not record:continue
    meta,path=record.split(b'\t',1);mode,kind,oid=meta.decode().split();path=path.decode()
    if selected(path):
        assert kind=='blob' and mode in {'100644','100755'},'Source archive does not follow symlinks/submodules'
        entries.append((path,oid))
required={'music-core/build.gradle.kts','vehicle/settings.gradle.kts','server/settings.gradle.kts','vehicle/app/src/main/res/raw/development_server.pem'}
assert required.issubset({p for p,_ in entries}),'Required build module or public certificate missing'
inventory=[];source=package/'source/TodaysTrack-source.zip'
proc=subprocess.Popen(['git','-C',str(root),'cat-file','--batch'],stdin=subprocess.PIPE,stdout=subprocess.PIPE)
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for path,oid in sorted(entries):
        proc.stdin.write((oid+'\n').encode());proc.stdin.flush()
        header=proc.stdout.readline().decode().split();assert header[1]=='blob'
        size=int(header[2]);data=proc.stdout.read(size);assert len(data)==size and proc.stdout.read(1)==b'\n'
        assert not any(pattern.search(data) for pattern in patterns),'Credential pattern in '+path
        info=zipfile.ZipInfo(path,date_time=(2026,11,14,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
        info.external_attr=(0o100755 if path.endswith('/gradlew') else 0o100644)<<16
        z.writestr(info,data);inventory.append({'path':path,'bytes':size,'sha256':sha(data)})
    code_inventory=[x for x in inventory if x['path'].startswith(('vehicle/','server/','music-core/'))]
    tree=sha(''.join(x['path']+'\0'+x['sha256']+'\n' for x in code_inventory).encode())
    z.writestr('source-inventory.json',json.dumps({'gitCommit':revision,'version':version,'versionCode':code,'buildInputTreeSha256':tree,'files':inventory},ensure_ascii=False,indent=2))
proc.stdin.close();assert proc.wait(timeout=10)==0
with zipfile.ZipFile(source) as z:assert z.testzip() is None
if args.source_only:
    print(json.dumps({'source':str(source),'commit':revision,'buildInputTreeSha256':tree,'files':len(inventory)}));raise SystemExit
verification=json.loads(args.verification.read_text(encoding='utf-8-sig'))
assert verification['versionName']==version and verification['versionCode']==code and verification['apkSha256']==file_sha(args.apk)
assert verification['signerSha256']=='56fd311ccd45d0dd255a6241701175f7a530a668cb243d609c51962d310c0490'
shutil.copy2(args.apk,package/'apk/TodaysTrack-release.apk')
(package/'evidence/release-verification.json').write_text(json.dumps(verification,ensure_ascii=False,indent=2),encoding='utf-8')
for name in ['runbook.md','verification.md','manual-filming-guide.md']:
    (package/'docs'/name).write_bytes(git('show',revision+':docs/submission/'+name))
if args.video:
    shutil.copy2(args.video,package/'TodaysTrack-demo.mp4')
if args.audio:
    (package/'audio').mkdir(exist_ok=True);shutil.copy2(args.audio,package/'audio/TodaysTrack-demo-song.mp3')
readme=f'''# 오늘의 트랙 · {version}({code})

APK: apk/TodaysTrack-release.apk · 소스: source/TodaysTrack-source.zip
Git commit: {revision}
실행 방법: docs/runbook.md · 실제 확인과 한계: docs/verification.md

기존 패키지/서명을 유지한 Android Views 앱입니다. 새 엔진의 실제 생성·저장·재생 증거와 최종 APK의 기기 확인은 release-verification.json에서 구분합니다. 청취 평가는 유예했습니다.
영상은 버전 고정 전0.3(4)의 동일 UI/생성·재생 코드에서 직접 촬영한 약2분28초 영상입니다. 합성 SDK 입력·생성 대기 단축·별도 저장곡 재감상 장면을 표시하며 기기 재생 소리를 직접 녹음했습니다.

키·DPAPI·서버/차량 DB·등록 자격·개인 메모리·Git 작업 폴더는 포함하지 않습니다. 소스는 위 커밋의 추적 파일만 포함하며 사용자 작업 중인 main 기획서 초안/접수 개인정보는 자동으로 넣지 않습니다. 공식 접수양식·동의·서명과 외부 제출은 사용자가 별도로 진행합니다. 기획7번은 공란을 유지합니다.
'''
(package/'README.md').write_text(readme,encoding='utf-8')
files=[]
for p in sorted(package.rglob('*')):
    if not p.is_file() or p.name=='manifest.json':continue
    assert not any(part in {'.local','.git'} for part in p.relative_to(package).parts)
    files.append({'path':p.relative_to(package).as_posix(),'bytes':p.stat().st_size,'sha256':file_sha(p)})
manifest={'versionName':version,'versionCode':code,'gitCommit':revision,'buildInputTreeSha256':tree,'files':files,'externalUpload':False,'personalFormsIncluded':False,'listeningAssessment':'deferred'}
(package/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
bundle=release/'TodaysTrack-submission.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for p in sorted(package.rglob('*')):
        if p.is_file():z.write(p,p.relative_to(package).as_posix())
with zipfile.ZipFile(bundle) as z:
    assert z.testzip() is None
    for f in files:assert sha(z.read(f['path']))==f['sha256']
    with zipfile.ZipFile(io.BytesIO(z.read('source/TodaysTrack-source.zip'))) as inner:assert inner.testzip() is None
result={'version':version,'versionCode':code,'gitCommit':revision,'bundle':str(bundle),'bundleSha256':file_sha(bundle),'source':str(source),'sourceSha256':file_sha(source),'apkSha256':file_sha(args.apk),'files':len(files)}
(release/'release-manifest.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
if args.update_local_files:
    # File copies preserve old artifacts. Legacy package/ and user forms are untouched.
    history=root/'submission/history/before-0.4-local-update';history.mkdir(parents=True,exist_ok=True)
    aliases={root/'apk/TodaysTrack-release.apk':args.apk,root/'submission/TodaysTrack-source.zip':source,root/'submission/TodaysTrack-submission.zip':bundle}
    if args.video:aliases[root/'submission/TodaysTrack-demo.mp4']=args.video
    for dest,src in aliases.items():
        dest.parent.mkdir(parents=True,exist_ok=True)
        if dest.exists() and file_sha(dest)!=file_sha(src):
            backup=history/(dest.stem+'-'+file_sha(dest)[:12]+dest.suffix)
            if not backup.exists():shutil.copy2(dest,backup)
        if dest.resolve()!=src.resolve():shutil.copy2(src,dest)
        assert file_sha(dest)==file_sha(src)
print(json.dumps(result,ensure_ascii=False))
