# Build Prompt for Claude Code — "FocusLock" (Android App Blocker)

> Paste everything below into Claude Code. It is written as a complete brief so Claude
> can build the app from start to finish without re-asking basic questions. Build in the
> phases listed at the end — do not try to write the whole app in one shot.

---

## 1. Goal (read this first)

Build a **native Android app** that limits how long the user can use *other* apps each day.

Example: the user sets Instagram to **10 minutes/day**. The app tracks Instagram's
foreground time. The moment the user crosses 10 minutes, the app **covers Instagram with a
full-screen lock screen and sends the user to the home screen**. Instagram stays "locked"
for the rest of the calendar day (until midnight). This must work for **any user-chosen
installed app**, not just a hardcoded list.

The user must be able to **add, edit, and delete** blocked apps and their daily time limits
through a **simple, friendly, uncluttered UI**. Because the blocker app never blocks itself,
the user can always open it to raise a limit or unlock an app early in an emergency.

This is a **local, on-device app**. There is **no server/backend**. All data lives in a
local database. Do not add any network calls or cloud services.

### What "block and lock" means technically (important)
Android does **not** allow one app to kill another app's process. So implement blocking like
established blockers (StayFree, AppBlock) do:
- Detect the foreground app.
- When a blocked app exceeds its daily limit, immediately launch a full-screen **overlay
  lock Activity** and perform the accessibility **GLOBAL_ACTION_HOME** action to leave the
  blocked app.
- If the user reopens the blocked app, boot them out again instantly. Effect: locked for the day.

---

## 2. Platform & why

- **Native Android only**, written in **Kotlin** with **Jetpack Compose** UI.
- Do **not** use React Native / Flutter / Expo — the core features are deep native system
  integrations (UsageStats, AccessibilityService, overlays, foreground service) that
  cross-platform frameworks handle poorly.
- iOS is out of scope (Apple does not permit force-closing other apps).

---

## 3. Tech stack (use these versions; keep them mutually compatible)

| Layer | Choice | Version |
|---|---|---|
| Language | Kotlin | 2.2.21 |
| Build tool | Gradle | 8.13 |
| Android Gradle Plugin | AGP | 8.13.2 |
| JDK | Temurin/OpenJDK | 17 |
| compileSdk / targetSdk | Android 16 | API **36** |
| minSdk | Android 8.0 | API **26** |
| UI toolkit | Jetpack Compose (Material 3) | Compose BOM `2025.06.01` (or newer BOM compatible with AGP 8.13) |
| Symbol processing | KSP | version matching Kotlin 2.2.21 |
| Dependency injection | Hilt | 2.57.2 |
| Local database | Room | 2.7.2 |
| Async | Kotlin Coroutines + Flow | 1.10.2 |
| Preferences | DataStore (Preferences) | latest stable |
| Background scheduling | WorkManager | 2.10.x |
| Navigation | Navigation-Compose | latest stable |
| Icons | Coil (to load app icons) | latest stable 3.x |

If any two versions conflict at build time, keep this set coherent — prefer the latest
patch versions that build together, and do **not** jump to AGP 9 / compileSdk 37 (more
likely to break). Report any version bump you make.

**Do not build a backend.** No Retrofit, no Ktor server, no Firebase.

---

## 4. Android permissions & special access (all required)

Declare in `AndroidManifest.xml` and request/guide the user through the runtime + special
grants with clear in-app onboarding screens:

- `android.permission.PACKAGE_USAGE_STATS` — special access; user grants in
  **Settings → Usage access**. Needed to measure per-app foreground time.
- `android.permission.SYSTEM_ALERT_WINDOW` — "Display over other apps"; needed to show the
  lock screen over blocked apps.
- **Accessibility Service** — user enables in **Settings → Accessibility**. Used to detect
  the current foreground app (event-driven) and to perform `GLOBAL_ACTION_HOME`.
- `android.permission.FOREGROUND_SERVICE` and
  `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` (API 34+) — to keep the monitor alive.
- `android.permission.POST_NOTIFICATIONS` (API 33+) — for the persistent monitor
  notification and "app locked" notices.
