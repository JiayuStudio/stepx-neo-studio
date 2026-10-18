/* First-party Studio file API adapter. Runs only inside the signed-in official WebView. */
const NeoCloud = (() => {
 const MAX=4*1024*1024, FILE=1024*1024, COUNT=300;
 const valid=p=>typeof p==='string'&&p.length>0&&p.length<=240&&!/[\\\x00-\x1f\x7f]/.test(p)&&!p.startsWith('/')&&p.split('/').every(x=>x&&x!=='.'&&x!=='..');
 const excluded=p=>/(^|\/)(\.git|node_modules|\.gradle|build|dist|\.next|\.cache)(\/|$)/i.test(p)||/(^|\/)(\.env(?:\..*)?|credentials(?:\..*)?|secrets?(?:\..*)?|local\.properties|capsule\.properties|id_rsa|id_ed25519)$/i.test(p)||/\.(p12|pfx|jks|keystore|pem|key)$/i.test(p);
 const hash=async b=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',b)),x=>x.toString(16).padStart(2,'0')).join('');
 const decode=e=>e.encoding==='base64'?Uint8Array.from(atob(e.content),c=>c.charCodeAt(0)):new TextEncoder().encode(e.content);
 function base64(b){let s='';for(let i=0;i<b.length;i+=16384)s+=String.fromCharCode(...b.subarray(i,i+16384));return btoa(s)}
 async function pack(b){let content,encoding='utf8';try{content=new TextDecoder('utf-8',{fatal:true,ignoreBOM:true}).decode(b);if(content.includes('\0'))throw Error()}catch{encoding='base64';content=base64(b)}return {encoding,content,sha256:await hash(b)}}
 async function run(op,signal,progress=()=>{}){
  if(location.origin!=='https://studio.stepx.com')throw Error('请先打开并登录官方 Studio');
  const req=async(path,init={})=>{if(signal?.aborted)throw Error('操作已停止');const r=await fetch(path,{...init,credentials:'same-origin',redirect:'error',signal,cache:'no-store'});if(!r.ok)throw Error(r.status===401||r.status===403?'请先在 Studio 登录当前账号':`Studio 请求失败（HTTP ${r.status}）`);return r};
  const json=async(p,i)=>{const r=await req(p,i);try{return await r.json()}catch{throw Error('Studio 响应格式变化，请重新登录或稍后再试')}};
  const pid=p=>{if(!/^p_[A-Za-z0-9_-]+$/.test(p))throw Error('无效云端项目');return '/api/projects/'+encodeURIComponent(p)};
  const path=(id,p)=>pid(id)+'/file?'+new URLSearchParams({path:p});
  const idle=async id=>{const a=await json('/api/projects/activity'),x=a.items?.find(x=>x.projectId===id);if(!x||!['idle','completed','failed'].includes(x.state))throw Error('云端项目正在执行任务或状态未知，请先在 Studio 停止任务')};
  const bytes=async r=>{if(+r.headers.get('content-length')>FILE)throw Error('单文件超过 1 MB，同步未完成');let reader=r.body.getReader(),chunks=[],n=0;try{for(;;){const x=await reader.read();if(x.done)break;n+=x.value.length;if(n>FILE)throw Error('单文件超过 1 MB，同步未完成');chunks.push(x.value)}}finally{reader.cancel().catch(()=>{})}const b=new Uint8Array(n);let at=0;for(const c of chunks){b.set(c,at);at+=c.length}return b};
  const listing=async id=>{for(let attempt=0;attempt<45;attempt++){if(signal?.aborted)throw Error('操作已停止');const r=await fetch(pid(id)+'/files',{credentials:'same-origin',redirect:'error',signal,cache:'no-store'});if(r.ok)return await r.text();if(r.status!==503)throw Error(r.status===401||r.status===403?'请先登录 Studio':`文件列表读取失败（HTTP ${r.status}）`);progress('等待云端工作区准备…');await new Promise(resolve=>setTimeout(resolve,1000));}throw Error('云端工作区尚未就绪，项目关联已保留，请稍后重试')};
  const read=async(id,initialize=false)=>{
   await req(pid(id)+'/sandbox/ensure',{method:'POST'});await idle(id);
   if(initialize){progress('初始化新项目工作区…');const sessions=(await json(pid(id)+'/sessions')).sessions;if(!Array.isArray(sessions)||sessions.length!==1||typeof sessions[0].id!=='string')throw Error('新项目会话状态变化，请打开 Studio 核对');const requestId=crypto.randomUUID(),v=await json(pid(id)+'/workspace/command',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({sessionId:sessions[0].id,command:{requestId,action:'status'}})});if(v.result?.requestId!==requestId||v.result.error||v.result.status?.busy!==false)throw Error('工作区未准备好，请稍后重试');}
   const meta=(await json(pid(id))).project;if(meta?.id!==id||typeof meta.name!=='string')throw Error('云端项目身份不匹配');
   const list=await listing(id);if(/^\s*</.test(list))throw Error('Studio 文件清单无效');
   const listed=list.split('\n').map(x=>x.trim()).filter(Boolean);if(listed.some(p=>!valid(p.endsWith('/')?p.slice(0,-1):p)))throw Error('云端文件路径无效');const names=listed.filter(p=>!p.endsWith('/'));if(new Set(names).size!==names.length)throw Error('云端文件路径无效');
   const skipped=names.filter(excluded),included=names.filter(p=>!excluded(p));if(included.length>COUNT)throw Error('项目超过 300 个源文件，未进行部分同步');
   const entries=Object.create(null);let total=0;
   for(const p of included){progress('读取 '+p);let b=await bytes(await req(path(id,p)));total+=b.length;if(total>MAX)throw Error('项目源码超过 4 MB，未进行部分同步');entries[p]=await pack(b)}
   const after=(await (await req(pid(id)+'/files')).text()).split('\n').map(x=>x.trim()).filter(x=>x&&!x.endsWith('/'));if(JSON.stringify(after)!==JSON.stringify(names))throw Error('同步期间云端文件清单变化，请重试');for(const p of included){progress('核对 '+p);if(await hash(await bytes(await req(path(id,p))))!==entries[p].sha256)throw Error('同步期间云端文件变化：'+p)}
   await idle(id);
   return {project:{id,name:meta.name,builderType:meta.builderType||'app'},entries,skipped,bytes:total};
  };
  if(op.action==='list'){const v=await json('/api/projects');if(!Array.isArray(v.projects))throw Error('云端项目列表格式无效');return {projects:v.projects.map(p=>({id:p.id,name:p.name,builderType:p.builderType||'app'}))}}
  if(op.action==='pull')return read(op.projectId,op.initialize===true);
  if(op.action==='create'){
   const v=await json('/api/projects',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:op.name,projectType:'web',builderType:'app',productType:'app',model:'auto',description:'从 StepX Neo Studio 上传的本地项目'})});
   if(!v.project?.id)throw Error('创建结果未知，请先刷新云端项目列表，不要重复上传');
   return {project:{id:v.project.id,name:v.project.name,builderType:v.project.builderType||'app'}};
  }
  if(op.action==='upload'){
   const id=op.projectId;pid(id);let names=Object.keys(op.entries||{}),total=0,written=[];
   if(!names.length||names.length>COUNT||names.some(p=>!valid(p)||excluded(p)))throw Error('上传文件范围无效');
   for(const p of names){let b=decode(op.entries[p]);total+=b.length;if(b.length>FILE||total>MAX||await hash(b)!==op.entries[p].sha256)throw Error('上传文件校验失败')}
   try{
    await idle(id);
    for(const p of names){
     progress('上传 '+p);await idle(id);
     const present=(await (await req(pid(id)+'/files')).text()).split('\n');let current=present.includes(p)?await hash(await bytes(await req(path(id,p)))):null;
     if(current!== (op.expected?.[p]??null))throw Error('云端文件在核对后变化，已停止：'+p);
     if(current===null){const all=(await (await req(pid(id)+'/files')).text()).split('\n');const parts=p.split('/');for(let k=1;k<parts.length;k++){const dir=parts.slice(0,k).join('/');if(!all.some(x=>x.startsWith(dir+'/')))await req(path(id,dir)+'&kind=directory',{method:'POST'});}await req(path(id,p)+'&kind=file',{method:'POST'});}
     const body=decode(op.entries[p]);await req(path(id,p),{method:'PUT',headers:{'Content-Type':'application/octet-stream'},body});
     if(await hash(await bytes(await req(path(id,p))))!==op.entries[p].sha256)throw Error('上传后回读不一致：'+p);
     written.push(p);
    }
    return {written,complete:true};
   }catch(e){return {written,complete:false,error:(signal?.aborted?'操作已停止':e.message)+'；已核验 '+written.length+' 个文件。可重新核对后继续。'}}
  }
  throw Error('未知同步操作');
 }
 return {run,valid,excluded,pack,hash,decode};
})();
