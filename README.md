<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:1e1b4b,50:4f46e5,100:ec4899&height=220&section=header&text=FocusLock&fontSize=72&fontColor=ffffff&animation=fadeIn&fontAlignY=38&desc=Take%20back%20your%20time%2C%20one%20app%20at%20a%20time&descAlignY=58&descSize=18" alt="FocusLock" width="100%"/>

<a href="#-getting-started">
  <img src="https://readme-typing-svg.demolab.com?font=Fira+Code&weight=600&size=22&duration=3000&pause=900&color=6366F1&center=true&vCenter=true&width=620&lines=Set+a+daily+limit+for+any+app+%E2%8F%B1%EF%B8%8F;Hit+the+limit+%E2%86%92+locked+until+midnight+%F0%9F%94%92;100%25+on-device.+No+internet.+No+tracking+%F0%9F%9B%A1%EF%B8%8F;Native+Kotlin+%2B+Jetpack+Compose+%F0%9F%92%9C" alt="Typing tagline"/>
</a>

<br/>

![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android-8.0%20→%2016-3DDC84?style=for-the-badge&logo=android&logoColor=white)
<br/>
![Hilt](https://img.shields.io/badge/DI-Hilt%202.57-FF6F00?style=flat-square)
![Room](https://img.shields.io/badge/DB-Room%202.7-00897B?style=flat-square)
![Coroutines](https://img.shields.io/badge/Async-Coroutines%20%2B%20Flow-0095D5?style=flat-square)
![Offline](https://img.shields.io/badge/Network-none%20%E2%80%94%20100%25%20offline-success?style=flat-square)
![minSdk](https://img.shields.io/badge/minSdk-26-informational?style=flat-square)
![targetSdk](https://img.shields.io/badge/targetSdk-36-informational?style=flat-square)

<br/>

<img src="docs/assets/lock-demo.svg" alt="Usage ring fills to the daily limit, then the app locks" width="640"/>

</div>

<img src="https://user-images.githubusercontent.com/73097560/115834477-dbab4500-a447-11eb-908a-139a6edaec5c.gif" width="100%" alt="divider"/>

## 📖 About

**FocusLock** is a native Android app blocker that limits how long you can use *other* apps each day.

Give Instagram **10 minutes a day**. FocusLock quietly measures how long Instagram is on screen. The second you cross 10 minutes, Instagram is **covered by a full-screen lock page and you're sent to the home screen**. Open it again and you're bounced right back out, until midnight, when a fresh day starts.

It works with **any app you choose** from your installed apps, and FocusLock never locks itself. If there's an emergency, you can always open it and unlock an app for a few extra minutes.

> [!NOTE]
> Android does not let one app kill another. FocusLock blocks the same way established blockers (StayFree, AppBlock) do: it detects the foreground app, presses **Home** through an Accessibility Service and shows a lock screen on top.

<img src="https://user-images.githubusercontent.com/73097560/115834477-dbab4500-a447-11eb-908a-139a6edaec5c.gif" width="100%" alt="divider"/>

## ✨ Features

<table>
<tr>
<td width="50%" valign="top">

### ⏱️ Per-app daily limits
Pick any launchable app, set a limit from **1 to 240 minutes** with a slider (5-min steps) or type an exact number.

### 📊 Live dashboard
Every rule is a card with the app icon, a **used vs. limit** progress indicator, time remaining and today's status (**Locked** or **Paused**).

### 🔒 Locked until midnight
Hit the limit and the app is locked for the rest of the day. Reopening it gets you sent Home again **instantly**.

### 🆘 Emergency "Unlock now"
Need it right now? Grant **+5 / 15 / 30 / 60 minutes** for today only. The math accounts for any overshoot, so the app doesn't re-lock the moment you unlock it.

</td>
<td width="50%" valign="top">

### ⏸️ Pause without deleting
Turn a rule off for a while and turn it back on later. A paused rule never locks.

### ↩️ Safe delete with undo
Remove a rule with a confirm dialog **plus** an undo snackbar. Undo restores today's usage too, so delete-and-undo can't be used to reset the counter.

### 🛡️ Can't lock yourself out
FocusLock itself and **every home-screen launcher** are protected and can never be blocked.

### ✅ Honest permissions checklist
A clear onboarding screen shows each required permission as granted or missing, with a one-tap button to the right Settings page.

</td>
</tr>
</table>

<img src="https://user-images.githubusercontent.com/73097560/115834477-dbab4500-a447-11eb-908a-139a6edaec5c.gif" width="100%" alt="divider"/>

## ⚙️ How blocking works

```mermaid
sequenceDiagram
    autonumber
    actor U as You
    participant IG as Limited app (e.g. Instagram)
    participant S as FocusLockService<br/>(foreground service)
    participant DB as Room DB
    participant A as FocusLockAccessibilityService
    participant L as LockActivity

    U->>IG: Opens Instagram
    loop Every 3 s while a limited app is on screen
        S->>S: Read UsageStats events
        S->>DB: Add foreground time to today's row
    end
    Note over S: Sleeps exactly until the limit<br/>would be reached, so the lock lands on time
    S->>DB: usedSeconds ≥ limit → isLockedToday = true
    DB-->>A: Flow emits updated locked-app set
    S->>A: enforceNow(package)
    A->>U: GLOBAL_ACTION_HOME (back to home screen)
    A->>L: Show "Locked for today"
    U->>IG: Tries to reopen Instagram
    IG-->>A: TYPE_WINDOW_STATE_CHANGED
    A->>U: Sent Home again, instantly
    L->>U: "Manage in FocusLock" deep-links to the edit screen
```

**Two cooperating services:**

| | `FocusLockService` | `FocusLockAccessibilityService` |
|---|---|---|
| **Job** | *Measures* time | *Enforces* the lock |
| **How** | Reads `UsageStatsManager` events (exact timestamps, so nothing is lost between polls) | Listens for `TYPE_WINDOW_STATE_CHANGED` and checks an in-memory map of locked apps, with no DB hit per event |
| **Battery** | Adaptive cadence: **3 s** while a limited app is visible, **5 s** idle, **60 s** with the screen off; wakes instantly on screen-on | Event-driven, never reads screen content (`canRetrieveWindowContent` is off) |

If Accessibility is turned off, the tracker falls back to covering the app with the lock screen, and the dashboard explains that full blocking needs the permission.

<img src="https://user-images.githubusercontent.com/73097560/115834477-dbab4500-a447-11eb-908a-139a6edaec5c.gif" width="100%" alt="divider"/>

## 🏗️ Architecture

Single-activity **MVVM** app with a repository layer, wired together by **Hilt**.

```mermaid
flowchart TB
    subgraph UI["🎨 UI · Jetpack Compose + Material 3"]
        P[PermissionsScreen]
        D[DashboardScreen]
        AD[AddAppScreen]
        E[EditAppScreen]
        LS[LockActivity]
    end

    subgraph VM["🧠 ViewModels · StateFlow"]
        PVM[PermissionsViewModel]
        DVM[DashboardViewModel]
        AVM[AddAppViewModel]
        EVM[EditAppViewModel]
        LVM[LockViewModel]
    end

    subgraph REPO["📦 Repositories"]
        ALR[AppLimitRepository]
        UR[UsageRepository]
        IAR[InstalledAppsRepository]
    end

    subgraph DOMAIN["⚖️ Rules"]
        LR[LockRules<br/><i>single source of truth</i>]
        BP[BlockPolicy]
        PA[ProtectedApps]
    end

    subgraph DATA["💾 Data"]
        ROOM[(Room<br/>BlockedApp · DailyUsage)]
        USM[[UsageStatsManager]]
        PM[[PackageManager]]
    end

    subgraph BG["🛰️ Background"]
        FS[FocusLockService]
        UT[UsageTracker]
        AS[FocusLockAccessibilityService]
    end

    P --> PVM
    D --> DVM --> ALR & UR
    AD --> AVM --> IAR & ALR
    E --> EVM --> ALR & UR
    LS --> LVM --> UR
    ALR & UR --> ROOM
    IAR --> PM
    FS --> UT --> USM
    UT --> ROOM
    UT --> LR
    BP --> ROOM
    BP --> LR
    AS --> BP & PA
    AS -->|Home + show| LS
```

<details>
<summary><b>📂 Project structure</b> (click to expand)</summary>

```text
app/src/main/java/com/focuslock/app/
├── FocusLockApp.kt                  # @HiltAndroidApp
├── MainActivity.kt                  # single activity, handles focuslock://edit deep links
├── data/
│   ├── block/        BlockPolicy, ProtectedApps
│   ├── db/           AppDatabase, BlockedApp, DailyUsage + DAOs
│   ├── permissions/  PermissionChecker, SettingsIntents
│   ├── repository/   AppLimitRepository, UsageRepository, InstalledAppsRepository, DailyLimit, RemovedRule
│   └── usage/        UsageTracker, UsageEventsReader, ForegroundTimeAccumulator, LockRules
├── di/               DatabaseModule
├── service/          FocusLockService, FocusLockAccessibilityService, LockLauncher, LockNotifier
└── ui/
    ├── add/          search & pick an installed app, set its limit
    ├── dashboard/    live cards with progress & status
    ├── edit/         change limit · pause · unlock now · remove
    ├── lock/         the "Locked for today" screen
    ├── permissions/  onboarding checklist
    ├── common/ icons/ nav/ theme/
app/schemas/          Room schema exports (v1 → v3)
app/src/test/         JVM unit tests for LockRules and ForegroundTimeAccumulator
```

</details>

<img src="https://user-images.githubusercontent.com/73097560/115834477-dbab4500-a447-11eb-908a-139a6edaec5c.gif" width="100%" alt="divider"/>

## 🗄️ Data model

```mermaid
erDiagram
    BLOCKED_APP ||--o{ DAILY_USAGE : "tracked per day"
    BLOCKED_APP {
        string packageName PK
        string appLabel
        int    dailyLimitMinutes
        bool   isEnabled
        long   createdAt
    }
    DAILY_USAGE {
        long   id PK
        string packageName
        string dateKey "yyyy-MM-dd, local time"
        long   usedSeconds
        bool   isLockedToday
        long   bonusSeconds "extra time from Unlock now"
        long   grantedSeconds
    }
```

Usage is stored **per calendar day** (unique on `packageName + dateKey`). A new day automatically means a new, empty row, so limits reset at local midnight even if the phone was switched off at the time.

<img src="https://user-images.githubusercontent.com/73097560/115834477-dbab4500-a447-11eb-908a-139a6edaec5c.gif" width="100%" alt="divider"/>

## 🧰 Tech stack

<div align="center">

| Layer | Choice | Version |
|:--|:--|:--:|
| Language | Kotlin | `2.2.21` |
| UI | Jetpack Compose · Material 3 | BOM `2025.06.01` |
| Build | Gradle · Android Gradle Plugin | `8.13` · `8.13.2` |
| JDK | OpenJDK / JetBrains Runtime | `17` |
| SDK | compile / target · min | `36` · `26` |
| DI | Hilt (+ KSP) | `2.57.2` |
| Database | Room | `2.7.2` |
| Async | Coroutines + Flow | `1.10.2` |
| Preferences | DataStore | `1.1.7` |
| Background | WorkManager | `2.10.2` |
| Navigation | Navigation-Compose | `2.9.1` |
| App icons | Coil 3 | `3.2.0` |

</div>

<img src="https://user-images.githubusercontent.com/73097560/115834477-dbab4500-a447-11eb-908a-139a6edaec5c.gif" width="100%" alt="divider"/>

## 🔐 Permissions & why each one is needed

| Permission | How it's granted | Why |
|---|---|---|
| 📈 **Usage access** `PACKAGE_USAGE_STATS` | Settings → *Usage access* | Measure how long each limited app is in the foreground |
| 🪟 **Display over other apps** `SYSTEM_ALERT_WINDOW` | Settings → *Display over other apps* | Lets the lock screen start from the background, on top of a blocked app |
| ♿ **Accessibility service** | Settings → *Accessibility* → FocusLock | Instantly detect which app came to the front and press **Home** |
| 🔔 **Notifications** `POST_NOTIFICATIONS` | Runtime dialog (Android 13+) | Persistent tracking notification and "App locked" notices |
| 🛰️ **Foreground service** `FOREGROUND_SERVICE(_SPECIAL_USE)` | Automatic | Keeps tracking alive in the background (Android 14+ special-use type) |

> [!IMPORTANT]
> **There is no `INTERNET` permission.** FocusLock is local-only by design: no backend, no analytics, no tracking. Installed apps are discovered through a `<queries>` element (launcher + home intents) instead of the broad `QUERY_ALL_PACKAGES`.

<img src="https://user-images.githubusercontent.com/73097560/115834477-dbab4500-a447-11eb-908a-139a6edaec5c.gif" width="100%" alt="divider"/>

## 🚀 Getting started

### Prerequisites

- **Android Studio** (latest stable)
- **JDK 17** (the JetBrains Runtime bundled with Android Studio works)
- **Android SDK 36**
- An Android phone running **8.0+** with **Developer options → USB debugging** turned on

### Build & install

```bash
# 1. Clone
git clone https://github.com/shreyashk07004/Focus-Lock.git
cd Focus-Lock

# 2a. Install straight onto a USB-connected phone
./gradlew installDebug

# 2b. …or build an APK to sideload
./gradlew assembleDebug
#     → app/build/outputs/apk/debug/app-debug.apk
```

> On Windows use `gradlew.bat` instead of `./gradlew`. You can also just open the folder in Android Studio and press ▶️ **Run**.

<details>
<summary><b>📱 First launch: granting the special permissions</b> (click to expand)</summary>

<br/>

FocusLock opens on a **permissions checklist**. Each row is 🟢 granted or 🔴 missing, with a button that jumps to the right Settings page.

1. **Usage access**: find *FocusLock* in the list and switch it on.
2. **Display over other apps**: allow FocusLock.
3. **Accessibility**: open *Installed apps / Downloaded services* → *FocusLock* → turn it on and confirm.
   - On **Android 13+** sideloaded apps may show *"Restricted setting"*. Open **Settings → Apps → FocusLock → ⋮ → Allow restricted settings**, then try again.
4. **Notifications**: allow when prompted.

Return to FocusLock. Once everything is green, tap **+ Add app**, pick an app, set a limit and you're done. 🎉

</details>

<details>
<summary><b>🧪 Real device vs. emulator</b></summary>

<br/>

| Works on an emulator | Needs a real phone |
|---|---|
| Adding, editing, pausing and removing rules | Accurate usage tracking over real app sessions |
| Dashboard, navigation and theming | Accessibility-based blocking (Home + lock screen) |
| Unit tests | Battery, screen-off and OEM background-kill behavior |

Emulators can grant the permissions, but blocking only feels right on real hardware where you actually use the limited apps.

</details>

<img src="https://user-images.githubusercontent.com/73097560/115834477-dbab4500-a447-11eb-908a-139a6edaec5c.gif" width="100%" alt="divider"/>

## 🧪 Tests

The critical logic lives in plain Kotlin so it can be tested on the JVM:

- **`LockRulesTest`**: when an app counts as locked, effective limits with bonus time, "Unlock now" overshoot math
- **`ForegroundTimeAccumulatorTest`**: turning raw usage events into accurate foreground time

```bash
./gradlew testDebugUnitTest
```

<img src="https://user-images.githubusercontent.com/73097560/115834477-dbab4500-a447-11eb-908a-139a6edaec5c.gif" width="100%" alt="divider"/>

## 🗺️ Roadmap

- [x] Room data model + installed-app picker with icons
- [x] Dashboard, add / edit / pause / delete with undo
- [x] Permissions onboarding checklist
- [x] Foreground-service usage tracking with adaptive polling
- [x] Accessibility blocking + "Locked for today" screen
- [x] Per-day reset via date-keyed usage rows
- [x] Emergency "Unlock now" with bonus minutes
- [ ] Restart tracking automatically after reboot (boot receiver)
- [ ] Settings screen: reset hour, strict mode, theme picker
- [ ] Weekly usage history & insights

<img src="https://user-images.githubusercontent.com/73097560/115834477-dbab4500-a447-11eb-908a-139a6edaec5c.gif" width="100%" alt="divider"/>

## 🤝 Contributing

Issues and pull requests are welcome. Please keep the project's core promises:

1. **Local only**: no network calls, no analytics.
2. **Battery-friendly**: event-driven detection, no tight polling loops.
3. **Honest permissions**: explain every grant, never trick the user.

<div align="center">

<br/>

Made with 💜 and Kotlin by [**@shreyashk07004**](https://github.com/shreyashk07004)

⭐ If FocusLock helps you focus, consider giving it a star!

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:ec4899,50:4f46e5,100:1e1b4b&height=120&section=footer&animation=twinkling" alt="footer" width="100%"/>

</div>
