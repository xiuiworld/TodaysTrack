import {useEffect, useState, type CSSProperties, type ReactNode, type SVGProps} from 'react';
import {createRoot} from 'react-dom/client';
import {Theme, defineTheme} from '@astryxdesign/core/theme';
import {neutralTheme} from '@astryxdesign/theme-neutral/built';
import {Stack, StackItem} from '@astryxdesign/core/Stack';
import {Grid} from '@astryxdesign/core/Grid';
import {Heading} from '@astryxdesign/core/Heading';
import {Text} from '@astryxdesign/core/Text';
import {Button} from '@astryxdesign/core/Button';
import {Icon} from '@astryxdesign/core/Icon';
import {Thumbnail} from '@astryxdesign/core/Thumbnail';
import {controls, selectedRecord, tracks} from '../../ui-review/model.mjs';
import '@astryxdesign/core/reset.css';
import '@astryxdesign/core/astryx.css';
import '@astryxdesign/theme-neutral/theme.css';

// Pure review presentation: the parent owns the existing mock model and action rules.
// This bundle imports no Android code, data store, music file or API client.
const channel='todaystrack-home-v1';
type Snapshot={state:any;config:any;hitboxes:boolean};
const palettes={
  porcelain:{accent:'#2e5b50',body:'#f4f5ef',surface:'#fcfcf8',ink:'#24372f',muted:'#52665b',border:'#d6dfd4'},
  warm:{accent:'#756044',body:'#f5f1e9',surface:'#fdfbf6',ink:'#3c352b',muted:'#6b6051',border:'#dfd6c8'},
  ink:{accent:'#304e65',body:'#f0f3f4',surface:'#fbfcfc',ink:'#253847',muted:'#536779',border:'#d6dfe4'}
};
const themes=Object.fromEntries(Object.entries(palettes).map(([name,p])=>[name,defineTheme({
  name:`todaystrack-home-v1-${name}`,extends:neutralTheme,
  tokens:{'--color-accent':p.accent,'--color-background-body':p.body,'--color-background-surface':p.surface,'--color-background-card':p.surface,'--color-text-primary':p.ink,'--color-text-secondary':p.muted,'--color-border':p.border,'--radius-container':'16px','--radius-element':'10px'},
  components:{
    button:{base:{minHeight:'76px',minWidth:'76px',padding:'0',border:'0',backgroundColor:'transparent',borderRadius:'12px',boxShadow:'none',height:'auto',whiteSpace:'normal'}},
    text:{base:{fontFamily:'system-ui, "Malgun Gothic", sans-serif',fontSize:'var(--tt-font)',lineHeight:'1.5',textWrap:'pretty'},'type:supporting':{fontSize:'calc(var(--tt-font) - 4px)'},'type:label':{fontSize:'calc(var(--tt-font) - 2px)',fontWeight:'600'}},
    heading:{base:{fontFamily:'system-ui, "Malgun Gothic", sans-serif',lineHeight:'1.3',textWrap:'balance',letterSpacing:'-.025em'},'level:1':{fontSize:'24px'},'level:2':{fontSize:'28px'},'level:3':{fontSize:'20px'}},
    thumbnail:{base:{borderRadius:'12px',outline:'1px solid var(--color-border)',outlineOffset:'-1px'}}
  }
})]));
const glyphPaths={home:'M3 10 12 3 21 10v11h-6v-7H9v7H3z',records:'M5 3h14v18H5z M8 8h8 M8 12h8 M8 16h8',library:'M8 18V5l12-2v13 M8 18c0 4-6 4-6 0s6-4 6 0 M20 16c0 4-6 4-6 0s6-4 6 0',play:'m9 5 11 7-11 7z',pause:'M8 5v14 M16 5v14',plus:'M12 4v16 M4 12h16'};
function glyph(name:keyof typeof glyphPaths){return function Glyph(props:SVGProps<SVGSVGElement>){return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...props}><path d={glyphPaths[name]} /></svg>;};}
const glyphs=Object.fromEntries(Object.keys(glyphPaths).map(name=>[name,glyph(name as keyof typeof glyphPaths)]));
function art(id:string){let hash=0;for(let i=0;i<id.length;i++)hash=(Math.imul(31,hash)+id.charCodeAt(i))|0;return `/assets/cover_${['concrete','curve','planes'][((hash%3)+3)%3]}.png`;}
function send(action:string,value?:string){window.parent.postMessage({channel,type:'action',action,value},location.origin);}

