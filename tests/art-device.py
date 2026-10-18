"""Opt-in native image API QA. Local fixtures only; no paid API requests."""
import os,json,time,subprocess,threading,http.server,base64,io
from pathlib import Path
import struct,zlib
ADB=os.environ.get('ADB','adb');ID='qa-art-122';PROFILE='qa-art-api';CHAT='qa-art-chat';PORT=8773
counts={'images':0,'custom':0,'poll':0,'download':0,'chat':0};failures=[];finish_slow=False
def chunk(t,d):return struct.pack('!I',len(d))+t+d+struct.pack('!I',zlib.crc32(t+d)&0xffffffff)
PNG=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',48,48,8,6,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+bytes([240,130,60,200])*48)*48))+chunk(b'IEND',b'')
def adb(*a):return subprocess.check_output([ADB,*a])
def js(s):return json.loads(subprocess.check_output(['node','tests/cdp.mjs'],input=s.encode()))
def state():return json.loads(adb('exec-out','run-as','studio.jiayu.neo','cat','files/workspace.json'))
def action(a,p):js('send('+json.dumps(a)+','+json.dumps(p)+');true');time.sleep(.15)
def wait(fn,seconds=90):
 for _ in range(int(seconds*4)):
  st=state()
  if fn(st):return st
  time.sleep(.25)
 raise AssertionError('Timed out: '+str(js('({art:artStatus,error:window.qaArtError})')))
def project(st=None):return next(p for p in (st or state())['projects'] if p['id']==ID)
class Handler(http.server.BaseHTTPRequestHandler):
 def log_message(self,*a):pass
 def reply(self,d):
  b=json.dumps(d).encode();self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(b)));self.end_headers();self.wfile.write(b)
 def do_GET(self):
  if self.path=='/task/slow' and not finish_slow:
   counts['poll']+=1;self.reply({'id':'slow','state':'pending'})
  elif self.path in ['/task/fixture','/task/slow']:
   counts['poll']+=1;self.reply({'id':'fixture','state':'done','payload':{'picture':'http://127.0.0.1:8773/image'}})
  elif self.path in ['/image','/image-final']:
   counts['download']+=1
   if self.headers.get('Authorization'):failures.append('credential forwarded to image download')
   if self.path=='/image':self.send_response(302);self.send_header('Location','/image-final');self.end_headers()
   else:self.send_response(200);self.end_headers();self.wfile.write(PNG)
  else:self.reply({'data':[{'id':'gpt-image-2'},{'id':'chat-only'}]})
 def do_POST(self):
  body=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
  if self.path=='/v1/images/generations':
   counts['images']+=1;self.reply({'data':[{'b64_json':base64.b64encode(PNG).decode()}],'usage':{'prompt_tokens':3,'completion_tokens':4}})
  elif self.path=='/custom':
   counts['custom']+=1
   if body['description'] not in ['A "quoted" prompt\nwith {{model}} literal','cancel-case']:failures.append('template escaping')
   self.reply({'job':'slow' if body['description']=='cancel-case' else 'fixture','state':'pending'})
  elif self.path=='/chat/completions':
   counts['chat']+=1
   if counts['chat']==1:r={'tools':[{'name':'write_files','args':{'files':{'index.html':'<!doctype html><html><body><h1>Art tool test</h1><img id="hero" src="{{hero}}"></body></html>'}}},{'name':'generate_image','args':{'prompt':'A harmless orange square','path':'assistant.png','referenceFile':'index.html','placeholder':'{{hero}}'}},{'name':'check_project','args':{}},{'name':'build_apk','args':{}}]}
   else:r={'summary':'Saved and built image fixture','files':{}}
   self.reply({'choices':[{'message':{'content':json.dumps(r)},'finish_reason':'stop'}],'usage':{'prompt_tokens':10,'completion_tokens':20}})
  else:self.send_response(404);self.end_headers()
