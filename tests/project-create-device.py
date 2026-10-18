"""Owned fixture: natural-language creation, subsequent edits and protected old projects."""
import os,json,time,subprocess,threading,http.server
from pathlib import Path
ADB=os.environ.get('ADB','adb');PROFILE='qa-create-api';rounds=0

def adb(*a):return subprocess.check_output([ADB,*a])
def js(s):return json.loads(subprocess.check_output(['node','tests/cdp.mjs'],input=s.encode()))
def state():return json.loads(adb('exec-out','run-as','studio.jiayu.neo','cat','files/workspace.json'))
class Handler(http.server.BaseHTTPRequestHandler):
 def log_message(self,*a):pass
 def do_POST(self):
  global rounds
  body=json.loads(self.rfile.read(int(self.headers['Content-Length'])));rounds+=1
  text=json.dumps({'summary':'Created fixture' if rounds==1 else 'Edited fixture','files':{'index.html':'<!doctype html><html><body><h1>Fixture '+str(rounds)+'</h1></body></html>'}})
  self.send_response(200);self.send_header('Content-Type','text/event-stream');self.end_headers()
  for i in range(0,len(text),9):
   self.wfile.write(('data: '+json.dumps({'choices':[{'delta':{'content':text[i:i+9]}}]})+'\n\n').encode());self.wfile.flush();time.sleep(.08)
  self.wfile.write(('data: '+json.dumps({'choices':[{'delta':{},'finish_reason':'stop'}],'usage':{'prompt_tokens':10,'completion_tokens':20}})+'\n\ndata: [DONE]\n\n').encode());self.wfile.flush()
def wait():
 for _ in range(150):
  v=js('({busy,projectId,chatId,edit:chat()?.messages.at(-1)?.edit?.status})')
  if not v['busy']:
   assert v.get('edit')=='applied',v
   return v
  time.sleep(.25)
 raise Exception('timeout')
server=http.server.ThreadingHTTPServer(('127.0.0.1',8767),Handler);threading.Thread(target=server.serve_forever,daemon=True).start();adb('reverse','tcp:8767','tcp:8767')
try:
 before=state();originals={p['id']:p for p in before['projects']}
 profile={'id':PROFILE,'name':'Creation test fixture','endpoint':'http://127.0.0.1:8767','model':'fixture','protocol':'compatible','key':'fixture-only','maxTokens':512}
 js('send("profile",'+json.dumps(profile)+');true');time.sleep(.3)
 old=before['projects'][0]['id'];js('S=JSON.parse(Neo.read());profileId="'+PROFILE+'";projectId='+json.dumps(old)+';chatId=null;page="ai";githubResearch=false;draft="新建一个创建通道测试项目。写一个页面";render();sendMessage();true')
 first=wait();assert first['projectId'] not in originals
 st=state();created=next(p for p in st['projects'] if p['id']==first['projectId']);assert 'Fixture 1' in created['files']['index.html']
 js('draft="将标题改为Fixture 2，保持此项目";sendMessage();true');second=wait();assert second['projectId']==first['projectId'] and second['chatId']==first['chatId']
 st=state();assert all(next(p for p in st['projects'] if p['id']==id)==p for id,p in originals.items())
 assert sum(1 for p in st['projects'] if p['id'] not in originals)==1
 out={'result':'PASS','projectId':first['projectId'],'chatId':first['chatId'],'creationAndFollowup':True,'originalProjectsPreserved':True,'apiCalls':rounds}
 Path('evidence/project-create-device.json').write_text(json.dumps(out));print(json.dumps(out))
finally:server.shutdown();adb('reverse','--remove','tcp:8767')
