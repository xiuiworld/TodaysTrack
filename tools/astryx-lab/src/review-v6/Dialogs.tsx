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
  let title='오늘의 트랙',content:ReactNode=<Stack gap={4}>
    <Text>주행의 기록을 음악으로 남겨요.</Text>
    <Stack gap={1}><Text type="label">기록 모으기</Text><Text type="supporting">기록을 시작하고, 마치면 마감해 주세요.</Text></Stack>
    <Stack gap={1}><Text type="label">음악 남기기</Text><Text type="supporting">마감한 기록으로 음악을 만들고 보관함에서 다시 들어요.</Text></Stack>
    <Stack gap={1} paddingBlock={3} style={{borderTop:'1px solid var(--color-border)'}}><Text type="label">데이터 안내</Text><Text type="supporting">정밀 위치는 차량에 보관하고, 음악을 만들 때 위치를 뺀 요약만 전송해요.</Text><Text type="supporting">조작 제한 중에는 일부 버튼을 사용할 수 없어요.</Text></Stack>
    <Text type="supporting">검토용 예시 · 수집·생성·재생은 모의입니다.</Text>
  </Stack>;
  if(kind==='settings'){title='새 기록 설정';content=<Settings snapshot={snapshot} close={close}/>;}
  if(kind==='generate'){title='음악 만들기';content=<Stack gap={3}><Text>정밀 위치를 뺀 기록 요약을 생성 서버·음악 AI로 전송합니다.</Text><Text type="supporting">지금은 검토 모의이며 실제 전송·생성은 없습니다.</Text><Stack direction="horizontal" gap={2} hAlign="end"><Action label="취소" width={132} onClick={close}/><Action label="만들기" primary width={132} disabled={!controls(snapshot.state).canGenerate||r?.id!==snapshot.state.selectedId||!!r?.intentId} onClick={()=>{if(controls(snapshot.state).canGenerate&&r?.id===snapshot.state.selectedId&&!r.intentId){send('generate');close();}}}/></Stack></Stack>;}
  if(kind==='observations'){title='기록 정보';content=r?<Stack gap={3}>
    <Stack direction="horizontal" gap={4} wrap="wrap"><Stack gap={1}><Text type="supporting">기록 범위</Text><Text type="label">{r.kind}</Text></Stack><Stack gap={1}><Text type="supporting">음악 설정</Text><Text type="label">{r.genre} · {r.lyrics}</Text></Stack></Stack>
    <Stack gap={1}><Text type="supporting">음악 상태</Text><Text type="label">{r.open?'기록 수집 중':r.state==='AVAILABLE'?'보관함에 저장됨':r.state==='ACCEPTANCE_UNCERTAIN'?'접수 확인 중':r.state==='RECONCILING'?'작업 확인 필요':r.intentId?'음악 생성 대기':'아직 음악을 만들지 않음'}</Text></Stack>
    <Text type="supporting">거리는 연속으로 관측한 구간의 값입니다. 관측 공백의 경로·실제 운전 시간은 알 수 없어요.</Text>
    <details style={{borderTop:'1px solid var(--color-border)'}}><summary style={{minHeight:'var(--tt-hit)',lineHeight:'var(--tt-hit)',cursor:'pointer',fontWeight:600}}>요청·작업 정보</summary><Stack gap={2} style={{overflowWrap:'anywhere'}}><Text type="supporting">기록 ID · {r.id}</Text><Text type="supporting">요청 ID · {r.intentId||'아직 없음'}</Text><Text type="supporting">작업 ID · {r.jobId||'미확인'}</Text><Text type="supporting">고정 입력 · {r.input||'아직 없음'}</Text></Stack></details>
  </Stack>:<Text>기록을 선택해 주세요.</Text>;}
  return <dialog ref={ref} aria-labelledby="dialog-title" onCancel={event=>{event.preventDefault();close();}} style={{...frameStyle(snapshot.config),border:'1px solid var(--color-border)',borderRadius:'16px',padding:'var(--spacing-6)',width:'calc(100% - 48px)',maxWidth:'640px',maxHeight:'calc(100vh - 48px)',boxSizing:'border-box'}}>
    <Stack gap={3}><Heading level={2} id="dialog-title">{title}</Heading>{content}{!['settings','generate'].includes(kind)&&<Stack hAlign="end"><Action label="닫기" width={132} onClick={close}/></Stack>}</Stack>
  </dialog>;
}
