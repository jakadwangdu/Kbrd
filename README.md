# Keymold keyboard (Android)

A real Android keyboard (InputMethodService) that shows the Keymold UI from `app/src/main/assets/keyboard.html` in a WebView.

## Get the APK
1. Create an empty GitHub repo, then from this folder: `git init && git add . && git commit -m "Keymold keyboard" && git branch -M main && git remote add origin <repo-url> && git push -u origin main`
2. Open the repo's Actions tab. The "Android debug build and release" workflow runs `gradle assembleDebug`.
3. When the build passes on `main`, the release job publishes `Keymold-debug.apk` to the Releases page as `v1.0.<run number>`.
4. Pull requests only build and upload the APK as a workflow artifact; they do not create a release.

## Install on the phone
Download the APK from Releases, allow installs from your browser, open Keymold, tap "1. Enable keyboard", turn Keymold on, then tap "2. Switch to Keymold".

## Update the UI
Edit `app/src/main/assets/keyboard.html` and push to `main`. A new release is built automatically.

## Not included yet
- Native voice typing (needs RECORD_AUDIO and SpeechRecognizer; the web speech API does not exist in Android WebView).
- Inserting clipboard screenshots into other apps.
