// Ignore formatting changes only; require the complete fixed scanner in a single heredoc.
function neoRuntimeCommandMatches(command,code){
 const m=command.trim().match(/^node\s+<<'NEO_USAGE_SCAN'\r?\n([\s\S]+)\r?\nNEO_USAGE_SCAN$/);if(!m)return false;
 const tokens=s=>(s.match(/"(?:\\[\s\S]|[^"\\])*"|'(?:\\[\s\S]|[^'\\])*'|\/\/[^\n]*|\/\*[\s\S]*?\*\/|[A-Za-z_$][\w$]*|\d+(?:\.\d+)?|===|!==|=>|\?\?|\?\.|\+\+|--|\+=|-=|&&|\|\||<=|>=|!=|==|[^\s]/g)||[]).filter(t=>!t.startsWith('//')&&!t.startsWith('/*'));
 return JSON.stringify(tokens(m[1]))===JSON.stringify(tokens(code));
}
// Observed same-origin Studio API and guarded visible composer; no credential access.
function neoValidateRuntimeReport(r,nonce,pid){
 if(!r||r.schema!==1||r.nonce!==nonce||r.projectId!==pid||r.source!=='/home/ide/state/h1/projects/-home-ide-workspace')throw Error('用量报告来源不匹配');
 if(r.status!=='ok')throw Error(r.status==='unavailable'?'当前运行环境没有已落盘的用量记录':'运行日志不可读：'+String(r.error||'未知原因').slice(0,100));
 if(!Number.isSafeInteger(r.generatedAt)||r.generatedAt<0||r.generatedAt>Date.now()+300000||!Array.isArray(r.groups)||!r.groups.length||r.groups.length>500)throw Error('用量报告格式异常');
 for(const k of ['files','rows','skipped'])if(!Number.isSafeInteger(r[k])||r[k]<0)throw Error('用量报告计数异常');
 const seen=new Set(),totals={requests:0,input:0,output:0,cacheRead:0,cacheWrite:0,total:0,first:Infinity,last:0};
 const groups=r.groups.map(g=>{
  if(typeof g.model!=='string'||g.model.length>100||!/^\d{4}-\d{2}-\d{2}$/.test(g.day)||seen.has(g.day+'\0'+g.model))throw Error('用量报告分组异常');seen.add(g.day+'\0'+g.model);
  const out={day:g.day,model:g.model};for(const k of ['requests','input','output','cacheRead','cacheWrite','first','last']){if(!Number.isSafeInteger(g[k])||g[k]<0)throw Error('用量数字异常');out[k]=g[k];}
  if(!g.requests||g.first>g.last||g.last>r.generatedAt+300000||new Date(g.first).toISOString().slice(0,10)!==g.day||new Date(g.last).toISOString().slice(0,10)!==g.day)throw Error('用量时间范围异常');
  for(const k of ['requests','input','output','cacheRead','cacheWrite']){totals[k]+=g[k];if(!Number.isSafeInteger(totals[k]))throw Error('用量数字超出范围');}
  totals.first=Math.min(totals.first,g.first);totals.last=Math.max(totals.last,g.last);return out;
 });
 totals.total=totals.input+totals.output+totals.cacheRead+totals.cacheWrite;if(!Number.isSafeInteger(totals.total))throw Error('用量数字超出范围');
 return {schema:1,projectId:pid,source:r.source,generatedAt:r.generatedAt,files:r.files,rows:r.rows,skipped:r.skipped,limited:!!r.limited,groups,totals};
}
async function neoRuntimeRead(path){
 const c=new AbortController(),timer=setTimeout(()=>c.abort(),12000);try{const r=await fetch(path,{method:'GET',credentials:'same-origin',cache:'no-store',signal:c.signal});if(!r.ok)throw Error(r.status===401||r.status===403?'登录失效或项目不可访问':'Studio 接口 HTTP '+r.status);return await r.json()}finally{clearTimeout(timer)}
}
async function neoCollectRuntime(op){
 const {pid,nonce,code,sessions}=op;const base='/api/projects/'+encodeURIComponent(pid);
 const frames=[];for(const s of sessions){let before='9007199254740991';for(let p=0;p<3;p++){
  const j=await neoRuntimeRead(base+'/history?beforeSeq='+before+'&sessionId='+encodeURIComponent(s.id));if(!Array.isArray(j.frames))throw Error('Studio 历史格式已变化');frames.push(...j.frames);
  if(!j.hasMore)break;const nums=j.frames.map(f=>Number(f.seq)).filter(Number.isSafeInteger);if(!nums.length)break;const next=String(Math.min(...nums));if(BigInt(next)>=BigInt(before))break;before=next;
 }}
 const ownTurns=new Set(frames.filter(f=>f.payload?.messageAppend&&JSON.stringify(f.payload.messageAppend).includes('[Neo Studio 项目用量 '+nonce+']')).map(f=>f.turnId));
 const calls=new Map();for(const f of frames){const t=f.payload?.toolUse;if(!t||t.name!=='Bash')continue;let input;try{input=JSON.parse(t.inputJson)}catch{continue}if(typeof input.command==='string'&&neoRuntimeCommandMatches(input.command,code))calls.set(f.turnId+'\0'+t.toolCallId,f.turnId);}
 for(const f of frames){const t=f.payload?.toolResult;if(!t||t.isError||!calls.has(f.turnId+'\0'+t.toolCallId)||typeof t.content!=='string')continue;
  const lines=t.content.split('\n').filter(l=>l.startsWith('NEO_STUDIO_USAGE_V1='));for(const line of lines){if(line.length>250000)throw Error('用量报告过大');const report=neoValidateRuntimeReport(JSON.parse(line.slice('NEO_STUDIO_USAGE_V1='.length)),nonce,pid);const done=frames.find(x=>x.turnId===f.turnId&&x.payload?.turnCompleted);if(done&&(done.payload.turnCompleted.hasCodeChanges||done.payload.turnCompleted.interrupted))throw Error('诊断回执含代码变更或被中断，请在 Studio 核对；旧报告保留');return done?{status:'runtime',report,turnId:f.turnId}:{status:'waiting'};}
 }
 if(frames.some(f=>f.payload?.turnCompleted&&([...calls.values()].includes(f.turnId)||ownTurns.has(f.turnId))))throw Error('诊断已结束，但没有有效工具用量报告；请查看 Studio 回执');
 return {status:'waiting',accepted:calls.size>0||ownTurns.size>0};
}
async function neoRunStudioUsage(op){
 const url=location.href,match=location.pathname.match(/^\/builder\/(app|agent|skill)\/([\w-]+)\/?$/);
 const delay=ms=>new Promise(r=>setTimeout(r,ms));
 const cancelled=()=>window.__neoRuntimeCancelled||window.__neoRuntimeId!==op.nonce;
 try{
  if(location.origin!=='https://studio.stepx.com'||!match)throw Error('先在 Studio 打开要统计的项目');
  const pid=match[2],base='/api/projects/'+pid;const info=await neoRuntimeRead(base);const raw=await neoRuntimeRead(base+'/sessions');
  if(!Array.isArray(raw.sessions)||!raw.sessions.length||raw.sessions.length>10)throw Error('会话数量或格式不支持本次读取');
  if(cancelled())return;
  const code=op.scanner.replaceAll('__NEO_NONCE__',op.nonce).replaceAll('__NEO_PROJECT__',pid).trim();
  const prompt='[Neo Studio 项目用量 '+op.nonce+']\n只做当前项目的只读用量诊断。请使用 Bash 原样执行下面完整命令，不修改脚本、不改任何文件、不构建、不部署、不读凭据或环境变量。不输出日志原文或其他项目内容。完成后简短回复；不要自行估算 Token。若命令失败，直接报告错误，不扩大读取范围。\n```sh\nnode <<\'NEO_USAGE_SCAN\'\n'+code+'\nNEO_USAGE_SCAN\n```';
  // Opening the collapsed panel mounts its composer/session asynchronously.
  // Wait for a stable visible conversation before inserting; never replace a draft.
  let signature='',stable=0;
  for(let i=0;i<12;i++){
   if(cancelled())return;if(location.href!==url)throw Error('Studio 项目已切换');
   const snap=studioLink({action:'snapshot',url});if(snap.error)throw Error(snap.error);
   if(!snap.retry){if(snap.busy||snap.draft)throw Error('Studio 正在生成或已有草稿，已保留，请处理后重试');const next=snap.users+':'+snap.turns;stable=next===signature?stable+1:0;signature=next;if(stable>=2)break;}
   await delay(900);
  }
  if(stable<2)throw Error('Studio 对话仍在加载，请稍后重试');
  let filled;for(let i=0;i<10;i++){if(cancelled())return;if(location.href!==url)throw Error('Studio 项目已切换');filled=studioLink({action:'fill',url,text:prompt});if(!filled.retry)break;await delay(700);}
  if(!filled?.filled)throw Error(filled?.error||'Studio 输入框尚未就绪');
  if(cancelled())throw Error('已停止，未发送的诊断草稿保留在 Studio');
  await delay(500);if(cancelled())return;
  const sent=studioLink({action:'submit',url,text:prompt});if(!sent.clicked)throw Error(sent.error||'诊断草稿未提交');
  window.__neoRuntimeProgress='已发送只读诊断，等待日志用量。保持前台；可在 Studio 查看进度。';
  const started=Date.now(),end=started+480000;let failures=0,accepted=false;
  while(Date.now()<end){await delay(5000);if(cancelled())return;if(location.href!==url)throw Error('Studio 项目已切换，已停止读取；已提交的诊断仍可能继续');
   let found;try{found=await neoCollectRuntime({pid,nonce:op.nonce,code,sessions:raw.sessions});failures=0;}catch(e){if(e.name==='AbortError'||/HTTP 5/.test(e.message)){if(++failures<4)continue}throw e;}
   accepted=accepted||found.accepted||found.status==='runtime';if(!accepted&&Date.now()-started>30000)throw Error('未确认诊断送达，请检查 Studio 网络和回执；没有自动重发，旧报告保留');
   if(found.status==='runtime'){found.report.projectName=String(info.project?.name||'当前项目').slice(0,80);return {...found,time:Date.now(),message:'已读取 SDK 日志中的真实用量；范围仅限该项目已落盘记录。'};}
  }
  throw Error('等待超时。诊断可能仍在 Studio 运行，请核对后再重试；旧报告保留。');
 }catch(e){return {status:'error',time:Date.now(),message:e.name==='AbortError'?'Studio 请求超时，请核对登录与网络':e.message==='Failed to fetch'?'Studio 网络请求失败，请稍后重试；不会重复提交诊断':e.message};}
}
