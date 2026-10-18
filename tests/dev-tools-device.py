"""Run actual SSE -> staged tools -> native compile/build -> final write on an attached debug app."""
import os,json,time,subprocess,threading,http.server
from pathlib import Path
ADB=os.environ.get('ADB','adb');ID='qa-dev-tools-20261018';PROFILE='qa-dev-tools-api';CHAT='qa-dev-tools-chat'
def adb(*args):return subprocess.check_output([ADB,*args])
def js(s):return json.loads(subprocess.check_output(['node','tests/cdp.mjs'],input=s.encode()))
def state():return json.loads(adb('shell','run-as','studio.jiayu.neo','cat','files/workspace.json'))
rounds=0
class Handler(http.server.BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_POST(self):
  global rounds
  body=json.loads(self.rfile.read(int(self.headers['Content-Length'])));assert body['stream'] is True
  rounds+=1
  if rounds==1:r={'tools':[{'name':'write_files','args':{'files':{'app.js':'window.fixtureValue=42;'}}},{'name':'check_project','args':{}},{'name':'build_apk','args':{}}]}
  else:r={'summary':'Tool fixture ready','files':{}}
  text=json.dumps(r);self.send_response(200);self.send_header('Content-Type','text/event-stream');self.end_headers()
  for i in range(0,len(text),12):
   self.wfile.write(('data: '+json.dumps({'choices':[{'delta':{'content':text[i:i+12]}}]})+'\n\n').encode());self.wfile.flush();time.sleep(.1)
  self.wfile.write(('data: '+json.dumps({'choices':[{'delta':{},'finish_reason':'stop'}],'usage':{'prompt_tokens':10,'completion_tokens':20}})+'\n\ndata: [DONE]\n\n').encode());self.wfile.flush()
server=http.server.ThreadingHTTPServer(('127.0.0.1',8767),Handler);threading.Thread(target=server.serve_forever,daemon=True).start();adb('reverse','tcp:8767','tcp:8767')
try:
 p={'id':ID,'name':'Developer tools fixture','files':{'index.html':'<!doctype html><html><body><button id="b">0</button><script src="app.js"></script></body></html>'}}
 profile={'id':PROFILE,'name':'Local test fixture','endpoint':'http://127.0.0.1:8767','model':'fixture','protocol':'compatible','key':'fixture-only','streaming':True,'maxTokens':512}
 js('send("project",'+json.dumps(p)+');send("profile",'+json.dumps(profile)+');true');time.sleep(.3)
 chat={'id':CHAT,'projectId':ID,'name':'Developer tools fixture','messages':[{'role':'user','content':'fixture task'}]}
 params={'profileId':PROFILE,'projectId':ID,'chatId':CHAT,'projectContext':True,'githubResearch':False,'editMode':'auto','messages':chat['messages']}
 js('send("chat",'+json.dumps(chat)+');send("send",'+json.dumps(params)+');projectId="'+ID+'";page="projects";render();true')
 evidence={'streamPreview':False,'greenDot':False}
 for i in range(180):
  live=js('(()=>({active:!!aiLive.active,text:aiLive.text||"",dot:!!document.querySelector(".project-live-dot:not([hidden])"),stage:aiLive.stage}))()')
  evidence['streamPreview']|=live['active'] and len(live['text'])>0
  evidence['greenDot']|=live['active'] and live['dot']
  st=state();c=next((c for c in st['chats'] if c['id']==CHAT),{})
  if c.get('messages',[{}])[-1].get('role')=='assistant' and not live['active']:break
  time.sleep(.4)
 else:raise Exception('Tool fixture timeout')
 m=c['messages'][-1];p=next(p for p in st['projects'] if p['id']==ID)
 assert m['edit']['status']=='applied',m.get('edit');assert p['files']['app.js']=='window.fixtureValue=42;'
 trail=m['toolCalls'];assert len(trail)==3 and all('error' not in t for t in trail),trail
 assert trail[1]['result']['ok'] is True;assert len(p['builds'])==1
 evidence.update(result='PASS',toolNames=[t['name'] for t in trail],usageTotal=m['usage']['total'],apkBuilt=True,projectWritten=True)
 assert evidence['streamPreview'];assert evidence['greenDot'],evidence
 Path('evidence/dev-tools-device.json').write_text(json.dumps(evidence,indent=2));print(json.dumps(evidence))
finally:server.shutdown();adb('reverse','--remove','tcp:8767')
