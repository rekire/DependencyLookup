#!/bin/bash
set -e
echo Analyse the libraries...
java -jar scanner/build/libs/scanner.jar PlayServices $ANDROID_HOME/extras/google/m2repository/com/google/android/gms
java -jar scanner/build/libs/scanner.jar SupportLibrary $ANDROID_HOME/extras/android/m2repository/com/android/support
echo Compare output with current stored version...
mkdir tmp
mv *.csv *.json tmp
git fetch --unshallow
git checkout origin/gh-pages -q
rm *.csv *.json
mv tmp/* .
rmdir tmp
echo Changes found:
git ls-files --other --directory --exclude-standard --no-empty-directory
