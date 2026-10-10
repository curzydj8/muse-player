#!/bin/bash
# Muse Player v2 构建脚本（极简：无 Service，MediaPlayer 直接在 Activity）
set -e
cd "$(dirname "$0")"

export JAVA_HOME=~/jdk17
export PATH=$JAVA_HOME/bin:$PATH
BT=~/android-sdk/build-tools/34.0.0
PLATFORM=~/android-sdk/platforms/android-34/android.jar
OUT=muse-player.apk
KEYSTORE=../muse-offline-player/muse-release.keystore
KS_PASS=museplayer

echo "[1/6] aapt2 compile..."
rm -rf build && mkdir -p build/compiled_res build/gen build/classes
$BT/aapt2 compile --dir app/src/main/res -o build/compiled_res/

echo "[2/6] aapt2 link..."
$BT/aapt2 link -o build/base.apk -I $PLATFORM \
  --manifest app/src/main/AndroidManifest.xml \
  --min-sdk-version 24 --target-sdk-version 34 \
  --java build/gen build/compiled_res/*.flat

echo "[3/6] javac..."
javac -encoding UTF-8 -source 8 -target 8 -cp $PLATFORM -d build/classes \
  $(find build/gen app/src/main/java -name "*.java") 2>&1 | tee build/javac.log | grep -v "bootstrap"; if grep -q "error:" build/javac.log; then echo "JAVAC FAILED"; exit 1; fi

echo "[4/6] d8 (dex)..."
$BT/d8 --lib $PLATFORM --output build/ $(find build/classes -name "*.class")

echo "[5/6] 打包 classes.dex..."
cp build/base.apk build/unsigned.apk
(cd build && zip -q -j unsigned.apk classes.dex)

echo "[6/6] zipalign + apksigner..."
$BT/zipalign -f 4 build/unsigned.apk build/aligned.apk
$BT/apksigner sign --ks $KEYSTORE --ks-pass pass:$KS_PASS \
  --key-pass pass:$KS_PASS --out $OUT build/aligned.apk

echo "构建完成：$OUT"
ls -lh $OUT
