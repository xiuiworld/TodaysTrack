import {Stack,StackItem} from '@astryxdesign/core/Stack';
import {Heading} from '@astryxdesign/core/Heading';
import {Text} from '@astryxdesign/core/Text';
import {Button} from '@astryxdesign/core/Button';
import {Icon} from '@astryxdesign/core/Icon';
import {Thumbnail} from '@astryxdesign/core/Thumbnail';
import {Action,controls,tracks,trackLabel,art,glyphs,type ScreenProps} from './shared';
import {MusicSection} from './MusicSection';
export const BackArrow=()=> <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m12 5-7 7 7 7M5 12h15"/></svg>;
const More=()=> <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor" aria-hidden="true"><circle cx="5" cy="12" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="19" cy="12" r="1.6"/></svg>;
export function RecordsScreen({snapshot,open}:ScreenProps){
  const s=snapshot.state,c=controls(s),all=tracks(s);
  return <Stack paddingInline={6} paddingBlock={3} gap={2}>
    <Stack direction="horizontal" hAlign="between" vAlign="center"><Stack direction="horizontal" gap={3} vAlign="center"><Heading level={2}>기록</Heading><Text type="supporting">{s.records.length}개</Text></Stack><Action label={s.records.some((r:any)=>r.open)?'기록 이어가기':'새 기록 시작'} action="start" disabled={!c.canStart} width={176}/></Stack>
    {!s.records.length?<Stack gap={3} paddingBlock={6}><Icon icon={glyphs.records} size="lg" color="secondary"/><Heading level={3}>아직 기록이 없어요</Heading><Text type="supporting">첫 기록을 시작해 보세요.</Text></Stack>:s.records.map((r:any)=>{const song=all.find((t:any)=>t.recordId===r.id);const status=r.open?'기록 중':r.state==='ACCEPTANCE_UNCERTAIN'?'접수 확인 중':r.state==='RECONCILING'?'작업 확인 필요':r.intentId?'음악 생성 대기':'만든 음악 없음';return <Button key={r.id} label={`${r.title} · ${r.date}`} data-review-action="record" data-review-value={r.id} width="100%" variant="ghost" style={{minHeight:"max(calc(var(--tt-hit) * 1.5), calc(var(--tt-cover) / 2 + var(--spacing-6)), calc(var(--tt-font) * 4.35 + var(--spacing-5)))",position:"relative"}} isDisabled={!c.canSelect&&r.id!==s.selectedId} onClick={()=>open('record',r.id)}><Stack direction="horizontal" gap={4} paddingBlock={3} vAlign="center" width="100%" minHeight="var(--tt-hit)" style={{position:'absolute',inset:0,borderBottom:'1px solid var(--color-border)',textAlign:'start',whiteSpace:'normal'}}>
      {song?<Thumbnail src={art(r.id)} alt="" label="고정 장식 표지" style={{width:'calc(var(--tt-cover) / 2)',height:'calc(var(--tt-cover) / 2)',flexShrink:0}}/>:<Stack width="calc(var(--tt-cover) / 2)" minHeight="calc(var(--tt-cover) / 2)" hAlign="center" vAlign="center" style={{flexShrink:0,border:'1px solid var(--color-border)',borderRadius:'var(--radius-element)'}}><Icon icon={glyphs.records} size="lg" color="secondary"/></Stack>}
      <StackItem size="fill"><Stack gap={1}><Text type="label" maxLines={1}>{r.title}</Text><Text type="supporting" maxLines={1}>{r.date}</Text><Text type="supporting" maxLines={1}>{song?'만든 음악 · '+trackLabel(song):status}</Text></Stack></StackItem>
      <Text type="supporting" hasTabularNumbers>{r.distance} km</Text><Icon icon="chevronRight" size="sm"/>
    </Stack></Button>;})}
  </Stack>;
}
export function RecordDetailScreen({snapshot,open,id,back}:{snapshot:ScreenProps['snapshot'];open:ScreenProps['open'];id:string;back:()=>void}){
  const r=snapshot.state.records.find((item:any)=>item.id===id);if(!r)return <Stack padding={6}><Text>기록을 선택해 주세요.</Text></Stack>;
  return <Stack paddingInline={6} paddingBlock={2} gap={3}>
    <Stack direction="horizontal" gap={1} vAlign="center"><Action label="기록 목록으로 돌아가기" quiet width={76} onClick={back}><BackArrow/></Action><StackItem size="fill"><Heading level={2}>{r.title}</Heading></StackItem><Action label="기록 정보" quiet width={76} onClick={()=>open('observations',id)}><More/></Action></Stack>
    <Stack direction="horizontal" gap={6} paddingBlock={3} vAlign="center"><StackItem size="fill"><Stack gap={1}><Text type="supporting">기록 시각</Text><Text type="label">{r.date}</Text></Stack></StackItem><StackItem size="fill"><Stack gap={1} style={{borderInlineStart:'1px solid var(--color-border)',paddingInlineStart:'var(--tt-gap)'}}><Text type="supporting">연속 관측 거리</Text><Heading level={3}>{r.distance} km</Heading></Stack></StackItem></Stack>
    <MusicSection snapshot={snapshot} open={open} record={r}/>
  </Stack>;
}
