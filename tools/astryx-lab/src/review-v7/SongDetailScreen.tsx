import {Stack,StackItem} from '@astryxdesign/core/Stack';
import {Heading} from '@astryxdesign/core/Heading';
import {Text} from '@astryxdesign/core/Text';
import {Button} from '@astryxdesign/core/Button';
import {Icon} from '@astryxdesign/core/Icon';
import {Thumbnail} from '@astryxdesign/core/Thumbnail';
import {Action,controls,tracks,trackLabel,art,glyphs,send,type ScreenProps} from './shared';
import {SourceNote} from './MusicSection';
import {BackArrow} from './RecordsScreen';
export function SongDetailScreen({snapshot,open,id,back}:ScreenProps&{id:string;back:()=>void}){
  const s=snapshot.state,t=tracks(s).find((item:any)=>item.id===id),c=controls(s);
  if(!t)return <Stack padding={6} gap={3}><Text>이 음악을 찾을 수 없어요.</Text><Action label="보관함으로" width={176} onClick={back}/></Stack>;
  const r=s.records.find((item:any)=>item.id===t.recordId),playing=s.playingId===t.id;
  const albumSize='clamp(calc(220px * var(--tt-ui)), calc(var(--tt-cover) * 2), calc(260px * var(--tt-ui)))';
  return <Stack paddingInline={6} paddingBlock={1} gap={2}>
    <Stack direction="horizontal" gap={1} vAlign="center"><Action label="보관함으로 돌아가기" quiet width={76} onClick={back}><BackArrow/></Action><Heading level={2}>음악 상세</Heading></Stack>
    <Stack direction="horizontal" gap={6} vAlign="center" as="section" aria-label="음악 정보">
      <Thumbnail src={art(t.recordId)} alt="" label="고정 장식 표지" style={{width:albumSize,height:albumSize,flexShrink:0}}/>
      <StackItem size="fill"><Stack gap={3}><Heading level={2}>{trackLabel(t)}</Heading><Text type="supporting">{r.genre} · {r.lyrics}</Text><SourceNote snapshot={snapshot} provenance={t.provenance}/><Stack gap={1} paddingBlock={2}><Text type="supporting">{r.title}에서 만든 음악</Text><Text type="supporting">{r.date}</Text></Stack><Action label="원래 기록 보기" quiet width={176} disabled={!c.canSelect&&r.id!==s.selectedId} onClick={()=>open('record',r.id)}/></Stack></StackItem>
    </Stack>
    <Stack as="section" aria-label="재생 제어" direction="horizontal" hAlign="between" vAlign="center" minHeight="var(--tt-hit)" style={{borderTop:'1px solid var(--color-border)'}}>
      <Text type="supporting">{playing?'재생 중':'재생 대기'}</Text>
      <Button label={playing?'일시정지':'재생'} data-review-action={playing?'pause':'play'} data-review-value={t.id} variant="ghost" width="var(--tt-hit)" isDisabled={!playing&&!c.canPlay} onClick={()=>{if(playing||c.canPlay)send(playing?'pause':'play',t.id);}}><Stack hAlign="center" vAlign="center" style={{width:'calc(var(--tt-hit) - 12px * var(--tt-ui))',height:'calc(var(--tt-hit) - 12px * var(--tt-ui))',borderRadius:'50%',backgroundColor:'var(--color-accent)',color:'var(--color-background-surface)'}}><Icon icon={playing?glyphs.pause:glyphs.play} size="lg"/></Stack></Button>
      <Text type="supporting" hasTabularNumbers>{t.duration}</Text>
    </Stack>
  </Stack>;
}
