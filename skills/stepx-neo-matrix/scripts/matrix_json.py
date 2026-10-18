#!/usr/bin/env python3
"""Generate a deterministic Neo test animation or validate the portable format."""
import argparse,json,pathlib
p=argparse.ArgumentParser(description=__doc__);p.add_argument('action',choices=['sample','validate']);p.add_argument('file',type=pathlib.Path);a=p.parse_args()
if a.action=='sample':
 frames=[]
 for shape in range(2):
  pixels=[0]*391
  for y in range(23):
   for x in range(17):
    lit=((x in (1,15) and 3<=y<=19) or (y in (3,19) and 1<=x<=15)) if shape==0 else (3<=y<=19 and (x==y-3 or x==19-y))
    if lit:pixels[y*17+x]=255
  frames.append({'durationMs':1000,'pixels':pixels})
 with a.file.open('x',encoding='utf-8') as f:json.dump({'schema':'stepx-matrix/1','width':17,'height':23,'loop':True,'frames':frames},f)
text=a.file.read_text(encoding='utf-8');s=json.loads(text)
def require(value,message):
 if not value:raise SystemExit(message)
require(len(text)<=350000,'File too large')
require(isinstance(s,dict) and s.get('schema')=='stepx-matrix/1','Invalid schema')
require(type(s.get('width')) is int and s['width']==17 and type(s.get('height')) is int and s['height']==23,'Invalid dimensions')
require(type(s.get('loop')) is bool,'loop must be boolean')
f=s.get('frames');require(isinstance(f,list) and 1<=len(f)<=120,'Invalid frame count')
for i,v in enumerate(f):
 require(isinstance(v,dict),'Frame must be an object');d=v.get('durationMs');pixels=v.get('pixels')
 require(type(d) is int and 200<=d<=60000,f'Frame {i}: invalid duration')
 require(isinstance(pixels,list) and len(pixels)==391 and all(type(x) is int and 0<=x<=255 for x in pixels),f'Frame {i}: invalid pixels')
print(json.dumps({'valid':True,'frames':len(f),'durationMs':sum(v['durationMs'] for v in f),'loop':s['loop']}))
