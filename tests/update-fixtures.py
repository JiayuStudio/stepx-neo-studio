import os
"""Prepare APK verifier fixtures on a debug bootstrap, without changing user data."""
from pathlib import Path
import subprocess,shutil,os
root=Path(__file__).resolve().parents[1]
os.chdir(root)
adb=os.environ.get('ADB','adb')
bt=os.path.join(os.environ['ANDROID_SDK_ROOT'],'build-tools',os.environ.get('ANDROID_BUILD_TOOLS_VERSION','36.0.0'))
jdk=os.environ['JAVA_HOME']
os.environ['JAVA_HOME']=jdk
d=Path('out/update-tests');d.mkdir(exist_ok=True)
shutil.copy('dist/StepXNeoStudio-1.1.4-release.apk',d/'valid.apk')
shutil.copy('dist/StepXNeoStudio-1.1.4-debug.apk',d/'debug.apk')
shutil.copy('../v1.1.3.opa/dist/StepXNeoStudio-1.1.3-release.apk',d/'older.apk')
shutil.copy('assets/runtime-template.apk',d/'wrong-package.apk')
data=bytearray((d/'valid.apk').read_bytes());data[len(data)//2]^=1;(d/'tampered.apk').write_bytes(data)
key=d/'fixture.p12'
if not key.exists():subprocess.run([jdk+'/bin/keytool','-genkeypair','-keystore',str(key),'-storepass','fixture-only-password','-keypass','fixture-only-password','-alias','fixture','-keyalg','RSA','-validity','1','-dname','CN=Updater Test'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
subprocess.run([bt+'/apksigner','sign','--ks',str(key),'--ks-pass','pass:fixture-only-password','--out',str(d/'wrong-signer.apk'),str(d/'valid.apk')],check=True,stdout=subprocess.DEVNULL)
subprocess.run([adb,'shell','run-as','studio.jiayu.neo','mkdir','-p','files/update-tests'],check=True)
for p in d.glob('*.apk'):
 with p.open('rb') as f:subprocess.run([adb,'shell','run-as','studio.jiayu.neo','sh','-c',"'cat > files/update-tests/"+p.name+"'"],stdin=f,check=True)
print('Six APK fixtures ready')
