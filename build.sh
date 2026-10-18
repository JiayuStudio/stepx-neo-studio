#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
JDK="${JAVA_HOME:?Set JAVA_HOME}"
BT="$SDK/build-tools/36.0.0"
MODE="${1:-release}"
KEYDIR="${NEO_SIGNING_DIR:?Set NEO_SIGNING_DIR}"
mkdir -p "$KEYDIR" out dist
chmod 700 "$KEYDIR"
if [ ! -f "$KEYDIR/keystore.p12" ]; then
  umask 077
  /usr/bin/openssl rand -hex 24 > "$KEYDIR/password"
  "$JDK/bin/keytool" -genkeypair -keystore "$KEYDIR/keystore.p12" -storepass:file "$KEYDIR/password" -keypass:file "$KEYDIR/password" -alias neo -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=JIAYU Neo Studio, O=JIAYU STUDIO, C=CN' >/dev/null 2>&1
fi
rm -rf out/classes out/dex out/compiled.zip
mkdir -p out/classes out/dex out/gen
cp AndroidManifest.xml out/AndroidManifest.xml
if [ "$MODE" = debug ] || [ "$MODE" = update-test ]; then
python3 - <<'PY'
from pathlib import Path
p=Path('out/AndroidManifest.xml');s=p.read_text().replace('android:usesCleartextTraffic="false"','android:usesCleartextTraffic="true" android:debuggable="true"');p.write_text(s)
PY
fi
# QA bootstrap only: exercise a real upgrade from code 115 to the published release.
if [ "$MODE" = update-test ]; then
  python3 - <<'PYTEST'
from pathlib import Path
p=Path('out/AndroidManifest.xml');p.write_text(p.read_text().replace('versionCode="120"','versionCode="115"').replace('versionName="1.1.10"','versionName="1.1.5"'))
PYTEST
fi
"$BT/aapt2" compile --dir res -o out/compiled.zip
"$BT/aapt2" link -o out/resources.apk -I "$SDK/platforms/android-36/android.jar" --manifest out/AndroidManifest.xml --java out/gen -A assets out/compiled.zip
"$JDK/bin/javac" -classpath vendor/apksig-android-4.4.0.jar -encoding UTF-8 -source 8 -target 8 -bootclasspath "$SDK/platforms/android-36/android.jar:$BT/core-lambda-stubs.jar" -d out/classes src/studio/jiayu/neo/*.java shared-src/studio/jiayu/matrix/*.java
find out/classes -name '*.class' -print0 | xargs -0 "$BT/d8" --min-api 30 --lib "$SDK/platforms/android-36/android.jar" --output out/dex vendor/apksig-android-4.4.0.jar
python3 - <<'PY'
from pathlib import Path
import zipfile,shutil
shutil.copyfile('out/resources.apk','out/unaligned.apk')
with zipfile.ZipFile('out/unaligned.apk','a',zipfile.ZIP_DEFLATED) as z:
 for p in Path('out/dex').glob('*.dex'):z.write(p,p.name)
PY
"$BT/zipalign" -f 4 out/unaligned.apk out/aligned.apk
JAVA_HOME="$JDK" "$BT/apksigner" sign --ks "$KEYDIR/keystore.p12" --ks-key-alias neo --ks-pass "file:$KEYDIR/password" --out "dist/StepXNeoStudio-1.1.10-$MODE.apk" out/aligned.apk
JAVA_HOME="$JDK" "$BT/apksigner" verify --verbose "dist/StepXNeoStudio-1.1.10-$MODE.apk"
shasum -a 256 "dist/StepXNeoStudio-1.1.10-$MODE.apk" > "dist/StepXNeoStudio-1.1.10-$MODE.sha256"