function Action({label,action,value,primary=false,disabled=false,width,children}:{label:string;action:string;value?:string;primary?:boolean;disabled?:boolean;width?:number|string;children?:ReactNode}){
  return <Button label={label} variant="ghost" width={width} isDisabled={disabled} onClick={()=>{if(!disabled)send(action,value);}}>
    <Stack direction="horizontal" gap={2} hAlign="center" vAlign="center" width={typeof width==='number'?width-24:'100%'} minHeight="var(--tt-visible-button)" style={{padding:typeof width==='number'&&width<=144?'0 var(--spacing-2)':'0 var(--spacing-3)',borderRadius:'var(--radius-element)',backgroundColor:primary?'var(--color-accent)':'var(--color-background-body)',color:primary?'var(--color-background-surface)':'var(--color-text-primary)',whiteSpace:'normal'}}>
      {children||<Text type="label" color="inherit">{label}</Text>}
    </Stack>
  </Button>;
}

function stateCopy(s:any,r:any){
  if(!r)return {label:'기록 없음',title:'첫 기록을 시작해 보세요',description:'사진·메모 없이 기록하고, 마친 뒤 음악으로 남겨요.'};
  if(r.open)return {label:s.capturing?'수집 중':'마감 전 기록',title:s.capturing?'여정을 기록하고 있어요':'이전 기록을 이어갈 수 있어요',description:s.capturing?'화면을 닫아도 기록 상태는 유지됩니다.':'이전 기록의 설정으로 이어서 수집할 수 있어요.'};
  if(r.state==='AVAILABLE')return {label:'저장 완료',title:'음악을 들을 준비가 됐어요',description:'이 기록의 곡이 저장되어 있어요. 다시 만들지 않고 감상합니다.'};
  if(r.state==='ACCEPTANCE_UNCERTAIN')return {label:'접수 미확인',title:'접수 여부를 확인하고 있어요',description:'기록과 고정 입력은 보관됩니다. 같은 작업만 조회하며 새로 생성하지 않아요.'};
  if(r.state==='RECONCILING')return {label:'확인 필요',title:'작업 확인을 마치지 못했어요',description:'기록과 기존 작업은 보관됩니다. 새로 생성하지 않고 원래 작업을 확인해요.'};
  if(r.intentId)return {label:'생성 대기',title:'생성 서버 연결을 기다려요',description:'기록과 입력은 보관됩니다. 기존 작업에서 이어갑니다.'};
  return {label:'기록 마감',title:'이 기록을 음악으로 남겨보세요',description:'기록에 모인 관측을 바탕으로 한 곡을 만듭니다.'};
}

function RecordRow({record,rules,selected}:{record:any;rules:any;selected:boolean}){
  return <Button label={`${record.title} · ${record.date}${record.open?' · 진행 중':''}`} aria-pressed={selected} variant="ghost" width="100%" isDisabled={!rules.canSelect} onClick={()=>{if(rules.canSelect)send('select',record.id);}}>
    <Stack direction="horizontal" gap={3} vAlign="center" width="100%" minHeight="76px" style={{position:'absolute',inset:0,padding:'0 var(--spacing-3)',borderRadius:'var(--radius-element)',borderInlineStart:`3px solid ${selected?'var(--color-accent)':'transparent'}`,backgroundColor:selected?'var(--color-background-body)':'transparent',textAlign:'start',whiteSpace:'normal'}}>
      <Thumbnail src={art(record.id)} alt="" label="고정 장식용 기본 표지" style={{width:'40px',height:'40px',flexShrink:0}} />
      <StackItem size="fill"><Stack gap={0}><Text type="label" maxLines={1}>{record.title}{record.open?' · 진행 중':''}</Text><Text type="supporting" maxLines={1}>{record.date}</Text></Stack></StackItem>
      <Icon icon="chevronRight" size="sm" color="secondary" />
    </Stack>
  </Button>;
}

