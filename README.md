# Schedule Maker

A daily and weekly planner for Android, with piano and study plans made with Claude, focus mode that blocks distracting apps, and reminders.

## Install on your phone

1. On your Android phone, open **https://github.com/IroTheAngel/schedule-maker/releases/latest**
2. Tap **ScheduleMaker.apk** to download it, then open it.
3. If Android asks, allow your browser to **install unknown apps**, then tap **Install**.

New versions install over the old one, and your schedule is kept.

## Turn on focus blocking (once)

Focus mode can send you back to Schedule Maker when you open YouTube, TikTok and so on.
The first time you press **Focus**, the app shows you how:

1. Open **Settings → Accessibility → Installed apps** (or Downloaded apps) → **Schedule Maker focus** → turn it on.
2. If the switch is greyed out: **Settings → Apps → Schedule Maker → ⋮ → Allow restricted settings**, then try step 1 again.

## How it is built

- `www/index.html`: the whole app (HTML, CSS and JavaScript in one file)
- `android/`: the Android project made with [Capacitor](https://capacitorjs.com)
  - `FocusPlugin.java`: opens the Claude app, receives text shared into the app, turns focus on and off
  - `FocusService.java`: the accessibility service that blocks apps during focus
- `.github/workflows/build.yml`: every push to `main` builds the APK and publishes it as a release

The signing key in `android/app/schedulemaker.keystore` is only for this personal app, so updates install over earlier versions.
