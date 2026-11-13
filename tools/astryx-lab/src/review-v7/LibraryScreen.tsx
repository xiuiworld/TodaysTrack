import {Stack,StackItem} from '@astryxdesign/core/Stack';
import {Heading} from '@astryxdesign/core/Heading';
import {Text} from '@astryxdesign/core/Text';
import {Button} from '@astryxdesign/core/Button';
import {Icon} from '@astryxdesign/core/Icon';
import {Thumbnail} from '@astryxdesign/core/Thumbnail';
import {Action,controls,tracks,trackLabel,art,glyphs,type ScreenProps} from './shared';
import {SourceNote} from './MusicSection';
export function LibraryScreen({snapshot,open}:ScreenProps){const s=snapshot.state,c=controls(s),items=tracks(s);return <Stack paddingInline={6} paddingBlock={3} gap={2}>
  <Stack direction="horizontal" gap={3} vAlign="center" minHeight="var(--tt-hit)"><Heading level={2}>보관함</Heading><Text type="supporting">{items.length}곡</Text></Stack>
  {!items.length?<Stack gap={3} paddingBlock={6}><Icon icon={glyphs.library} size="lg" color="secondary"/><Heading level={3}>아직 저장된 음악이 없어요</Heading><Action label="기록 보러 가기" action="page" value="records" width={200}/></Stack>:items.map((t:any)=>{const r=s.records.find((item:any)=>item.id===t.recordId),playing=s.playingId===t.id;return <Stack key={t.id} direction="horizontal" gap={2} vAlign="center" data-record-id={t.recordId} data-selected={t.recordId===s.selectedId} style={{borderBottom:'1px solid var(--color-border)',borderInlineStart:playing?'3px solid var(--color-accent)':'3px solid transparent',paddingInlineStart:'var(--spacing-2)'}}>
    <StackItem size="fill"><Button label={`음악 상세 · ${trackLabel(t)} · ${r.date}`} data-review-action="song-detail" data-review-value={t.id} width="100%" variant="ghost" style={{minHeight:"max(calc(var(--tt-hit) * 1.5), calc(var(--tt-cover) / 2 + var(--spacing-6)), calc(var(--tt-font) * 4.35 + var(--spacing-5)))",position:"relative"}} isDisabled={!c.canSelect&&r.id!==s.selectedId} onClick={()=>open('song',t.id)}><Stack direction="horizontal" gap={4} paddingBlock={3} vAlign="center" width="100%" minHeight="var(--tt-hit)" style={{position:'absolute',inset:0,textAlign:'start',whiteSpace:'normal'}}>
      <Thumbnail src={art(t.recordId)} alt="" label="고정 장식 표지" style={{width:'calc(var(--tt-cover) / 2)',height:'calc(var(--tt-cover) / 2)',flexShrink:0}}/>
      <StackItem size="fill"><Stack gap={1}><Text type="label" maxLines={1}>{trackLabel(t)}</Text><Text type="supporting" maxLines={1}>{r.title} · {r.date}</Text><SourceNote snapshot={snapshot} provenance={t.provenance}/></Stack></StackItem>
    </Stack></Button></StackItem>
    <Text type="supporting" hasTabularNumbers>{playing?'재생 중 · ':''}{t.duration}</Text><Action label={`${playing?'일시정지':'재생'} · ${trackLabel(t)} · ${r.date}`} action={playing?'pause':'play'} value={t.id} primary={playing} disabled={!playing&&!c.canPlay} width={76}><Icon icon={playing?glyphs.pause:glyphs.play} size="lg"/></Action>
  </Stack>;})}
</Stack>;}
