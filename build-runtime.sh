#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
JDK="${JAVA_HOME:?Set JAVA_HOME}"
BT="$SDK/build-tools/36.0.0"
mkdir -p out/runtime/classes out/runtime/dex
"$BT/aapt2" compile --dir runtime-res -o out/runtime/res.zip
"$BT/aapt2" link -o out/runtime/base.apk -I "$SDK/platforms/android-36/android.jar" --manifest runtime-manifest.xml out/runtime/res.zip
"$JDK/bin/javac" -encoding UTF-8 -source 8 -target 8 -bootclasspath "$SDK/platforms/android-36/android.jar:$BT/core-lambda-stubs.jar" -d out/runtime/classes runtime-src/studio/jiayu/runtime/*.java shared-src/studio/jiayu/matrix/*.java
find out/runtime/classes -name '*.class' -print0 | xargs -0 "$BT/d8" --min-api 30 --lib "$SDK/platforms/android-36/android.jar" --output out/runtime/dex
python3 - <<'PY'
from pathlib import Path
import shutil,zipfile
shutil.copyfile('out/runtime/base.apk','assets/runtime-template.apk')
with zipfile.ZipFile('assets/runtime-template.apk','a',zipfile.ZIP_DEFLATED) as z:
 for p in Path('out/runtime/dex').glob('*.dex'):z.write(p,p.name)
PY
