import os
"""On-device compiler checks; creates and removes one explicit disposable project."""
import subprocess,json,time,os,hashlib,urllib.request,re,xml.etree.ElementTree as E
from pathlib import Path
ADB=os.environ.get('ADB','adb')
ID='qa-local-runner-118';HOST='https://p'+hashlib.sha256(ID.encode()).hexdigest()[:24]+'.neo-preview.invalid'
def adb(*a):return subprocess.check_output([ADB,*a])
def js(code,target='https://neo.local/'):
 env=dict(os.environ,NEO_TEST_PAGE=target);return json.loads(subprocess.check_output(['node','tests/cdp.mjs'],input=code.encode(),env=env))
def ui():
 adb('shell','uiautomator','dump','/sdcard/neo-studio-window.xml');return E.fromstring(adb('shell','cat','/sdcard/neo-studio-window.xml'))
def tap(label):
 ns=[]
 for _ in range(4):
  ns=[n for n in ui().iter('node') if n.get('text')==label]
  if ns:break
  time.sleep(.4)
 assert ns,label
 b=list(map(int,re.findall(r'\d+',ns[-1].get('bounds'))));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(.3)
checks=[]
def check(v,n):assert v,n;checks.append(n);print('PASS',n,flush=True)
def run(files,binary=None):
 p={'id':ID,'name':'本地运行验收临时项目','cloudSource':True,'files':files,'binaryFiles':binary or {}}
 js('send("project",'+json.dumps(p)+');true');time.sleep(.2);js('send("localRun",{id:'+json.dumps(ID)+'});true');time.sleep(.3)
 for i in range(80):
  pages=json.load(urllib.request.urlopen('http://127.0.0.1:9228/json/list'))
  if any(p['url'].startswith(HOST) for p in pages):time.sleep(.5);return None
  if any(p['url'].startswith('https://neo-compiler.invalid') for p in pages):
   error=js("window.__neoBuild?.error||''",'https://neo-compiler.invalid')
   if error:return error
  time.sleep(.25)
 raise Exception('Compiler timeout')
static={'index.html':'<!doctype html><meta name="viewport" content="width=device-width"><link rel="stylesheet" href="styles/main.css"><button id="b" onclick="inc()">0</button><img id="i" src="img/p.svg"><script src="src/main.js"></script>', 'styles/main.css':'body{background:rgb(12,34,56)}button{height:70px;width:100px}', 'src/main.js':'var n=0;function inc(){document.querySelector("#b").textContent=++n}', 'img/p.svg':'<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><rect width="10" height="10" fill="red"/></svg>','data/a.json':'{"ok":true}'}
check(run(static,{'data/b.bin':'AP8BAgM='}) is None,'static nested project starts offline')
r=js('(async()=>({css:getComputedStyle(document.body).backgroundColor,img:document.querySelector("#i").naturalWidth,json:await fetch("/data/a.json").then(r=>r.json()),bin:Array.from(new Uint8Array(await fetch("/data/b.bin").then(r=>r.arrayBuffer()))),bridge:typeof Neo,blocked:await fetch("https://example.com").then(r=>r.status).catch(e=>e.name)}))()',HOST)
check(r['css']=='rgb(12, 34, 56)' and r['img']==10 and r['json']['ok'] and r['bin']==[0,255,1,2,3],'nested CSS image JSON and binary served locally')
check(r['bridge']=='undefined' and r['blocked']=='TypeError','project has no native bridge and network is blocked')
tap('0');check(js('document.querySelector("#b").textContent',HOST)=='1','real Android touch invokes classic global handler')
tap('重载');check(js('document.querySelector("#b").textContent',HOST)=='0','reload reruns snapshot')
react={'index.html':'<div id="root"></div><script type="module" src="/src/main.tsx"></script>','package.json':json.dumps({'dependencies':{'react':'^19.3.0','react-dom':'^19.3.0'}}),'src/main.tsx':'import {createRoot} from "react-dom/client";import {X} from "@/X";import "./a.css";createRoot(document.getElementById("root")!).render(<X/>);','src/X.tsx':'import data from "./value.json";export function X(){return <h1 className="text-red-500">{data.value}</h1>}','src/value.json':'{"value":"PHONE_TSX_OK"}','src/a.css':'@tailwind utilities;'}
check(run(react) is None,'React TSX aliases and JSON compile on phone')
check(js('document.body.innerText',HOST)=='PHONE_TSX_OK' and js('getComputedStyle(document.querySelector("h1")).color',HOST)=='rgb(239, 68, 68)','React content and Tailwind style render')
bad=dict(react);bad['src/main.tsx']='import x from "not-installed-package";console.log(x)';check('not-installed-package' in run(bad),'missing dependency reports its name')
bad=dict(react);bad['src/main.tsx']='const x: = invalid';check('src/main.tsx' in run(bad),'syntax failure reports source location')
bad=dict(react);bad['package.json']='{"dependencies":{"react":"^18.0.0","react-dom":"^18.0.0"}}';check('依赖版本不兼容' in run(bad),'incompatible dependency version rejected')
tap('关闭');js('send("projectTrash",{id:'+json.dumps(ID)+'});true');tap('移到最近删除');js('send("projectPurge",{id:'+json.dumps(ID)+'});true');tap('彻底删除')
s=json.loads(adb('shell','run-as','studio.jiayu.neo','cat','files/workspace.json'));check(not any(p['id']==ID for p in s['projects']) and not any(x['project']['id']==ID for x in s['projectTrash']),'temporary project removed')
Path('evidence/local-runner-checks.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2))
