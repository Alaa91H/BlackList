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

### New in 1.16.0

- **Temporary exact-number blocks** — in **Blacklist**, enter one valid phone number and choose a fixed local expiry of **1 hour, 1 day, 7 days or 30 days**. The active temporary-block section shows the canonical number and localized end time; each rule can be cancelled directly.
- **Conservative precedence and bounds** — a temporary exact block is evaluated only after emergency protection, a current temporary allow and the whitelist. It never blocks an emergency number, never overrides an allow/whitelist, expires in the immutable policy snapshot even before cleanup, accepts only canonical E.164 identities, replaces a matching temporary block atomically and caps active entries at 100.
- **Portable local policy, no activity data** — encrypted policy backup/restore now validates and carries active temporary exact blocks. The feature adds no Room migration, runtime permission, network request, cloud service, analytics, tracker, worker, timer, boot receiver, shared call-log/SMS read, or call-screening-path I/O.
- **Precise silence semantics** — the existing opt-in quiet screening uses Android’s `setSilenceCall(true)`: the eligible incoming call continues without ringing. It is distinct from block/reject and does not promise to hide the system call log or missed-call notification.

### New in 1.15.0

- **User-controlled Quick Settings placement** — **Settings → Quick access** now provides an explicit path to add the existing **Temporary call block** tile. On Android 13 and later, the app asks the system to show its one-tap placement prompt; Android 7–12 users receive the equivalent manual-add guidance.
- **One-hour reversible override, now discoverable** — the tile enables the existing local **Temporary Block All** override for one hour and cancels it on a second tap. It changes no permanent rule, profile, whitelist, reputation verdict, notification preference, or call history; a secured locked device must be unlocked before the override can change.
- **Platform-native and private** — the tile uses Android’s built-in Quick Settings TileService and a user-confirmed system prompt. It adds no runtime permission, network operation, worker, boot receiver, timer, cloud service, analytics, tracker, or call-screening-path I/O.

### New in 1.14.0

- **User-controlled local history expiry** — in **Settings → Privacy**, retain BlackList’s in-app blocked-call history forever (the default) or for the most recent **7, 30, 90 or 365 days**.
- **Strictly limited effect** — after a future blocked call has already been answered and logged, BlackList removes only older records from its own private `blocked_call_logs` timeline. It does not alter blocking rules, caller reputation, security events, notifications, Android’s shared call log, or the new record.
- **Safe continuity without activity export** — the preference uses a non-destructive Room 10→11 migration and follows encrypted policy backup/restore. Older backups resolve safely to **Keep forever**; blocked-call history itself continues to be excluded from every backup.
- **No new background or data capability** — cleanup is local and happens only after the Telecom response. It adds no network access, storage permission, timer, worker, boot receiver, cloud, analytics, tracker, or pre-response I/O.

### New in 1.13.0

- **Confirmed local false-positive recovery** — from a blocked-call record, choose **Not spam**, review the exact impact, and explicitly save a durable local `NOT_SPAM` verdict for that dialable caller.
- **Precise, bounded effect** — the verdict is normalized with the same regional policy as call screening and suppresses imported offline-reputation scoring for that exact caller. It does **not** add a whitelist entry, weaken emergency/manual rules, or bypass broad local policies.
- **Private-by-design feedback** — the recovery action writes only local caller-reputation data after the call is already logged; it adds no network request, report submission, background work, permission, or pre-response I/O.

### New in 1.12.0

