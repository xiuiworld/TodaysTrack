import {Stack,StackItem} from '@astryxdesign/core/Stack';
import {Heading} from '@astryxdesign/core/Heading';
import {Text} from '@astryxdesign/core/Text';
import {Button} from '@astryxdesign/core/Button';
import {Icon} from '@astryxdesign/core/Icon';
import {Thumbnail} from '@astryxdesign/core/Thumbnail';
import {Action,controls,tracks,trackLabel,art,glyphs,type ScreenProps} from './shared';
import {SourceNote} from './MusicSection';
import {PlayButton} from './PlaybackControls';
import {CollectionHeading,CollectionBody,CollectionTail,collectionRowStyle} from './CollectionLayout';
export function LibraryScreen({snapshot,open}:ScreenProps){const s=snapshot.state,c=controls(s),items=tracks(s);return <Stack paddingInline={6} paddingBlock={3} gap={2}>
  <CollectionHeading title="보관함" count={items.length+'곡'}/>
  {!items.length?<Stack gap={3} paddingBlock={6}><Icon icon={glyphs.library} size="lg" color="secondary"/><Heading level={3}>아직 저장된 음악이 없어요</Heading><Action label="기록 보러 가기" action="page" value="records" width={200}/></Stack>:items.map((t:any)=>{const r=s.records.find((item:any)=>item.id===t.recordId),playing=s.playingId===t.id;return <Stack key={t.id} direction="horizontal" gap={4} vAlign="center" data-record-id={t.recordId} data-selected={t.recordId===s.selectedId} style={{borderBottom:'1px solid var(--color-border)'}}>
    <StackItem size="fill"><Button label={`음악 상세 · ${trackLabel(t)} · ${r.date}`} data-review-action="song-detail" data-review-value={t.id} width="100%" variant="ghost" style={collectionRowStyle} isDisabled={!c.canSelect&&r.id!==s.selectedId} onClick={()=>open('song',t.id)}><CollectionBody recordId={t.recordId} title={trackLabel(t)} subtitle={r.title+' · '+r.date} note={<SourceNote snapshot={snapshot} provenance={t.provenance}/>}/></Button></StackItem>
    <CollectionTail><Text type="supporting" hasTabularNumbers>{playing?'재생 중 · ':''}{t.duration}</Text><PlayButton snapshot={snapshot} track={t} label={`${playing?'일시정지':'재생'} · ${trackLabel(t)} · ${r.date}`}/></CollectionTail>
  </Stack>;})}
</Stack>;}