server=http.server.ThreadingHTTPServer(('127.0.0.1',PORT),Handler);threading.Thread(target=server.serve_forever,daemon=True).start();adb('reverse','tcp:'+str(PORT),'tcp:'+str(PORT))
try:
 assert js('typeof artPage === "function"'), 'Drawing UI script not loaded'
 js('(()=>{window.qaArtError="";const previous=window.receive;window.receive=(t,d)=>{if(t==="error")window.qaArtError=d;previous(t,d)};return true})()')
 action('project',{'id':ID,'name':'Art pipeline QA','files':{'index.html':'<!doctype html><html><body><h1>Art test</h1></body></html>'}})
 p={'id':PROFILE,'name':'Fixture images','protocol':'images','endpoint':'http://127.0.0.1:8773','model':'gpt-image-2','key':'fixture-only','size':'1024x1024','keyHeader':'Authorization','keyPrefix':'Bearer '}
 action('artProfile',p);action('artSettings',{'profileId':PROFILE,'allowAI':True})
 action('artGenerate',{'projectId':ID,'prompt':'Fixture','path':'sync.png','referenceFile':'index.html','preview':False})
 wait(lambda s:'/sync.png' in project(s)['files']['index.html']);time.sleep(.3)
 first=counts['images'];action('artGenerate',{'projectId':ID,'prompt':'Fixture','path':'../escape.png','preview':False});time.sleep(.4);assert counts['images']==first
 action('artGenerate',{'projectId':ID,'prompt':'Fixture','path':'sync.png','preview':False});time.sleep(.4);assert counts['images']==first
 # Custom async JSON + URL download, one submission only.
 p.update(protocol='custom',endpoint='http://127.0.0.1:8773/custom',template=json.dumps({'description':'{{prompt}}','model':'{{model}}'}),taskIdPath='job',statusPath='state',successStatus='done',pollUrl='http://127.0.0.1:8773/task/{{taskId}}',urlPath='payload.picture')
 action('artProfile',p);action('artGenerate',{'projectId':ID,'prompt':'A "quoted" prompt\nwith {{model}} literal','path':'custom.png','referenceFile':'index.html','preview':False})
 wait(lambda s:'/custom.png' in project(s)['files']['index.html']);time.sleep(.3);assert counts['custom']==1 and counts['download']==2
 # Pause and resume the same remote task, without a second POST.
 action('artGenerate',{'projectId':ID,'prompt':'cancel-case','path':'resumed.png','referenceFile':'index.html','preview':False})
 st=wait(lambda s:any(j.get('taskId')=='slow' for j in s['artJobs']));job=next(j for j in st['artJobs'] if j.get('taskId')=='slow');action('artCancel',{});wait(lambda s:any(j['id']==job['id'] and j['status']=='paused' for j in s['artJobs']));time.sleep(.4);finish_slow=True
 submitted=counts['custom'];action('artResume',{'jobId':job['id'],'preview':False});wait(lambda s:'/resumed.png' in project(s)['files']['index.html']);time.sleep(.4);assert counts['custom']==submitted
 # Restore sync profile, drive the actual agent tool loop and real native APK builder.
 action('artDelete',{'id':PROFILE});p={'id':PROFILE,'name':'Fixture images','protocol':'images','endpoint':'http://127.0.0.1:8773','model':'gpt-image-2','key':'fixture-only'};action('artProfile',p);action('artSettings',{'profileId':PROFILE,'allowAI':True})
 action('profile',{'id':'qa-art-chat-api','name':'Fixture chat','protocol':'compatible','endpoint':'http://127.0.0.1:8773','model':'fixture','key':'fixture-only','streaming':False,'maxTokens':1024})
 chat={'id':CHAT,'name':'Art tool QA','projectId':ID,'messages':[{'role':'user','content':'Generate and build a fixture illustration'}]};action('chat',chat)
 action('send',{'profileId':'qa-art-chat-api','projectId':ID,'chatId':CHAT,'projectContext':True,'githubResearch':False,'editMode':'auto','messages':chat['messages']})
 st=wait(lambda s:len(next(c for c in s['chats'] if c['id']==CHAT)['messages'])>1,120)
 m=next(c for c in st['chats'] if c['id']==CHAT)['messages'][-1];assert m['edit']['status']=='applied',m['edit'];assert all('error' not in t for t in m['toolCalls']),m['toolCalls'];assert 'assistant.png' in project(st)['binaryFiles'];assert '/assistant.png' in project(st)['files']['index.html'];assert '{{hero}}' not in project(st)['files']['index.html'];assert project(st)['builds'];assert not failures,failures
 # No key in persisted JSON, project or build metadata.
 assert 'fixture-only' not in json.dumps(st)
 out={'result':'PASS','syncBase64':True,'customAsyncURL':True,'resumeWithoutResubmit':True,'noKeyOnDownloadRedirect':True,'templateEscaping':True,'invalidPathsNoSubmission':True,'agentToolLoop':True,'nativeAPKBuild':True,'counts':counts}
 Path('evidence/art-device.json').write_text(json.dumps(out,indent=2));print(json.dumps(out))
finally:server.shutdown();adb('reverse','--remove','tcp:'+str(PORT))
