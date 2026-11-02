#!/bin/bash
# Flat files ko Android project ke sahi folders mein rakhta hai (GitHub Actions ye khud chalata hai)
J=app/src/main/java/com/smartguard/app
mkdir -p $J app/src/main/res/values app/src/main/res/drawable
[ -f root_build.gradle.kts ] && mv root_build.gradle.kts build.gradle.kts
[ -f app_build.gradle.kts ] && mv app_build.gradle.kts app/build.gradle.kts
[ -f AndroidManifest.xml ] && mv AndroidManifest.xml app/src/main/
[ -f strings.xml ] && mv strings.xml app/src/main/res/values/
[ -f ic_launcher.xml ] && mv ic_launcher.xml app/src/main/res/drawable/
for f in *.kt; do
  [ -f "$f" ] && mv "$f" $J/
done
echo "Project structure ready"
