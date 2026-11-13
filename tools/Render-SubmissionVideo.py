"""Versioned local edit: current actual UI captures plus preserved real-generation history."""
from pathlib import Path
import hashlib,json,re,subprocess,sys

ROOT=Path(__file__).resolve().parents[1]
CHECKPOINTS=ROOT/'submission/editing'
OUT=CHECKPOINTS/'v3'
OUT.mkdir(parents=True,exist_ok=True)
FF=ROOT/'tools/.local/media/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe'
AUDIO=ROOT/'submission/audio/TodaysTrack-8km-Acoustic.mp3'
LIVE=ROOT/'validation/journey-probe/runs/release-live-20261112-115006/screen.mp4'
VERSION=re.search(r'versionName\s*=\s*"([^"]+)"',(ROOT/'vehicle/app/build.gradle.kts').read_text(encoding='utf-8-sig')).group(1)
CURRENT_UI=ROOT/'validation/evidence/ui-v3-release-current-ui.png'

def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def run_folder(mode):
    value=Path(read(ROOT/('validation/journey-probe/ui-'+mode+'-latest.json'))['run_path'])
    path=value.resolve() if value.is_absolute() else (ROOT/value).resolve()
    path.relative_to((ROOT/'validation/journey-probe/runs').resolve())
    assert read(path/'result.json')['version']==VERSION
    return path

INSPECT=run_folder('inspect');FIXTURE=run_folder('fixture');OFFLINE=run_folder('offline')
assert read(FIXTURE/'result.json')['vehicleSavedObserved']
assert read(OFFLINE/'result.json')['offlineDefaultNetworkAbsent']
assert CURRENT_UI.is_file() and sha(CURRENT_UI)==sha(INSPECT/'home.png')
timeline=read(OFFLINE/'timeline.json')
assert len(timeline)>1 and timeline[-1]['positionMs']>timeline[0]['positionMs']
offline_seconds=(timeline[-1]['positionMs']-timeline[0]['positionMs'])/1000+2
concat=['ffconcat version 1.0']
for i,item in enumerate(timeline):
    duration=max(.04,(timeline[i+1]['positionMs']-item['positionMs'])/1000) if i+1<len(timeline) else 2
    concat+=["file '"+(OFFLINE/item['file']).as_posix()+"'",f'duration {duration:.6f}']
concat+=["file '"+(OFFLINE/timeline[-1]['file']).as_posix()+"'"]
(OUT/'offline.ffconcat').write_text('\n'.join(concat),encoding='utf-8')

def run(args,name):
    with (OUT/(name+'.log')).open('w',encoding='utf-8') as log:
        subprocess.run([str(FF),'-hide_banner','-y',*map(str,args)],cwd=OUT,stdout=log,stderr=log,check=True,timeout=600)
def clock(seconds):
    c=round(seconds*100);return f'{c//360000}:{c//6000%60:02}:{c//100%60:02}.{c%100:02}'
def subtitles(name,duration,title,lines,footer,card=False):
    header='''[Script Info]
ScriptType: v4.00+
PlayResX: 1920
PlayResY: 1080
WrapStyle: 2
[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Text,맑은 고딕,30,&H002D2620,&H002D2620,&H00FFFFFF,&H00000000,0,0,0,0,100,100,0,0,1,0,0,7,0,0,0,1
[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
'''
    def event(x,y,size,color,text,bold=False):
        return f'Dialogue: 0,0:00:00.00,{clock(duration)},Text,,0,0,0,,{{\\pos({x},{y})\\fs{size}\\c&H{color}&\\b{1 if bold else 0}}}{text}\n'
    content=event(64,46,25,'8A7563',"TODAY’S TRACK  /  오늘의 트랙")
    content+=event(64,160,48 if card else 40,'2D2620',title,True)
    y=350 if card else 330
    for text in lines:
        content+=event(64,y,34 if card else 28,'7D7164',text);y+=66 if card else 58
    content+=event(64,1015,22,'7D7164',footer)
    path=OUT/(name+'.ass');path.write_text(header+content,encoding='utf-8-sig');return path.name

