# Footnote — Orbit Gesture Prototype

Minimal Android prototype to validate the orbit-wheel gesture on real phones.
You long-press anywhere on screen, drag outward to one of six slots, and
release. The selected slot launches an app (Phone, Camera, Browser, Messages,
Maps, Settings).

This is **not** a launcher yet. It's a regular app you open from the icon to
feel the gesture in isolation. Day-1 goal: does the gesture feel good after
ten uses?

## Phone-only build path (no laptop required)

You'll create a GitHub repo from your phone, push this code, and let GitHub
Actions build the APK. You then download the APK on your phone and install.

### 1. Create an empty repo on GitHub

On your phone, open `https://github.com/new`:

- Repo name: `footnote-android`
- Visibility: Private
- Do **not** initialize with README, .gitignore, or license

### 2. Push this folder to it

From a terminal that has access to this directory (`/home/user/footnote-android/`):

```bash
cd /home/user/footnote-android
git init -b main
git add .
git commit -m "feat: orbit gesture prototype v0.1"
git remote add origin https://github.com/<your-username>/footnote-android.git
git push -u origin main
```

### 3. Wait for the build

On your phone, open the repo's **Actions** tab. The "Build APK" workflow
runs automatically on push. First build takes 3-5 minutes (downloads SDK).

### 4. Download and install the APK

When the run finishes:

1. Open the run, scroll to the bottom, tap **footnote-debug-apk** to download.
   (GitHub serves the artifact as a zip.)
2. Open the zip in your phone's file manager. Extract `app-debug.apk`.
3. Tap the APK. Android will prompt you to allow "Install from unknown sources"
   for whichever app you opened the APK from (Files, Drive, browser).
4. Install. Tap the Footnote icon in your app drawer.

### 5. Use it

- Long-press anywhere on the dark screen.
- Without lifting, drag your finger outward.
- Each slot label fades in around the press point. Drag toward one — it
  highlights when the finger crosses the activation ring.
- Lift to fire. The slot launches its app.
- Lift inside the activation ring to cancel.

The corner counter shows orbit uses this session and the last slot fired.

## Day-1 retention test

The chat's recommendation: get this on **20 phones for a week**, watch whether
people are still using the orbit wheel on day three without prompting.

For yourself: aim for at least 10 uses per day for the first three days. If
the gesture feels awkward by day two, the product needs a different
interaction. If it feels natural by day three, the launcher is worth building.

## What's intentionally missing

- **Not registered as a HOME launcher.** Adds friction (default-app prompt,
  uninstall difficulty); not worth it for a one-week gesture test.
- **Slots are hardcoded.** No customization. Six is the count; six common
  destinations chosen for v0.
- **No analytics, no cloud.** Counter is in-memory only. For a 20-tester
  validation you read it off the screen and message the result.
- **No icons in the wheel.** Text labels only. Faster to iterate; visuals
  come after the gesture is proven.
- **No Pro features, no billing.** Pricing experiments come after retention
  is real.

## Iteration ideas if v0 feels right

- Add icons in slots (use `PackageManager.getApplicationIcon`)
- Slot count: try 4 vs 6 vs 8 — which converts to muscle memory fastest?
- Activation radius: tune `72.dp` in `OrbitWheel.kt` if too tight/loose
- Long-press timeout: customize via local override of `viewConfiguration`
- Edge swipe trigger: long-press from screen edge, wheel slides in
- Persist slot config across launches (start of "real" launcher work)

## File map

```
app/src/main/java/com/footnote/app/
  MainActivity.kt    # Full-screen scaffold, slot config, counter HUD
  OrbitWheel.kt      # Gesture detection + Canvas drawing of the wheel
app/src/main/AndroidManifest.xml   # Single-activity launcher entry
app/build.gradle.kts                # Compose + Material3 deps
.github/workflows/build.yml         # Auto-builds debug APK on push
```

## Build environment (for reference)

- Kotlin 1.9.22, AGP 8.2.2
- compileSdk 34, minSdk 26, targetSdk 34
- Compose BOM 2024.02.00
- Java 17 (used by the GitHub Actions runner)
