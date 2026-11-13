import {Stack,StackItem} from '@astryxdesign/core/Stack';
import {Heading} from '@astryxdesign/core/Heading';
import {Text} from '@astryxdesign/core/Text';
import {Button} from '@astryxdesign/core/Button';
import {Icon} from '@astryxdesign/core/Icon';
import {Action,WorkLabel,controls,tracks,trackLabel,send,type ScreenProps} from './shared';
import {TransferNotice} from './Chrome';
export function RecordsScreen({snapshot,open}:ScreenProps){const s=snapshot.state,c=controls(s);return <Stack padding={6} gap={3}>
  <Stack direction="horizontal" hAlign="between" vAlign="center"><Heading level={2}>기록</Heading><Action label={s.records.some((r:any)=>r.open)?'기록 이어서 수집':'새 기록 시작'} action="start" disabled={!c.canStart} primary width={224}/></Stack>
  {!s.records.length?<Stack gap={3}><Text>저장된 기록이 없어요</Text><Text type="supporting">홈에서 첫 기록을 시작하세요.</Text></Stack>:s.records.map((r:any)=><Button key={r.id} label={`${r.title} · ${r.date}`} data-review-action="record" data-review-value={r.id} width="100%" variant="ghost" isDisabled={!c.canSelect&&r.id!==s.selectedId} onClick={()=>open('record',r.id)}><Stack direction="horizontal" gap={3} vAlign="center" width="100%" minHeight="var(--tt-hit)" style={{position:'absolute',inset:0,borderBottom:'1px solid var(--color-border)',textAlign:'start',whiteSpace:'normal'}}><StackItem size="fill"><Stack gap={0}><Text type="label" maxLines={1}>{r.title}</Text><Text type="supporting" maxLines={1}>{r.date}</Text></Stack></StackItem><Stack gap={0}><Text hasTabularNumbers>{r.distance} km</Text><WorkLabel state={s} record={r}/></Stack><Icon icon="chevronRight" size="sm"/></Stack></Button>)}
</Stack>;}
export function RecordDetailScreen({snapshot,open,id,back}:{snapshot:ScreenProps['snapshot'];open:ScreenProps['open'];id:string;back:()=>void}){
  const s=snapshot.state,r=s.records.find((item:any)=>item.id===id),c=controls(s);if(!r)return <Stack padding={6}><Text>기록을 선택해 주세요.</Text></Stack>;
  const track=tracks(s).find((item:any)=>item.recordId===r.id),saved=r.state==='AVAILABLE';
  return <Stack paddingInline={6} paddingBlock={3} gap={3}>
    <Stack direction="horizontal" hAlign="between" vAlign="center"><Action label="기록 목록으로" width={176} onClick={back}/><WorkLabel state={s} record={r}/></Stack>
    <Heading level={2}>{r.title}</Heading><Text type="supporting">{r.date}</Text>
    <Stack direction="horizontal" gap={6} wrap="wrap"><Stack gap={1}><Text type="supporting">연속 관측 거리</Text><Text size="2xl" hasTabularNumbers>{r.distance} km</Text></Stack><Stack gap={1}><Text type="supporting">기록 설정</Text><Text>{r.kind} · {r.genre} · {r.lyrics}</Text></Stack></Stack>
    {r.intentId&&<Text type="supporting">{s.provenance==='FIXTURE'?'대역 · 새 AI 생성 아님':'새 AI 음악 · 검토 예시'}{r.state==='ACCEPTANCE_UNCERTAIN'?' · 원래 작업만 확인합니다.':''}</Text>}
    {track&&<Text type="label">{trackLabel(track)}</Text>}
    <Stack direction="horizontal" gap={2} wrap="wrap">
      {r.open?<Action label={s.capturing?'기록 마무리':'기록 이어서 수집'} action={s.capturing?'finish':'start'} disabled={s.capturing?!c.canStop:!c.canStart} primary width={224}/>:<Action label={saved?'음악 듣기':r.intentId?'기존 작업 확인':'음악 만들기'} action={saved?'play':'generate'} value={track?.id} disabled={saved?!c.canPlay:!c.canGenerate||r.id!==s.selectedId} primary width={224}/>}
      <Action label="관측·작업 정보" width={176} onClick={()=>open('observations',id)}/>
    </Stack>
    <TransferNotice/>
  </Stack>;
}
