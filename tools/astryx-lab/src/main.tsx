import {useState} from 'react';
import {createRoot} from 'react-dom/client';
import {Theme} from '@astryxdesign/core/theme';
import {neutralTheme} from '@astryxdesign/theme-neutral/built';
import {Stack} from '@astryxdesign/core/Stack';
import {Heading} from '@astryxdesign/core/Heading';
import {Text} from '@astryxdesign/core/Text';
import {Button} from '@astryxdesign/core/Button';
import '@astryxdesign/core/reset.css';
import '@astryxdesign/core/astryx.css';
import '@astryxdesign/theme-neutral/theme.css';
import ThemeShowcase from './showcase';

function App() {
  const [mode, setMode] = useState<'light' | 'dark'>('light');
  return (
    <Theme theme={neutralTheme} mode={mode}>
      <Stack as="main" direction="vertical" gap={6} padding={8} minHeight="100vh" style={{backgroundColor:'var(--color-background-body)'}}>
        <Stack direction="vertical" gap={2}>
          <Text type="supporting">TODAY’S TRACK / DESIGN TOOLS / ASTRYX 0.7.15</Text>
          <Heading level={1}>Astryx를 직접 눌러보세요.</Heading>
          <Text color="secondary">React 컴포넌트와 테마의 실습 화면입니다. 오늘의 트랙 디자인 적용안이 아닙니다.</Text>
        </Stack>
        <Stack direction="horizontal" gap={3} wrap="wrap">
          <Button label={mode === 'light' ? '전체 화면 어둡게' : '전체 화면 밝게'} onClick={() => setMode(value => value === 'light' ? 'dark' : 'light')} />
          <Button label="준비 완료 · 비활성 버튼 예시" isDisabled />
        </Stack>
        <Stack direction="vertical" gap={3}>
          <Heading level={2}>같은 구조, 두 가지 표시 모드</Heading>
          <Text type="supporting">Astryx의 ThemeShowcase 블록에서 시작했습니다. 아래 두 예제는 서로 독립적으로 동작합니다.</Text>
          <ThemeShowcase />
        </Stack>
        <Stack direction="vertical" gap={3}>
          <Heading level={2}>이렇게 요청하면 됩니다</Heading>
          <Text>“Astryx로 기록 목록을 더 간결하게 보여줘.”</Text>
          <Text>“같은 구조로 밝은 안과 어두운 안을 비교해줘.”</Text>
          <Text>“터치 영역은 76 이상 유지하고 보이는 버튼만 줄여줘.”</Text>
          <Text color="secondary">요청을 받으면 CLI로 문서·템플릿을 확인하고 별도 수정안을 만듭니다. 현재 검토실의 적용·비교 흐름과 실제 Android Views 반영은 별도 단계입니다.</Text>
        </Stack>
        <Stack direction="vertical" gap={2}>
          <Heading level={2}>직접 찾는 명령</Heading>
          <Text type="code">npm run astryx -- search button</Text>
          <Text type="code">npm run astryx -- component Button</Text>
          <Text type="code">npm run astryx -- template --list</Text>
          <Text type="code">npm run astryx -- docs tokens</Text>
          <Text type="supporting">위 명령은 tools/astryx-lab 폴더에서 실행합니다.</Text>
        </Stack>
        <Text type="supporting">일반 웹 컴포넌트 실습이며 차량용 터치 프레임·CarUx·SDK·실제 기록·음악·접근성을 검증하지 않습니다. 외부 글꼴/CDN/API를 사용하지 않습니다.</Text>
      </Stack>
    </Theme>
  );
}
createRoot(document.getElementById('root')!).render(<App />);
