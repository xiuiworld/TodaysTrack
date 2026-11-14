import {Stack,StackItem} from '@astryxdesign/core/Stack';
import {Heading} from '@astryxdesign/core/Heading';
import {Text} from '@astryxdesign/core/Text';
import {Button} from '@astryxdesign/core/Button';
import {Icon} from '@astryxdesign/core/Icon';
import {Thumbnail} from '@astryxdesign/core/Thumbnail';
import {Action,controls,tracks,trackLabel,art,glyphs,send,type ScreenProps} from './shared';
import {SourceNote} from './MusicSection';
import {BackArrow} from './RecordsScreen';
import {PlaybackControls} from './PlaybackControls';
export function SongDetailScreen({snapshot,open,id,back}:ScreenProps&{id:string;back:()=>void}){
  const s=snapshot.state,t=tracks(s).find((item:any)=>item.id===id),c=controls(s);
  if(!t)return <Stack padding={6} gap={3}><Text>이 음악을 찾을 수 없어요.</Text><Action label="보관함으로" width={176} onClick={back}/></Stack>;
  const r=s.records.find((item:any)=>item.id===t.recordId),playing=s.playingId===t.id;
  const albumSize='clamp(calc(220px * var(--tt-ui)), calc(var(--tt-cover) * 2), calc(260px * var(--tt-ui)))';
  return <Stack paddingInline={6} paddingBlock={1} gap={2}>
    <Stack direction="horizontal" gap={1} vAlign="center"><Action label="보관함으로 돌아가기" quiet width={76} onClick={back}><BackArrow/></Action><StackItem size="fill"><Heading level={2}>음악 상세</Heading></StackItem><Action label="원래 기록 보기" quiet width={176} disabled={!c.canSelect&&r.id!==s.selectedId} onClick={()=>open('record',r.id)}><Text type="label" maxLines={1}>기록 보기</Text><Icon icon="chevronRight" size="sm"/></Action></Stack>
    <Stack direction="horizontal" gap={6} vAlign="center" as="section" aria-label="음악 정보">
      <Thumbnail src={art(t.recordId)} alt="" label="고정 장식 표지" style={{width:albumSize,height:albumSize,flexShrink:0}}/>
      <StackItem size="fill"><Stack gap={3}><Heading level={2}>{trackLabel(t)}</Heading><Text type="supporting">{r.genre} · {r.lyrics}</Text><SourceNote snapshot={snapshot} provenance={t.provenance}/><Stack gap={1} paddingBlock={2}><Text type="supporting">{r.title}에서 만든 음악</Text><Text type="supporting">{r.date}</Text></Stack></Stack></StackItem>
    </Stack>
    <Stack style={{borderTop:'1px solid var(--color-border)'}}><PlaybackControls snapshot={snapshot} track={t}/></Stack>
  </Stack>;
}
