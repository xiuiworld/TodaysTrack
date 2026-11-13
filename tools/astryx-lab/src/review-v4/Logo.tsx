import type {SVGProps} from 'react';
import {Icon} from '@astryxdesign/core/Icon';
import {Stack} from '@astryxdesign/core/Stack';
import {Heading} from '@astryxdesign/core/Heading';
// Original vector mark: a t-shaped track, a rounded turn, and its endpoint.
export function TrackMark(props:SVGProps<SVGSVGElement>){return <svg viewBox="0 0 48 48" fill="none" {...props}><rect width="48" height="48" rx="12" fill="currentColor"/><path d="M13 17H35M23 10V29C23 35 27 38 33 38" stroke="#fff" strokeWidth="4.5" strokeLinecap="round" strokeLinejoin="round"/><circle cx="35" cy="38" r="2.7" fill="#fff"/></svg>;}
export function Logo(){return <Stack direction="horizontal" gap={3} vAlign="center"><Icon icon={TrackMark} size="lg" style={{width:'calc(36px * var(--tt-ui))',height:'calc(36px * var(--tt-ui))'}}/><Heading level={1}>오늘의 트랙</Heading></Stack>;}
