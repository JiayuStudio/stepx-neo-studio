from http.server import ThreadingHTTPServer,BaseHTTPRequestHandler
import json
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_POST(self):
  data=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
  assert data['model']=='fixture' and data['stream']==False
  assert data['max_tokens']==128
  if self.path.startswith('/anthropic'):
   assert self.headers['x-api-key']=='fixture-secret' and data['system']=='system'
   r={'content':[{'type':'text','text':'anthropic reply'}],'usage':{'input_tokens':10,'output_tokens':20,'cache_read_input_tokens':80,'cache_creation_input_tokens':30}}
  else:
   assert self.headers['Authorization']=='Bearer fixture-secret'
   assert data['messages'][0]['role']=='system'
   r={'choices':[{'message':{'content':'fixture reply'}}],'usage':{'prompt_tokens':100,'completion_tokens':40,'prompt_tokens_details':{'cached_tokens':60},'completion_tokens_details':{'reasoning_tokens':10}}}
  if self.path.startswith('/thinking'):assert data['thinking']=={'type':'disabled'}
  if self.path.startswith('/incomplete'):
   r['choices']=[{'finish_reason':'length','message':{'content':'','reasoning_content':'partial analysis'}}]
  if self.path.startswith('/missing'):r.pop('usage')
  if self.path.startswith('/bad'):self.send_response(401);self.end_headers();return
  if self.path.startswith('/redirect'):self.send_response(307);self.send_header('Location','http://127.0.0.1:8768/leak');self.end_headers();return
  self.send_response(200);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(json.dumps(r).encode())
ThreadingHTTPServer(('127.0.0.1',8768),Handler).serve_forever()
