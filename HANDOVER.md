# Footnote — Context Handover

**Last updated:** 2026-05-02 (Build 16 shipped: dwell-on-Branch + bloom-from-slot)

This doc gets a fresh chat session up to speed in five minutes. Read top to bottom, then read the plan file referenced below for the full architectural plan and decision history.

---

## What Footnote is

A native Android **predictive radial-menu launcher**. One fluid gesture set on an orbit wheel reaches anything on the phone — any app, any system setting, any in-app function — chosen contextually from recent usage and likely-next actions. Long-term vision: summonable from any screen via a system overlay (edge-swipe, volume combo, Quick Settings tile), with an Accessibility Service supplying live foreground-app awareness for sharper predictions.

Repo: https://github.com/lincolnhyde/Footnote-android (separate Android Studio project, NOT in `Global-Platform/`)

User is a non-technical product owner. Builds are validated on their personal Android phone. The loop: agent codes → CI builds APK → user installs → user reports back.

---

## First thing to do in the new session

**Latest shipped APK (Build 16, dwell + bloom):**
https://raw.githubusercontent.com/lincolnhyde/Footnote-android/dl-build-16/latest.apk

The user installed this just before opening the new session. Greet them, ask how the dwell-on-Branch + bloom-from-slot model feels, and direct next steps based on their answer:
- **Feels good:** move to Phase 2 (system overlay) per "Where to start" below.
- **Needs tuning:** dial-twist options listed in "Option C — Phase 1.7 polish."
- **Still feels wrong:** revisit gesture model. The research doc trail in the plan file lists 4 alternatives we didn't pick (concentric ring, marking-menu, slot-bloom inline, velocity-based).

If you push any code, before sending the user a URL:
1. Confirm CI green: `curl -sS -H "Authorization: Bearer $GITHUB_PAT" "https://api.github.com/repos/lincolnhyde/Footnote-android/actions/runs?per_page=3&branch=main" | grep -E '"(run_number|status|conclusion|head_sha)"' | head -12`
2. If green: pull the APK and mirror to `dl-build-N` per the procedure below.
3. If red: pull `https://raw.githubusercontent.com/lincolnhyde/Footnote-android/ci-log-N/build.log` and grep for `^e: ` to find the Kotlin compile error.

---

## Where the gesture model is right now

Long history, condensed:

