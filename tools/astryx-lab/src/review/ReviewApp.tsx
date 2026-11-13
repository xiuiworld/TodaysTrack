import {useEffect,useState} from 'react';
import {Theme} from '@astryxdesign/core/theme';
import {Stack,StackItem} from '@astryxdesign/core/Stack';
import {Text} from '@astryxdesign/core/Text';
import {Heading} from '@astryxdesign/core/Heading';
import {neutralTheme} from '@astryxdesign/theme-neutral/built';
import {Action,channel,controls,frameStyle,send,themes,type Snapshot} from './shared';
import {Header,Navigation} from './Chrome';
import {HomeScreen} from './HomeScreen';
import {RecordsScreen,RecordDetailScreen} from './RecordsScreen';
import {LibraryScreen} from './LibraryScreen';
import {SongDetailScreen} from './SongDetailScreen';
import {Dialogs} from './Dialogs';
function Screens({snapshot}:{snapshot:Snapshot}){
  const [detailId,setDetailId]=useState<string|null>(null),[dialog,setDialog]=useState<{kind:string;id?:string}|null>(null);
  const s=snapshot.state,c=controls(s);
  const selected=s.records.find((r:any)=>r.id===s.selectedId);
  // Announce the new step instead of leaving keyboard focus on a button whose action changed.
  useEffect(()=>{const heading=document.querySelector<HTMLElement>('main h2');if(heading&&!snapshot.isComparison&&!document.querySelector('dialog[open]')){heading.tabIndex=-1;heading.style.outline='none';heading.focus({preventScroll:true});}},[s.page,detailId,snapshot.view?.trackId,s.capturing,s.selectedId,selected?.state,selected?.intentId]);
  useEffect(()=>{document.body.dataset.hitboxes=snapshot.hitboxes?'show':'hide';},[snapshot.hitboxes]);
  useEffect(()=>{setDetailId(snapshot.view?.detailId||null);setDialog(null);},[s.page,snapshot.view?.detailId,snapshot.view?.trackId]);
  useEffect(()=>{if(detailId&&!s.records.some((r:any)=>r.id===detailId))setDetailId(null);},[s.records]);
  const open=(kind:string,id?:string)=>{
    if(kind==='record'&&id){if(!c.canSelect&&id!==s.selectedId)return;window.parent.postMessage({channel,type:'view',detailId:id},location.origin);}
    else if(kind==='song'&&id)window.parent.postMessage({channel,type:'music-view',trackId:id},location.origin);
    else if(kind==='settings'){if(c.canChangeSettings)setDialog({kind});}
    else setDialog({kind,id});
  };
  const props={snapshot,open};
  const theme=themes[snapshot.config.tone]||themes.porcelain;
  if(s.screenClosed)return <Theme theme={theme} mode="light"><Stack height="100vh" hAlign="center" vAlign="center" gap={4} style={frameStyle(snapshot.config)}><Heading level={1}>화면을 닫았습니다</Heading><Action label="화면 다시 열기" action="reopen" primary width={224}/></Stack></Theme>;
  return <Theme theme={theme} mode="light"><Stack as="main" height="100vh" width="100%" gap={0} style={frameStyle(snapshot.config)}>
    <style>{`[data-compact-action]:hover:not(:disabled):not([aria-disabled="true"]) [data-action-face], [data-compact-action]:hover:not(:disabled):not([aria-disabled="true"]) [data-play-disc] { box-shadow: inset 0 0 0 999px rgba(0,0,0,.04); } [data-compact-action]:active:not(:disabled):not([aria-disabled="true"]) [data-action-face], [data-compact-action]:active:not(:disabled):not([aria-disabled="true"]) [data-play-disc] { box-shadow: inset 0 0 0 999px rgba(0,0,0,.08); }`}</style>
    <Header {...props}/><StackItem size="fill" isScrollable>{s.page==='library'?snapshot.view?.trackId?<SongDetailScreen {...props} id={snapshot.view.trackId} back={()=>window.parent.postMessage({channel,type:'music-view',trackId:null},location.origin)}/>:<LibraryScreen {...props}/>:s.page==='records'?detailId?<RecordDetailScreen {...props} id={detailId} back={()=>window.parent.postMessage({channel,type:'view',detailId:null},location.origin)}/>:<RecordsScreen {...props}/>:<HomeScreen {...props}/>}</StackItem>
    <Navigation page={s.page} onPage={()=>{setDetailId(null);setDialog(null);}}/>
    {dialog&&<Dialogs key={dialog.kind} snapshot={snapshot} kind={dialog.kind} id={dialog.id} close={()=>setDialog(null)}/>}
  </Stack></Theme>;
}
export function ReviewApp(){const [snapshot,setSnapshot]=useState<Snapshot|null>(null);useEffect(()=>{
  const receive=(event:MessageEvent)=>{if(event.origin===location.origin&&event.source===window.parent&&event.data?.channel===channel&&event.data.type==='render'&&event.data.state&&event.data.config)setSnapshot(event.data);};
  window.addEventListener('message',receive);window.parent.postMessage({channel,type:'ready'},location.origin);return()=>window.removeEventListener('message',receive);
},[]);return snapshot?<Screens snapshot={snapshot}/>:<Theme theme={neutralTheme} mode="light"><Stack padding={6}><Text>화면을 불러오고 있습니다.</Text></Stack></Theme>;}
