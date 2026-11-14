import {build} from 'esbuild';
import {mkdir,copyFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
process.chdir(fileURLToPath(new URL('.',import.meta.url)));
await mkdir('.local/home-review',{recursive:true});
await build({entryPoints:['src/home-review.tsx'],bundle:true,outfile:'.local/home-review/home.js',jsx:'automatic',format:'esm',platform:'browser',target:['es2022'],define:{'process.env.NODE_ENV':'"production"'},minify:true,loader:{'.woff':'file','.woff2':'file','.ttf':'file','.svg':'file'},logLevel:'info'});
await copyFile('home-review.html','.local/home-review/index.html');
await mkdir('.local/home-review-v1',{recursive:true});
await build({entryPoints:['src/home-review-v1.tsx'],bundle:true,outfile:'.local/home-review-v1/home.js',jsx:'automatic',format:'esm',platform:'browser',target:['es2022'],define:{'process.env.NODE_ENV':'"production"'},minify:true,logLevel:'info'});
await copyFile('home-review.html','.local/home-review-v1/index.html');
await mkdir('.local/home-review-v2',{recursive:true});
await build({entryPoints:['src/home-review-v2.tsx'],bundle:true,outfile:'.local/home-review-v2/home.js',jsx:'automatic',format:'esm',platform:'browser',target:['es2022'],define:{'process.env.NODE_ENV':'"production"'},minify:true,logLevel:'info'});
await copyFile('home-review.html','.local/home-review-v2/index.html');
await mkdir('.local/app-review-v3',{recursive:true});
await build({entryPoints:['src/app-review-v3.tsx'],bundle:true,outfile:'.local/app-review-v3/home.js',jsx:'automatic',format:'esm',platform:'browser',target:['es2022'],define:{'process.env.NODE_ENV':'"production"'},minify:true,logLevel:'info'});
await copyFile('home-review.html','.local/app-review-v3/index.html');

await mkdir('.local/app-review-v4',{recursive:true});
await build({entryPoints:['src/app-review-v4.tsx'],bundle:true,outfile:'.local/app-review-v4/home.js',jsx:'automatic',format:'esm',platform:'browser',target:['es2022'],define:{'process.env.NODE_ENV':'"production"'},minify:true,logLevel:'info'});
await copyFile('home-review.html','.local/app-review-v4/index.html');

await mkdir('.local/app-review-v5',{recursive:true});
await build({entryPoints:['src/app-review-v5.tsx'],bundle:true,outfile:'.local/app-review-v5/home.js',jsx:'automatic',format:'esm',platform:'browser',target:['es2022'],define:{'process.env.NODE_ENV':'"production"'},minify:true,logLevel:'info'});
await copyFile('home-review.html','.local/app-review-v5/index.html');

await mkdir('.local/app-review-v6',{recursive:true});
await build({entryPoints:['src/app-review-v6.tsx'],bundle:true,outfile:'.local/app-review-v6/home.js',jsx:'automatic',format:'esm',platform:'browser',target:['es2022'],define:{'process.env.NODE_ENV':'"production"'},minify:true,logLevel:'info'});
await copyFile('home-review.html','.local/app-review-v6/index.html');

await mkdir('.local/app-review-v7',{recursive:true});
await build({entryPoints:['src/app-review-v7.tsx'],bundle:true,outfile:'.local/app-review-v7/home.js',jsx:'automatic',format:'esm',platform:'browser',target:['es2022'],define:{'process.env.NODE_ENV':'"production"'},minify:true,logLevel:'info'});
await copyFile('home-review.html','.local/app-review-v7/index.html');

await mkdir('.local/app-review-v8',{recursive:true});
await build({entryPoints:['src/app-review-v8.tsx'],bundle:true,outfile:'.local/app-review-v8/home.js',jsx:'automatic',format:'esm',platform:'browser',target:['es2022'],define:{'process.env.NODE_ENV':'"production"'},minify:true,logLevel:'info'});
await copyFile('home-review.html','.local/app-review-v8/index.html');

await mkdir('.local/app-review-v9',{recursive:true});
await build({entryPoints:['src/app-review-v9.tsx'],bundle:true,outfile:'.local/app-review-v9/home.js',jsx:'automatic',format:'esm',platform:'browser',target:['es2022'],define:{'process.env.NODE_ENV':'"production"'},minify:true,logLevel:'info'});
await copyFile('home-review.html','.local/app-review-v9/index.html');

await mkdir('.local/app-review-v10',{recursive:true});
await build({entryPoints:['src/app-review-v10.tsx'],bundle:true,outfile:'.local/app-review-v10/home.js',jsx:'automatic',format:'esm',platform:'browser',target:['es2022'],define:{'process.env.NODE_ENV':'"production"'},minify:true,logLevel:'info'});
await copyFile('home-review.html','.local/app-review-v10/index.html');

await mkdir('.local/app-review-v11',{recursive:true});
await build({entryPoints:['src/app-review-v11.tsx'],bundle:true,outfile:'.local/app-review-v11/home.js',jsx:'automatic',format:'esm',platform:'browser',target:['es2022'],define:{'process.env.NODE_ENV':'"production"'},minify:true,logLevel:'info'});
await copyFile('home-review.html','.local/app-review-v11/index.html');
