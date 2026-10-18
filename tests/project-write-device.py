import os
"""Disposable localhost API fixtures exercise actual native write paths on Neo."""
import http.server,threading,subprocess,time,json
from pathlib import Path
ADB=os.environ.get('ADB','adb')
ID='qa-edit-20261018';OTHER='qa-write-other-20261018';PROFILE='qa-local-write'
def adb(*a):return subprocess.check_output([ADB,*a])
def js(s):return json.loads(subprocess.check_output(['node','tests/cdp.mjs'],input=s.encode()))
def state():return json.loads(adb('shell','run-as','studio.jiayu.neo','cat','files/workspace.json'))
def project():return next(x for x in state()['projects'] if x['id']==ID)
response={};requests=[]
class H(http.server.BaseHTTPRequestHandler):
 def do_POST(self):
  requests.append(json.loads(self.rfile.read(int(self.headers['Content-Length']))));time.sleep(response.get('delay',.1));r={'model':'fixture','choices':[{'message':{'content':json.dumps({'files':response.get('files',{'qa.txt':'fixture'})})},'finish_reason':response.get('finish','stop')}],'usage':{'prompt_tokens':10,'completion_tokens':10,'total_tokens':20}};data=json.dumps(r).encode();self.send_response(200);self.end_headers()
  try:self.wfile.write(data)
  except BrokenPipeError:pass
 def log_message(self,*a):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',8766),H);threading.Thread(target=server.serve_forever,daemon=True).start();adb('reverse','tcp:8766','tcp:8766')
checks=[];chatids=[]
def check(b,n):assert b,n;checks.append(n);print('PASS',n,flush=True)
def run(mode='auto',action=None):
 cid='qa-write-'+str(time.time_ns());chatids.append(cid)
 js('(()=>{send("chat",'+json.dumps({'id':cid,'name':'QA fixture','messages':[{'role':'user','content':'fixture task'}]})+');send("send",'+json.dumps({'profileId':PROFILE,'chatId':cid,'projectId':ID,'projectContext':True,'editMode':mode,'messages':[{'role':'user','content':'fixture task'}]})+');return true})()');time.sleep(.3)
 if action:action()
 for _ in range(80):
  st=state();rows=[r for r in st['usage'] if r.get('chatId')==cid]
  if rows:break
  time.sleep(.2)
 else:raise Exception('request timeout')
 time.sleep(.15);c=next(c for c in state()['chats'] if c['id']==cid);return c['messages'][-1].get('edit')
try:
 js('send("profile",'+json.dumps({'id':PROFILE,'name':'QA localhost','endpoint':'http://127.0.0.1:8766','protocol':'compatible','model':'fixture','key':'fixture-only','maxTokens':1000})+');true');time.sleep(.3)
 js('send("project",'+json.dumps({'id':OTHER,'name':'Other fixture','files':{'index.html':'OTHER_UNCHANGED'}})+');true');time.sleep(.3)
 response.update(files={'qa.txt':'first'},delay=.4);e=run(action=lambda:js('projectId="'+OTHER+'";page="ai";render();true'))
 check(e['status']=='applied' and project()['files']['qa.txt']=='first','applies to request-bound project after selection changes')
 check(next(p for p in state()['projects'] if p['id']==OTHER)['files']=={'index.html':'OTHER_UNCHANGED'},'other project unchanged')
 check('KEEP_UNRELATED' in requests[-1]['messages'][-1]['content'],'native request includes full latest project snapshot')
 response.update(files={'qa.txt':'stale'},delay=1)
 def concurrent():
  p=project();p['files']['manual.txt']='NEWER EDIT';js('send("project",'+json.dumps(p)+');true')
 e=run(action=concurrent);check(e['status']=='rejected' and project()['files']['qa.txt']=='first' and project()['files']['manual.txt']=='NEWER EDIT','stale response cannot overwrite concurrent edits')
 response.update(files={'qa.txt':'review'},delay=.1);before=project()['files'];e=run('review');check(e['status']=='review' and project()['files']==before,'review mode does not write')
 js('send("applyProjectFiles",'+json.dumps({'id':ID,'base':e['base'],'files':e['files']})+');true');time.sleep(.3);check(project()['files']['qa.txt']=='review','review uses native apply channel')
 js('send("applyProjectFiles",'+json.dumps({'id':ID,'base':e['base'],'files':{'qa.txt':'duplicate'}})+');true');time.sleep(.2);check(project()['files']['qa.txt']=='review','repeat stale apply rejected')
 response.update(files={'qa.txt':'truncated'},finish='length');e=run();check(e['status']=='rejected' and project()['files']['qa.txt']=='review','truncated output never writes')
 response.update(files={'../escape.js':'bad'},finish='stop');e=run();check(e['status']=='rejected' and '../escape.js' not in project()['files'],'invalid model path rejected')
 response.update(files={'qa.txt':'cancelled'},delay=1);before=project()['files'];e=run(action=lambda:js('send("cancel");true'));check(project()['files']==before,'cancelled request leaves files unchanged')
 p=project();js('send("restoreProjectFiles",'+json.dumps({'id':ID,'base':p['files']})+');true');time.sleep(.3);check(project()['files']['qa.txt']=='first','restore previous snapshot through native channel')
 Path('evidence/project-write-device.json').write_text(json.dumps({'result':'PASS','checks':checks,'requests':len(requests)},ensure_ascii=False,indent=2))
finally:
 Path('evidence/qa-write-ids.json').write_text(json.dumps({'chats':chatids,'profile':PROFILE,'projects':[ID,OTHER]}));server.shutdown();adb('reverse','--remove','tcp:8766')
