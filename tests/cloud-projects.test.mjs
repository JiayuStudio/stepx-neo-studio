import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
const code=readFileSync(new URL('../assets/cloud-projects.js',import.meta.url),'utf8');
let data,puts,log,failPath,race,activity='completed';
const ctx={crypto:webcrypto,TextEncoder,TextDecoder,Uint8Array,URLSearchParams,AbortController,setTimeout,location:{origin:'https://studio.stepx.com'},atob,btoa,fetch:async(url,opts={})=>{
 log.push({url,method:opts.method||'GET'});if(opts.signal?.aborted)throw Error('aborted');
 if(url==='/api/projects/activity')return Response.json({items:[{projectId:'p_test',state:activity}]});
 if(url==='/api/projects')return Response.json({projects:[{id:'p_test',name:'Fixture',builderType:'app'}]});
 if(url.endsWith('/sessions'))return Response.json({sessions:[{id:'s_fixture'}]});
 if(url.endsWith('/workspace/command')){const q=JSON.parse(opts.body);assert.equal(q.command.action,'status');return Response.json({result:{requestId:q.command.requestId,error:'',status:{busy:false}}})}
 if(url.endsWith('/sandbox/ensure'))return Response.json({});
 if(url.endsWith('/files'))return new Response(['src/',...Object.keys(data)].join('\n'));
 if(url==='/api/projects/p_test')return Response.json({project:{id:'p_test',name:'Fixture',builderType:'app'}});
 const u=new URL(url,'https://studio.stepx.com'),p=u.searchParams.get('path');
 if(opts.method==='POST'){if(u.searchParams.get('kind')==='file')data[p]=new Uint8Array();return new Response('');}
 if(opts.method==='PUT'){if(p===failPath)return new Response('',{status:503});data[p]=new Uint8Array(opts.body);puts.push(p);return new Response('');}
 if(p===race){data[p]=new TextEncoder().encode('changed elsewhere');race=null;}
 return p in data?new Response(data[p]):new Response('',{status:404});
}};
vm.createContext(ctx);vm.runInContext(code+';globalThis.api=NeoCloud;',ctx);const api=ctx.api,encode=s=>new TextEncoder().encode(s);
function reset(){data={'index.html':encode('old'),'src/main.tsx':encode('source'),'image.bin':new Uint8Array([0,255,128,7]),'.env':encode('do-not-read')};puts=[];log=[];failPath=null;race=null;activity='completed';}
let passed=0;async function test(name,f){reset();await f();passed++;console.log('PASS',name)}
await test('downloads nested source and preserves binary bytes; excludes credentials',async()=>{const r=await api.run({action:'pull',projectId:'p_test'});assert.equal(r.entries['src/main.tsx'].content,'source');assert.deepEqual([...api.decode(r.entries['image.bin'])],[0,255,128,7]);assert.equal(r.entries['.env'],undefined);assert(!log.some(x=>x.url.includes('path=.env')));assert.equal(r.skipped[0],'.env')});
await test('new nested files and binary upload are read back',async()=>{const entries={'new/deep/a.txt':await api.pack(encode('hello')),'new.bin':await api.pack(new Uint8Array([0,254,3]))};const r=await api.run({action:'upload',projectId:'p_test',entries,expected:{}});assert(r.complete);assert.equal(puts.length,2);assert.deepEqual([...data['new.bin']],[0,254,3])});
await test('cloud change before PUT stops overwrite',async()=>{const entry=await api.pack(encode('wanted')),expected=await api.hash(data['index.html']);race='index.html';const r=await api.run({action:'upload',projectId:'p_test',entries:{'index.html':entry},expected:{'index.html':expected}});assert(!r.complete);assert.equal(puts.length,0)});
await test('partial failure reports only read-back-verified paths',async()=>{failPath='b.txt';const r=await api.run({action:'upload',projectId:'p_test',entries:{'a.txt':await api.pack(encode('a')),'b.txt':await api.pack(encode('b'))},expected:{}});assert(!r.complete);assert.deepEqual([...r.written],['a.txt']);assert(!log.some(x=>x.method==='DELETE'))});
await test('oversized cloud source is rejected before native import',async()=>{data['huge.txt']=new Uint8Array(1048577);await assert.rejects(api.run({action:'pull',projectId:'p_test'}),/1 MB/)});
await test('path traversal rejected',async()=>{data['../escape']=encode('bad');await assert.rejects(api.run({action:'pull',projectId:'p_test'}),/路径/);for(const p of ['/abs','a/../b','a\\b','a\nb\nc'])assert(!api.valid(p))});
await test('running cloud task blocks transfer',async()=>{activity='running';await assert.rejects(api.run({action:'pull',projectId:'p_test'}),/正在执行/)});
await test('hash mismatch rejects upload',async()=>{await assert.rejects(api.run({action:'upload',projectId:'p_test',entries:{'a.txt':{encoding:'utf8',content:'x',sha256:'wrong'}}}),/校验/);assert.equal(puts.length,0)});
await test('foreign origin and cancellation rejected',async()=>{ctx.location.origin='https://example.com';await assert.rejects(api.run({action:'list'}),/官方/);ctx.location.origin='https://studio.stepx.com';const c=new AbortController();c.abort();await assert.rejects(api.run({action:'list'},c.signal),/停止/)});
await test('new project uses only read-only workspace status to initialize',async()=>{const r=await api.run({action:'pull',projectId:'p_test',initialize:true});assert(r.entries['index.html']);assert(log.some(x=>x.url.endsWith('/workspace/command')));assert.equal(puts.length,0)});
console.log(`${passed} cloud transport checks passed`);
