import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const port = Number(process.env.UI_REVIEW_PORT || 4173);
const files = new Map([
  ['/', ['./index.html', 'text/html; charset=utf-8']],
  ...['review.css', 'app.mjs', 'model.mjs', 'candidate.json'].map(name => [`/${name}`, [`./${name}`, name.endsWith('css') ? 'text/css' : name.endsWith('json') ? 'application/json' : 'text/javascript']]),
  ...['concrete', 'curve', 'planes'].map(name => [`/assets/cover_${name}.png`, [`../../vehicle/app/src/main/res/drawable-nodpi/cover_${name}.png`, 'image/png']]),
  ...['index.html','home.js','home.css'].map(name=>[`/home-review/${name}`,[`../astryx-lab/.local/home-review/${name}`,name.endsWith('html')?'text/html; charset=utf-8':name.endsWith('css')?'text/css':'text/javascript']]),
  ...['index.html','home.js','home.css'].map(name=>[`/home-review/v1/${name}`,[`../astryx-lab/.local/home-review-v1/${name}`,name.endsWith('html')?'text/html; charset=utf-8':name.endsWith('css')?'text/css':'text/javascript']]),
  ...['index.html','home.js','home.css'].map(name=>[`/home-review/v2/${name}`,[`../astryx-lab/.local/home-review-v2/${name}`,name.endsWith('html')?'text/html; charset=utf-8':name.endsWith('css')?'text/css':'text/javascript']]),
  ...['index.html','home.js','home.css'].map(name=>[`/home-review/v3/${name}`,[`../astryx-lab/.local/app-review-v3/${name}`,name.endsWith('html')?'text/html; charset=utf-8':name.endsWith('css')?'text/css':'text/javascript']]),
  ...['index.html','home.js','home.css'].map(name=>[`/home-review/v4/${name}`,[`../astryx-lab/.local/app-review-v4/${name}`,name.endsWith('html')?'text/html; charset=utf-8':name.endsWith('css')?'text/css':'text/javascript']]),
  ...['index.html','home.js','home.css'].map(name=>[`/home-review/v5/${name}`,[`../astryx-lab/.local/app-review-v5/${name}`,name.endsWith('html')?'text/html; charset=utf-8':name.endsWith('css')?'text/css':'text/javascript']]),
  ...['index.html','home.js','home.css'].map(name=>[`/home-review/v6/${name}`,[`../astryx-lab/.local/app-review-v6/${name}`,name.endsWith('html')?'text/html; charset=utf-8':name.endsWith('css')?'text/css':'text/javascript']]),
  ...['index.html','home.js','home.css'].map(name=>[`/home-review/v7/${name}`,[`../astryx-lab/.local/app-review-v7/${name}`,name.endsWith('html')?'text/html; charset=utf-8':name.endsWith('css')?'text/css':'text/javascript']]),
  ...['index.html','home.js','home.css'].map(name=>[`/home-review/v8/${name}`,[`../astryx-lab/.local/app-review-v8/${name}`,name.endsWith('html')?'text/html; charset=utf-8':name.endsWith('css')?'text/css':'text/javascript']]),
  ...['index.html','home.js','home.css'].map(name=>[`/home-review/v9/${name}`,[`../astryx-lab/.local/app-review-v9/${name}`,name.endsWith('html')?'text/html; charset=utf-8':name.endsWith('css')?'text/css':'text/javascript']]),
  ...['index.html','home.js','home.css'].map(name=>[`/home-review/v10/${name}`,[`../astryx-lab/.local/app-review-v10/${name}`,name.endsWith('html')?'text/html; charset=utf-8':name.endsWith('css')?'text/css':'text/javascript']]),
  ...['index.html','home.js','home.css'].map(name=>[`/home-review/v11/${name}`,[`../astryx-lab/.local/app-review-v11/${name}`,name.endsWith('html')?'text/html; charset=utf-8':name.endsWith('css')?'text/css':'text/javascript']]),
  ...['index.html','app.mjs','model.mjs','review.css','candidate.json'].map(name=>[`/versions/white-v03/${name}`,[`./versions/white-v03/${name}`,name.endsWith('html')?'text/html; charset=utf-8':name.endsWith('css')?'text/css':name.endsWith('json')?'application/json':'text/javascript']])
]);
const server = http.createServer(async (req, res) => {
  if (!['GET', 'HEAD'].includes(req.method)) { res.writeHead(405, { Allow: 'GET, HEAD' }); res.end(); return; }
  // Exact allowlist: do not serve repository files, Room data, certificates or keys.
  const file = files.get(new URL(req.url, 'http://localhost').pathname);
  if (!file) { res.writeHead(404); res.end('Not found'); return; }
  try {
    const data = await readFile(fileURLToPath(new URL(file[0], import.meta.url)));
    const isHome=new URL(req.url,'http://localhost').pathname.startsWith('/home-review/');
    res.writeHead(200, { 'Content-Type': file[1], 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'Content-Security-Policy': `default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; font-src 'none'; frame-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors ${isHome?"'self'":"'none'"}`, 'Referrer-Policy': 'no-referrer' });
    res.end(req.method === 'HEAD' ? undefined : data);
  } catch { res.writeHead(500); res.end('Review asset unavailable'); }
});
server.listen(port, '127.0.0.1', () => console.log(`Today's Track UI review: http://127.0.0.1:${port}\nStatic review only. Ctrl+C to stop.`));
server.on('error', error => { console.error(`UI review server: ${error.code}`); process.exitCode = 1; });
