// Read-only adapter for the observed official project-history schema (2026-10-18).
// Never accesses cookies, credentials, conversation text or an API on another origin.
async function neoInspectStudioUsage(){
 const result={time:Date.now(),status:'unavailable',frames:0,sessions:0,zeroEntries:0,reportedSubagentTokens:0,reportedSubagents:0,limited:false};
 const match=location.pathname.match(/^\/builder\/(app|agent|skill)\/([\w-]+)\/?$/);
 if(location.origin!=='https://studio.stepx.com'||!match)return {...result,status:'project_required',message:'请先在 Studio 打开具体项目，再检查用量。'};
 result.projectId=match[2];const base='/api/projects/'+encodeURIComponent(match[2]);
 const read=async path=>{const c=new AbortController(),timer=setTimeout(()=>c.abort(),10000);try{const r=await fetch(path,{method:'GET',credentials:'same-origin',cache:'no-store',signal:c.signal});if(!r.ok)throw Error(r.status===401||r.status===403?'登录失效或当前项目不可访问':'官方接口返回 HTTP '+r.status);return await r.json()}finally{clearTimeout(timer)}};
 try{
  const info=await read(base);result.projectName=typeof info.project?.name==='string'?info.project.name.slice(0,80):'当前项目';
  const sessions=(await read(base+'/sessions')).sessions;if(!Array.isArray(sessions))throw Error('官方会话结构已变化');
  result.limited=sessions.length>10;const agents=new Map();
  for(const s of sessions.slice(0,10)){
   if(typeof s.id!=='string')throw Error('官方会话结构已变化');result.sessions++;let before='9007199254740991';
   for(let page=0;page<5;page++){
    const j=await read(base+'/history?beforeSeq='+before+'&sessionId='+encodeURIComponent(s.id));if(!Array.isArray(j.frames))throw Error('官方历史结构已变化');result.frames+=j.frames.length;
    for(const f of j.frames){const a=f.payload?.subagentUpdate;if(!a||typeof a.subagentId!=='string')continue;const n=Number(a.totalTokens);if(!Number.isSafeInteger(n)||n<0)continue;if(n===0)result.zeroEntries++;const k=s.id+':'+(f.turnId||'')+':'+a.subagentId;agents.set(k,Math.max(agents.get(k)||0,n));}
    if(!j.hasMore)break;const seq=j.frames.map(f=>Number(f.seq)).filter(n=>Number.isSafeInteger(n)&&n>=0);if(!seq.length||page===4||result.frames>10000){result.limited=true;break;}const next=String(Math.min(...seq));if(BigInt(next)>=BigInt(before)){result.limited=true;break}before=next;
   }
   if(result.frames>10000){result.limited=true;break}
  }
  for(const n of agents.values())if(n>0){result.reportedSubagentTokens+=n;result.reportedSubagents++;}
  result.status=result.reportedSubagents?'partial':'unavailable';
  result.message=result.reportedSubagents?'官方历史返回部分子任务 Token；主会话和账户总量仍未知。':'官方历史未提供主会话输入/输出 Token'+(result.zeroEntries?'；子任务 totalTokens 均为 0，不能确认为真实用量。':'，没有可确认的 Token 用量。');
 }catch(e){result.status='error';result.message=e.name==='AbortError'?'官方接口超时，请稍后重试':e.message;}
 return result;
}
