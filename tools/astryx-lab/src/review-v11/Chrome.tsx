import {Stack} from '@astryxdesign/core/Stack';
import {Button} from '@astryxdesign/core/Button';
import {Icon} from '@astryxdesign/core/Icon';
import {Text} from '@astryxdesign/core/Text';
import {Logo} from './Logo';
import {Action,controls,glyphs,send,type ScreenProps} from './shared';
export function Header({snapshot,open}:ScreenProps){const s=snapshot.state,c=controls(s);return <Stack as="header" direction="horizontal" paddingInline={6} minHeight="var(--tt-hit)" vAlign="center" hAlign="between" style={{flexShrink:0,borderBottom:'1px solid var(--color-border)'}}>
  <Logo/><Stack direction="horizontal" gap={2} vAlign="center"><Text type="supporting">{c.allowed?'정차 중':s.ux==='unknown'?'제한 확인 중':'조작 제한'}</Text><Stack direction="horizontal" gap={0} vAlign="center" style={{position:'relative'}}>
    <Stack aria-hidden="true" style={{position:'absolute',insetInline:'calc(12px * var(--tt-ui))',height:'var(--tt-visible-button)',border:'1px solid var(--color-border)',borderRadius:'var(--radius-element)',pointerEvents:'none'}}/>
    <Action label="앱 정보" quiet width={76} onClick={()=>open('info')}><Stack style={{transform:"translateX(calc(8px * var(--tt-ui)))"}}><Icon icon="info" size="lg"/></Stack></Action><Action label="새 기록 설정" quiet disabled={!c.canChangeSettings} width={76} onClick={()=>open('settings')}><Stack style={{transform:"translateX(calc(-8px * var(--tt-ui)))"}}><Icon icon={glyphs.settings} size="lg"/></Stack></Action>
  </Stack></Stack>
</Stack>;}
export function Navigation({page,onPage}:{page:string;onPage:(page:string)=>void}){return <Stack as="nav" direction="horizontal" minHeight="var(--tt-hit)" style={{flexShrink:0,borderTop:'1px solid var(--color-border)'}}>
  {([['home','홈'],['records','기록'],['library','보관함']] as const).map(([key,label])=><Button key={key} label={label} data-review-action="page" data-review-value={key} aria-current={page===key?'page':undefined} variant="ghost" width="33.333%" onClick={()=>{onPage(key);send('page',key);}}><Stack direction="horizontal" gap={2} width="100%" minHeight="var(--tt-hit)" hAlign="center" vAlign="center" style={{color:page===key?'var(--color-text-primary)':'var(--color-text-secondary)',borderTop:page===key?'2px solid var(--color-accent)':'2px solid transparent'}}><Icon icon={glyphs[key]} size="lg"/><Text type="label" color="inherit">{label}</Text></Stack></Button>)}
</Stack>;}
export function TransferNotice(){return <Text type="supporting">생성 시 정밀 위치를 뺀 요약을 생성 서버·음악 AI로 전송합니다.</Text>;}
