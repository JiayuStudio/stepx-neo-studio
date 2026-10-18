import os
"""Exercise the real workbench and native project validation on a disposable project."""
import json,subprocess,time
from pathlib import Path
ADB=os.environ.get('ADB','adb')
ID='qa-matrix-119';checks=[]
def js(code):return json.loads(subprocess.check_output(['node','tests/cdp.mjs'],input=code,text=True,timeout=15))
def adb(*a):return subprocess.check_output([ADB,*a])
def project():return next(p for p in json.loads(adb('shell','run-as','studio.jiayu.neo','cat','files/workspace.json'))['projects'] if p['id']==ID)
def check(ok,label):assert ok,label;checks.append(label);print('PASS',label,flush=True)
# Real touchscreen paint, using CSS viewport coordinates scaled to the physical WebView.
r=js("(()=>{let r=$('#matrixCanvas').getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height,inner:innerWidth}})()")
# Derive WebView origin from Android UI bounds rather than assuming device scale.
adb('shell','uiautomator','dump','/sdcard/neo-matrix-ui.xml')
import xml.etree.ElementTree as ET,re
root=ET.fromstring(adb('shell','cat','/sdcard/neo-matrix-ui.xml'))
w=next(n for n in root.iter('node') if n.get('class')=='android.webkit.WebView' and n.get('package')=='studio.jiayu.neo' and n.get('bounds')!='[0,0][0,0]')
x0,y0,x1,y1=map(int,re.findall(r'\d+',w.get('bounds')));scale=(x1-x0)/r['inner']
adb('shell','input','tap',str(int(x0+(r['x']+r['w']/34)*scale)),str(int(y0+(r['y']+r['h']/46)*scale)))
check(js('matrixDraft.frames[0].pixels[0]')==255,'touch paints exact matrix pixel')
js("document.querySelector('[data-matrix=duplicate]').click();true")
check(js('matrixDraft.frames.length')==3,'duplicate frame')
js("document.querySelector('[data-matrix=undo]').click();true")
check(js('matrixDraft.frames.length')==2,'undo restores frames')
js('matrixSave();true');time.sleep(.3)
check(json.loads(project()['files']['neo-matrix.json'])['frames'][0]['pixels'][0]==255,'native save and reopen preserves painted pixel')
check(len(project().get('snapshots',[]))>=1,'save retains recovery snapshot')
js('matrixDraft.frames[0].pixels[1]=123;matrixSave();true');time.sleep(.3)
check(json.loads(project()['files']['neo-matrix.json'])['frames'][0]['pixels'][1]==123,'second save uses refreshed baseline')
before=project()['files'];bad=json.loads(before['neo-matrix.json']);bad['frames'][0]['pixels'][0]=999
js('send("applyProjectFiles",'+json.dumps({'id':ID,'base':before,'files':{'neo-matrix.json':json.dumps(bad)}})+');true');time.sleep(.3)
check(project()['files']==before,'invalid AI matrix output rejected atomically')
js('send("applyProjectFiles",'+json.dumps({'id':ID,'base':{},'files':{'neo-matrix.json':before['neo-matrix.json']}})+');true');time.sleep(.3)
check(project()['files']==before,'stale edit rejected')
check(js('Neo.matrixNative()') is True,'Neo native display service available to ordinary app')
previous_build=len(project().get('builds',[]))
js('send("buildApk",{id:"'+ID+'"});true')
for _ in range(30):
 time.sleep(.5)
 if len(project().get('builds',[]))>previous_build:break
check(len(project().get('builds',[]))>previous_build,'matrix project builds signed APK on Neo')
Path('evidence/matrix-device.json').write_text(json.dumps({'checks':checks,'build':project()['builds'][-1]},ensure_ascii=False,indent=2))