- `android.permission.RECEIVE_BOOT_COMPLETED` — restart monitoring after reboot.
- `android.permission.SCHEDULE_EXACT_ALARM` / use WorkManager — for the midnight daily reset.
- To list installed apps, use `PackageManager` with a `<queries>` element. Avoid
  `QUERY_ALL_PACKAGES` if possible; if unavoidable, add it and explain why.

Build a clear **onboarding/permissions checklist screen** that shows which permissions are
granted (green) vs missing (red) with a one-tap button to open the right settings page for
each. The app cannot function until Usage Access, Overlay, and Accessibility are granted —
gate the main features behind this and explain it plainly.

---

## 5. Architecture

Use **MVVM + a repository layer**, single-activity Compose app.

Components:
1. **UI layer (Compose + Material 3)** — screens listed in §7. State via `ViewModel` +
   `StateFlow`. Navigation-Compose for routing.
2. **Domain/repository layer** — `AppLimitRepository`, `UsageRepository`. Exposes Flows to
   the UI, hides Room + system APIs.
3. **Data layer (Room)** — see §6 for schema. Also DataStore for simple settings.
4. **Foreground monitoring service** (`FocusLockService`, `FOREGROUND_SERVICE_SPECIAL_USE`)
   — long-running; polls `UsageStatsManager` (e.g. every ~2–5s) to accumulate today's usage
   per blocked app, compares against limits, and triggers blocking. Shows a low-priority
   persistent notification (required for foreground services).
5. **AccessibilityService** (`FocusLockAccessibilityService`) — receives
   `TYPE_WINDOW_STATE_CHANGED` events to know the current foreground package instantly (more
   responsive than polling), and performs `performGlobalAction(GLOBAL_ACTION_HOME)` when a
   locked app is detected. Use this together with UsageStats for accuracy.
6. **Lock screen** (`LockActivity`) — full-screen Compose Activity shown over a blocked app:
   states which app is locked, shows time used vs limit, and offers a "Manage in FocusLock"
   button that deep-links into the blocker app. It must NOT offer a free "dismiss" that
   defeats the block — unlocking happens only inside the main app.
7. **Daily reset** — a WorkManager periodic/one-time-at-midnight worker (or exact alarm)
   that clears today's usage counters and lock states at local midnight. Also recompute
   correctly if the phone was off at midnight (compare stored date to current date on
   service start).
8. **Boot receiver** — restarts `FocusLockService` after reboot.

Keep the monitoring loop **battery-efficient**: prefer accessibility events for detection,
use UsageStats for time accounting, avoid tight busy-loops, and use appropriate coroutine
dispatchers.

---

## 6. Data model (Room)

**Entity `BlockedApp`**
- `packageName: String` (primary key)
- `appLabel: String` (cached display name)
- `dailyLimitMinutes: Int`
- `isEnabled: Boolean` (user can pause a rule without deleting it)
- `createdAt: Long`

**Entity `DailyUsage`**
- `id` (auto)
- `packageName: String`
- `dateKey: String` (e.g. `yyyy-MM-dd`, local time)
- `usedSeconds: Long`
- `isLockedToday: Boolean`
- unique index on (`packageName`, `dateKey`)

Provide DAOs returning `Flow<...>` so the UI updates live. Handle Room schema migrations
cleanly (or `fallbackToDestructiveMigration` during early dev only).

Settings in DataStore: e.g. `resetHour` (default 0 = midnight), `strictMode` toggle,
onboarding-complete flag.

---

## 7. Screens & features (keep the UI friendly, clean, simple)

Design language: **Material 3, rounded cards, generous spacing, one clear primary action per
screen, large tap targets, light/dark theme support.** Aim for something that feels calm and
unique — not a cluttered settings dump.

1. **Onboarding / Permissions** — friendly intro + the permissions checklist from §4.
2. **Home / Dashboard** — list of blocked apps as cards. Each card shows: app icon + name,
   a progress ring/bar of *used vs daily limit*, remaining time, and today's status
   (Active / **Locked**). Big **"+ Add app"** button. Empty state with a friendly prompt.
