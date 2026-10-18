"""Opt-in phone QA for compiled APKs. Use only a debug host and an unlocked test device.

Phases: build (opens system installer), inspect (after installing), errors, cleanup.
Does not call any AI/cloud service. The fixture ID is exclusive to this test.
"""
import os, json, subprocess, time, hashlib, zipfile, io, sys, re
from pathlib import Path
import xml.etree.ElementTree as ET
ADB=os.environ.get('ADB','adb')
ID='qa-compiled-apk-121'
OUT=Path('evidence/compiled-apk'); OUT.mkdir(parents=True,exist_ok=True)
def adb(*a): return subprocess.check_output([ADB,*a])
def js(code): return json.loads(subprocess.check_output(['node','tests/cdp.mjs'],input=code.encode()))
def state(): return json.loads(adb('exec-out','run-as','studio.jiayu.neo','cat','files/workspace.json'))
def project(): return next(p for p in state()['projects'] if p['id']==ID)
def tap(text):
 adb('shell','uiautomator','dump','/sdcard/neo-compiled-apk-qa.xml')
 nodes=[n for n in ET.fromstring(adb('exec-out','cat','/sdcard/neo-compiled-apk-qa.xml')).iter('node') if n.get('text')==text]
 assert nodes, 'Missing button: '+text
 clickable=[n for n in nodes if n.get('clickable')=='true']; n=(clickable or nodes)[-1]
 b=list(map(int,re.findall(r'\d+',n.get('bounds'))));assert b[2]>b[0] and b[3]>b[1]
 adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(.3)
def write(p): js('send("project",'+json.dumps(p)+');true');time.sleep(.3)
def wait_build(number):
 for _ in range(65):
  p=project()
  if p.get('buildNumber',0)>=number:return p['builds'][-1]
  time.sleep(1)
 raise AssertionError('Build did not complete')
FILES={
 'index.html':'<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div><script type="module" src="/src/main.tsx"></script></body></html>',
 'package.json':json.dumps({'dependencies':{'react':'^19.3.0','react-dom':'^19.3.0'}}),
 'src/main.tsx':'''import {useState,useEffect} from 'react';import {createRoot} from 'react-dom/client';import './nested/style.css';
function App(){const [status,setStatus]=useState('Checking resources');const [count,setCount]=useState(0);useEffect(()=>{(async()=>{
 const results=[];const check=(name,ok)=>{results.push(ok);window.NeoDev.test(name,ok?'pass':'fail');};
 check('binary bytes',Array.from(new Uint8Array(await fetch('/data/probe.bin').then(r=>r.arrayBuffer()))).join(',')==='0,255,1,2,3');
 check('public text alias',(await fetch('/badge.svg').then(r=>r.text())).includes('<svg'));
 check('nested stylesheet',getComputedStyle(document.body).backgroundColor==='rgb(12, 34, 56)');
 check('host bridge isolated',typeof window.Neo==='undefined');
 check('outside asset blocked',(await fetch('/project.json')).status===404);
 const img=new Image();await new Promise((resolve,reject)=>{img.onload=resolve;img.onerror=reject;img.src='/probe.png'});check('binary public image',img.naturalWidth===1);
 setStatus(results.every(Boolean)?'APK RESOURCES PASS':'APK RESOURCES FAIL');
 })().catch(e=>{setStatus('FAIL '+e.message);window.NeoDev.test('resources','fail',e.message)})},[]);
 return <main><h1>{status}</h1><img src="/badge.svg" width="80"/><button onClick={()=>{setCount(count+1);window.NeoDev.test('real touch','pass',String(count+1))}}>Count {count}</button></main>}
createRoot(document.getElementById('root')).render(<App/>);''',
 'src/nested/style.css':'body{background:rgb(12,34,56);color:white;font-family:sans-serif;margin:30px}button{display:block;padding:24px;margin-top:30px;font-size:24px}',
 'public/badge.svg':'<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="80" height="80" fill="#20de99"/></svg>'}
