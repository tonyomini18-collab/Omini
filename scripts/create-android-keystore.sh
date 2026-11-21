#!/usr/bin/env bash
# Run this ONCE on your Mac. It creates the Android signing key and stores it in GitHub secrets.
# Needs: keytool (comes with Java) and the GitHub CLI (brew install gh, then gh auth login).
# KEEP the generated omini-upload-key.jks file and the password somewhere safe.
# If you lose them you can never update the installed Android app again.
set -euo pipefail

REPO="${1:-tonyomini18-collab/Omini}"
JKS="omini-upload-key.jks"

read -r -s -p "Choose a password for the key (6+ characters): " PASS
echo
if [ "${#PASS}" -lt 6 ]; then echo "Password too short"; exit 1; fi

keytool -genkeypair -v -keystore "$JKS" -alias upload -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=Omini POS, O=Omini, C=SS"

base64 < "$JKS" | tr -d '\n' | gh secret set ANDROID_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$PASS" | gh secret set ANDROID_STORE_PASSWORD --repo "$REPO"
printf '%s' "$PASS" | gh secret set ANDROID_KEY_PASSWORD --repo "$REPO"

echo "Done. Secrets saved to $REPO. Back up $JKS and your password now."
