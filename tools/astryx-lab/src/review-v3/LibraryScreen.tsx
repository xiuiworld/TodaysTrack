import {Stack,StackItem} from '@astryxdesign/core/Stack';
import {Heading} from '@astryxdesign/core/Heading';
import {Text} from '@astryxdesign/core/Text';
import {Icon} from '@astryxdesign/core/Icon';
import {Thumbnail} from '@astryxdesign/core/Thumbnail';
import {Action,controls,tracks,trackLabel,art,glyphs,type ScreenProps} from './shared';
export function LibraryScreen({snapshot}:ScreenProps){const s=snapshot.state,c=controls(s),items=tracks(s);return <Stack padding={6} gap={4}>
  <Stack direction="horizontal" hAlign="between" vAlign="center"><Heading level={2}>보관함</Heading><Text type="supporting">{items.length}곡</Text></Stack>
  {!items.length?<Stack gap={3}><Icon icon={glyphs.library} size="lg"/><Text>저장된 음악이 없어요</Text><Action label="홈으로" action="page" value="home" width={176}/></Stack>:items.map((t:any)=><Stack key={t.id} direction="horizontal" gap={3} vAlign="center" style={{borderBottom:'1px solid var(--color-border)',minHeight:'var(--tt-hit)'}}>
    <Thumbnail src={art(t.recordId)} alt="" label="고정 장식 표지" style={{width:'calc(56px * var(--tt-ui))',height:'calc(56px * var(--tt-ui))',flexShrink:0}}/>
    <StackItem size="fill"><Stack gap={0}><Text type="label" maxLines={1}>{trackLabel(t)}</Text><Text type="supporting">{s.records.find((r:any)=>r.id===t.recordId)?.date}</Text><Text type="supporting">{t.provenance==='FIXTURE'?'대역 · 새 AI 생성 아님':'새 AI 음악 · 검토 예시'}</Text></Stack></StackItem>
    <Text type="supporting" hasTabularNumbers>{s.playingId===t.id?'재생 중 · ':''}{t.duration}</Text><Action label={s.playingId===t.id?'일시정지':'재생'} action={s.playingId===t.id?'pause':'play'} value={t.id} disabled={s.playingId===t.id?false:!c.canPlay} width={76}><Icon icon={s.playingId===t.id?glyphs.pause:glyphs.play} size="lg"/></Action>
  </Stack>)}
</Stack>;}
