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

### New in 1.11.0

- **Offline reputation-list import with transparent provenance** — import a CSV file that you explicitly select, inspect its source, declared version and SHA-256 fingerprint, then confirm before it is stored locally.
- **No background network activity** — source URLs are display-only provenance metadata; BlackList never fetches them, refreshes a list, retains file access, or adds an `INTERNET` permission.
- **Conservative and explainable enforcement** — only exact E.164 matches with a score of **80–100** can raise a call to the blocking threshold. Emergency safeguards, temporary allowances, whitelist entries, explicit blacklist rules, callback grace, schedules and any local user verdict always take precedence.
- **Portable local policy** — encrypted local backups now include imported source metadata and entries while continuing to exclude call history and caller-behaviour history.

### New in 1.10.0

- **Home-screen blocked-call statistics widget** — add a compact, launcher-native view of today’s and total blocked-call counts, with one-tap app opening and manual refresh.
- **Fresh without call-screening delay** — the widget refreshes after a blocked call has already been logged, when the user clears the local log, and through the platform’s periodic widget refresh; no database work is added before the Telecom response.
- **Aggregate-only visibility** — the widget exposes counts, never phone numbers, contacts, rule details, call reasons, or network data.

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
| **Opt-in Callback Grace** | After you dial a valid number, allow only that exact number to call back for 15 minutes; never overrides an explicit block. |
| **Advanced Scheduling** | Time-based rules with day-of-week bitmask and per-schedule trusted caller exceptions. Example: *Block all except whitelist 22:00–06:00 Mon–Fri while allowing an on-call number*. Overnight spans supported. |
| **Home-screen Stats Widget** | Compact local counts for blocked calls today and in total, with manual refresh and direct opening of the app. No number, contact, or call-reason content is exposed. |
| **Offline Reputation Lists** | Optional user-selected, bounded CSV lists with auditable source metadata and SHA-256 fingerprint. No URL fetching, automatic updates, cloud sync, or retained storage access. Exact E.164 scores from 80–100 can block only after all manual and safety policies. |
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
   Recent outgoing call to this exact number and opt-in enabled? → 15-minute callback allowance
   Recent outgoing emergency call? → short responder callback allowance
   Active schedule has a matching trusted caller exception? → ALLOW for that schedule only
   Schedule active? → evaluate schedule mode (ALL / ALL_EXCEPT_WHITELIST / UNKNOWN_PRIVATE / BLACKLIST)
   Temporary firewall or broad policy? → BLOCK
   Unknown/private policy? → BLOCK when enabled
   Local risk, behavior and exact offline-reputation signals? → BLOCK only at the configured threshold (offline score 80–100 is a risk floor)
   otherwise → ALLOW
   ```
4. If blocked → `CallResponse.Builder().setDisallowCall(true).setRejectCall(true)` + optional private BlackList log + optional notification. By default the Android call log is retained; **Settings → Privacy → Private blocked-call history** can hide blocked calls from the shared system log while keeping the in-app record.

---

## Offline Reputation List Format

The import is intentionally **local-file-only**. In **Settings → Offline reputation lists**, choose one CSV file with Android's one-time document picker. BlackList parses the selected bytes once on a background thread, presents a preview, and applies the immutable preview only after explicit confirmation. It does not persist URI access and never reads the file again after the preview.

The first metadata field must declare the source. `version` and `url` are optional; the URL is **provenance text only**, must use HTTPS, and is never opened or fetched. The header and each accepted number are strict so an imported list cannot rely on ambiguous national or suffix matching.

```csv
# BlackList Offline Reputation List
# source: Example Research Group
# version: 2026.08
# url: https://example.org/reputation.csv
number,score,category
+4930123456,90,telemarketing
+14155552671,65,marketing
```

| Field | Requirement and effect |
|---|---|
| `source` | Required readable source name, displayed before confirmation and with the imported source. |
| `version` / `url` | Optional provenance metadata. The displayed URL must be HTTPS; it is never requested by the app. |
| `number` | Exact E.164 number: `+` followed by 7–15 ASCII digits. No prefixes, suffixes, wildcards, or national-format matching. |
| `score` | Integer from 0–100. Scores **80–100** form a risk floor that may block only after higher-priority local policies. Lower scores remain local risk context and do not directly block. |
| `category` | Optional, bounded readable label shown in the review and explainable decision. |

Files are capped at **1 MiB**, **10,050 lines** and **10,000 source rows**. The device retains at most **10 sources**, **5,000 accepted entries per source**, and **10,000 accepted entries total**. Duplicate values within a source retain the highest score; the source fingerprint prevents the same byte-identical list from being imported twice. If several imported sources name one exact number, the highest score is used while the decision retains every source and category for provenance.

Imported entries never overwrite local caller reputation or a user verdict. They are evaluated only after emergency protection, temporary allow, whitelist, explicit blacklist and legacy exact blocks, callback grace, schedule exceptions/schedules, and broad local policies. An explicit `TRUSTED` or `NOT_SPAM` local verdict suppresses imported reputation scoring. Removing a source deletes only its own imported entries. Encrypted local backup and restore preserve the imported source metadata and entries as policy data; local call history and behaviour history remain excluded.

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
- [x] Emergency short-number safeguard, emergency callback grace, and an opt-in exact-number callback grace after a local outgoing call.
- [x] Encrypted local backup and restore for policy data, including imported offline reputation sources; call history and diagnostics remain excluded.
- [x] Optional private blocked-call history, retaining the explainable log locally while suppressing only blocked calls from Android’s shared call log.
- [x] Per-schedule exact-number trusted caller exceptions, bounded locally and subordinate to explicit blocks.
- [x] Home-screen blocked-call statistics widget with aggregate-only counts, safe refresh actions, and no additional permission.
- [x] Optional offline reputation-list import with transparent provenance and no background network access.

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
