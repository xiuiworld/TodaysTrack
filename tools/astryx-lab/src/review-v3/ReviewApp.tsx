import {useEffect,useRef,useState} from 'react';
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
import {Dialogs} from './Dialogs';
function Screens({snapshot}:{snapshot:Snapshot}){
  const [detailId,setDetailId]=useState<string|null>(null),[dialog,setDialog]=useState<{kind:string;id?:string}|null>(null);
  const pendingDetail=useRef<string|null>(null);
  const s=snapshot.state,c=controls(s);
  useEffect(()=>{document.body.dataset.hitboxes=snapshot.hitboxes?'show':'hide';},[snapshot.hitboxes]);
  useEffect(()=>{setDetailId(null);setDialog(null);},[s.page]);
  useEffect(()=>{if(detailId&&!s.records.some((r:any)=>r.id===detailId))setDetailId(null);},[s.records]);
  const open=(kind:string,id?:string)=>{
    if(kind==='record'&&id){if(!c.canSelect&&id!==s.selectedId)return;if(id!==s.selectedId)send('select',id);setDetailId(id);if(s.page!=='records')send('page','records');}
    else if(kind==='settings'){if(c.canChangeSettings)setDialog({kind});}
    else setDialog({kind,id});
  };
  // Keep a requested record detail when the parent confirms home -> records navigation.
  useEffect(()=>{if(s.page==='records'&&pendingDetail.current){setDetailId(pendingDetail.current);pendingDetail.current=null;}},[s.page]);
  const props={snapshot,open:(kind:string,id?:string)=>{if(kind==='record'&&s.page!=='records')pendingDetail.current=id||null;open(kind,id);}};
  const theme=themes[snapshot.config.tone]||themes.porcelain;
  if(s.screenClosed)return <Theme theme={theme} mode="light"><Stack height="100vh" hAlign="center" vAlign="center" gap={4} style={frameStyle(snapshot.config)}><Heading level={1}>화면을 닫았습니다</Heading><Action label="화면 다시 열기" action="reopen" primary width={224}/></Stack></Theme>;
  return <Theme theme={theme} mode="light"><Stack as="main" height="100vh" width="100%" gap={0} style={frameStyle(snapshot.config)}>
    <Header {...props}/><StackItem size="fill" isScrollable>{s.page==='library'?<LibraryScreen {...props}/>:s.page==='records'?detailId?<RecordDetailScreen {...props} id={detailId} back={()=>setDetailId(null)}/>:<RecordsScreen {...props}/>:<HomeScreen {...props}/>}</StackItem>
    <Navigation page={s.page} onPage={()=>{setDetailId(null);setDialog(null);}}/>
    {dialog&&<Dialogs key={dialog.kind} snapshot={snapshot} kind={dialog.kind} id={dialog.id} close={()=>setDialog(null)}/>}
  </Stack></Theme>;
}
export function ReviewApp(){const [snapshot,setSnapshot]=useState<Snapshot|null>(null);useEffect(()=>{
  const receive=(event:MessageEvent)=>{if(event.origin===location.origin&&event.source===window.parent&&event.data?.channel===channel&&event.data.type==='render'&&event.data.state&&event.data.config)setSnapshot(event.data);};
  window.addEventListener('message',receive);window.parent.postMessage({channel,type:'ready'},location.origin);return()=>window.removeEventListener('message',receive);
},[]);return snapshot?<Screens snapshot={snapshot}/>:<Theme theme={neutralTheme} mode="light"><Stack padding={6}><Text>화면을 불러오고 있습니다.</Text></Stack></Theme>;}
