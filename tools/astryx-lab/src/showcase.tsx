// Adapted from Astryx 0.7.15 ThemeShowcase template.
// Copyright (c) Meta Platforms, Inc. and affiliates. MIT licensed.
import {useState} from 'react';
import {Theme} from '@astryxdesign/core/theme';
import {neutralTheme} from '@astryxdesign/theme-neutral/built';
import {Card} from '@astryxdesign/core/Card';
import {Grid} from '@astryxdesign/core/Grid';
import {Section} from '@astryxdesign/core/Section';
import {Stack} from '@astryxdesign/core/Stack';
import {Heading} from '@astryxdesign/core/Heading';
import {Text} from '@astryxdesign/core/Text';
import {Button} from '@astryxdesign/core/Button';

function ThemeCard({label}: {label: string}) {
  const [clicks, setClicks] = useState(0);
  const [completed, setCompleted] = useState(0);
  return (
    <Card padding={4} width="100%">
      <Stack direction="vertical" gap={3}>
        <Heading level={3}>{label}</Heading>
        <Text color="secondary">같은 컴포넌트가 테마의 색·글꼴·모서리를 함께 사용합니다.</Text>
        <Stack direction="horizontal" gap={2} wrap="wrap">
          <Button label={`버튼 눌러보기 · ${clicks}`} variant="primary" onClick={() => setClicks(n => n + 1)} />
          <Button label="로딩 표시 확인" clickAction={async () => { await new Promise(resolve => setTimeout(resolve, 600)); setCompleted(n => n + 1); }} />
          <Button label="비활성 상태" isDisabled />
        </Stack>
        <Text type="supporting">버튼 {clicks}회 · 로딩 모의 완료 {completed}회. 통신·생성·저장은 없습니다.</Text>
      </Stack>
    </Card>
  );
}

export default function ThemeShowcase() {
  return (
    <Section variant="muted" padding={4} maxWidth={600}>
      <Grid columns={{minWidth: 240, repeat: 'fit'}} gap={3} width="100%">
        <Theme theme={neutralTheme} mode="light"><ThemeCard label="Neutral · 밝게" /></Theme>
        <Theme theme={neutralTheme} mode="dark"><ThemeCard label="Neutral · 어둡게" /></Theme>
      </Grid>
    </Section>
  );
}