BINARY={'data/probe.bin':'AP8BAgM=','public/probe.png':'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a54cAAAAASUVORK5CYII='}
phase=sys.argv[1]
if phase=='build':
 assert not any(p['id']==ID for p in state()['projects']), 'Fixture already exists; inspect or clean up first'
 p={'id':ID,'name':'Compiled APK QA','cloudSource':True,'files':FILES,'binaryFiles':BINARY};write(p)
 js('S=JSON.parse(Neo.read());projectId='+json.dumps(ID)+';page="builds";render();document.querySelector("[data-a=buildInstall]").click();true')
 b=wait_build(1);(OUT/'build.json').write_text(json.dumps(b));raw=adb('exec-out','run-as','studio.jiayu.neo','cat','files/builds/'+b['file']);(OUT/'fixture.apk').write_bytes(raw)
 assert hashlib.sha256(raw).hexdigest()==b['sha256'];z=zipfile.ZipFile(io.BytesIO(raw));names=z.namelist()
 assert z.read('assets/project/data/probe.bin')==bytes([0,255,1,2,3]);assert z.read('assets/project/public/probe.png')==__import__('base64').b64decode(BINARY['public/probe.png'])
 assert 'assets/project/__vendor/LICENSES.txt' in names and not any('esbuild.wasm' in n for n in names)
 assert project()['files']==FILES and project()['binaryFiles']==BINARY
 print('PASS compiled package, binary bytes, vendor licenses, source preserved; awaiting system install confirmation')
elif phase=='inspect':
 # Finish the installer before starting another activity; Neo otherwise retains its old result screen.
 try:tap('完成')
 except AssertionError:pass
 b=json.loads((OUT/'build.json').read_text());adb('shell','am','start','-n',b['package']+'/studio.jiayu.runtime.RuntimeActivity');time.sleep(2)
 if os.environ.get('NEO_QA_TOUCH'):
  x,y=os.environ['NEO_QA_TOUCH'].split(',');adb('shell','input','tap',x,y)
 else:tap('Count 0')
 adb('shell','input','tap','1120','210');tap('查看运行日志')
 adb('shell','uiautomator','dump','/sdcard/neo-compiled-apk-qa.xml');root=ET.fromstring(adb('exec-out','cat','/sdcard/neo-compiled-apk-qa.xml'))
 text=next(n.get('text') for n in root.iter('node') if n.get('text','').startswith('{'))
 report=json.loads(text);assert len(report['tests'])==7 and all(t['status']=='pass' for t in report['tests']), report
 (OUT/'runtime-report.json').write_text(json.dumps(report,indent=2));tap('关闭')
 print('PASS seven independent runtime checks including real Android touch')
elif phase=='errors':
 adb('shell','am','start','-n','studio.jiayu.neo/.MainActivity');time.sleep(.5)
 p=project();before=p['buildNumber'];write(dict(p,files={**FILES,'src/main.tsx':'import value from "missing-qa-dependency";console.log(value)'}))
 js('window.qaErrors=[];window.qaReceive=window.receive;window.receive=(t,d)=>{if(t==="error")qaErrors.push(d);qaReceive(t,d)};send("buildApk",{id:'+json.dumps(ID)+'});true')
 for _ in range(65):
  errors=js('qaErrors');
  if errors:break
  time.sleep(1)
 assert errors and 'missing-qa-dependency' in str(errors)
 assert project()['buildNumber']==before
 (OUT/'failure-check.json').write_text(json.dumps({'missingDependencyNamed':True,'noFalseBuild':True}))
 write(p);js('window.receive=qaReceive;true');print('PASS named dependency error and unchanged successful build records')
elif phase=='cleanup':
 b=json.loads((OUT/'build.json').read_text());adb('shell','am','force-stop',b['package']);adb('uninstall',b['package'])
 adb('shell','am','start','-n','studio.jiayu.neo/.MainActivity');time.sleep(.5)
 js('send("projectTrash",{id:'+json.dumps(ID)+'});true');tap('移到最近删除')
 js('send("projectPurge",{id:'+json.dumps(ID)+'});true');tap('彻底删除')
 assert not any(p['id']==ID for p in state()['projects']) and not any(x['project']['id']==ID for x in state()['projectTrash'])
 adb('shell','rm','-f','/sdcard/neo-compiled-apk-qa.xml');print('PASS owned fixture app, project and build files removed')
else:raise SystemExit('Unknown phase')
