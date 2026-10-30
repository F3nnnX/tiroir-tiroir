#!/bin/bash
# Compilation reproductible de Tiroir Tiroir (sans Gradle). Sortie : build/tiroir-tiroir.apk
set -euo pipefail
cd "$(dirname "$0")"
SDK=$HOME/android-sdk
BT=$SDK/build-tools/34.0.0
JAR=$SDK/platforms/android-34/android.jar
[ -f cle.jks ] || { echo "cle.jks absente (voir README)"; exit 1; }
[ -f res/mipmap-xhdpi/icone.png ] || java outils/Icone.java res/mipmap-xhdpi/icone.png
rm -rf build
mkdir -p build/classes
javac --release 8 -nowarn -classpath "$JAR" -d build/classes $(find src -name '*.java')
"$BT/d8" --min-api 26 --lib "$JAR" --output build $(find build/classes -name '*.class')
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml --min-sdk-version 26 --target-sdk-version 34 \
    -o build/nonsigne.apk build/res.zip
zip -qj build/nonsigne.apk build/classes.dex
"$BT/zipalign" -f -p 4 build/nonsigne.apk build/aligne.apk
"$BT/apksigner" sign --ks cle.jks --ks-pass file:cle.mdp --out build/tiroir-tiroir.apk build/aligne.apk
"$BT/apksigner" verify build/tiroir-tiroir.apk && echo "OK : build/tiroir-tiroir.apk"
