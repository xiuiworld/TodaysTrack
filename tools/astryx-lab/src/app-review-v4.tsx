import {createRoot} from 'react-dom/client';
import '@astryxdesign/core/reset.css';
import '@astryxdesign/core/astryx.css';
import '@astryxdesign/theme-neutral/theme.css';
import {ReviewApp} from './review-v4/ReviewApp';
createRoot(document.getElementById('root')!).render(<ReviewApp/>);
