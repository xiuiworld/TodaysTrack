import {Stack,StackItem} from '@astryxdesign/core/Stack';
import {Text} from '@astryxdesign/core/Text';
import {Button} from '@astryxdesign/core/Button';
import {Icon} from '@astryxdesign/core/Icon';
import {durationSeconds,positionFor} from '../../../ui-review/model.mjs';
import {channel,controls,glyphs,send,type Snapshot} from './shared';
export const clockLabel=(value:number)=>{const seconds=Math.max(0,Math.floor(value));return Math.floor(seconds/60)+':'+String(seconds%60).padStart(2,'0');};
export function PlayButton({snapshot,track,label}:{snapshot:Snapshot;track:any;label?:string}){
  const s=snapshot.state,playing=s.playingId===track.id,c=controls(s);
  return <Button label={label||(playing?'일시정지':'재생')} aria-pressed={playing} data-compact-action="true" data-review-action={playing?'pause':'play'} data-review-value={track.id} variant="ghost" width="var(--tt-hit)" style={{borderRadius:'50%',backgroundColor:'transparent',backgroundImage:'none',flexShrink:0}} isDisabled={!playing&&!c.canPlay} onClick={()=>{if(playing||c.canPlay)send(playing?'pause':'play',track.id);}}><Stack hAlign="center" vAlign="center" data-play-disc="true" style={{width:'calc(44px * var(--tt-ui))',height:'calc(44px * var(--tt-ui))',borderRadius:'50%',backgroundColor:'var(--tt-player)',color:'var(--color-background-surface)'}}><Icon icon={playing?glyphs.pause:glyphs.play} size="lg"/></Stack></Button>;
}
export function PlaybackControls({snapshot,track}:{snapshot:Snapshot;track:any}){
  const duration=durationSeconds(track),position=Math.min(duration,Math.floor(positionFor(snapshot.state,track.id))),c=controls(snapshot.state);
  return <Stack as="section" aria-label="재생 제어" direction="horizontal" gap={3} vAlign="start" minHeight="var(--tt-hit)">
    <PlayButton snapshot={snapshot} track={track}/>
    <StackItem size="fill"><Stack style={{position:'relative',height:'var(--tt-hit)'}}>
      <input type="range" aria-label="재생 위치" aria-valuetext={clockLabel(position)+' / '+track.duration} min="0" max={duration} step="1" value={position} disabled={!c.canPlay} onChange={event=>window.parent.postMessage({channel,type:'seek',trackId:track.id,position:Number(event.target.value)},location.origin)} style={{width:'100%',height:'var(--tt-hit)',minHeight:'var(--tt-hit)',minWidth:0,margin:0,accentColor:'var(--tt-player)',background:'transparent',cursor:c.canPlay?'pointer':'default'}}/>
      <Stack direction="horizontal" hAlign="between" style={{position:'absolute',insetInline:0,bottom:0,pointerEvents:'none'}}><Text type="supporting" hasTabularNumbers>{clockLabel(position)}</Text><Text type="supporting" hasTabularNumbers>{track.duration}</Text></Stack>
    </Stack></StackItem>
  </Stack>;
}