3. **Add app** — searchable list of installed apps (icon + name), pick one, set a daily
   limit with an easy control (slider + number field, e.g. 5–240 min), save. Prevent adding
   FocusLock itself and system launchers.
4. **Edit app** — change the limit, enable/disable the rule, or **unlock now** (emergency:
   clears today's lock and optionally adds bonus minutes). This is the "emergency edit" flow
   — make it fast: open app → tap the app → change limit / unlock → done.
5. **Delete app** — swipe-to-delete or a delete button with a confirm dialog.
6. **Settings** — reset hour, strict mode, theme, re-check permissions, about.
7. **Lock screen (LockActivity)** — as described in §5.6.

Make add/edit/delete genuinely effortless: swipe actions, inline editing where possible,
undo snackbars, and instant live updates.

---

## 8. Build, run, and install on a real phone

Set up the project so I can install it on my own Android phone:
- Standard Gradle Android project (Kotlin DSL `build.gradle.kts`), `applicationId`
  like `com.focuslock.app`.
- Provide a **debug build** I can install via USB: `./gradlew installDebug` with the phone
  in developer mode + USB debugging, **or** build an APK with `./gradlew assembleDebug`
  (output in `app/build/outputs/apk/debug/`) that I can transfer and sideload.
- Add a short **README** with: prerequisites (Android Studio latest stable, JDK 17,
  Android SDK 36), how to open, how to build the APK, how to install on a phone, and how to
  grant the three special permissions on first launch.
- Because Accessibility + Usage Access can't be tested well on a bare emulator, note which
  parts need a **real device**.

---

## 9. Development roadmap (build in these phases — commit after each)

Build incrementally and verify each phase compiles before moving on. This keeps things
debuggable and avoids one giant broken build.

- **Phase 0 — Project setup:** Gradle/AGP/Kotlin/Compose/Hilt/Room wired up, app runs with a
  "Hello" Compose screen. Confirm it builds and installs.
- **Phase 1 — Data + list installed apps:** Room entities/DAOs, repository, and a screen
  that lists installed apps with icons (Coil + PackageManager).
- **Phase 2 — CRUD UI:** Dashboard, Add/Edit/Delete blocked apps with limits, all persisted
  and live-updating. No blocking yet.
- **Phase 3 — Permissions onboarding:** the checklist screen + deep links to each settings
  page; gate features until granted.
- **Phase 4 — Usage tracking:** foreground service + UsageStatsManager accumulating per-app
  daily usage into Room; show live progress on the dashboard.
- **Phase 5 — Blocking:** AccessibilityService foreground detection + LockActivity overlay +
  GLOBAL_ACTION_HOME. When usage ≥ limit, lock the app for the day.
- **Phase 6 — Daily reset + boot:** WorkManager midnight reset, correct date handling, boot
  receiver restarts the service.
- **Phase 7 — Polish:** emergency unlock, strict mode, theming, empty/error states, battery
  and edge-case handling, README.

At the start of each phase, briefly restate the plan for that phase, then implement it.

---

## 10. Testing checklist (verify before calling it done)

- Adding, editing, deleting apps persists across app restarts.
- Usage time for a blocked app increases only while that app is actually foregrounded.
- Crossing the limit locks the app within a couple of seconds; reopening re-locks instantly.
- FocusLock itself is never locked and can always adjust limits.
- Emergency unlock immediately restores access to a locked app.
- At local midnight, counters and locks reset; also correct if the phone was off at midnight.
- Blocking resumes after a reboot.
- Works on a real device with all three special permissions granted; degrades gracefully
  (clear message) when a permission is missing.

---

## 11. Constraints / get these right

- Local-only, no backend, no analytics, no tracking.
- Don't hardcode target apps — the user chooses from installed apps.
- Respect battery: event-driven detection, no tight polling loops.
- Handle Android 13/14/15/16 behavior changes (notification permission, foreground service
  special-use type, exact alarm restrictions).
- Clear, honest permission explanations — never trick the user into grants.
- Clean architecture, commented where non-obvious, so I can extend it later.

Start with **Phase 0** now.
