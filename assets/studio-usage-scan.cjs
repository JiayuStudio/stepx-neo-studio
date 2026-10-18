// Neo Studio usage scanner v1: read only this sandbox's observed SDK project logs.
// No credentials, environment, network, user content, writes or subprocesses.
const fs = require('fs'), path = require('path');
const root = '/home/ide/state/h1/projects/-home-ide-workspace';
const report = {schema:1, nonce:'__NEO_NONCE__', projectId:'__NEO_PROJECT__', source:root,
 generatedAt:Date.now(), files:0, rows:0, skipped:0, limited:false, groups:[]};
const requests = new Map(); let bytes = 0;
function visit(dir, depth) {
 if (depth > 3) { report.limited = true; return; }
 for (const name of fs.readdirSync(dir).sort()) {
  const file = path.join(dir,name); const st = fs.lstatSync(file);
  if (st.isSymbolicLink()) { report.limited = true; continue; }
  if (st.isDirectory()) { if (name !== 'memory') visit(file,depth+1); continue; }
  if (!st.isFile() || !name.endsWith('.jsonl')) continue;
  if (report.files >= 200 || st.size > 20000000 || bytes + st.size > 80000000) { report.limited = true; continue; }
  bytes += st.size; report.files++;
  for (const line of fs.readFileSync(file,'utf8').split('\n')) {
   if (!line.trim()) continue;
   let row; try { row=JSON.parse(line); } catch { report.skipped++; continue; }
   const m=row.message, u=m?.usage;
   if (row.type !== 'assistant' || !u) continue;
   const fields=['input_tokens','output_tokens','cache_read_input_tokens','cache_creation_input_tokens'];
   if (typeof m.id !== 'string' || !m.id || !fields.every(k=>Number.isSafeInteger(u[k])&&u[k]>=0) || !Number.isFinite(Date.parse(row.timestamp))) { report.skipped++; continue; }
   const values=fields.map(k=>u[k]);
   const model=typeof m.model==='string'?m.model.slice(0,100):'unknown';
   const key=model+'\0'+m.id; const time=Date.parse(row.timestamp); const prev=requests.get(key);
   // Streaming snapshots: take maxima per field, never add duplicate message IDs.
   if(prev){for(let i=0;i<4;i++)prev.values[i]=Math.max(prev.values[i],values[i]);prev.time=Math.max(prev.time,time);}
   else requests.set(key,{model,time,values});
   report.rows++;
  }
 }
}
try {
 if (fs.lstatSync(root).isSymbolicLink()) throw Error('Unexpected log root');
 visit(root,0); const groups=new Map();
 for(const r of requests.values()) {
  const day=new Date(r.time).toISOString().slice(0,10), key=day+'\0'+r.model;
  let g=groups.get(key);if(!g){g={day,model:r.model,requests:0,input:0,output:0,cacheRead:0,cacheWrite:0,first:r.time,last:r.time};groups.set(key,g);}
  g.requests++;g.first=Math.min(g.first,r.time);g.last=Math.max(g.last,r.time);
  ['input','output','cacheRead','cacheWrite'].forEach((k,i)=>g[k]+=r.values[i]);
 }
 report.groups=[...groups.values()];
 if(report.groups.length>500)throw Error('Too many groups');
 report.status=report.groups.length?'ok':'unavailable';
} catch(e) { report.status='error';report.error=e.code||e.message;report.groups=[]; }
console.log('NEO_STUDIO_USAGE_V1='+JSON.stringify(report));
