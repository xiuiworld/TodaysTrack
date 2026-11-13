import {Stack,StackItem} from '@astryxdesign/core/Stack';
import {Heading} from '@astryxdesign/core/Heading';
import {Text} from '@astryxdesign/core/Text';
import {Icon} from '@astryxdesign/core/Icon';
import {Thumbnail} from '@astryxdesign/core/Thumbnail';
import {Action,controls,tracks,trackLabel,art,glyphs,type ScreenProps} from './shared';
export const sourceLabel=(track:any)=>track.provenance==='FIXTURE'?'대역 · 새 AI 생성 아님':'새 AI 음악 · 검토 예시';
export function MusicSection({snapshot,open,record:r}:ScreenProps&{record:any}){
  const s=snapshot.state,c=controls(s),songs=tracks(s).filter((t:any)=>t.recordId===r.id);
  const pending=!!r.intentId&&!songs.length,playing=(id:string)=>s.playingId===id;
  const status=r.state==='ACCEPTANCE_UNCERTAIN'?'접수를 확인하고 있어요':r.state==='RECONCILING'?'작업을 확인해 주세요':'음악을 준비하고 있어요';
  return <Stack as="section" aria-label="이 기록의 음악" gap={3} paddingBlock={4} style={{borderTop:'1px solid var(--color-border)'}}>
    <Heading level={3}>이 기록의 음악</Heading>
    {songs.length?songs.map((t:any)=><Stack key={t.id} direction="horizontal" gap={4} vAlign="center" data-track-id={t.id}>
      <Thumbnail src={art(t.recordId)} alt="" label="고정 장식 표지" style={{width:'var(--tt-cover)',height:'var(--tt-cover)',flexShrink:0}}/>
      <StackItem size="fill"><Stack gap={1}><Heading level={3}>{trackLabel(t)}</Heading><Text type="supporting">{r.genre} · {r.lyrics} · {t.duration}</Text><Text type="supporting">{sourceLabel(t)}</Text></Stack></StackItem>
      <Action label={playing(t.id)?'일시정지':'재생'} action={playing(t.id)?'pause':'play'} value={t.id} primary disabled={!playing(t.id)&&!c.canPlay} width={132}><Icon icon={playing(t.id)?glyphs.pause:glyphs.play} size="lg"/><Text type="label" color="inherit">{playing(t.id)?'일시정지':'재생'}</Text></Action>
    </Stack>):<Stack direction="horizontal" gap={4} vAlign="center">
      <Stack width="var(--tt-cover)" minHeight="var(--tt-cover)" hAlign="center" vAlign="center" style={{flexShrink:0,border:'1px dashed var(--color-border)',borderRadius:'var(--radius-element)'}}><Icon icon={glyphs.library} size="lg" color="secondary"/></Stack>
      <StackItem size="fill"><Stack gap={1}><Text type="label">{r.open?'기록을 모으고 있어요':pending?status:'아직 만든 음악이 없어요'}</Text><Text type="supporting">{r.open?'기록을 마친 뒤 음악으로 남길 수 있어요.':pending?'기록과 입력을 보관하며 원래 작업만 확인합니다.':r.genre+' · '+r.lyrics}</Text>{pending&&<Text type="supporting">{sourceLabel({provenance:s.provenance})}</Text>}</Stack></StackItem>
      {r.open?<Action label={s.capturing?'기록 마감':'기록 이어가기'} action={s.capturing?'finish':'start'} disabled={s.capturing?!c.canStop:!c.canStart} width={176}/>:<Action label={pending?'상태 확인':'이 기록으로 만들기'} action={pending?'generate':undefined} onClick={pending?undefined:()=>open('generate',r.id)} primary={!pending} disabled={!c.canGenerate||r.id!==s.selectedId} width={pending?152:200}/>}
    </Stack>}
  </Stack>;
}
