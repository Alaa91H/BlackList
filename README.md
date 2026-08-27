# BlackList — Professional Call Blocker (100% Offline)

> The most professional, privacy-first call blocker for Android. **No cloud. No AI. No trackers. 100% offline.**

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="128" alt="BlackList Icon" />
</p>

<p align="center">
  <a href="https://github.com/Alaa91H/BlackList/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/Alaa91H/BlackList?style=for-the-badge&label=Release" /></a>
  <img alt="Platform" src="https://img.shields.io/badge/Platform-Android%2026%2B-3DDC84?style=for-the-badge" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=for-the-badge" />
  <img alt="Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge" />
  <img alt="License" src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" />
  <img alt="Offline" src="https://img.shields.io/badge/Offline-100%25-success?style=for-the-badge" />
</p>

---

## Why BlackList?

Most call blockers send your contacts and call history to remote servers. **BlackList never does.**

### New in 1.7.0

- **Private blocked-call history** — optionally keep blocked calls in BlackList’s private local log only, hiding them from Android’s shared call history. The default remains transparent: blocked calls continue to appear in the system log unless you opt in.
- **Clear privacy boundary** — the setting applies only to calls BlackList blocks. Allowed and silenced calls retain their normal Android history, and the private in-app blocked log remains available with the decision reason.
- **Portable protection settings** — the preference is safely carried by the encrypted policy backup and restore flow, with no new permission or network access.

### Recent safety hardening

- **Emergency callback grace** — after Android reports an outgoing emergency call, BlackList opens a short local-only allowance for a dispatcher or responder to call back. Explicit blacklist rules still win; the allowance only protects against broad policies such as schedules, block-all modes, and unknown/private filtering.
- **Regional and platform awareness** — Android Telecom caller verification contributes to local risk scoring, regional emergency short numbers remain protected, and local number parsing follows the configured device region.

- **Room Database only** — everything stays on your device.
- **Zero network permission** — the app cannot exfiltrate data even if it wanted to.
- **CallScreeningService** — blocks calls *silently before the phone rings*.
- **Clean Architecture + MVVM** — maintainable, testable, production-grade.

---

## Features

| Feature | Description |
|---------|-------------|
| **Blacklist** | Add numbers manually, from contacts, or from call log. Wildcard-aware normalization (E.164 + national). |
| **Whitelist** | Family / priority numbers that **always bypass** any blocking rule. |
| **Block Unknown** | Silently reject numbers not in your contacts (uses `READ_CONTACTS`). |
| **Block Private/Hidden** | Reject withheld / private / unknown callers. |
| **Block All Except Whitelist** | Nuclear mode — only whitelisted numbers ring. |
| **Advanced Scheduling** | Time-based rules with day-of-week bitmask. Example: *Block all except whitelist 22:00–06:00 Mon–Fri*. Overnight spans supported. |
| **Blocked Log** | Professional timeline of every blocked call: number, display name, reason, timestamp. Clear with one tap. |
| **Smart Notifications** | Optional low-priority notification for each blocked call (off by default if you prefer total silence). |
| **Material Design 3 (2026)** | Jetpack Compose, dynamic colors (Material You), Light/Dark/System theme, smooth animations & transitions. |
| **Localization** | System language by default. Full RTL support — perfect Arabic layout, plus English, French, German, Spanish, Turkish, Russian, Persian, Urdu, Hindi, Chinese, Japanese, Korean, Portuguese, Italian. |
| **About Screen** | Luxurious screen with developer info and one-tap GitHub link. |

---

## Architecture

```
com.blacklist.app
├── data
│   ├── local
│   │   ├── BlackListDatabase (Room)
│   │   ├── dao (BlockedNumberDao, WhitelistedNumberDao, BlockedCallLogDao, ScheduleRuleDao, AppSettingsDao)
│   │   └── entity (BlockedNumberEntity, WhitelistedNumberEntity, BlockedCallLogEntity, ScheduleRuleEntity, AppSettingsEntity)
│   └── repository (BlackListRepositoryImpl)
├── domain
│   ├── model (CallEvent, VerificationStatus, Decision)
│   └── repository (BlackListRepository interface)
├── service (BlackListCallScreeningService — CallScreeningService)
├── util (PhoneNumberUtils, ContactUtils, ScheduleEvaluator)
├── di (ServiceLocator, ViewModelFactory)  // manual DI — no Hilt, smaller APK, no reflection
└── ui
    ├── theme (Material 3, dynamic color, Light/Dark)
    ├── navigation (NavGraph, Routes)
    ├── components (common)
    └── screens (home, blacklist, whitelist, blockedlog, schedule, settings, about)
```

**Principles**: Clean Architecture, MVVM, single source of truth (Room), unidirectional data flow with `StateFlow`/`Flow`.

---

## How Call Blocking Works

