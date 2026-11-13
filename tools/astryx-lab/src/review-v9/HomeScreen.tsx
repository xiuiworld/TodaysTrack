import {Stack,StackItem} from '@astryxdesign/core/Stack';
import {Heading} from '@astryxdesign/core/Heading';
import {Text} from '@astryxdesign/core/Text';
import {Button} from '@astryxdesign/core/Button';
import {Icon} from '@astryxdesign/core/Icon';
import {Thumbnail} from '@astryxdesign/core/Thumbnail';
import {Action,controls,selectedRecord,tracks,trackLabel,art,send,uiScale,glyphs,type ScreenProps} from './shared';
import {SourceNote} from './MusicSection';
import {PlaybackControls} from './PlaybackControls';
export function HomeScreen({snapshot,open}:ScreenProps){
  const {state:s,config}=snapshot,r=selectedRecord(s),c=controls(s),all=tracks(s),ui=uiScale(config);
  const music=all.find((t:any)=>t.id===(s.playingId||s.playback?.trackId))||all.find((t:any)=>t.recordId===r?.id)||all[0],latest=s.records[0];
  const mode=s.capturing?'capture':r?.intentId&&r.state!=='AVAILABLE'?'job':r?.state==='AVAILABLE'?'saved':r&&!r.open&&!r.intentId?'generate':'start';
  const settings=mode==='start'?s.defaults:r;
  const primary=mode==='capture'?['기록 마감','finish',c.canStop]:mode==='job'?['기존 작업 확인','generate',c.canGenerate]:mode==='saved'?['보관함으로','page',true]:mode==='generate'?['음악 만들기','generate',c.canGenerate]:['새 기록 시작','start',c.canStart];
  const title=mode==='capture'?'기록 중':mode==='job'?(r.state==='ACCEPTANCE_UNCERTAIN'?'접수 확인 중':r.state==='RECONCILING'?'작업 확인 필요':'생성 대기'):mode==='saved'?'음악 저장 완료':mode==='generate'?'기록 마감 완료':'새 기록';
  return <Stack height="100%" paddingInline={6} paddingBlock={3} gap={2} style={{gap:'var(--tt-gap)'}}>
    <Stack direction="horizontal" gap={6} width="100%" vAlign="center" style={{flex:'1 0 auto',minHeight:220*ui+'px'}}>
      <StackItem size="fill"><Stack gap={3}><Heading level={2}>{title}</Heading>
        {mode==='start'?<Text type="supporting">{settings.kind} · {settings.genre} · {settings.lyrics}</Text>:mode==='capture'?<><Text hasTabularNumbers>연속 관측 거리 {r.distance} km</Text><Text type="supporting">{r.kind} · {r.genre} · {r.lyrics}</Text></>:<><Text type="supporting">{r.date}</Text><Text type="supporting">{mode==='job'?'기록과 입력을 보관하며, 원래 작업만 확인합니다.':'연속 관측 거리 '+r.distance+' km · '+r.genre+' · '+r.lyrics}</Text>{r.intentId&&<SourceNote snapshot={snapshot} provenance={s.provenance}/>}</>}
        <Stack direction="horizontal" gap={2} wrap="wrap"><Action label={primary[0] as string} action={primary[1] as string} value={mode==='saved'?'library':undefined} onClick={mode==='generate'?()=>open('generate',r.id):undefined} primary disabled={!primary[2]} width={224}/>{mode==='capture'?<Action label="화면 닫기" action="close" width={132}/>:mode==='saved'?<Action label="새 기록 시작" action="start" disabled={!c.canStart} width={176}/>:mode!=='start'&&<Action label="기록 상세" width={132} onClick={()=>open('record',r.id)}/>}</Stack>
        {!c.allowed&&<Text type="supporting">{s.ux==='unknown'?'차량 조작 제한 확인 중':'현재 조작이 제한되어 있습니다'}</Text>}
      </Stack></StackItem>
      <StackItem size="fill"><Stack gap={3} style={{borderInlineStart:'1px solid var(--color-border)',paddingInlineStart:'var(--tt-gap)'}}><Heading level={3}>{music&&music.recordId!==r?.id?'이전에 만든 음악':'음악'}</Heading>
        {music?<><Stack direction="horizontal" gap={3} vAlign="center"><Thumbnail src={art(music.recordId)} alt="" label="고정 장식 표지" style={{width:'var(--tt-cover)',height:'var(--tt-cover)',flexShrink:0}}/><StackItem size="fill"><Stack gap={1}><Text type="label" maxLines={2}>{trackLabel(music)}</Text><Text type="supporting">{s.records.find((item:any)=>item.id===music.recordId)?.date}</Text><SourceNote snapshot={snapshot} provenance={music.provenance}/></Stack></StackItem></Stack>
          <PlaybackControls snapshot={snapshot} track={music}/>
        </>:<Stack gap={2} minHeight={120*ui} vAlign="center"><Icon icon={glyphs.library} size="lg" color="secondary"/><Text>저장된 음악이 없어요</Text><Text type="supporting">기록을 마친 뒤 음악을 만들 수 있습니다.</Text></Stack>}
      </Stack></StackItem>
    </Stack>
    <Button label="기록 상세 보기" data-review-action="recent" variant="ghost" width="100%" onClick={()=>latest?open('record',latest.id):send('page','records')}><Stack direction="horizontal" gap={4} vAlign="center" width="100%" minHeight="var(--tt-hit)" style={{position:'absolute',inset:0,borderBlockStart:'1px solid var(--color-border)',textAlign:'start',whiteSpace:'normal'}}><StackItem size="fill"><Stack gap={0}><Text type="label" maxLines={1}>{latest?latest.title+(latest.open?' · 진행 중':''):'아직 기록이 없어요'}</Text>{latest&&<Text type="supporting">{latest.date} · {latest.distance} km</Text>}</Stack></StackItem><Icon icon="chevronRight" size="sm"/></Stack></Button>
  </Stack>;
}