scenes=[
 dict(name='01-intro',duration=6,card=True,title='기록한 여정을, 다시 듣는 한 곡으로',lines=[f'현재 차량 앱 {VERSION}','실제 SDK 기록 → 차량 시작 생성 → 저장·오프라인 재감상','이번 연결 확인은 대역 / 실제 새 AI 음악 이력은 별도 표시'],footer='합성 환경 · 실제 Pleos SDK · 청취 평가는 사용자 지시로 유예'),
 dict(name='02-home',duration=10,folder=INSPECT,file='home.png',title='오늘의 기록을 고릅니다',lines=['흰색 화면 · 최근 기록과 상세','고정 흑백 기본 표지','실제 저장된 날짜·설정 표시'],footer='현재 release 실제 화면 캡처 · 예시 UI 이미지가 아님'),
 dict(name='03-records',duration=8,folder=INSPECT,file='records.png',title='이전 기록도 그대로',lines=['전체 기록 선택','기존 기록·작업·곡 보존','새 기록은 기억한 설정으로 시작'],footer='선택·설정은 실제 앱에서 확인 · 기본 표지는 실제 주행 사진이 아님'),
 dict(name='04-capture',duration=8,crop=[0,0,1280,800],folder=FIXTURE,file='recording.png',title='SDK 입력을 기록합니다',lines=['실제 위치·누적거리 SDK 수신','화면 유지형 유한 시험','기록 마무리 후 요약 확인'],footer='전용 Pleos 에뮬레이터 합성 입력 · 화면 일부 확대 · 백그라운드 지원 주장 아님'),
 dict(name='05-summary',duration=10,crop=[760,420,1800,1040],folder=FIXTURE,file='details.png',title='공백까지 남긴 여정',lines=['확인된 관측 순서와 거리','공백 속 경로는 미상','감정·방문 장소 측정 아님'],footer='실제 기록 상세 창 확대 · 원본 위치는 차량에 보관'),
 dict(name='06-fixture',duration=10,crop=[0,0,1280,800],folder=FIXTURE,file='saved.png',title='생성 연결과 저장 확인',lines=['차량에서 한 번 음악 만들기','검사·다운로드 후 저장','이번 음원은 기존 대역'],footer='실제 화면 일부 확대 · 대역 · 새 AI 생성 아님 · 추가 음악 유료 송신0'),
 dict(name='07-live-history',duration=8,historical=True,start=49,end=57,title='실제 새 AI 음악 생성 이력',lines=['새8km 기록 → 차량 시작 요청','Lyria3.5 실제 송신1회','기존 입력·요청·음원 해시 보존'],footer='보존 촬영0.1.0 · 현재 UI 촬영과 구분 · 새 유료 요청을 재연하지 않음'),
 dict(name='08-live-saved',duration=8,historical=True,start=87,end=95,title='새 곡과 기록의 연결',lines=['실제 새 곡 약90초','파일 전체 디코딩·차량 저장','같은 곡을 현재 앱에서도 유지'],footer='보존 촬영0.1.0 · 대역 음원과 실제 새 생성곡의 해시는 서로 다름'),
 dict(name='09-offline',duration=offline_seconds,offline=True,folder=OFFLINE,title='재실행 후 오프라인 재감상',lines=[f'현재 release {VERSION}','Wi-Fi·모바일 데이터 없음','첫 재생 보류 뒤 같은 세션에서 재개'],footer='실제 화면 약1fps·간격 편집 / 동일 MP3 일부 후삽입 / 직접 오디오 녹음·청취 평가 아님'),
 dict(name='10-end',duration=7,card=True,title='앱과 재현 자료를 함께',lines=['release APK · 소스 · 실행 안내 · 검증 기록','생성: PC 로컬 서버 필요 / 저장곡: 오프라인 재감상','실차·장시간·일반 백그라운드·폰·운영 기능은 후속'],footer='로컬 제출 준비 · 외부 게시·접수 미실행 · 청취 평가 유예'),
]
serial=[{k:(str(v.relative_to(ROOT)) if isinstance(v,Path) else v) for k,v in scene.items()} for scene in scenes]
project={'version':3,'method':'actual current UI captures + explicitly labeled historical generation; local FFmpeg edit',
 'currentUiAppVersion':VERSION,'historicalGenerationAppVersion':'0.1.0-submission','historicalFullPlaybackAppVersion':'0.1.1-submission',
 'currentUiPresentation':'real release screenshot sequence; offline frames timed from MediaSession positions',
 'listeningQualityAssessed':False,'listeningEvaluationStatus':'deferred_by_user','listeningDeferredBlocksImplementation':False,
 'sources':{'currentUi':str(CURRENT_UI.relative_to(ROOT)),'currentUiSha256':sha(CURRENT_UI),'inspect':str(INSPECT.relative_to(ROOT)),
 'fixture':str(FIXTURE.relative_to(ROOT)),'offline':str(OFFLINE.relative_to(ROOT)),'audioSha256':sha(AUDIO)},'scenes':serial}
