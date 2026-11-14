import type {ReactNode} from 'react';
import {Stack,StackItem} from '@astryxdesign/core/Stack';
import {Heading} from '@astryxdesign/core/Heading';
import {Text} from '@astryxdesign/core/Text';
import {Icon} from '@astryxdesign/core/Icon';
import {Thumbnail} from '@astryxdesign/core/Thumbnail';
import {art,glyphs} from './shared';
export const collectionRowStyle={minHeight:'max(calc(var(--tt-hit) * 1.5), calc(var(--tt-cover) / 2 + var(--spacing-6)), calc(var(--tt-font) * 4.8 + var(--spacing-6)))',position:'relative' as const};
export function CollectionHeading({title,count,action}:{title:string;count:string;action?:ReactNode}){return <Stack direction="horizontal" hAlign="between" vAlign="center" minHeight="var(--tt-hit)"><Stack direction="horizontal" gap={3} vAlign="center"><Heading level={2}>{title}</Heading><Text type="supporting">{count}</Text></Stack>{action}</Stack>;}
export function CollectionTail({children}:{children:ReactNode}){return <Stack direction="horizontal" gap={2} width="calc(152px * var(--tt-ui))" minHeight="var(--tt-hit)" hAlign="end" vAlign="center" style={{flexShrink:0}}>{children}</Stack>;}
export function CollectionBody({recordId,hasCover=true,title,subtitle,note,tail}:{recordId:string;hasCover?:boolean;title:string;subtitle:string;note?:ReactNode;tail?:ReactNode}){
  return <Stack direction="horizontal" gap={4} paddingBlock={3} vAlign="center" width="100%" minHeight="var(--tt-hit)" style={{position:'absolute',inset:0,textAlign:'start',whiteSpace:'normal'}}>
    {hasCover?<Thumbnail src={art(recordId)} alt="" label="고정 장식 표지" style={{width:'calc(var(--tt-cover) / 2)',height:'calc(var(--tt-cover) / 2)',flexShrink:0}}/>:<Stack width="calc(var(--tt-cover) / 2)" minHeight="calc(var(--tt-cover) / 2)" hAlign="center" vAlign="center" style={{flexShrink:0,border:'1px solid var(--color-border)',borderRadius:'var(--radius-element)'}}><Icon icon={glyphs.records} size="lg" color="secondary"/></Stack>}
    <StackItem size="fill"><Stack gap={1} style={{minHeight:'calc(var(--tt-font) * 4.8)'}}><Text type="label" maxLines={1}>{title}</Text><Text type="supporting" maxLines={1}>{subtitle}</Text>{note}</Stack></StackItem>{tail}
  </Stack>;
}