- **Opt-in quiet screening for unknown and private calls** — choose whether eligible unknown or withheld calls are rejected (the existing default) or quietly silenced while the call continues without ringing.
- **Preserved safety precedence** — quiet screening is evaluated only after emergency protection, temporary allowances, whitelists, explicit and legacy block rules, callback grace, schedules, and temporary firewall protection. It never weakens an explicit blocking action.
- **Portable preference and safe upgrade** — the choices are disabled by default, reset when a curated protection profile is applied, survive encrypted local backup and restore, and use a non-destructive Room 9→10 migration.

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
| **Block Unknown** | Reject numbers not in your contacts (uses `READ_CONTACTS`), or optionally silence eligible unknown calls instead. |
| **Block Private/Hidden** | Reject withheld / private callers, or optionally silence them instead. |
| **Block All Except Whitelist** | Nuclear mode — only whitelisted numbers ring. |
| **Opt-in Callback Grace** | After you dial a valid number, allow only that exact number to call back for 15 minutes; never overrides an explicit block. |
| **Advanced Scheduling** | Time-based rules with day-of-week bitmask and per-schedule trusted caller exceptions. Example: *Block all except whitelist 22:00–06:00 Mon–Fri while allowing an on-call number*. Overnight spans supported. |
| **Temporary Block Quick Tile** | User-added Quick Settings toggle for the existing one-hour local block-all override. Add it from **Settings → Quick access** (Android 13+) or via the Quick Settings edit menu (Android 7–12); a second tap cancels it. |
| **Temporary Exact Block** | Locally block one valid non-emergency number for 1 hour, 1 day, 7 days or 30 days. The active temporary-block list shows its expiry and offers one-tap cancellation; temporary allows and whitelists still win. |
| **Home-screen Stats Widget** | Compact local counts for blocked calls today and in total, with manual refresh and direct opening of the app. No number, contact, or call-reason content is exposed. |
| **Offline Reputation Lists** | Optional user-selected, bounded CSV lists with auditable source metadata and SHA-256 fingerprint. No URL fetching, automatic updates, cloud sync, or retained storage access. Exact E.164 scores from 80–100 can block only after all manual and safety policies. |
| **Blocked Log** | Professional local timeline of blocked calls with number, display name, reason, timestamp, and recovery actions. Retain it forever (default), or automatically remove only entries older than 7, 30, 90 or 365 days; this never changes screening rules or Android’s shared call log. |
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
   Unknown/private policy? → BLOCK by default, or SILENCE only when the matching opt-in is enabled
   Local risk, behavior and exact offline-reputation signals? → BLOCK only at the configured threshold (offline score 80–100 is a risk floor)
   otherwise → ALLOW
   ```
4. If blocked → `CallResponse.Builder().setDisallowCall(true).setRejectCall(true)` + optional private BlackList log + optional notification. If an eligible unknown/private policy is explicitly set to **Silence instead**, the call is not rejected and Android suppresses its ring while retaining normal system call history and notification behavior. By default the Android call log is retained; **Settings → Privacy → Private blocked-call history** can hide blocked calls from the shared system log while keeping the in-app record. In the same Privacy section, **Blocked-call history retention** can keep BlackList’s in-app records forever (the default) or expire records older than 7, 30, 90 or 365 days after a later blocked call has already been answered and logged. This setting never changes a screening decision or Android’s shared call log.

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

Imported entries never overwrite local caller reputation or a user verdict. They are evaluated only after emergency protection, temporary allow, whitelist, explicit blacklist and legacy exact blocks, callback grace, schedule exceptions/schedules, and broad local policies. An explicit `TRUSTED` or `NOT_SPAM` local verdict suppresses imported reputation scoring. From a blocked-call record, **Not spam** asks for confirmation and saves exactly that local `NOT_SPAM` verdict for a usable dialable number; it neither whitelists the caller nor overrides broad policies. Removing a source deletes only its own imported entries. Encrypted local backup and restore preserve the imported source metadata and entries as policy data; local call history and behaviour history remain excluded.

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
| `READ_CONTACTS` | Optional; checks whether a caller is in contacts for *Block Unknown*. |
| `POST_NOTIFICATIONS` | Optional; shows BlackList's low-priority blocked-call notification on Android 13+. |
| `BIND_SCREENING_SERVICE` | Service-level permission used by Android to bind `CallScreeningService`; it is not requested from the user. |

BlackList declares **no** `INTERNET`, call-log, phone-state, call-placement, or call-answering permission. The user grants the Call Screening role through Android's standard role UI.

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
- [x] User-controlled, bounded retention for BlackList’s in-app blocked-call history, with a safe Keep forever default and no scheduled background cleanup.
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
