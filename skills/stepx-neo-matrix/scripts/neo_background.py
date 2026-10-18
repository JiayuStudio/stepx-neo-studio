#!/usr/bin/env python3
"""Explicit, reversible per-app Neo freezer development control; no global changes."""
import argparse, json, re, subprocess
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True)
p.add_argument('--package',required=True);p.add_argument('action',choices=['status','enable','disable'])
a=p.parse_args()
if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+',a.package):p.error('Invalid package name')
def adb(*args):
 r=subprocess.run([a.adb,'-s',a.serial,*args],capture_output=True,text=True,timeout=20)
 if r.returncode:raise SystemExit(r.stderr.strip() or r.stdout.strip())
 if 'Exception occurred' in r.stdout or 'Permission Denial' in r.stdout:raise SystemExit(r.stdout)
 return r.stdout
rows=adb('shell','pm','list','packages','-U',a.package).splitlines()
m=next((re.fullmatch(r'package:'+re.escape(a.package)+r' uid:(\d+)',x.strip()) for x in rows if x.startswith('package:'+a.package+' ')),None)
if not m:raise SystemExit('Exact installed package not found')
uid=int(m.group(1))
if uid%100000<10000:raise SystemExit('Refusing a system UID')
packages=adb('shell','pm','list','packages','--uid',str(uid)).splitlines()
if [x.split()[0] for x in packages]!=['package:'+a.package]:raise SystemExit('Shared UID: refusing to affect another package')
def listed():
 s=adb('shell','dumpsys','activity','freeze','whitelist')
 if 'freeze white apps:' not in s:raise SystemExit('Unsupported Neo freezer diagnostic')
 return bool(re.search(r'^\s*freeze white app: '+re.escape(a.package)+r'\s*$',s,re.M))
before=listed()
if a.action=='enable':
 if not before:
  result=adb('shell','dumpsys','activity','freeze','whiteAdd',a.package)
  if 'white app list:' not in result:raise SystemExit('Missing enable acknowledgement')
 if not listed():raise SystemExit('Whitelist verification failed')
 result=adb('shell','dumpsys','activity','freeze','doUnfreeze',str(uid))
 if 'set app freeze  : false uid '+str(uid) not in result:raise SystemExit('Unfreeze not acknowledged; whitelist may already be enabled. Run status.')
elif a.action=='disable' and before:
 adb('shell','dumpsys','activity','freeze','whiteRemove',a.package)
 if listed():raise SystemExit('Entry still present; inspect before retrying')
print(json.dumps({'package':a.package,'uid':uid,'action':a.action,'previouslyAllowed':before,'allowed':listed()},ensure_ascii=False))
