# Footnote — Context Handover

**Last updated:** 2026-05-02 (end of Build 8 / Phase 1.5)

This doc gets a fresh chat session up to speed in five minutes. Read top to bottom, then read the plan file referenced at the bottom for the full architectural plan.

---

## What Footnote is

A native Android **predictive radial-menu launcher**. One fluid gesture set on an orbit wheel reaches anything on the phone — any app, any system setting, any in-app function — chosen contextually from recent usage and likely-next actions. Long-term vision: summonable from any screen via a system overlay (edge-swipe, volume combo, Quick Settings tile), with an Accessibility Service supplying live foreground-app awareness for sharper predictions.

Repo: https://github.com/lincolnhyde/Footnote-android (single Android Studio project, **separate from `Global-Platform/`** — Footnote is its own thing)

User is a non-technical product owner. Builds are validated on their personal Android phone. Iteration loop is: agent codes → CI builds APK → user installs → user reports back.

---

## Current state — Build 8 just shipped

### What works end-to-end

- Long-press anywhere → orbit wheel appears.
- Top-level slots: curated apps (Spotify / Maps / YouTube — only those that are installed) + "All apps ›" + "Settings ›".
- **Fluid drill**: drag toward a Branch, keep dragging past the wheel ring (~40 dp past) **without lifting** — the ring cross-fades into the branch's children.
- **Fluid pop**: drag inward through the activation ring, then back outward — pops one level. (220 ms cross-cooldown stops drill+pop double-firing.)
- **Release-drill (alternate)**: dropping on a Branch also drills, requires a second long-press to proceed (Phase 1 fallback that still works).
- **Crowded rings paginate**: > 8 slots split into pages of 6 reals + ‹prev/next› nav at fixed left/right wedges + blank no-op wedges to fill the ring. Page-indicator dots render above the wheel between the breadcrumb and the orbit.
- **Selection history persisted** in Room (`footnote.db` → `selections` table). Phase 4 ranker will read this.
- **Settings panels** drillable: ~25 `Settings.ACTION_*` entries (Wi-Fi, Bluetooth, Display, Battery, etc.).
- **All apps** auto-discovered via `PackageManager.queryIntentActivities`, alphabet-grouped. Letter groups paginate when crowded.

### What's deliberately not yet built

- **No system overlay yet** — Footnote is still in-app only. Phase 2 wires `OverlayService` + `WindowManager.addView(ComposeView, ...)`.
- **No Accessibility Service yet** — Phase 3.
- **No predictive ranker yet** — Phase 4. Selection history is collected; it's just not yet read.
- **No icons on slots** — labels only. Phase 2 polish.
- **Curated catalog has only Spotify / Maps / YouTube** — Phase 4 fills out to ~15 (WhatsApp, Gmail, Chrome, Camera, Phone, Messages, Calendar, Photos, Slack, Instagram, X, Files).

---

## Build / install flow

### CI auto-builds every push to `main`

