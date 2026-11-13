import type {CSSProperties,ReactNode,SVGProps} from 'react';
import {defineTheme} from '@astryxdesign/core/theme';
import {neutralTheme} from '@astryxdesign/theme-neutral/built';
import {Stack} from '@astryxdesign/core/Stack';
import {Button} from '@astryxdesign/core/Button';
import {Text} from '@astryxdesign/core/Text';
export {controls,selectedRecord,tracks} from '../../../ui-review/model.mjs';
export type Snapshot={state:any;config:any;hitboxes:boolean;showDiagnostics?:boolean;view?:{detailId:string|null;trackId?:string|null};isComparison?:boolean};
export type ScreenProps={snapshot:Snapshot;open:(kind:string,id?:string)=>void};
export const channel='todaystrack-home-v1';
export function send(action:string,value?:string){window.parent.postMessage({channel,type:'action',action,value},location.origin);}
export function art(id:string){let hash=0;for(let i=0;i<id.length;i++)hash=(Math.imul(31,hash)+id.charCodeAt(i))|0;return `/assets/cover_${['concrete','curve','planes'][((hash%3)+3)%3]}.png`;}
export const trackLabel=(track:any)=>track.title.replace(/\s*· 검토 예시$/,'');
export const uiScale=(config:any)=>config.width>=1400?1.5:1;
export const frameStyle=(config:any):CSSProperties=>{
  const ui=uiScale(config);
  return {'--tt-ui':ui,'--tt-hit':76*ui+'px','--tt-font':config.font*ui+'px','--tt-visible-button':config.button*ui+'px','--tt-cover':config.cover*ui+'px','--tt-gap':config.gap*ui+'px',fontFamily:'system-ui, "Malgun Gothic", sans-serif',backgroundColor:'var(--color-background-surface)',color:'var(--color-text-primary)'} as CSSProperties;
};
const palettes={porcelain:'#252a32',warm:'#695243',ink:'#314b72'};
export const themes=Object.fromEntries(Object.entries(palettes).map(([name,accent])=>[name,defineTheme({
  name:`todaystrack-app-v8-${name}`,extends:neutralTheme,
  tokens:{'--color-accent':accent,'--color-background-body':'#ffffff','--color-background-surface':'#ffffff','--color-background-card':'#ffffff','--color-text-primary':'#24282f','--color-text-secondary':'#59616b','--color-border':'#e2e5e9','--radius-container':'12px','--radius-element':'10px'},
  components:{
    button:{base:{minHeight:'var(--tt-hit, 76px)',minWidth:'var(--tt-hit, 76px)',padding:'0',border:'0',backgroundColor:'transparent',borderRadius:'12px',boxShadow:'none',height:'auto',whiteSpace:'normal'}},
    text:{base:{fontFamily:'system-ui, "Malgun Gothic", sans-serif',fontSize:'var(--tt-font)',lineHeight:'1.45'},'type:supporting':{fontSize:'calc(var(--tt-font) - 4px)'},'type:label':{fontSize:'calc(var(--tt-font) - 2px)',fontWeight:'600'}},
    heading:{base:{fontFamily:'system-ui, "Malgun Gothic", sans-serif',lineHeight:'1.3',textWrap:'balance',letterSpacing:'-.025em'},'level:1':{fontSize:'calc(var(--tt-font) * 1.2)'},'level:2':{fontSize:'calc(var(--tt-font) * 1.4)'},'level:3':{fontSize:'var(--tt-font)'}},
    thumbnail:{base:{borderRadius:'10px',outline:'1px solid var(--color-border)',outlineOffset:'-1px'}}
  }
})]));
const paths={home:'M3 10 12 3 21 10v11h-6v-7H9v7H3z',records:'M5 3h14v18H5z M8 8h8 M8 12h8 M8 16h8',library:'M8 18V5l12-2v13 M8 18c0 4-6 4-6 0s6-4 6 0 M20 16c0 4-6 4-6 0s6-4 6 0',play:'m9 5 11 7-11 7z',pause:'M8 5v14 M16 5v14'};
export const glyphs={...Object.fromEntries(Object.entries(paths).map(([name,path])=>[name,function Glyph(props:SVGProps<SVGSVGElement>){return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...props}><path d={path}/></svg>;} ])),settings:function SettingsGlyph(props:SVGProps<SVGSVGElement>){return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" {...props}><path d="m9 3-.6 2.2-2 .9-2-.7-2 3.4L4 10.4v2.3l-1.6 1.6 2 3.4 2-.7 2 .9.6 2.1h4l.6-2.1 2-.9 2 .7 2-3.4-1.6-1.6v-2.3l1.6-1.6-2-3.4-2 .7-2-.9L13 3z"/><circle cx="11" cy="11.5" r="3"/></svg>;}};
export function Action({label,action,value,primary=false,quiet=false,disabled=false,width=200,children,onClick}:{label:string;action?:string;value?:string;primary?:boolean;quiet?:boolean;disabled?:boolean;width?:number|string;children?:ReactNode;onClick?:()=>void}){
  return <Button label={label} data-review-action={action||label} data-review-value={value||''} variant="ghost" width={typeof width==='number'?`calc(${width}px * var(--tt-ui))`:width} isDisabled={disabled} onClick={()=>{if(!disabled){if(onClick)onClick();else if(action)send(action,value);}}}>
    <Stack direction="horizontal" gap={2} hAlign="center" vAlign="center" width={typeof width==='number'?`calc(${width-24}px * var(--tt-ui))`:'100%'} minHeight="var(--tt-visible-button)" style={{padding:'0 var(--spacing-2)',borderRadius:'var(--radius-element)',border:primary||quiet?'none':'1px solid var(--color-border)',backgroundColor:primary?'var(--color-accent)':quiet?'transparent':'var(--color-background-surface)',color:primary?'var(--color-background-surface)':'var(--color-text-primary)',whiteSpace:'normal'}}>{children||<Text type="label" color="inherit">{label}</Text>}</Stack>
  </Button>;
}
export function WorkLabel({state,record}:{state:any;record:any}){
  const label=record.open?(state.capturing?'수집 중':'마감 전'):record.state==='AVAILABLE'?'저장 완료':record.state==='ACCEPTANCE_UNCERTAIN'?'접수 확인 중':record.state==='RECONCILING'?'작업 확인 필요':record.intentId?'생성 대기':'기록 마감';
  return <Text type="supporting">{label}</Text>;
}
