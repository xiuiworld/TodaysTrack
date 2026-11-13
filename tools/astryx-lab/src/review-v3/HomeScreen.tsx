import {Stack,StackItem} from '@astryxdesign/core/Stack';
import {Heading} from '@astryxdesign/core/Heading';
import {Text} from '@astryxdesign/core/Text';
import {Button} from '@astryxdesign/core/Button';
import {Icon} from '@astryxdesign/core/Icon';
import {Thumbnail} from '@astryxdesign/core/Thumbnail';
import {Action,controls,selectedRecord,tracks,trackLabel,art,send,uiScale,glyphs,type ScreenProps} from './shared';
import {TransferNotice} from './Chrome';
export function HomeScreen({snapshot,open}:ScreenProps){
  const {state:s,config}=snapshot,r=selectedRecord(s),c=controls(s),all=tracks(s),ui=uiScale(config);
  const music=all.find((t:any)=>t.id===s.playingId)||all.find((t:any)=>t.recordId===r?.id)||all[0],latest=s.records[0];
  const mode=s.capturing?'capture':r?.intentId&&r.state!=='AVAILABLE'?'job':r&&!r.open&&!r.intentId?'generate':'start';
  const settings=mode==='start'?s.defaults:r;
  const primary=mode==='capture'?['기록 마무리','finish',c.canStop]:mode==='job'?['기존 작업 확인','generate',c.canGenerate]:mode==='generate'?['음악 만들기','generate',c.canGenerate]:['새 기록 시작','start',c.canStart];
  const title=mode==='capture'?'기록 중':mode==='job'?(r.state==='ACCEPTANCE_UNCERTAIN'?'접수 확인 중':r.state==='RECONCILING'?'작업 확인 필요':'생성 대기'):mode==='generate'?'기록을 음악으로':'새 기록';
  return <Stack height="100%" paddingInline={6} paddingBlock={3} gap={2} style={{gap:'var(--tt-gap)'}}>
    <Stack direction="horizontal" gap={6} width="100%" vAlign="center" style={{flex:'1 0 auto',minHeight:220*ui+'px'}}>
      <StackItem size="fill"><Stack gap={3}><Heading level={2}>{title}</Heading>
        {mode==='start'?<Text type="supporting">{settings.kind} · {settings.genre} · {settings.lyrics}</Text>:mode==='capture'?<><Text hasTabularNumbers>연속 관측 거리 {r.distance} km</Text><Text type="supporting">{r.kind} · {r.genre} · {r.lyrics}</Text></>:<><Text type="supporting">{r.date}</Text><Text type="supporting">{mode==='job'?'기록과 입력을 보관하며, 원래 작업만 확인합니다.':'연속 관측 거리 '+r.distance+' km · '+r.genre+' · '+r.lyrics}</Text>{r.intentId&&<Text type="supporting">{s.provenance==='FIXTURE'?'대역 · 새 AI 생성 아님':'새 AI 음악 · 검토 예시'}</Text>}</>}
        <Stack direction="horizontal" gap={2} wrap="wrap"><Action label={primary[0] as string} action={primary[1] as string} primary disabled={!primary[2]} width={224}/>{mode==='capture'?<Action label="화면 닫기" action="close" width={132}/>:mode!=='start'&&<Action label="기록 상세" width={132} onClick={()=>open('record',r.id)}/>}</Stack>
        {!c.allowed&&<Text type="supporting">{s.ux==='unknown'?'차량 조작 제한 확인 중':'현재 조작이 제한되어 있습니다'}</Text>}
      </Stack></StackItem>
      <StackItem size="fill"><Stack gap={3} style={{borderInlineStart:'1px solid var(--color-border)',paddingInlineStart:'var(--tt-gap)'}}><Heading level={3}>음악</Heading>
        {music?<><Stack direction="horizontal" gap={3} vAlign="center"><Thumbnail src={art(music.recordId)} alt="" label="고정 장식 표지" style={{width:'var(--tt-cover)',height:'var(--tt-cover)',flexShrink:0}}/><StackItem size="fill"><Stack gap={1}><Text type="label" maxLines={2}>{trackLabel(music)}</Text><Text type="supporting">{s.records.find((item:any)=>item.id===music.recordId)?.date}</Text><Text type="supporting">{music.provenance==='FIXTURE'?'대역 · 새 AI 생성 아님':'새 AI 음악 · 검토 예시'}</Text></Stack></StackItem></Stack>
          <Stack direction="horizontal" gap={2} vAlign="center"><Action label={s.playingId?'일시정지':'음악 듣기'} action={s.playingId?'pause':'play'} value={music.id} disabled={s.playingId?false:!c.canPlay} width={200}/><Text type="supporting" hasTabularNumbers>{s.playingId?'재생 중 · ':''}{music.duration}</Text></Stack>
        </>:<Stack gap={2} minHeight={120*ui} vAlign="center"><Icon icon={glyphs.library} size="lg" color="secondary"/><Text>저장된 음악이 없어요</Text><Text type="supporting">기록을 마친 뒤 음악을 만들 수 있습니다.</Text></Stack>}
      </Stack></StackItem>
    </Stack>
    <Button label="최근 기록 보기" data-review-action="recent" variant="ghost" width="100%" onClick={()=>latest?open('record',latest.id):send('page','records')}><Stack direction="horizontal" gap={4} vAlign="center" width="100%" minHeight="var(--tt-hit)" style={{position:'absolute',inset:0,borderBlockStart:'1px solid var(--color-border)',textAlign:'start',whiteSpace:'normal'}}><Text type="label">최근 기록</Text><StackItem size="fill"><Stack gap={0}><Text type="label" maxLines={1}>{latest?latest.title+(latest.open?' · 진행 중':''):'아직 기록이 없어요'}</Text>{latest&&<Text type="supporting">{latest.date} · {latest.distance} km</Text>}</Stack></StackItem><Icon icon="chevronRight" size="sm"/></Stack></Button>
    <TransferNotice/>
  </Stack>;
}
