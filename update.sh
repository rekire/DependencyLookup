#!/bin/bash
set -e
echo Analyse the libraries...
java -jar scanner/build/libs/scanner.jar PlayServices $ANDROID_HOME/extras/google/m2repository/com/google/android/gms
java -jar scanner/build/libs/scanner.jar SupportLibrary $ANDROID_HOME/extras/android/m2repository/com/android/support
echo Compare output with current stored version...
git clone --depth=50 --branch=gh-pages git@github.com:rekire/DependencyLookup.git gh-pages
rm gh-pages/*.csv gh-pages/*.json
mv *.csv *.json gh-pages

# prepare the key
chmod 600 bot_id_rsa
eval `ssh-agent -s`
ssh-add bot_id_rsa

# look for changes
cd gh-pages
touch `date +Test-%H%M%S`
if [ `git ls-files --other --directory --exclude-standard | wc -l` -eq 0 ]; then
  echo No changes. Done.
  exit 0
fi
echo Changes found, creating patch...
git add *
git config user.name "Travis CI"
git config user.email "bot@reki.re"
git commit -m "Automatic documentation update"
git push origin gh-pages
