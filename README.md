# Schedule Maker

A daily and weekly planner for Android, with piano and study plans made with Claude, focus mode that blocks distracting apps, and reminders.

## Install on your phone

1. On your Android phone, open **https://github.com/IroTheAngel/schedule-maker/releases/latest**
2. Tap **ScheduleMaker.apk** to download it, then open it.
3. If Android asks, allow your browser to **install unknown apps**, then tap **Install**.

New versions install over the old one, and your schedule is kept.

## Turn on focus blocking (once)

Focus mode sends you back to Schedule Maker when you open YouTube, TikTok and so on.
The first time you press **Focus**, the app asks for two permissions and opens the right settings page:

1. **Usage access**: find Schedule Maker and turn it on.
2. **Display over other apps**: turn it on for Schedule Maker.

## How it is built

- `www/index.html`: the whole app (HTML, CSS and JavaScript in one file)
- `android/`: the Android project made with [Capacitor](https://capacitorjs.com)
  - `FocusPlugin.java`: opens the Claude app, receives text shared into the app, turns focus on and off
  - `BlockerService.java`: runs during focus and brings you back when a blocked app opens
- `.github/workflows/build.yml`: every push to `main` builds the APK and publishes it as a release

The signing key in `android/app/schedulemaker.keystore` is only for this personal app, so updates install over earlier versions.