- `.github/workflows/build.yml` runs `./gradlew assembleDebug` on push.
- Passes `-PappVersionCode=${run_number}` so each build has a unique versionCode (Android won't silently skip an upgrade).
- App reads `appVersionName` from the same arg → version label appears in the UI bottom-left as `v0.2.N · build N`. Use this to verify the right APK is running.
- Stable debug keystore is **committed to the repo** (`app/debug.keystore`, password `footnotedebug`). All builds sign with the same key so APKs upgrade cleanly without uninstall.
- A GitHub release is published per build at `https://github.com/lincolnhyde/Footnote-android/releases/tag/prototype-N`.

### User can't reliably download from GitHub release URLs

GitHub redirects release-asset downloads to `release-assets.githubusercontent.com`, which stalls on the user's mobile network. **Mirror every APK** to an orphan branch `dl-build-N` containing only `latest.apk`, served via `raw.githubusercontent.com` (no redirect, downloads at 30 MB/s). Mirror commands at the end of this doc.

URL format the user installs:
```
https://raw.githubusercontent.com/lincolnhyde/Footnote-android/dl-build-N/latest.apk
```

---

## Codebase layout

All sources under `app/src/main/java/com/footnote/app/`:

```
MainActivity.kt              Single activity. Hosts FootnoteScreen() composable
                             with title + version label HUD + OrbitHost.

OrbitWheel.kt                The radial menu Composable. ~250 lines.
                             - pointerInput(Unit) so detector lives across
                               slot changes (Build 7 fix).
                             - LaunchedEffect(slots) drives frameAlpha for
                               the cross-fade on drill/pop/page change.
                             - Drill: pointer past wheelRadius+40dp on a
                               Branch / nav slot → onDrillRequested(idx).
                             - Pop: was-inside-activation → exits outward
                               → onPopRequested().
                             - 220 ms cross-cooldown gates both.

ui/orbit/
  OrbitHost.kt               Frame-stack + page-stack state holder.
                             Composes the breadcrumb + page-dot indicator
                             + OrbitWheel. Wires drill/pop/page handlers.
  Pagination.kt              List<Slot>.paginate(pageIndex) + .pageCount().
                             Layout: prev at idx 6 (left), next at idx 2
                             (right), reals at 0,1,3,4,5,7, blanks fill rest.

catalog/
  Slot.kt                    sealed Slot (Leaf / Branch),
                             sealed SlotAction (LaunchIntent / LaunchApp /
                             Deeplink / SettingsPanel / Pop / PagePrev /
                             PageNext / NoOp), sealed SlotIcon.
  SlotProvider.kt            Tiny interface — slots(ContextSnapshot).
  CatalogRoot.kt             Composes top-level: curated + AllApps + Settings.
  curated/CuratedCatalog.kt  Kotlin DSL of curated apps. Each entry has
                             package name + Slot.Branch with deep-linked
                             leaves. fallbackPackage for when schemes rot.
  providers/
    SystemSettingsProvider.kt   ~25 Settings.ACTION_* leaves.
    InstalledAppsProvider.kt    PackageManager query, alphabet-grouped.
    CuratedAppsProvider.kt      Filters CuratedCatalog by isInstalled.

core/IntentLauncher.kt       Resolves SlotAction → Intent and launches.
                             Handles Deeplink fallback (try uri direct →
                             fall back to launcher intent for fallbackPkg).
                             Toasts "No app for that action" on failure.

ranking/
  ContextSnapshot.kt         data class with foregroundPkg / hourOfDay /
                             dayOfWeek / triggerSource. ContextSnapshot.now().
                             TriggerSource enum (IN_APP / NOTIFICATION /
                             EDGE_SWIPE / VOLUME_COMBO / QS_TILE).
  SelectionLogger.kt         Async Room insert into selections table.
  (SlotRanker.kt — not yet written; Phase 4.)

data/
  SelectionEntity.kt         Room @Entity for selections.
  SelectionDao.kt            Insert / count / recent / pruneBefore queries.
  FootnoteDb.kt              @Database(version=1). Singleton accessor.
                             Uses KSP for code-gen.
```

Build files:
- `build.gradle.kts` — root, declares plugins (AGP 8.2.2, Kotlin 1.9.22, KSP 1.9.22-1.0.17).
- `app/build.gradle.kts` — Compose BOM 2024.02.00, Material3, Room 2.6.1, KSP-driven Room compiler.
- `AndroidManifest.xml` — only `QUERY_ALL_PACKAGES` permission so far. Phase 2 will add `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, etc.

---

## Where to start in the next session

The full implementation plan lives in:
**`/root/.claude/plans/review-this-chat-and-validated-blanket.md`**

It has the complete Phase 1 → Phase 4 breakdown (in-app → overlay service → accessibility → ranking) plus the Phase 1.5 polish that just shipped.

**Default next move: Phase 2 — system-wide overlay.** This is the riskiest piece in the plan; the user is keen to see it work. Outline:
1. Add manifest permissions: `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`.
2. New `overlay/OverlayService.kt` — foreground service, persistent low-priority notification with "Summon" action.
3. New `overlay/OverlayWindow.kt` — `WindowManager.addView(ComposeView, ...)` with manually wired `LifecycleOwner` + `ViewModelStoreOwner` + `SavedStateRegistryOwner`. WindowManager params: `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_NO_LIMITS | FLAG_LAYOUT_IN_SCREEN | FLAG_HARDWARE_ACCELERATED | FLAG_WATCH_OUTSIDE_TOUCH`, `PixelFormat.TRANSLUCENT`. **No `FLAG_NOT_TOUCHABLE`.**
4. New `overlay/OverlayController.kt` — singleton. `summon(TriggerSource)` starts the service with `ACTION_SHOW`.
5. New `ui/onboarding/OnboardingActivity.kt` + screens (Welcome → Overlay grant → Try gesture). Polls `Settings.canDrawOverlays(this)` on resume.
6. `MainActivity.onCreate` checks if overlay perm + service running; routes to `OnboardingActivity` if either missing.
7. Notification carries a `PendingIntent` to summon (`SummonReceiver` for the broadcast). On Android 14+ use `foregroundServiceType="specialUse"`.

Phase 2 ship-test: install over Build 8, grant overlay permission, tap notification "Summon" while inside Chrome → orbit appears, fire a settings panel → returned to Chrome unchanged. Outside-tap dismisses without consuming touches in Chrome.

If the user wants something smaller first, candidate Phase 1.6 polish:
- Render app icons on slots (currently labels only; `SlotIcon.AppIcon` model exists, just not consumed by `OrbitWheel`).
- Animate the page transition more clearly (currently a basic fade; could slide left/right).
- Add the remaining 12 curated apps to `CuratedCatalog`.

---

## Decisions already locked

- **Single-module Android project**, package-split.
- **Accessibility Service from day 1** (Phase 3) — user accepted the rough onboarding for full vision.
- **Scope**: system settings + every installed app + curated sub-orbits for ~15 popular apps.
- **Drill gesture**: drag past the wheel ring (no hover-time, no double-trigger). User picked this in plan review.
- **Crowded ring**: paginate at > 8 slots, 6 reals + 2 nav per page.
- **Curated DSL over JSON** for the curated catalog (intents don't serialize, context-conditional logic is natural in code).
- **Room for selection history**, DataStore (when added) for user prefs.
- **Single-fluid-gesture**: never lift the finger between drill / pop / fire.

---

## Things to watch / known issues

- **Some `Settings.ACTION_*` constants are deprecated** (e.g. `ACTION_PRIVACY_SETTINGS` since API 17). They still compile and `IntentLauncher` toasts gracefully on failure. Phase 4 polish: replace with current equivalents where they exist.
- **Curated deeplinks rot silently.** Spotify/YouTube/Maps URI schemes change. `Deeplink.fallbackPackage` covers the common case. Phase 4: weekly WorkManager health check probing `PackageManager.resolveActivity`.
- **Compose lambdas + data class equality.** Slot.Branch carries a `suspend (ContextSnapshot) -> List<Slot>` lambda. Lambdas don't have stable equality unless captured-once (top-level `val` or stable instance field). The catalog and providers all use stable references; if a future contributor creates Slot.Branch inside a hot path, `LaunchedEffect(slots)` could fire spuriously and cause needless ring fades.
- **OEM Accessibility quirks** (Phase 3, future): Xiaomi MIUI revokes a11y on app update; OnePlus disables on background-kill. Detect at every cold start; banner if disabled.
- **Android 14+ FGS restrictions** (Phase 2): `startForegroundService` from background broadcast must `startForeground()` within 5 s. Plan to launch from `BOOT_COMPLETED` only when user toggles boot-restart on.

---

## Mirror-an-APK procedure (for future builds)

Run after each green CI build, replacing `N` with the build number. Stays on the user's machine — not pushed to remote without intent.

```bash
# 1. Pull the just-built APK from the release
curl -sSL -o /tmp/footnote-prototype-N.apk \
  "https://github.com/lincolnhyde/Footnote-android/releases/download/prototype-N/footnote-prototype-N.apk"

# 2. Create orphan branch with only the APK
cd /home/user/footnote-android
git checkout --orphan dl-build-N
git rm -rf .
cp /tmp/footnote-prototype-N.apk latest.apk
git add latest.apk
git commit --no-gpg-sign -m "apk mirror for build N"

# 3. Push the orphan branch (use a short-lived PAT; never log it)
git push origin dl-build-N

# 4. Back to main
git checkout main
```

User installs from: `https://raw.githubusercontent.com/lincolnhyde/Footnote-android/dl-build-N/latest.apk`

---

## Project rules / norms

- User wants **forward motion**, not analysis paralysis. Make a recommendation, ship a build, iterate from feedback.
- **Don't push without committing**, don't force-push to `main`, don't skip git hooks unless user asks.
- **Tests** — unit tests are valuable but the project doesn't have a JVM test suite yet. The validation loop is "user installs and tries it." When in doubt, ship and let the user be the test.
- **Tone**: short, direct, no padding. The user's frustration spike happened around download-stalling — when something blocks them, fix it before continuing.
- **Plan-mode is on by default** in this workspace per the chat session config. Use `AskUserQuestion` for clarifying forks; use `ExitPlanMode` for plan approval. Never write to files outside the plan file while in plan mode.

---

## Quick sanity-check on session start

1. Read this file.
2. Read the plan: `/root/.claude/plans/review-this-chat-and-validated-blanket.md`.
3. Check the latest build tag: `git ls-remote --tags https://github.com/lincolnhyde/Footnote-android.git | grep prototype | tail -3`.
4. Confirm the user's last working build is the same as the highest `prototype-N` tag.
5. Ask the user what they'd like to work on next.