- **Build 7 (Phase 1):** flat → hierarchical Slot model (Leaf / Branch). Drilling required releasing on a Branch then long-pressing again. Worked but broke the "single fluid gesture" promise.
- **Build 8 → 13 (Phase 1.5):** drag-past-the-ring drill. Drag your finger past the wheel ring on a Branch (40 dp past at first, then 14 dp) → drill. Plus pop (drag inward through activation, back outward) and pagination (>8 slots → 6 reals + ‹prev/›next + blanks per page).
- **Build 14 (Phase 1.6):** tightened drill threshold + sharper visual + haptic on drill/pop.
- **Build 15:** failed in CI on a stray import (`kotlinx.coroutines.withFrameMillis` doesn't exist; the function lives in `androidx.compose.runtime`). Fixed in next push.
- **Build 16 (Phase 1.7) — SHIPPED, INSTALLED ON USER'S PHONE:** **drag-past-ring abandoned entirely.** Replaced with:
  - **Dwell-on-Branch:** pause your finger on a Branch (or page-nav) slot for ~280 ms; a progress arc fills around the slot label; at completion drill fires with a haptic tick.
  - **Bloom-from-slot:** when drill fires, the children render around the **slot's screen position** (where the finger already is), not the original wheel center. Mental model: the slot itself reveals its contents.
  - **Release on Branch still drills** (with the same bloom origin), so impatient users don't hit dead ends.
  - Pop preserved (drag inward through activation, back outward). Pagination preserved.

User feedback that drove Phase 1.7: *"It doesn't really work we need a better way to make the second on further selections, look into this further and investigate better ways to achieve this"*. Research recommended dwell + bloom (Steam Big Picture, AssistiveTouch, macOS dock stacks all use variants of this). User picked "bloom from the slot" when asked.

If Build 15 feels right, **Phase 2 (system overlay) is the next move.**

---

## Build / install flow

CI auto-builds every push to `main`. Each build:
- Bumps `versionCode` to `github.run_number` so Android can't silently skip the upgrade.
- Signs with the stable debug keystore committed at `app/debug.keystore` (password `footnotedebug`).
- Publishes a release at `https://github.com/lincolnhyde/Footnote-android/releases/tag/prototype-N`.
- **On failure**, dumps the full gradle log to a fresh orphan branch `ci-log-N` so the next session can read it via `raw.githubusercontent.com` (Azure blob storage where GitHub keeps step logs is blocked from this sandbox).

### User can't reliably download from GitHub release URLs

GitHub redirects release-asset downloads to `release-assets.githubusercontent.com`, which stalls on the user's mobile network. **Mirror every successful APK** to an orphan branch `dl-build-N` containing only `latest.apk`, served via `raw.githubusercontent.com` (no redirect, ~30 MB/s).

User installs from:
```
https://raw.githubusercontent.com/lincolnhyde/Footnote-android/dl-build-N/latest.apk
```

### Mirror-an-APK procedure

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

# 3. Push the orphan branch (PAT required; never log it)
git push origin dl-build-N

# 4. Back to main
git checkout main
```

---

## Codebase layout

All sources under `app/src/main/java/com/footnote/app/`:

```
MainActivity.kt              Single activity. Hosts FootnoteScreen() composable
                             with title + version label HUD + OrbitHost.

OrbitWheel.kt                The radial menu Composable. ~280 lines.
                             - pointerInput(Unit) so detector lives across
                               slot changes and frame transitions.
                             - LaunchedEffect(slots) drives frameAlpha for
                               the cross-fade on drill/pop/page change.
                             - LaunchedEffect(hoveredSlotIdx) drives the
                               dwell timer using withFrameMillis. On
                               completion, computes slot screen position and
                               calls onDrillRequested(idx, slotPos).
                             - anchorOverride: Offset? param — when non-null,
                               wheel centers there (sub-frame). When null,
                               falls back to internal pressStart (root frame).
                             - Pop: was-inside-activation → exits outward
                               → onPopRequested().
                             - Haptic: KEYBOARD_TAP on drill and pop fire.

ui/orbit/
  OrbitHost.kt               Frame-stack + page-stack + anchor-stack state
                             holder. Composes breadcrumb + page-dot
                             indicator + OrbitWheel. Wires drill / pop /
                             page handlers. Anchor-overrides parallel to
                             frames so each level remembers where it bloomed
                             from.
  Pagination.kt              List<Slot>.paginate(pageIndex) + .pageCount().
                             Layout: 8-wedge ring with prev at idx 6 (left),
                             next at idx 2 (right), reals at 0,1,3,4,5,7,
                             blanks (NoOp) fill rest on the last page.

catalog/
  Slot.kt                    sealed Slot (Leaf / Branch),
                             sealed SlotAction (LaunchIntent / LaunchApp /
                             Deeplink / SettingsPanel / Pop / PagePrev /
                             PageNext / NoOp), sealed SlotIcon.
  SlotProvider.kt            Tiny interface — slots(ContextSnapshot).
  CatalogRoot.kt             Composes top-level: curated + AllApps + Settings.
  curated/CuratedCatalog.kt  Kotlin DSL of curated apps. Each entry is a
                             package name + Slot.Branch with deep-linked
                             leaves. fallbackPackage for when schemes rot.
                             Today: Spotify / Maps / YouTube. Phase 4
                             expands to 15.
  providers/
    SystemSettingsProvider.kt   ~25 Settings.ACTION_* leaves.
    InstalledAppsProvider.kt    PackageManager query, alphabet-grouped.
    CuratedAppsProvider.kt      Filters CuratedCatalog by isInstalled.

core/IntentLauncher.kt       Resolves SlotAction → Intent and launches.
                             Handles Deeplink fallback (try uri direct →
                             fall back to launcher intent for fallbackPkg).
                             Toasts "No app for that action" on failure.
                             NoOp / Pop / PagePrev / PageNext map to null
                             (intercepted by host, never launch an Intent).

ranking/
  ContextSnapshot.kt         data class — foregroundPkg / hourOfDay /
                             dayOfWeek / triggerSource. ContextSnapshot.now().
  SelectionLogger.kt         Async Room insert into selections table.
  (SlotRanker.kt — not yet written; Phase 4.)

data/
  SelectionEntity.kt         Room @Entity for selections.
  SelectionDao.kt            Insert / count / recent / pruneBefore queries.
  FootnoteDb.kt              @Database(version=1). Singleton accessor.
                             KSP-generated.
```

CI workflow at `.github/workflows/build.yml`:
- Runs `./gradlew assembleDebug --no-daemon --stacktrace` with `-PappVersionCode` + `-PappVersionName` flags.
- On failure: writes last 300 lines of build log to GITHUB_STEP_SUMMARY AND mirrors the full log to a fresh orphan `ci-log-N` branch (the only reliable way to read it from this sandbox).
- On success: publishes a GitHub release with the APK + uploads as workflow artifact.

Build files:
- `build.gradle.kts` (root) — AGP 8.2.2, Kotlin 1.9.22, KSP 1.9.22-1.0.17.
- `app/build.gradle.kts` — Compose BOM 2024.02.00, Material3, Room 2.6.1 with KSP.
- `AndroidManifest.xml` — only `QUERY_ALL_PACKAGES` permission so far. Phase 2 will add `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, etc.

---

## Decisions already locked

- **Single-module Android project**, package-split.
- **Accessibility Service from day 1** (Phase 3) — user accepted the rough onboarding for the full vision.
- **Scope**: system settings + every installed app + curated sub-orbits for ~15 popular apps.
- **Drill gesture: dwell-on-Branch (~280 ms) with bloom-from-slot**. Drag-past-ring is dead. Hover-time was researched against drag-past, marking-menu trails, concentric rings, and slot-bloom inline; dwell + portal won on first-timer ergonomics + real-world precedent.
- **Crowded ring: paginate at > 8 slots, 6 reals + 2 nav per page.**
- **Curated DSL over JSON** for the curated catalog (intents don't serialize, context-conditional logic is natural in code).
- **Room for selection history**, DataStore (when added) for user prefs.
- **Single-fluid-gesture: never lift the finger between drill / pop / fire.**
- **Stable debug keystore committed to repo** so APKs upgrade cleanly without uninstall.

---

## Where to start in the new session

The full plan: **`/root/.claude/plans/review-this-chat-and-validated-blanket.md`**

Sequence options:

### Option A — Verify Build 15 first
If Build 15 hasn't been validated by the user yet, send them the mirrored URL and wait for feedback. The dwell-on-Branch + bloom-from-slot model is a significant behavior shift; their feel-test matters before moving on.

### Option B — Phase 2: system overlay (the planned next big step)
1. Add manifest permissions: `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`.
2. New `overlay/OverlayService.kt` — foreground service, persistent low-priority notification with "Summon" action.
3. New `overlay/OverlayWindow.kt` — `WindowManager.addView(ComposeView, ...)` with manually wired `LifecycleOwner` + `ViewModelStoreOwner` + `SavedStateRegistryOwner`. WindowManager params: `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_NO_LIMITS | FLAG_LAYOUT_IN_SCREEN | FLAG_HARDWARE_ACCELERATED | FLAG_WATCH_OUTSIDE_TOUCH`, `PixelFormat.TRANSLUCENT`. **No `FLAG_NOT_TOUCHABLE`.**
4. New `overlay/OverlayController.kt` — singleton. `summon(TriggerSource)` starts the service with `ACTION_SHOW`.
5. New `ui/onboarding/OnboardingActivity.kt` — Welcome → Overlay grant → Try gesture. Polls `Settings.canDrawOverlays(this)` on resume.
6. `MainActivity.onCreate` checks if overlay perm + service running; routes to `OnboardingActivity` if either missing.
7. Notification carries a `PendingIntent` to summon (`SummonReceiver` for the broadcast). On Android 14+ use `foregroundServiceType="specialUse"`.

Phase 2 ship-test: install over Build 15+, grant overlay permission, tap notification "Summon" while inside Chrome → orbit appears, fire a settings panel → returned to Chrome unchanged. Outside-tap dismisses without consuming touches in Chrome.

### Option C — Phase 1.7 polish if dwell needs tuning
Common dial twists if the user reports issues:
- **Dwell too long / too short**: change `DwellMs = 280L` in `OrbitWheel.kt`.
- **Progress arc not visible enough**: bump opacity from `0.85f` to `1.0f`, stroke width from `2.5f` to `3.5f`, ring radius from `22.dp` to `26.dp`.
- **Bloom feels jarring**: animate the anchor smoothly. Currently anchor "teleports" between frames; could animate via `Animatable<Offset>` with `Offset.VectorConverter` over ~150 ms.
- **Accidental dwells while aiming at a Leaf to fire it**: not currently a problem — only Branches and PagePrev/PageNext are dwell-eligible. Leaves fire on release as before.

---

## Things to watch / known issues

- **Some `Settings.ACTION_*` constants are deprecated** (e.g. `ACTION_PRIVACY_SETTINGS` since API 17). They still compile; `IntentLauncher` toasts gracefully on failure. Phase 4 polish: replace with current equivalents.
- **Curated deeplinks rot silently.** Spotify / YouTube / Maps URI schemes change. `Deeplink.fallbackPackage` covers the common case. Phase 4: weekly WorkManager health check probing `PackageManager.resolveActivity`.
- **Compose lambdas + data class equality.** `Slot.Branch` carries a `suspend (ContextSnapshot) -> List<Slot>` lambda. Lambdas don't have stable equality unless captured-once (top-level `val` or stable instance field). Catalog and providers use stable references. Don't create Slot.Branch inside a hot path or `LaunchedEffect(slots)` will fire spuriously.
- **OEM Accessibility quirks** (Phase 3, future): Xiaomi MIUI revokes a11y on app update; OnePlus disables on background-kill. Detect at every cold start; banner if disabled.
- **Android 14+ FGS restrictions** (Phase 2): `startForegroundService` from background broadcast must `startForeground()` within 5 s. Plan to launch from `BOOT_COMPLETED` only when user toggles boot-restart on.
- **Sandbox can't fetch CI logs from Azure blob.** Workflow mirrors failure logs to `ci-log-N` orphan branches; pull via raw.githubusercontent.com.

---

## Project rules / norms

- User wants **forward motion**, not analysis paralysis. Make a recommendation, ship a build, iterate from feedback.
- **Don't push without committing**, don't force-push to `main`, don't skip git hooks unless user asks.
- **Tests** — unit tests are valuable but the project doesn't have a JVM test suite yet. Validation loop is "user installs and tries it." When in doubt, ship and let the user be the test.
- **Tone**: short, direct, no padding. The user's frustration spike happened around download stalling and around "drilling doesn't work" — when something blocks them, fix it before continuing.
- **Plan mode is on by default** in this workspace per the chat session config. Use `AskUserQuestion` for clarifying forks; use `ExitPlanMode` for plan approval. Never write to files outside the plan file while in plan mode.
- **Builds are numbered by github.run_number, NOT by phase.** Each phase typically produces 1-3 builds. Current phase: 1.7 (dwell + bloom). Current build: **16 (shipped, mirrored at dl-build-16)**.

---

## Quick sanity check on session start

1. Read this file.
2. Read the plan: `/root/.claude/plans/review-this-chat-and-validated-blanket.md`.
3. Check the latest build: see "First thing to do" above.
4. Skim the latest few commits to understand what just shipped:
   ```bash
   git -C /home/user/footnote-android log --oneline -10
   ```
5. Greet the user with current state and ask what they want to test or build next.
