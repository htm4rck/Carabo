import {createServer} from 'node:http';
import {readFile,stat} from 'node:fs/promises';
import path from 'node:path';
const settings=JSON.parse(await readFile('architecture-test.config.json','utf8'));
const root=path.resolve(settings.output),prefix=settings.baseHref;
const types={'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.woff':'font/woff','.woff2':'font/woff2','.png':'image/png','.jpg':'image/jpeg','.webp':'image/webp','.ico':'image/x-icon','.mp4':'video/mp4'};
createServer(async(req,res)=>{try{
 const pathname=decodeURIComponent(new URL(req.url,'http://127.0.0.1').pathname);
 if(pathname==='/'&&prefix!=='/'){res.writeHead(302,{Location:prefix}).end();return;}
 if(!pathname.startsWith(prefix)||/^\/(api|auth|setup|business|operation)(\/|$)/.test(pathname)){res.writeHead(404,{'Content-Type':'application/json'}).end('{}');return;}
 let file=path.resolve(root,pathname.slice(prefix.length));
 if(file!==root&&!file.startsWith(root+path.sep)){res.writeHead(404).end();return;}
 const exists=await stat(file).catch(()=>null);
 if(!exists?.isFile()){if(pathname!==prefix&&!req.headers.accept?.includes('text/html')){res.writeHead(404).end();return;}file=path.join(root,'index.html');}
 res.writeHead(200,{'Content-Type':types[path.extname(file)]??'application/octet-stream','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(await readFile(file));
}catch{res.writeHead(500).end();}}).listen(settings.port,'127.0.0.1',()=>console.log(settings.app+' production build fixture ready'));
