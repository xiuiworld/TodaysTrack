import {useEffect,useRef,useState,type CSSProperties,type ReactNode} from 'react';
import {Stack} from '@astryxdesign/core/Stack';
import {Heading} from '@astryxdesign/core/Heading';
import {Text} from '@astryxdesign/core/Text';
import {Action,channel,controls,frameStyle,send,type Snapshot} from './shared';
const options={kind:['하루 기록','여행 기록'],genre:['잔잔한 팝','어쿠스틱','일렉트로닉'],lyrics:['무가사','가사 있는 곡']};
const names={kind:'기록 범위',genre:'음악 장르',lyrics:'가사'};
function Settings({snapshot,close}:{snapshot:Snapshot;close:()=>void}){
  const [draft,setDraft]=useState({...snapshot.state.defaults});
  return <Stack gap={3}><Text type="supporting">새 기록에만 적용합니다.</Text>
    {(Object.keys(options) as (keyof typeof options)[]).map(key=><Stack key={key} gap={1}><Text as="label" type="label" htmlFor={'setting-'+key}>{names[key]}</Text><select id={'setting-'+key} value={draft[key]} onChange={event=>setDraft({...draft,[key]:event.target.value})} style={{font:'inherit',color:'var(--color-text-primary)',backgroundColor:'var(--color-background-surface)',border:'1px solid var(--color-border)',borderRadius:'var(--radius-element)',width:'100%',minHeight:'var(--tt-hit)',paddingInline:'var(--spacing-3)'}}>{options[key].map(value=><option key={value}>{value}</option>)}</select></Stack>)}
    <Stack direction="horizontal" gap={2} hAlign="end"><Action label="취소" width={132} onClick={close}/><Action label="저장" primary width={132} disabled={!controls(snapshot.state).canChangeSettings} onClick={()=>{if(controls(snapshot.state).canChangeSettings){window.parent.postMessage({channel,type:'settings-save',defaults:draft},location.origin);close();}}}/></Stack>
  </Stack>;
}
export function Dialogs({snapshot,kind,id,close}:{snapshot:Snapshot;kind:string;id?:string;close:()=>void}){
  const ref=useRef<HTMLDialogElement>(null),previous=useRef<HTMLElement|null>(null);
  useEffect(()=>{previous.current=document.activeElement as HTMLElement;ref.current?.showModal();return()=>previous.current?.focus({preventScroll:true});},[]);
  useEffect(()=>{if(kind==='settings'&&!controls(snapshot.state).canChangeSettings||kind==='generate'&&(!controls(snapshot.state).canGenerate||snapshot.state.selectedId!==id))close();},[snapshot.state.ux,snapshot.state.capturing,snapshot.state.selectedId]);
  const r=snapshot.state.records.find((item:any)=>item.id===id);
  let title='앱 정보',content:ReactNode=<Stack gap={3}><Text>오늘의 트랙 · 0.3 기준 UI 검토</Text><Text type="supporting">이 화면의 기록·곡은 검토용 예시입니다. 재생 소리는 없습니다.</Text><Text type="supporting">정밀 위치는 차량에 보관하며, 표지는 고정 장식 이미지입니다.</Text></Stack>;
  if(kind==='settings'){title='새 기록 설정';content=<Settings snapshot={snapshot} close={close}/>;}
  if(kind==='generate'){title='음악 만들기';content=<Stack gap={3}><Text>정밀 위치를 뺀 기록 요약을 생성 서버·음악 AI로 전송합니다.</Text><Text type="supporting">지금은 검토 모의이며 실제 전송·생성은 없습니다.</Text><Stack direction="horizontal" gap={2} hAlign="end"><Action label="취소" width={132} onClick={close}/><Action label="만들기" primary width={132} disabled={!controls(snapshot.state).canGenerate||r?.id!==snapshot.state.selectedId||!!r?.intentId} onClick={()=>{if(controls(snapshot.state).canGenerate&&r?.id===snapshot.state.selectedId&&!r.intentId){send('generate');close();}}}/></Stack></Stack>;}
  if(kind==='observations'){title='관측·작업 정보';content=r?<Stack gap={3}><Text type="supporting">관측 순서 예시 A → B → [공백] → A</Text><Text type="supporting">관측 공백의 경로·실제 운전 시간은 알 수 없습니다.</Text><Text type="supporting">기록 {r.id}</Text><Text type="supporting">고정 입력 {r.input||'아직 없음'}</Text><Text type="supporting">작업 {r.jobId||'미확인'} · {r.intentId||'아직 없음'}</Text></Stack>:<Text>기록을 선택해 주세요.</Text>;}
  return <dialog ref={ref} aria-labelledby="dialog-title" onCancel={event=>{event.preventDefault();close();}} style={{...frameStyle(snapshot.config),border:'1px solid var(--color-border)',borderRadius:'16px',padding:'var(--spacing-6)',width:'calc(100% - 48px)',maxWidth:'640px',maxHeight:'calc(100vh - 48px)',boxSizing:'border-box'}}>
    <Stack gap={3}><Heading level={2} id="dialog-title">{title}</Heading>{content}{!['settings','generate'].includes(kind)&&<Stack hAlign="end"><Action label="닫기" width={132} onClick={close}/></Stack>}</Stack>
  </dialog>;
}