function Home({snapshot}:{snapshot:Snapshot}){
  const {state:s,config,hitboxes}=snapshot;
  const r=selectedRecord(s),c=controls(s),copy=stateCopy(s,r);
  const available=r?.state==='AVAILABLE';
  const selectedTrack=tracks(s).find((t:any)=>t.recordId===r?.id);
  const activeTrack=tracks(s).find((t:any)=>t.id===s.playingId)||selectedTrack;
  const recent=[...s.records.filter((item:any)=>item.open),...s.records.filter((item:any)=>!item.open).slice(0,5)];
  const css={'--tt-font':`${config.font}px`,'--tt-visible-button':`${config.button}px`,'--tt-cover':`${config.cover}px`,'--tt-gap':`${config.gap}px`,fontFamily:'system-ui, "Malgun Gothic", sans-serif',backgroundColor:'var(--color-background-surface)',color:'var(--color-text-primary)'} as CSSProperties;
  useEffect(()=>{document.body.dataset.hitboxes=hitboxes?'show':'hide';},[hitboxes]);
  const theme=themes[config.tone]||themes.porcelain;
  if(s.screenClosed)return <Theme theme={theme} mode="light"><Stack width="100%" height="100vh" hAlign="center" vAlign="center" gap={4} style={css}><Heading level={1}>화면을 닫았습니다</Heading><Text color="secondary">검토 모의 · 수집 상태와 원래 기록을 유지합니다.</Text><Action label="화면 다시 열기" action="reopen" primary width={224} /></Stack></Theme>;
  return <Theme theme={theme} mode="light">
    <Stack direction="horizontal" width="100%" height="100vh" style={css}>
      <Stack as="nav" width={96} height="100%" padding={2} gap={1} hAlign="center" style={{flexShrink:0,backgroundColor:'var(--color-background-body)',borderInlineEnd:'1px solid var(--color-border)'}}>
        <Stack minHeight={90} hAlign="center" vAlign="center"><Text weight="bold" size="lg" justify="center">오늘의<br/>트랙</Text></Stack>
        {([['home','홈'],['records','기록'],['library','보관함']] as const).map(([page,label])=><Button key={page} label={label} aria-current={page==='home'?'page':undefined} variant="ghost" width={76} onClick={()=>send('page',page)}>
          <Stack gap={1} width={76} minHeight={76} hAlign="center" vAlign="center" style={{borderRadius:'var(--radius-container)',backgroundColor:page==='home'?'var(--color-background-surface)':'transparent',color:page==='home'?'var(--color-accent)':'var(--color-text-secondary)'}}><Icon icon={glyphs[page]} size="lg"/><Text type="label" color="inherit" size="sm">{label}</Text></Stack>
        </Button>)}
        <StackItem size="fill" />
        <Button label="앱 정보" variant="ghost" width={76} onClick={()=>send('info')}><Text type="supporting">앱 정보</Text></Button>
      </Stack>
      <StackItem size="fill"><Stack height="100%" gap={0}>
        <Stack as="header" direction="horizontal" minHeight={76} paddingInline={6} hAlign="between" vAlign="center" style={{flexShrink:0,borderBottom:'1px solid var(--color-border)'}}>
          <Heading level={1}>홈</Heading>
          <Stack direction="horizontal" gap={2} vAlign="center">
            {r&&!s.capturing&&<Action label={s.records.some((item:any)=>item.open)?'이전 기록 이어서 수집':'새 기록 시작'} action="start" disabled={!c.canStart} width={180} />}
            <Action label="새 기록 설정" action="settings" disabled={!c.canChangeSettings} width={144}/>
            <Stack direction="horizontal" gap={1} vAlign="center"><Icon icon={c.allowed?'success':'warning'} size="sm" color={c.allowed?'success':'warning'}/><Text type="supporting">{c.allowed?'정차 중':s.ux==='unknown'?'제한 확인 중':'조작 제한'}</Text></Stack>
          </Stack>
        </Stack>
        <StackItem size="fill" isScrollable>
          <Stack paddingInline={6} paddingBlock={3} gap={2} style={{gap:'calc(var(--tt-gap) / 2)'}}>
            <Stack direction="horizontal" gap={4} vAlign="center" width="100%" style={{gap:'var(--tt-gap)'}}>
              <StackItem size="fill"><Stack gap={2}>
                <Stack direction="horizontal" gap={3} vAlign="center"><Text type="supporting" color="accent">{copy.label}</Text>{r?.intentId&&<Text type="supporting">{s.provenance==='FIXTURE'?'대역 · 새 AI 생성 아님':'새 AI 음악 · 검토 예시'}</Text>}</Stack>
                <Heading level={2}>{r?.title||'첫 기록을 시작해 보세요'}</Heading>
                <Text type="supporting">{r?.date||`${s.defaults.kind} · ${s.defaults.genre} · ${s.defaults.lyrics}`}</Text>
                {r&&<Stack direction="horizontal" gap={6} vAlign="center">
                  <Stack gap={0}><Text type="supporting">연속 관측 거리</Text><Stack direction="horizontal" gap={1} vAlign="center"><Text size="3xl" weight="medium" hasTabularNumbers>{r.distance}</Text><Text color="secondary">km</Text></Stack></Stack>
                  <Stack gap={1}><Text type="label">{r.genre}</Text><Text type="supporting">{r.lyrics}</Text></Stack>
                </Stack>}
              </Stack></StackItem>
              <Thumbnail src={art(r?.id||'default')} alt="" label="고정 장식용 표지 · 실제 주행 사진 아님" style={{width:'var(--tt-cover)',height:'var(--tt-cover)',flexShrink:0}} />
            </Stack>
            <Stack gap={1}>
              <Text type="label">{copy.title}</Text>
              <Text type="supporting">{copy.description}</Text>
              {!c.allowed&&<Text type="supporting">{s.ux==='unknown'?'차량의 조작 제한을 확인하기 전까지 새 조작을 보류합니다.':'새 기록·선택·설정·생성·재생은 조작 가능 상태에서 이용할 수 있어요.'}</Text>}
            </Stack>
            <Stack direction="horizontal" gap={3} vAlign="center" wrap="wrap">
              {!r?<Action label="새 기록 시작" action="start" primary disabled={!c.canStart} width={224}/>:
                s.capturing?<><Action label="기록 마무리" action="finish" primary disabled={!c.canStop} width={224}/><Action label="화면 닫기" action="close" width={144}/></>:
                !r.open?<Action label={available?'음악 듣기':r.intentId?'기존 작업 확인':'음악 만들기'} action={available?'listen':'generate'} primary disabled={available?!c.canPlay:!c.canGenerate} width={224}/>:<Action label="이전 기록 이어서 수집" action="start" primary disabled={!c.canStart} width={224}/>}
              {r&&<Button label="기록 상세" variant="ghost" width={144} onClick={()=>send('details')}><Stack direction="horizontal" gap={1} hAlign="center" vAlign="center" width="100%"><Text type="label">기록 상세</Text><Icon icon="chevronRight" size="sm" /></Stack></Button>}
            </Stack>
            <Stack gap={0}><Text type="supporting">음악 만들기를 누르면 정확한 위치를 뺀 요약을 생성 서버·음악 AI로 전송합니다.</Text><Text type="supporting">정밀 위치는 차량에 보관됩니다 · 표지는 고정 장식 이미지입니다.</Text></Stack>
            <Stack gap={0} style={{borderTop:'1px solid var(--color-border)'}}>
              <Stack direction="horizontal" hAlign="between" vAlign="center" minHeight={76}><Heading level={3}>최근 기록</Heading><Button label="모든 기록" variant="ghost" width={132} onClick={()=>send('page','records')}><Text type="label" color="secondary">모든 기록 →</Text></Button></Stack>
              {recent.length?<Grid columns={{minWidth:280,max:2}} gap={2} width="100%">{recent.map((item:any)=><RecordRow key={item.id} record={item} rules={c} selected={item.id===s.selectedId}/>)}</Grid>:<Text type="supporting">아직 저장된 기록이 없어요. 첫 기록이 여기에 쌓입니다.</Text>}
            </Stack>
          </Stack>
        </StackItem>
        <Stack as="footer" direction="horizontal" minHeight={100} paddingInline={6} gap={3} vAlign="center" style={{backgroundColor:'var(--color-background-body)',borderTop:'1px solid var(--color-border)',flexShrink:0}}>
          <Icon icon={glyphs.library} size="lg" color="accent" />
          <StackItem size="fill"><Stack gap={0}><Text type="label" maxLines={1}>{activeTrack?.title||'선택한 기록의 음악'}</Text><Text type="supporting">{activeTrack?`${s.playingId?'재생 중 · 무음 모의':'차량 저장 표시 모의'} · ${activeTrack.provenance==='FIXTURE'?'대역 · 새 AI 생성 아님':'새 AI 음악 · 검토 예시'}`:'곡이 저장되면 여기서 바로 감상해요'}</Text></Stack></StackItem>
          {activeTrack&&<><Text type="supporting" hasTabularNumbers>{s.playingId?'0:12 / ':''}{activeTrack.duration}</Text><Action label={s.playingId?'일시정지':'재생'} action={s.playingId?'pause':'play'} value={activeTrack.id} disabled={s.playingId?false:!c.canPlay} width={76}><Icon icon={s.playingId?glyphs.pause:glyphs.play} size="lg" /></Action></>}
        </Stack>
      </Stack></StackItem>
    </Stack>
  </Theme>;
}

function App(){
  const [snapshot,setSnapshot]=useState<Snapshot|null>(null);
  useEffect(()=>{
    const receive=(event:MessageEvent)=>{if(event.origin===location.origin&&event.source===window.parent&&event.data?.channel===channel&&event.data.type==='render'&&event.data.state&&event.data.config)setSnapshot(event.data);};
    window.addEventListener('message',receive);
    window.parent.postMessage({channel,type:'ready'},location.origin);
    return()=>window.removeEventListener('message',receive);
  },[]);
  if(!snapshot)return <Theme theme={neutralTheme} mode="light"><Stack padding={6}><Text>홈 수정안을 불러오고 있습니다.</Text></Stack></Theme>;
  return <Home snapshot={snapshot}/>;
}
createRoot(document.getElementById('root')!).render(<App/>);
