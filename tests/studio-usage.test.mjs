import fs from 'node:fs';import vm from 'node:vm';import assert from 'node:assert/strict';
const script=fs.readFileSync('assets/studio-usage.js','utf8');
async function inspect(frames,options={}){let calls=[];let context={URL,Map,AbortController,setTimeout,clearTimeout,location:{origin:'https://studio.stepx.com',pathname:options.path||'/builder/app/p_test'},fetch:async path=>{calls.push(path);return{ok:!options.error,status:options.error||200,json:async()=>path==='/api/projects/p_test'?{project:{name:'fixture'}}:path.endsWith('/sessions')?{sessions:[{id:'s_test'}]}:{frames,hasMore:false}}}};vm.createContext(context);let result=await vm.runInContext(script+';neoInspectStudioUsage()',context);return{result,calls}}
let {result}=await inspect([{payload:{subagentUpdate:{subagentId:'x',totalTokens:'0'}}}]);assert.equal(result.status,'unavailable');assert.equal(result.zeroEntries,1);assert.equal(result.reportedSubagentTokens,0);
({result}=await inspect([1,2,3].map(n=>({turnId:'turn',payload:{subagentUpdate:{subagentId:'x',totalTokens:String(n*100)}}}))));assert.equal(result.reportedSubagentTokens,300);assert.equal(result.reportedSubagents,1);assert.equal(result.status,'partial');
({result}=await inspect([{payload:{messageAppend:{text:'usage totalTokens 9000'}}}]));assert.equal(result.reportedSubagentTokens,0);
let r=await inspect([],{path:'/'});assert.equal(r.result.status,'project_required');assert.equal(r.calls.length,0);
({result}=await inspect([],{error:401}));assert.equal(result.status,'error');assert.match(result.message,/登录/);
({result}=await inspect([{payload:{subagentUpdate:{subagentId:'x',totalTokens:'-7'}}}]));assert.equal(result.reportedSubagentTokens,0);
console.log('PASS: zero is unknown; cumulative de-duplication; no text estimates; origin/project scope; HTTP error; invalid counts');