1. User grants the **Call Screening Role** (`ROLE_CALL_SCREENING` on Android 10+).
2. `BlackListCallScreeningService` extends `CallScreeningService` — the OS delivers every incoming call to `onScreenCall()` **before ringing**.
3. Evaluation order (all in <1.5 s, timeout-safe):
   ```
   Emergency short number? → ALLOW (always wins)
   Temporary allow / whitelist? → ALLOW
   Explicit blacklist or legacy exact block? → BLOCK
   Recent outgoing emergency call? → short callback allowance
   Schedule active? → evaluate schedule mode (ALL / ALL_EXCEPT_WHITELIST / UNKNOWN_PRIVATE / BLACKLIST)
   Temporary firewall or broad policy? → BLOCK
   Unknown/private policy? → BLOCK when enabled
   Local risk and behavior signals (including failed network verification)? → BLOCK only at the configured threshold
   otherwise → ALLOW
   ```
4. If blocked → `CallResponse.Builder().setDisallowCall(true).setRejectCall(true)` + optional private BlackList log + optional notification. By default the Android call log is retained; **Settings → Privacy → Private blocked-call history** can hide blocked calls from the shared system log while keeping the in-app record.

---

## Tech Stack

- **Language**: Kotlin 2.0.21
- **UI**: Jetpack Compose BOM 2024.09.02, Material 3 1.3.1, Navigation Compose 2.8.5, Activity Compose 1.9.3
- **Database**: Room 2.6.1 + KSP 2.0.21-1.0.28, SQLite 2.4.0
- **Async**: Coroutines 1.8.1, Flow, lifecycle-runtime 2.9.2
- **Min SDK** 26 (Android 8.0) — **Target/Compile** 36
- **Build**: Gradle 8.13, AGP 8.13.2, JDK 17 (Temurin)

No Firebase, no analytics SDK, no internet permission.

---

## Permissions

| Permission | Why |
|------------|-----|
| `READ_PHONE_STATE` | Detect incoming call state |
| `READ_CALL_LOG` | Optional — pick numbers from log |
| `READ_CONTACTS` | Check if caller is in contacts (for *Block Unknown*) |
| `CALL_PHONE` / `ANSWER_PHONE_CALLS` | Required for screening role |
| `POST_NOTIFICATIONS` | Show blocked-call notification (Android 13+) |
| `BIND_SCREENING_SERVICE` | System binds `CallScreeningService` |

---

## Building

```bash
# Requirements: JDK 17, Android SDK (platforms 36, build-tools 35+)
git clone https://github.com/Alaa91H/BlackList.git
cd BlackList

# Important: force English locale — Arabic locale formats digits as ٠١٢ which breaks Room codegen
$env:JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"
./gradlew assembleDebug      # → app/build/outputs/apk/debug/app-debug.apk (18 MB)
./gradlew assembleRelease    # → app/build/outputs/apk/release/app-release-unsigned.apk (1.5 MB, R8 minified)
```

> **Note on locale**: The project must be built with `user.language=en`. Building with `ar_SA` causes KSP/Room to emit Arabic-Indic digits (`١` instead of `1`) — a known JDK locale bug.

---

## Project Structure (folder view)

```
BlackList/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/blacklist/app/
│       └── res/{values, values-ar, drawable, mipmap-*, xml}
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

---

## Screens

- **Home** — protection status (shield animation), today/total stats, quick toggles, manage grid.
- **Blacklist / Whitelist** — searchable list, add dialog, swipe-to-delete.
- **Blocked Log** — chronological feed with reason chips.
- **Schedule** — create overnight-aware rules with day picker and mode dropdown.
- **Settings** — toggles for each blocking type, theme selector.
- **About** — hero card, developer card (Alaa), privacy commitment, tappable GitHub card.

---

## Roadmap

- [x] Local CSV import/export for blacklist and whitelist, with size limits, duplicate detection, and spreadsheet-formula safety.
- [x] Exact, prefix, range, country, temporary, and other local rule types in the firewall engine.
- [x] Emergency short-number safeguard and local emergency callback grace for broad-policy protection.
- [x] Encrypted local backup and restore for policy data only; call history and diagnostics remain excluded.
- [x] Optional private blocked-call history, retaining the explainable log locally while suppressing only blocked calls from Android’s shared call log.
- [ ] Per-number schedule override.
- [ ] Home-screen widget with today’s statistics.
- [ ] Optional offline reputation-list import with transparent provenance and no background network access.

See [CHANGELOG.md](CHANGELOG.md) for the complete release history.

---

## Developer

**Alaa (Alaa91H)** — Software Developer, creator of **ADM (Advanced Download Manager in Rust)**, passionate about highly optimized, offline-first tools.

- GitHub: [@Alaa91H](https://github.com/Alaa91H/Alaa91H)
- Project: [github.com/Alaa91H/BlackList](https://github.com/Alaa91H/BlackList)

---

## License

MIT — free and open source. See [LICENSE](LICENSE) if present.

---

## Privacy Policy (short version)

BlackList stores everything in `blacklist.db` on your device via Room. It declares **no `INTERNET` permission**. It never uploads contacts, call logs, or numbers anywhere. The APK can be verified: decompile it — you will find no network code.

---

<p align="center"><i>Made with ♥ by Alaa — Offline First, Privacy First.</i></p>
