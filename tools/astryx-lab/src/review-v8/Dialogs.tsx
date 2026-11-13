import {useEffect,useRef,useState,type ReactNode} from 'react';
import {Stack,StackItem} from '@astryxdesign/core/Stack';
import {Heading} from '@astryxdesign/core/Heading';
import {Text} from '@astryxdesign/core/Text';
import {Button} from '@astryxdesign/core/Button';
import {Action,channel,controls,frameStyle,send,type Snapshot} from './shared';
const options={kind:['하루 기록','여행 기록'],genre:['잔잔한 팝','어쿠스틱','일렉트로닉'],lyrics:['무가사','가사 있는 곡']};
const names={kind:'기록 범위',genre:'음악 장르',lyrics:'가사'};
function InfoRow({label,children}:{label:string;children:ReactNode}){return <Stack direction="horizontal" gap={4} paddingBlock={2} vAlign="center" style={{borderBottom:'1px solid var(--color-border)'}}><Text type="supporting" style={{width:'calc(112px * var(--tt-ui))',flexShrink:0}}>{label}</Text><StackItem size="fill"><Text type="label">{children}</Text></StackItem></Stack>;}
export function Dialogs({snapshot,kind,id,close}:{snapshot:Snapshot;kind:string;id?:string;close:()=>void}){
  const ref=useRef<HTMLDialogElement>(null),previous=useRef<HTMLElement|null>(null),[draft,setDraft]=useState({...snapshot.state.defaults});
  const c=controls(snapshot.state),r=snapshot.state.records.find((item:any)=>item.id===id);
  useEffect(()=>{previous.current=document.activeElement as HTMLElement;ref.current?.showModal();return()=>previous.current?.focus({preventScroll:true});},[]);
  useEffect(()=>{if(kind==='settings'&&!c.canChangeSettings||kind==='generate'&&(!c.canGenerate||snapshot.state.selectedId!==id))close();},[snapshot.state.ux,snapshot.state.capturing,snapshot.state.selectedId]);
  let title='이용 안내',content:ReactNode=<Stack gap={4}>
    <Stack gap={2}><Text type="label">기록에서 음악까지</Text><Stack direction="horizontal" gap={3} wrap="wrap"><Text type="supporting">기록 시작</Text><Text type="supporting">→</Text><Text type="supporting">마감</Text><Text type="supporting">→</Text><Text type="supporting">음악 만들기</Text></Stack><Text type="supporting">만든 음악은 보관함에서 감상할 수 있어요.</Text></Stack>
    <Stack gap={1}><Text type="label">데이터 안내</Text><InfoRow label="차량에 보관">정밀 위치</InfoRow><InfoRow label="음악 제작에 전송">위치를 뺀 기록 요약</InfoRow></Stack>
    <Text type="supporting">차량 조작 제한에 따라 일부 버튼이 비활성화됩니다.</Text>
  </Stack>;
  let confirm='확인',cancel=false,disabled=false,onConfirm=close;
  if(kind==='settings'){
    title='새 기록 설정';confirm='저장';cancel=true;disabled=!c.canChangeSettings;
    content=<Stack gap={2}><Text type="supporting">다음 기록부터 적용됩니다.</Text>{(Object.keys(options) as (keyof typeof options)[]).map(key=><Stack key={key} gap={0}><Text type="label" id={'setting-label-'+key}>{names[key]}</Text><Stack direction="horizontal" gap={2} role="group" aria-labelledby={'setting-label-'+key}>{options[key].map(value=><StackItem key={value} size="fill"><Button label={value} data-setting={key} data-choice={value} aria-pressed={draft[key]===value} variant="ghost" width="100%" isDisabled={!c.canChangeSettings} style={{position:'relative'}} onClick={()=>{if(c.canChangeSettings)setDraft({...draft,[key]:value});}}><Stack hAlign="center" vAlign="center" minHeight="var(--tt-visible-button)" style={{position:'absolute',insetInline:'var(--spacing-1)',top:'calc((var(--tt-hit) - var(--tt-visible-button)) / 2)',border:'1px solid var(--color-border)',borderRadius:'var(--radius-element)',backgroundColor:draft[key]===value?'var(--color-accent)':'var(--color-background-surface)',color:draft[key]===value?'var(--color-background-surface)':'var(--color-text-primary)'}}><Text type="label" color="inherit">{value}</Text></Stack></Button></StackItem>)}</Stack></Stack>)}</Stack>;
    onConfirm=()=>{if(c.canChangeSettings){window.parent.postMessage({channel,type:'settings-save',defaults:draft},location.origin);close();}};
  }
  if(kind==='generate'){
    title='음악 만들기';confirm='만들기';cancel=true;disabled=!c.canGenerate||r?.id!==snapshot.state.selectedId||!!r?.intentId;
    content=<Stack gap={3}><Stack gap={1}><Heading level={3}>{r?.title}</Heading><Text type="supporting">{r?.date}</Text></Stack><InfoRow label="사용할 설정">{r?.genre} · {r?.lyrics}</InfoRow><InfoRow label="전송할 정보">정밀 위치를 뺀 기록 요약</InfoRow><Text type="supporting">요약은 생성 서버·음악 AI로 전송합니다.</Text></Stack>;
    onConfirm=()=>{if(!disabled){send('generate');close();}};
  }
  if(kind==='observations'){
    title='기록 정보';content=r?<Stack gap={2}><Stack gap={0}><InfoRow label="기록 범위">{r.kind}</InfoRow><InfoRow label="기록 시각">{r.date}</InfoRow><InfoRow label="연속 관측 거리">{r.distance} km</InfoRow><InfoRow label="음악 설정">{r.genre} · {r.lyrics}</InfoRow><InfoRow label="음악 상태">{r.open?'기록 수집 중':r.state==='AVAILABLE'?'보관함에 저장됨':r.state==='ACCEPTANCE_UNCERTAIN'?'접수 확인 중':r.state==='RECONCILING'?'작업 확인 필요':r.intentId?'음악 생성 대기':'음악 만들기 전'}</InfoRow></Stack><Text type="supporting">관측이 끊긴 구간의 경로와 실제 운전 시간은 알 수 없어요.</Text>
      {snapshot.showDiagnostics&&<details><summary style={{minHeight:'var(--tt-hit)',lineHeight:'var(--tt-hit)',cursor:'pointer'}}>요청·작업 정보</summary><Stack gap={2} style={{overflowWrap:'anywhere'}}><Text type="supporting">기록 ID · {r.id}</Text><Text type="supporting">요청 ID · {r.intentId||'아직 없음'}</Text><Text type="supporting">작업 ID · {r.jobId||'미확인'}</Text><Text type="supporting">고정 입력 · {r.input||'아직 없음'}</Text></Stack></details>}
    </Stack>:<Text>기록을 선택해 주세요.</Text>;
  }
  return <dialog ref={ref} aria-labelledby="dialog-title" onCancel={event=>{event.preventDefault();close();}} style={{...frameStyle(snapshot.config),border:'1px solid var(--color-border)',borderRadius:'20px',padding:0,width:'calc(100% - 64px)',maxWidth:'calc(720px * var(--tt-ui))',maxHeight:'calc(100vh - 48px)',boxSizing:'border-box',overflow:'hidden'}}>
    <Stack gap={0} style={{maxHeight:'calc(100vh - 50px)'}}>
      <Stack direction="horizontal" paddingInline={5} vAlign="center" hAlign="between" minHeight="var(--tt-hit)" style={{flexShrink:0,borderBottom:'1px solid var(--color-border)'}}><Heading level={2} id="dialog-title">{title}</Heading><Action label="닫기" quiet width={76} onClick={close}><svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg></Action></Stack>
      <Stack paddingInline={5} paddingBlock={3} gap={3} style={{minHeight:0,overflowY:'auto',flex:'1 1 auto'}}>{content}{snapshot.showDiagnostics&&<Text type="supporting">검토 모의 · 실제 수집·전송·생성·소리 재생은 없습니다.</Text>}</Stack>
      <Stack direction="horizontal" gap={2} paddingInline={5} paddingBlock={1} hAlign="end" vAlign="center" style={{flexShrink:0,borderTop:'1px solid var(--color-border)'}}>{cancel&&<Action label="취소" quiet width={132} onClick={close}/>}<Action label={confirm} primary width={132} disabled={disabled} onClick={onConfirm}/></Stack>
    </Stack>
  </dialog>;
}
