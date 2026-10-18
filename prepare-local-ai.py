#!/usr/bin/env python3
"""Fetch pinned upstream runtimes; verify every artifact before use."""
import hashlib,json,pathlib,urllib.request,zipfile,os
root=pathlib.Path(__file__).resolve().parent
for item in json.loads((root/'vendor/local-ai.lock.json').read_text()):
 p=root/item['path'];p.parent.mkdir(parents=True,exist_ok=True)
 if not p.is_file() or hashlib.sha256(p.read_bytes()).hexdigest()!=item['sha256']:
  temp=p.with_suffix(p.suffix+'.download')
  with urllib.request.urlopen(item['url'],timeout=90) as response,temp.open('wb') as out:
   while chunk:=response.read(1024*1024):out.write(chunk)
  if hashlib.sha256(temp.read_bytes()).hexdigest()!=item['sha256']:temp.unlink();raise SystemExit('Dependency checksum mismatch')
  temp.replace(p)
 if item.get('extract'):
  with zipfile.ZipFile(p) as archive:
   for member,destination in item['extract'].items():
    output=root/destination;output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(archive.read(member))
print('Pinned local AI dependencies ready.')
