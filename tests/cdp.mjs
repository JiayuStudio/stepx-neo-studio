// Development-only QA against this app's adb-forwarded WebView, never production debugging.
const pages=await (await fetch('http://127.0.0.1:9228/json/list')).json();
const target=pages.find(p=>p.url.startsWith(process.env.NEO_TEST_PAGE||'https://neo.local/'));
if(!target)throw Error('Target missing');
const ws=new WebSocket(target.webSocketDebuggerUrl);await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j});
let id=0;const pending=new Map();ws.onmessage=e=>{let m=JSON.parse(e.data);if(m.id&&pending.has(m.id)){pending.get(m.id)(m);pending.delete(m.id)}};
const call=(method,params={})=>new Promise(r=>{const n=++id;pending.set(n,r);ws.send(JSON.stringify({id:n,method,params}))});
let code='';for await(const chunk of process.stdin)code+=chunk;
const r=await call('Runtime.evaluate',{expression:code,awaitPromise:true,returnByValue:true,userGesture:true});
if(r.result?.exceptionDetails){console.error(JSON.stringify(r.result.exceptionDetails));process.exitCode=1}else console.log(JSON.stringify(r.result?.result?.value??r));
ws.close();
