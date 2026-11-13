import http from 'node:http';
import {readdir, readFile} from 'node:fs/promises';
const root = new URL('./.local/dist/', import.meta.url);
const allowed = new Set(await readdir(root));
const types={html:'text/html; charset=utf-8',js:'text/javascript',css:'text/css',woff:'font/woff',woff2:'font/woff2',ttf:'font/ttf',svg:'image/svg+xml'};
const server=http.createServer(async(req,res)=>{
  if(!['GET','HEAD'].includes(req.method)){res.writeHead(405,{Allow:'GET, HEAD'});res.end();return;}
  const path=new URL(req.url,'http://localhost').pathname;
  const name=path==='/'?'index.html':path.slice(1);
  if(!allowed.has(name)){res.writeHead(404);res.end('Not found');return;}
  try {
    const data=await readFile(new URL(name,root));
    res.writeHead(200,{'Content-Type':types[name.split('.').at(-1)]||'application/octet-stream','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",'Referrer-Policy':'no-referrer'});
    res.end(req.method==='HEAD'?undefined:data);
  } catch{res.writeHead(500);res.end('Local asset unavailable');}
});
server.listen(4174,'127.0.0.1',()=>console.log('Astryx lab: http://127.0.0.1:4174 · static localhost only · Ctrl+C to stop'));
server.on('error',error=>{console.error(error.code);process.exitCode=1;});