(CHECKPOINTS/'project-v3.before.json').write_text(json.dumps(project,ensure_ascii=False,indent=2),encoding='utf-8')
for scene in scenes:
    name=scene['name'];duration=scene['duration']
    ass=subtitles(name,duration,scene['title'],scene['lines'],scene['footer'],scene.get('card',False))
    if scene.get('card'):
        args=['-f','lavfi','-i',f'color=c=0xf4f6f8:s=1920x1080:r=30:d={duration}','-vf',f'subtitles={ass}']
    else:
        if scene.get('historical'):
            source=['-ss',scene['start'],'-t',scene['end']-scene['start'],'-i',LIVE]
            crop='scale=1920:1080,crop=1260:900:640:68,'
        else:
            source=['-f','concat','-safe','0','-i','offline.ffconcat'] if scene.get('offline') else ['-loop','1','-i',scene['folder']/scene['file']]
            bounds=read(scene['folder']/'app-window.json')
            x1,y1,x2,y2=scene.get('crop',bounds.get('perFileBounds',{}).get(scene.get('file'),bounds['bounds']))
            crop=f'crop={x2-x1}:{y2-y1}:{x1}:{y1},'
        filt=f'[0:v]setpts=PTS-STARTPTS,{crop}scale=1232:880:force_original_aspect_ratio=decrease,pad=1232:880:(ow-iw)/2:(oh-ih)/2:color=0xf4f6f8,fps=30[app];color=c=0xf4f6f8:s=1920x1080:r=30:d={duration}[bg];[bg][app]overlay=656:108:shortest=1,subtitles={ass}[v]'
        args=source+['-filter_complex',filt,'-map','[v]']
    run(args+['-t',duration,'-an','-c:v','libx264','-preset','veryfast','-crf','20','-pix_fmt','yuv420p','-threads','2',name+'.mp4'],name)
(OUT/'chapters.ffconcat').write_text("ffconcat version 1.0\n"+'\n'.join("file '"+s['name']+".mp4'" for s in scenes),encoding='utf-8')
run(['-f','concat','-safe','0','-i','chapters.ffconcat','-c','copy','assembled.mp4'],'assemble')
audio_start=sum(s['duration'] for s in scenes[:8]);audio_offset=timeline[0]['positionMs']/1000
destination=ROOT/'submission/TodaysTrack-demo.mp4'
run(['-i','assembled.mp4','-i',AUDIO,'-filter_complex',f'[1:a]atrim=start={audio_offset},asetpts=PTS-STARTPTS,volume=0.65,adelay={round(audio_start*1000)}:all=1,apad[a]',
 '-map','0:v','-map','[a]','-c:v','copy','-c:a','aac','-b:a','192k','-t',sum(s['duration'] for s in scenes),'-movflags','+faststart',destination],'final')
project.update(output=str(destination.relative_to(ROOT)),sha256=sha(destination),audioStartSeconds=audio_start,audioSourceOffsetSeconds=audio_offset)
(CHECKPOINTS/'project-v3.after.json').write_text(json.dumps(project,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'video':str(destination),'version':VERSION,'seconds':sum(s['duration'] for s in scenes),'sha256':project['sha256']}))
