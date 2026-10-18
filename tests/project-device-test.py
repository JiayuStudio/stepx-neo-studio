import os
"""Real Neo gestures and project lifecycle using a dedicated disposable fixture only."""
import subprocess,json,time,re,hashlib,xml.etree.ElementTree as E
from pathlib import Path
ADB=os.environ.get('ADB','adb')
ID='qa-delete-20261018-unique'
def adb(*a,**kw):return subprocess.check_output([ADB,*a],**kw)
def js(code):return json.loads(subprocess.check_output(['node','tests/cdp.mjs'],input=code.encode()))
def state():return json.loads(adb('shell','run-as','studio.jiayu.neo','cat','files/workspace.json'))
def ui():
 adb('shell','uiautomator','dump','/sdcard/neo-studio-window.xml')
 return E.fromstring(adb('shell','cat','/sdcard/neo-studio-window.xml'))
def native(text):
 root=ui();ns=[n for n in root.iter('node') if n.get('text')==text];assert ns,('native text missing',text)
 b=list(map(int,re.findall(r'\d+',ns[-1].get('bounds'))));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(1)
def point(selector):
 r=js('(async()=>{let e=document.querySelector('+json.dumps(selector)+');e.scrollIntoView({block:"center"});await new Promise(r=>setTimeout(r,250));let r=e.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height,scale:1200/innerWidth}})()');return (round((r['x']+r['w']/2)*r['scale']),round(293+(r['y']+r['h']/2)*r['scale']))
def tap(selector):
 x,y=point(selector);adb('shell','input','tap',str(x),str(y));time.sleep(1)
def screenshot(name):
 ui();Path('evidence/'+name+'.png').write_bytes(adb('exec-out','screencap','-p'))
def row():return '[data-project-id="'+ID+'"]'
checks=[]
def check(b,n):assert b,n;checks.append(n);print('PASS',n,flush=True)
s=state()
if any(x['project']['id']==ID for x in s.get('projectTrash',[])):
 js('send("projectRestore",{id:'+json.dumps(ID)+'});true');native('恢复项目');s=state()
fixture=next(p for p in s['projects'] if p['id']==ID)
# App-private dummy build cache: never install it. Purge must remove only this prefix.
adb('shell','run-as','studio.jiayu.neo','sh','-c',"'echo fixture > files/builds/"+ID+"-1.apk'")
js("S=JSON.parse(Neo.read());defaults();page='projects';render();true")
x,y=point(row()+' .project-open');adb('shell','input','swipe',str(x),str(y),str(x),str(y),'650')
check(any(n.get('text')=='删除项目' for n in ui().iter('node')),'real long press opens project menu');screenshot('project-long-press');native('删除项目');native('取消');check(any(p['id']==ID for p in state()['projects']),'cancel deletion retains project')
x,y=point(row()+' .project-open');adb('shell','input','swipe',str(x+200),str(y),str(x-150),str(y),'320');time.sleep(.8)
check(js('document.querySelector('+json.dumps(row())+').classList.contains("revealed")'),'real left swipe reveals delete');check(js("page==='projects'"),'swipe does not open editor');screenshot('project-swipe')
tap(row()+' .swipe-delete');native('移到最近删除');s=state();check(not any(p['id']==ID for p in s['projects']) and len(s['projectTrash'])==1,'confirmed deletion enters trash');check(s['projectTrash'][0]['project']==fixture,'trash retains complete fixture')
js('send("project",'+json.dumps(fixture)+');true');time.sleep(.4);check(not any(p['id']==ID for p in state()['projects']),'late editor save cannot resurrect deleted project')
adb('shell','am','force-stop','studio.jiayu.neo');adb('shell','am','start','-n','studio.jiayu.neo/.MainActivity');time.sleep(1.5);pid=adb('shell','pidof','studio.jiayu.neo').decode().strip();adb('forward','tcp:9228','localabstract:webview_devtools_remote_'+pid);time.sleep(1)
check(len(state()['projectTrash'])==1,'trash persists after app restart')
js("page='projects';S=JSON.parse(Neo.read());defaults();render();true");tap('[data-a="trash"]');screenshot('project-trash');tap('[data-a="restoreProject"]');native('恢复项目');s=state();check(next(p for p in s['projects'] if p['id']==ID)==fixture,'restore retains files and project metadata');check(len(s['projectTrash'])==0,'restore removes trash copy')
tap('[data-a="projects"]');tap(row()+' .project-open');check(js("page==='editor'"),'ordinary tap opens editor');tap('[data-a="deleteProject"]');native('移到最近删除');check(js("getComputedStyle(document.querySelector('#notice')).pointerEvents==='none'"),'success notice does not block recovery controls');tap('[data-a="trash"]');tap('[data-a="purgeProject"]');native('取消');check(len(state()['projectTrash'])==1,'cancel permanent deletion retains recovery copy');tap('[data-a="purgeProject"]');native('彻底删除');s=state();check(len(s['projectTrash'])==0 and not any(p['id']==ID for p in s['projects']),'permanent deletion removes fixture');check(subprocess.run([ADB,'shell','run-as','studio.jiayu.neo','test','-e','files/builds/'+ID+'-1.apk']).returncode!=0,'purge removes matching app-private APK cache')
base=json.loads(Path('evidence/baseline-hashes.json').read_text())
for k in ['projects','profiles','chats','usage']:
 digest=hashlib.sha256(json.dumps(s.get(k,[]),sort_keys=True,ensure_ascii=False).encode()).hexdigest();check(digest==base[k],k+' unchanged from baseline')
check(js('document.documentElement.scrollWidth<=innerWidth'),'no horizontal page overflow')
Path('evidence/project-device-tests.json').write_text(json.dumps({'result':'PASS','checks':checks,'count':len(checks)},ensure_ascii=False,indent=2));print('ALL PASS',len(checks))
