#!/bin/bash
set -e
echo Analyse the libraries...
java -jar scanner/build/libs/scanner.jar PlayServices $ANDROID_HOME/extras/google/m2repository/com/google/android/gms
java -jar scanner/build/libs/scanner.jar SupportLibrary $ANDROID_HOME/extras/android/m2repository/com/android/support
echo Compare output with current stored version...
git clone --depth=50 --branch=gh-pages https://github.com/rekire/DependencyLookup.git gh-pages
rm gh-pages/*.csv gh-pages/*.json
mv *.csv *.json gh-pages
cd gh-pages
touch `date +Test-%H%M%S`
if [ -z `git diff --exit-code` ]; then
  echo No changes. Done.
  exit 0
fi
echo Changes found, creating patch...
git add *
git config user.name "Travis CI"
git config user.email "bot@reki.re"
git commit -m "Automatic documentation update"

# Get the deploy key by using Travis's stored variables to decrypt deploy_key.enc
ENCRYPTED_KEY_VAR="encrypted_${ENCRYPTION_LABEL}_key"
ENCRYPTED_IV_VAR="encrypted_${ENCRYPTION_LABEL}_iv"
ENCRYPTED_KEY=${!ENCRYPTED_KEY_VAR}
ENCRYPTED_IV=${!ENCRYPTED_IV_VAR}
openssl aes-256-cbc -K $ENCRYPTED_KEY -iv $ENCRYPTED_IV -in deploy_key.enc -out deploy_key -d
chmod 600 bot_id_rsa
eval `ssh-agent -s`
ssh-add bot_id_rsa

# Now that we're all set up, we can push.
git push origin gh-pages
