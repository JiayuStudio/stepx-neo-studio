import os
import subprocess,json,time
from pathlib import Path
adb=os.environ.get('ADB','adb')
def shell(*args):return subprocess.check_output([adb,'shell',*args],text=True).strip()
def cdp(code):return json.loads(subprocess.check_output(['node','tests/cdp.mjs'],input=code,text=True))
old={k:shell('settings','get','global',k) for k in ['airplane_mode_on','wifi_on','mobile_data']}
report={'before':old}
try:
 shell('cmd','connectivity','airplane-mode','enable');shell('svc','wifi','disable');time.sleep(2)
 report['during']={k:shell('settings','get','global',k) for k in old}
 report['connectivity']=shell('dumpsys','connectivity')
 cdp('send("buildApk",{id:"48ee5dae-e57b-46bb-b2cf-d0a44d12f0f0"});"started"')
 for _ in range(15):
  time.sleep(1)
  b=cdp('JSON.parse(Neo.read()).projects.find(p=>p.name==="Neo 功能实验室").builds')
  if len(b)>=2:report['build']=b[-1];break
finally:
 shell('cmd','connectivity','airplane-mode','enable' if old['airplane_mode_on']=='1' else 'disable')
 shell('svc','wifi','enable' if old['wifi_on']=='1' else 'disable')
 shell('svc','data','enable' if old['mobile_data']=='1' else 'disable')
 report['after']={k:shell('settings','get','global',k) for k in old}
Path('evidence/offline-device-build.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
print(json.dumps({k:v for k,v in report.items() if k!='connectivity'},ensure_ascii=False))
print('\n'.join(l for l in report.get('connectivity','').splitlines() if 'Active default network' in l))
