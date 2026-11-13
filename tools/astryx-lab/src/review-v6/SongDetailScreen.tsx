import {Stack,StackItem} from '@astryxdesign/core/Stack';
import {Heading} from '@astryxdesign/core/Heading';
import {Text} from '@astryxdesign/core/Text';
import {Icon} from '@astryxdesign/core/Icon';
import {Thumbnail} from '@astryxdesign/core/Thumbnail';
import {Action,controls,tracks,trackLabel,art,glyphs,type ScreenProps} from './shared';
import {sourceLabel} from './MusicSection';
import {BackArrow} from './RecordsScreen';
export function SongDetailScreen({snapshot,open,id,back}:ScreenProps&{id:string;back:()=>void}){
  const s=snapshot.state,t=tracks(s).find((item:any)=>item.id===id),c=controls(s);
  if(!t)return <Stack padding={6} gap={3}><Text>이 음악을 찾을 수 없어요.</Text><Action label="보관함으로" width={176} onClick={back}/></Stack>;
  const r=s.records.find((item:any)=>item.id===t.recordId),playing=s.playingId===t.id;
  return <Stack paddingInline={6} paddingBlock={2} gap={3}>
    <Stack direction="horizontal" gap={1} vAlign="center"><Action label="보관함으로 돌아가기" quiet width={76} onClick={back}><BackArrow/></Action><Heading level={2}>음악 상세</Heading></Stack>
    <Stack direction="horizontal" gap={6} paddingBlock={3} vAlign="center">
      <Thumbnail src={art(t.recordId)} alt="" label="고정 장식 표지" style={{width:'var(--tt-cover)',height:'var(--tt-cover)',flexShrink:0}}/>
      <StackItem size="fill"><Stack gap={2}><Heading level={3}>{trackLabel(t)}</Heading><Text type="supporting">{r.genre} · {r.lyrics} · {t.duration}</Text><Text type="supporting">{sourceLabel(t)}</Text><Action label={playing?'일시정지':'재생'} action={playing?'pause':'play'} value={t.id} primary disabled={!playing&&!c.canPlay} width={176}><Icon icon={playing?glyphs.pause:glyphs.play} size="lg"/><Text type="label" color="inherit">{playing?'일시정지':'재생'}</Text></Action></Stack></StackItem>
    </Stack>
    <Stack direction="horizontal" gap={3} paddingBlock={3} vAlign="center" style={{borderTop:'1px solid var(--color-border)'}}><StackItem size="fill"><Stack gap={1}><Text type="supporting">이 음악이 시작된 기록</Text><Text type="label">{r.title}</Text><Text type="supporting">{r.date}</Text></Stack></StackItem><Action label="원래 기록 보기" width={176} disabled={!c.canSelect&&r.id!==s.selectedId} onClick={()=>open('record',r.id)}/></Stack>
  </Stack>;
}
