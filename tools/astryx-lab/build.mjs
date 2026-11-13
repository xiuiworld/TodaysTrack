import {build} from 'esbuild';
import {mkdir, copyFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
process.chdir(fileURLToPath(new URL('.', import.meta.url)));
await mkdir('.local/dist', {recursive:true});
await build({
  entryPoints:['src/main.tsx'], bundle:true, outfile:'.local/dist/app.js',
  jsx:'automatic', format:'esm', platform:'browser', target:['es2022'],
  define:{'process.env.NODE_ENV':'"production"'}, minify:true,
  loader:{'.woff':'file','.woff2':'file','.ttf':'file','.svg':'file'},
  logLevel:'info'
});
await copyFile('index.html','.local/dist/index.html');
