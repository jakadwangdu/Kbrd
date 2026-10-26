# Kbrd

A minimal Android keyboard with voice typing, a full emoji library, clipboard, number pad and an AI assistant that runs on **your own API key**.

## Get the APK
1. Push to `main`. The "Kbrd debug build and release" workflow runs `gradle assembleDebug`.
2. When the build passes, `Kbrd-debug.apk` is published to Releases as `v1.0.<run number>`.
3. The website download button always points to the newest release.

## Install
Download the APK, allow installs from your browser, open Kbrd, tap "1. Enable keyboard", then "2. Switch to Kbrd". Debug builds get a new signature on every run, so uninstall the previous version before installing a new one.

## AI assistant
Open the app, tap "4. Set up AI", choose a provider (Groq, OpenRouter, Google Gemini, Together, Mistral or any OpenAI-compatible address), paste your key, tap Load models, Save, then Test. In the keyboard, tap the sparkle icon. Text is sent only when you tap an action, never in password fields, and the key is encrypted with the Android Keystore.

## Website
`docs/` holds the site (landing page, blog, privacy, sitemap). In repo settings, open Pages, pick `main` and `/docs`.

## Not included
- Inserting clipboard screenshots into other apps.
