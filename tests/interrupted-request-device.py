"""Force-stop an owned local fixture request and verify persisted interruption recovery."""
import os,json,time,subprocess,threading,http.server
from pathlib import Path
ADB=os.environ.get('ADB','adb');ID='qa-interrupted-request'
def adb(*args):return subprocess.check_output([ADB,*args])
def js(code):return json.loads(subprocess.check_output(['node','tests/cdp.mjs'],input=code.encode()))
def state():return json.loads(adb('exec-out','run-as','studio.jiayu.neo','cat','files/workspace.json'))
class Handler(http.server.BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_POST(self):
  self.rfile.read(int(self.headers['Content-Length']));time.sleep(30)
server=http.server.ThreadingHTTPServer(('127.0.0.1',8767),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
adb('reverse','tcp:8767','tcp:8767')
try:
 before=state();profile={'id':ID,'name':'Interruption fixture','endpoint':'http://127.0.0.1:8767','model':'fixture','protocol':'compatible','key':'fixture-only','maxTokens':512}
 js('send("profile",'+json.dumps(profile)+');true');time.sleep(.4)
 js('S=JSON.parse(Neo.read());profileId="'+ID+'";projectId=null;chatId=null;githubResearch=false;page="ai";newChat();draft="Interruption test";sendMessage();true')
 for _ in range(20):
  pending=state().get('pendingAi')
  if pending:break
  time.sleep(.1)
 assert pending
 adb('shell','am','force-stop','studio.jiayu.neo');adb('shell','am','start','-n','studio.jiayu.neo/.MainActivity');time.sleep(2)
 after=state();assert 'pendingAi' not in after
 c=next(c for c in after['chats'] if c['id']==pending['chatId']);assert '中断' in c['messages'][-1]['content']
 rows=[r for r in after['usage'] if r.get('id')==pending['id']];assert len(rows)==1 and rows[0]['status']=='interrupted' and rows[0]['usage']['known']==False
 assert after['projects']==before['projects']
 adb('shell','am','force-stop','studio.jiayu.neo');adb('shell','am','start','-n','studio.jiayu.neo/.MainActivity');time.sleep(2)
 assert sum(r.get('id')==pending['id'] for r in state()['usage'])==1
 pid=adb('shell','pidof','studio.jiayu.neo').decode().strip();adb('forward','tcp:9228','localabstract:webview_devtools_remote_'+pid)
 Path('evidence/interrupted-request-device.json').write_text(json.dumps({'result':'PASS','unknownUsageOnce':True,'projectsUnchanged':True,'chatId':pending['chatId'],'profileId':ID}))
 print('PASS: request recovery, one unknown usage row, all projects preserved')
finally:server.shutdown();adb('reverse','--remove','tcp:8767')
