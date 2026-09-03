# Changelog

All notable changes to BlackList are documented in this file. The project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.27.0] - 2026-09-03

### Added

- **First-time caller policy.** Users can optionally block or silence unsaved callers that have no prior local BlackList reputation history.
- **Repeated caller policy.** Users can optionally block or silence repeated attempts from the same number after a configurable local threshold.
- **Configurable repetition window.** The repeated-caller detector now supports a bounded 1–60 minute window and a bounded 2–10 attempt threshold.

### Changed

- Behavioral decisions remain local and memory-first on the CallScreeningService hot path. Contact-dependent first-time caller protection fails open when Contacts permission or contact data is unavailable.
- Emergency numbers, temporary allows, whitelisted numbers, and explicit blacklist rules retain precedence over the new behavioral policies.
- Encrypted backups preserve the new policies and safely restore older backups with both policies disabled.
- Added Room 14→15 migration with conservative defaults that preserve existing behavior.

### Quality

- Added engine coverage for first-time blocking, whitelist precedence, repeated-call silence at the configured threshold, and disabled repeated-call behavior.
- CI passed after correcting the test fixture to share the in-memory behavior engine across repeated attempts.

### Privacy and compatibility

- No network lookup, cloud reputation service, AccessibilityService, hidden API, ADB shell, or privileged Root/Shizuku operation is introduced.
- The feature uses only local contact availability, local reputation snapshots, and bounded in-memory behavioral state.

## [1.26.0] - 2026-09-03

### Added

- **Configurable international caller policy.** Settings now provide an opt-in policy for callers outside the device's configured home region.
- **International silence mode.** Users can choose to silence matching international calls instead of rejecting them.

### Privacy and compatibility

- International classification remains deterministic and offline through the existing local phone-number metadata.
- Existing installations keep international blocking disabled by default through a Room 13→14 migration.
- Encrypted backups now preserve both international policy settings while older backups restore with safe defaults.
- Explicit blacklist rules, emergency handling, whitelist precedence, and per-rule schedules remain higher-priority controls.

### Quality

- Added engine coverage for foreign-vs-home-region behavior and both block and silence actions.

## [1.25.0] - 2026-09-01

### Changed

- **Per-rule scheduling only.** Removed the standalone schedule page, navigation entry, and global schedule enforcement path. Scheduling is now configured exclusively inside each blacklist rule.
- **Manual temporary-block duration.** Renamed the user-facing flow to Temporary block and added a custom duration input in minutes. Values are bounded locally from 1 minute through 30 days.
- **Consistent temporary-block intake.** The custom duration is available both from the blacklist screen and from the explicitly shared-number flow.

### Privacy and safety

- Existing global schedule records remain migration-safe for older installations but are no longer applied by the call-screening engine. New enforcement decisions use only the schedules attached to matching rules.
- Temporary blocks remain exact-number, local, bounded, and subject to emergency, whitelist, and temporary-allow precedence.

### Quality

- Updated policy tests for the manual duration range and kept invalid/overflowing duration rejection coverage.
- Removed the obsolete ScheduleScreen and ScheduleViewModel registrations so no user-facing global scheduler remains.

## [1.24.0] - 2026-09-01

### Added

- **International caller rule.** Users can now create a dedicated rule that matches numbers outside the device's configured home region, using the local libphonenumber metadata and no network lookup.
- **International rule actions.** International rules support the same independent Reject or Silence enforcement and per-rule schedule controls as other blacklist rules.

### Quality

- Added engine coverage proving that a local-region number is not classified as international while a valid foreign E.164 number is matched.
- Extended encrypted backup validation to accept and restore the new rule type safely.

### Privacy

- International classification is deterministic and offline. No caller number is uploaded, queried against a remote reputation service, or persisted outside the existing local rule database.

## [1.23.0] - 2026-09-01

### Added

- **Hidden and unknown caller rules.** The blacklist editor now exposes the engine's existing restricted/hidden and unidentifiable caller matchers as first-class rules, with independent Reject or Silence enforcement and optional schedules.
- **Capability roadmap.** Added a documented capability matrix separating public Android APIs, policy-sensitive permissions, and optional Root/Shizuku integrations.

### Changed

- **Clear capability boundaries.** The product documentation now distinguishes ringtone silence from guaranteed suppression of Android's system call UI, call log, or missed-call artifacts.
- **Privilege safety posture.** Root/Shizuku support is planned as an optional, capability-detected adapter rather than a hidden-API or undocumented Telecom bypass.

### Quality

- Reused the existing hidden/unknown engine coverage and added UI paths that create valid rules without requiring a phone number pattern.
- Preserved the offline-first call-screening hot path and existing whitelist/emergency precedence.

### Privacy

- No AccessibilityService, hidden API, embedded ADB shell, cloud lookup, or destructive SMS/Telecom database mutation is introduced by this cycle.

## [1.22.0] - 2026-08-31

### Added

- **Call Log and SMS number intake.** Users can explicitly choose numbers from the device call history or messages alongside Contacts, with source switching, search, deduplication, and a bounded in-memory picker result.
- **Per-rule enforcement choice.** Every new blacklist rule can independently reject an incoming call or silence its ringtone while leaving the call connected.
- **Per-rule schedules.** Rules can be enabled for selected weekdays and local start/end times, including overnight windows that continue across midnight.

### Changed

- **Permission-gated history access.** `READ_CALL_LOG` and `READ_SMS` are requested only after the user selects the corresponding picker source and presses Grant. The call-screening service never reads Contacts, Call Log, or SMS.
- **Rule evaluation.** Scheduled rules are skipped outside their configured local window while preserving existing precedence and offline behavior.

### Quality

- Added focused coverage for regular and overnight per-rule windows, weekday boundaries, disabled and invalid schedules, and stable schedule formatting.
- Bounded history reads to the most recent 300 unique normalized numbers per source and safely return an empty list when access is unavailable.

### Privacy

- BlackList remains offline-first with no network permission, analytics, tracker, cloud sync, Root/Shizuku dependency, hidden-API bypass, or background history scan. Call Log and SMS data is read only after an explicit user action, used for the current picker session, and not persisted by the picker.

## [1.21.0] - 2026-08-27

### Added

- **Step-by-step draft decision path.** The in-editor draft preview now shows every local policy stage in the actual firewall precedence order. Each stage is labeled as checked with no decision, decisive, or not evaluated after an earlier decisive result.
- **Decisive-stage explanation.** Users can see whether an emergency safeguard, temporary allow, whitelist, temporary block, permanent blacklist rule, legacy block, callback grace, schedule, temporary firewall, broad policy, reputation/risk, or default allow produced the preview result.

### Changed

- **Trace derived from the final decision only.** The new read-only interpreter maps the preview's existing backend result to the stable evaluation order; it does not re-evaluate policy, access storage, or run on the CallScreeningService path.

### Quality

- Added trace assertions for permanent silent-rule decisions, whitelist precedence, emergency precedence, and stages that are not evaluated after an earlier decisive safeguard.

### Privacy

- The decision path is limited to the user-triggered, current-session draft preview. It adds no call, persistence, log, notification, permission, Call Log/SMS or Contacts read, worker, network request, Root/Shizuku dependency, hidden-API bypass, ADB command, or call-screening hot-path I/O.

## [1.20.0] - 2026-08-27

### Added

- **Full draft-rule decision preview.** The blacklist editor can now test one user-supplied number against the current local policy with an unsaved draft rule overlaid in memory. It shows the expected allow, reject, or ringtone-silence outcome, explanation, and matched rule IDs before the user saves anything.
- **Precedence visibility beyond rule overlap.** The preview exposes when emergency protection, temporary allow, whitelist, or a higher-priority persistent rule overrides the draft. This makes policy precedence inspectable at edit time rather than only after an incoming call.

### Changed

- **Isolated test behaviour.** A preview uses a fresh in-memory behavior engine and a snapshot overlay, so running a test cannot contribute an attempt to the live behavioral signals used by actual call screening.
- **Stale-result prevention.** Editing the draft rule, its enforcement choice, or the test number clears the previous result. Closing or saving the editor also clears the current-session preview.

### Quality

- Added focused coverage for an unsaved silent rule, whitelist and emergency precedence over a draft, and preservation of the source policy snapshot during preview.

### Privacy

- The draft preview accepts only a user-entered phone number of at most 64 characters. It never places a call, writes a rule, logs an event, posts a notification, persists the test number, reads Call Log/SMS, accesses Contacts, starts a worker, or performs network, Root/Shizuku, hidden-API, ADB, or call-screening-path I/O.

## [1.19.0] - 2026-08-27

### Added

- **Pre-save rule conflict preview.** The Blacklist rule editor now analyzes a draft locally before it is saved. It identifies provable overlaps with active permanent rules and states whether the draft or the existing rule takes precedence.
- **Duplicate guard with visible explanation.** An equivalent active rule is shown as a duplicate and cannot be saved. The same scope analysis is enforced in the repository after normalization, not merely in the UI.
- **Bounded live analysis.** The editor reviews at most the 200 highest-priority active permanent rules and explicitly reports when more rules were not inspected. Saving always performs the complete duplicate check.

### Changed

- **Stable matching tie-breaks.** When multiple rules match with the same numeric priority, the newer rule wins; equal timestamps then resolve to the greater rule ID. Runtime evaluation and the conflict preview use this same deterministic order.
- **Conservative overlap reporting.** The preview reports only overlaps that can be proven from local rule scopes: equivalent patterns, exact-versus-prefix/suffix/contains/range/country matches, nested prefix/suffix/contains patterns, intersecting numeric ranges, and identical countries. It does not invent a warning for ambiguous combinations.

### Quality

- Added isolated coverage for regional exact-number normalization, duplicate detection, precedence of an existing higher-priority rule, deterministic equal-priority ordering, disabled-rule exclusion, bounded preview disclosure, and exact-range overlap.

### Privacy

- The preview is read-only and memory-only. It adds no rule until the user presses Save, and adds no permission, Call Log/SMS access, default-handler role, network request, cloud service, analytics event, tracker, worker, Root/Shizuku dependency, hidden-API bypass, ADB command execution, or call-screening-path I/O.

## [1.18.0] - 2026-08-27

### Added

- **Per-rule call handling.** Every persistent blacklist rule can now explicitly reject matching calls or use Android’s supported ringtone-silence response. The selected handling is visible on each saved rule.
- **Consistent local coverage.** Exact, prefix, suffix, contains, numeric-range, and country rules all support the same deliberate handling choice.

### Changed

- **Truthful silence semantics.** The interface now states that silence keeps a matching call connected while requesting that Android mute its ringtone. It does not promise to suppress system call UI, shared call history, or missed-call notifications.
- **Deterministic rule execution.** The highest-priority matching persistent rule now determines whether its match is rejected or silenced. Emergency protection, temporary allows, and whitelist entries remain higher-priority safeguards.
- **Backward-compatible policy storage.** A non-destructive Room 11→12 migration adds the default `BLOCK` enforcement to existing rules. Encrypted local backups preserve the selected enforcement, while older backups safely restore as `BLOCK`.

### Fixed

- **Central emergency guard.** Repository validation now rejects an attempt to create a permanent exact-number blacklist rule for a regional emergency number, regardless of the UI entry path.

### Quality

- Added engine coverage for an exact silent rule, including the returned decision, explanatory backend, and matched rule action.

### Privacy

- This release adds no permission, Call Log/SMS access, default-handler role, network request, cloud service, analytics event, tracker, worker, timer, boot receiver, Root/Shizuku dependency, hidden-API bypass, ADB command execution, or call-screening-path I/O.

## [1.17.0] - 2026-08-27

### Added

- **Explicit shared-number intake.** BlackList is now available as a `text/plain` Android share target. A user can deliberately share text from another app, review a bounded set of valid local number suggestions, and choose one candidate.
- **Confirmed rule creation.** The review screen requires an explicit action before it persists an exact permanent block, whitelist entry, or one of the existing validated temporary exact blocks. The original shared text is retained only in memory for the current review.

### Changed

- **Bounded untrusted-text handling.** The share receiver accepts `text/plain` only, keeps at most 2,048 input characters, identifies at most eight de-duplicated phone-like candidates, and applies regional normalization plus emergency-number protection before a candidate reaches the action controls.
- **Accurate product boundaries.** README documentation now distinguishes a user-initiated share selection from Call Log/SMS access. BlackList neither reads Android’s shared call history, message inbox, attachments or clipboard in the background, nor requests the default Phone, Assistant or SMS role.

### Quality

- Added focused unit coverage for empty and non-phone payloads, duplicate candidate removal, bounded candidate output, bounded input handling, and rejection of alphanumeric embedded number fragments.

### Privacy

- The share flow adds no runtime permission, Call Log/SMS permission, default-handler role, content-provider query, retained URI permission, network request, cloud service, analytics event, tracker, worker, timer, boot receiver, root/Shizuku dependency, hidden-API bypass, shared-text log, or call-screening-path I/O.

## [1.16.0] - 2026-08-27

### Added

- **Temporary exact-number blocking.** Blacklist now offers a deliberate local action to block one valid non-emergency phone number for 1 hour, 1 day, 7 days or 30 days. Active temporary blocks are shown separately with a localized expiry time and an explicit cancel action.
- **Bounded local policy.** The feature accepts only canonical E.164 digit identities, replaces a temporary block for the same number atomically, and caps the set at 100 active temporary exact blocks.

### Changed

- **Stable safety precedence.** A temporary exact block is evaluated after emergency protection, temporary allows and whitelists, but before permanent blacklist/legacy rules and broad policies. It expires directly in the immutable policy snapshot, even before opportunistic cleanup removes its stored row.
- **Validated backup continuity.** Encrypted local policy backups now preserve temporary exact blocks only when their number identity and expiry are valid. Existing backup formats remain compatible.
- **Precise quiet-screening documentation.** The README now distinguishes Android’s supported `SILENCE` action—an incoming call continues without ringing—from block/reject. It makes no unsupported promise to hide a ringing call, the shared call log or missed-call notification.

### Fixed

- **Complete temporary-rule cleanup.** The local cleanup path now recognizes every internal temporary-rule type, including temporary exact blocks.

### Quality

- Added focused unit coverage for fixed expiry options, E.164 identity bounds, expiry rejection, active/expired exact matching, partial-number non-matching, and precedence over temporary allow/whitelist safeguards.

### Privacy

- The release adds no runtime permission, Call Log/SMS access, default-SMS role, network request, cloud service, root/Shizuku integration, hidden API bypass, analytics event, tracker, worker, timer, boot receiver, shared history export or call-screening-path I/O.

## [1.15.0] - 2026-08-27

### Added

- **User-confirmed Quick Settings placement.** Settings → Quick access now gives the existing Temporary call block tile a discoverable add path. Android 13+ opens the platform placement prompt only when the user presses the explicit button; Android 7–12 shows the corresponding manual-add instruction.
- **Localized result feedback.** English and Arabic feedback distinguishes added, already-added, declined and unavailable placement outcomes without exposing system error details.

### Changed

- **Clear, reversible one-hour shortcut.** The Quick Settings tile remains a toggle for the existing local Temporary Block All override: a first tap activates it for one hour and a second tap cancels it. It does not modify any permanent rule, protection profile, whitelist, reputation verdict, notification setting, local history or Android call log.
- **Platform-native semantics.** The declared TileService now uses a dedicated monochrome tile icon, declares its toggleable state and is categorized as a privacy control where the device UI supports that metadata.

### Fixed

- **Secure lock-screen handling.** A tap from a secured locked device now requests an unlock before it can change the temporary blocking override; unlocked and unsecured-device behavior stays immediate.

### Quality

- Added focused unit coverage for every secured/locked combination behind the tile’s unlock decision.

### Privacy

- The discovery flow reuses the existing local TileService and Android system placement UI. It adds no permission, network request, cloud service, analytics event, tracker, worker, timer, boot receiver, stored setting, backup field or call-screening-path I/O.

## [1.14.0] - 2026-08-27

### Added

- **User-controlled local history expiry.** Settings → Privacy now offers a deliberate retention choice for BlackList’s own blocked-call history: keep it forever (the default), or retain the most recent 7, 30, 90 or 365 days.
- **Clear, localized scope.** The English and Arabic interface explains that expiry affects only the in-app blocked-call timeline. It does not change call-blocking rules or Android’s shared call log.

### Changed

- **Post-response, bounded cleanup.** After a future call has already received its Telecom decision and its BlackList log record is written, the app removes only older local `blocked_call_logs` rows. Entries exactly on the cutoff and the newly logged call remain intact.
- **Portable policy without activity export.** The selected retention setting is included in encrypted policy backup and restore. Older backups safely resolve to “keep forever”; call history itself remains deliberately excluded.

### Fixed

- **Safe schema evolution.** A non-destructive Room 10→11 migration introduces the defaulted setting while preserving existing local rules, lists, schedules, reputation sources and call history.
- **Fail-conservative data handling.** Unsupported retained-setting values are rejected during backup restore and treated as “keep forever” by post-decision cleanup, preventing accidental removal of user history.

### Quality

- Added focused unit coverage for the complete fixed retention set, default no-expiry behavior, exclusive cutoff calculation, early-clock safety and invalid-value rejection.

### Privacy

- The feature adds no network request, cloud service, analytics event, tracker, shared-storage access, permission, timer, worker, boot receiver or call-screening-path I/O. Cleanup runs locally only after the call response.

## [1.13.0] - 2026-08-27

### Added

- **Confirmed false-positive recovery.** The Blocked Log now asks for explicit confirmation before saving a durable local `NOT_SPAM` verdict for an eligible blocked caller.
- **Clear local feedback.** The recovery flow reports concise success, invalid-number and retry-safe failure states in English and Arabic without exposing raw internal exceptions.

### Changed

- **Deliberate scope for user verdicts.** A confirmed `NOT_SPAM` verdict suppresses imported offline-reputation scoring for the same normalized caller. It does not create a whitelist entry, bypass unknown/private or block-all policies, or weaken emergency, manual-rule, callback-grace or schedule precedence.
- **Unified number identity.** False-positive recovery now uses the same regional `PhoneNumberNormalizer` configuration as call screening, preserving the identity key used by local caller reputation and offline reputation matching.

### Fixed

- **Explicit feedback instead of soft decay.** The former Blocked Log action only recorded an allowed-call counter after stripping the number; it could leave imported reputation active and use a different stored key. It now persists the requested local verdict directly and keeps caller formatting intact through shared normalization.

### Quality

- Added an isolated `ReputationEngine` regression test proving that `NOT_SPAM` is persisted as an explicit, durable local verdict, alongside the existing policy-precedence coverage.

### Privacy

- Recovery is user initiated and runs only after the call decision and local log write. It adds no network request, remote report, analytics event, background work, runtime permission or call-screening-path I/O.

## [1.12.0] - 2026-08-27

### Added

- **Optional quiet screening.** When *Block Unknown* or *Block Private* is enabled, users can independently choose to silence eligible calls rather than reject them. The opt-ins are disabled by default and are clearly unavailable until their parent protection policy is enabled.
- **Portable local preference.** Quiet-screening choices are now retained in encrypted local backup and restore, and a non-destructive Room 9→10 migration preserves every existing rule, list, source, schedule and setting.

### Changed

- **Safe decision routing.** Unknown and private policies return `SILENCE` only for their explicit opt-in; all existing users retain the reject behavior. Emergency protection, temporary allowances, whitelists, explicit and legacy blocks, callback grace, schedules, temporary firewall rules and block-all mode continue to take precedence.
- **Curated profile clarity.** Applying a Normal, Focus or Whitelist-only protection profile resets optional quiet-screening choices, avoiding an inherited custom action that the preset does not promise.

### Fixed

- **Accurate permission documentation.** The README now reflects the manifest: BlackList declares only optional Contacts and Notifications permissions, plus the service-only screening bind permission. It does not request call-log, phone-state, calling, call-answering or Internet permissions.
- **Profile state consistency.** Home-screen quick protection toggles now mark the selected configuration as Custom, matching the Settings screen behavior.

### Quality

- Added decision-engine regression coverage for explicit opt-in, default rejection, parent-policy gating, private-call silence, manual-rule precedence and profile resets.

### Privacy

- Quiet screening uses Android's local `CallResponse` capability only. It adds no network traffic, background job, runtime permission, data collection or pre-response I/O.

## [1.11.0] - 2026-08-27

### Added

- **Optional offline reputation-list import.** Users can select a local CSV once, review declared source provenance, optional version, display-only HTTPS URL, SHA-256 fingerprint, row counts and a sample, then explicitly confirm before the list is stored.
- **Auditable, bounded local policy.** Imported source metadata and exact-number entries are stored in separate indexed Room tables with cascade deletion. The device caps imports at 10 sources, 5,000 accepted entries per source and 10,000 accepted entries total.
- **Encrypted backup continuity.** Encrypted local policy backups now carry imported source metadata and entries, validate source-to-entry references before mutation, and remap source identifiers atomically on restore. Caller history and local behavioural reputation history remain excluded.

### Changed

- **Conservative exact-match risk policy.** Only strict E.164 entries (`+` followed by 7–15 ASCII digits) are accepted. An imported score of 80–100 is a risk floor for that exact number; lower scores remain informational risk context and do not directly block.
- **Explicit precedence and explainability.** Emergency safeguards, temporary allowances, whitelist entries, explicit blacklist rules, legacy exact blocks, callback grace, schedules and broad local policies remain ahead of imported data. Local `TRUSTED` and `NOT_SPAM` verdicts suppress imported scoring. High-risk decisions identify their local offline source.

### Fixed

- **Safe file trust boundary.** Parsing is strict UTF-8 and bounded to 1 MiB, 10,050 lines and 10,000 source rows; it validates required metadata and the exact `number,score,category` schema, rejects malformed provenance URLs, controls row and cell sizes, and uses locale-independent lowercase SHA-256 formatting.
- **No screening-path I/O.** Imported policy is aggregated in the immutable background-refreshed policy snapshot, so `CallScreeningService` performs no Room, contact, URI or file access before responding to Telecom.

### Quality

- Added regression coverage for canonical parsing, deterministic fingerprinting, malformed and oversized input, provenance validation, duplicate and invalid rows, aggregation across sources, threshold behavior, exact matching, and the established emergency/manual-policy precedence.

### Privacy

- The import is entirely user initiated and local: it uses Android’s one-time content selection, retains no URI permission, performs no URL fetch, network request, automatic refresh, background sync, cloud transfer, tracker or new runtime permission.

## [1.10.0] - 2026-08-27

### Added

- **Home-screen blocked-call statistics widget.** A launcher-native widget now presents the number of calls blocked today and the total recorded blocked calls, with a direct app shortcut and an explicit refresh action.
- **Aggregate-only widget data.** The widget intentionally renders only local counts; it never exposes phone numbers, contact names, call reasons, rule details, or any remote content on the launcher.

### Changed

- **Post-decision refresh path.** Widget updates are scheduled only after a blocked-call record has been written or the local blocked-call log is cleared. The Telecom response path remains free of widget and database work.
- **Bounded database queries.** The widget uses direct aggregate counts rather than materializing call-history rows, including a local-calendar start-of-day boundary for its daily total.

### Quality

- Added regression coverage for start-of-day calculation in explicit time zones, including an overnight date-boundary case.
- Registered the widget with Android’s standard `AppWidgetProvider` integration and a conservative 30-minute platform refresh interval.

### Privacy

- No network capability, analytics integration, runtime permission, cloud synchronization, call-log permission, or sensitive launcher content was introduced.

## [1.9.0] - 2026-08-27

### Added

- **Per-schedule trusted caller exceptions.** A schedule can now carry exact-number local exceptions, allowing selected callers to ring only while that specific schedule is active without making them permanent global whitelist entries.
- **Schedule exception management.** The Schedule screen now exposes a focused local editor to add and remove validated trusted numbers for each rule, with English and Arabic localization.

### Changed

- **Deliberate firewall precedence.** Schedule exceptions are evaluated only after emergency safeguards, manual temporary allowances, whitelists, explicit blacklist rules, and legacy exact blocks. A matching exception bypasses only the broad action of its own active schedule.
- **Portable local policy.** Encrypted policy backup and restore now preserve schedule exceptions while validating parent-rule relationships, normalized phone-number format, and bounded restore sizes.

### Fixed

- Added a non-destructive Room 7→8 migration with an indexed, cascade-deleted exception table so deleting a schedule cannot leave orphaned caller data.
- Policy snapshots now refresh schedule exceptions in the background, keeping Room I/O out of the `CallScreeningService` decision path.

### Quality

- Added regression coverage proving that a schedule exception allows only its matching number during an active schedule and never overrides an explicit blacklist rule.

### Privacy

- Schedule exceptions store only a local normalized number and parent schedule identifier. No network request, analytics event, cloud service, permission, or background sync was introduced.

## [1.8.0] - 2026-08-27

### Added

- **Opt-in recent outgoing-call callback grace.** After Android reports a definite outgoing call to a valid non-emergency number, users may allow only that exact number to call back for 15 minutes. The setting is disabled by default.
- **Bounded local callback state.** A small in-memory bridge prevents a snapshot-refresh race for an immediate callback, while an expiring internal policy rule preserves the allowance across normal process lifecycle events. Both stores cap their active entries and retain only normalized digits plus an expiry.

### Changed

- **Safer, explicit precedence.** Manual temporary allows and whitelists retain their established behavior; explicit blacklist rules and legacy exact blocks remain authoritative over the automatic callback allowance. The allowance applies only before broad schedules, temporary block-all mode, unknown/private filtering, and local risk policies.
- **User-controlled continuity.** The preference and any still-valid callback rules now travel through encrypted policy backup and restore, with strict validation of number format and temporary expiry bounds.

### Fixed

- Outgoing and unknown call directions now receive an immediate allow response before any local persistence. Only a definite `DIRECTION_OUTGOING` call can create a callback allowance; unknown directions fail open without creating one.
- Added a non-destructive Room 6→7 migration so existing local policy data is preserved and the new option stays disabled on upgrade.

### Quality

- Added regression coverage for exact-number matching, expiry, broad-policy recovery, explicit-block precedence, disabled-by-default settings, and stable manual temporary-allow behavior.

### Privacy

- The feature is fully local, requires no new permission or call-log access, makes no network request, and introduces no cloud service, analytics, tracker, or external reputation source.

## [1.7.0] - 2026-08-27

### Added

- **Private blocked-call history.** Users can now choose to keep blocked calls exclusively in BlackList’s private local log, preventing those calls from appearing in Android’s shared call history. The in-app blocked log remains available with the timestamp and explainable decision reason.

### Changed

- **Explicit transparency and privacy control.** The default remains transparent: blocked calls continue to appear in Android’s call log. Enabling the new setting affects only calls BlackList blocks; allowed and silenced calls preserve their normal system history.
- **Portable privacy preference.** The setting is included in the existing encrypted policy backup and restore flow.

### Fixed

- Added a non-destructive Room migration for the new privacy preference and regression tests that prevent accidental hiding of allowed or silenced calls.

### Privacy

- The optional private-history mode requires no new permission, network access, cloud service, analytics SDK, or external data source.

## [1.6.0] - 2026-08-27

### Added

- **Emergency callback grace.** When Android routes an outgoing emergency call through the screening service, BlackList opens a short, local-only 15-minute allowance for an emergency dispatcher or responder to call back. The expiry is persisted in the local policy database and also kept in memory so a rapid callback is never delayed by storage refresh.

### Changed

- **Safer decision precedence.** Explicit user blacklist rules and legacy exact blocks continue to win over the emergency callback allowance. The allowance only precedes broad schedules, temporary block-all rules, unknown/private policies, and risk-based blocking.
- **Backup continuity.** Encrypted backups now carry the emergency callback expiry when it is still valid, while restore validation rejects unreasonably long grace windows.

### Fixed

- Added a non-destructive Room migration for the new local expiry field. Existing policy data is retained when upgrading.
- Added regression coverage proving that an active grace window allows callbacks through broad blocking policies, cannot override explicit blocks, and ceases to apply immediately after expiry.

### Privacy

- The feature uses no network service, location, contact lookup, call-log permission, analytics SDK, or new runtime permission. It stores only one local timestamp.

## [1.5.0] - 2026-08-27

### Added

- **Network verification awareness.** On Android 11 and later, BlackList now preserves Android Telecom's caller-number verification outcome as part of the immutable call event. A failed verification increases the local risk score and is retained in the explainable decision record. This uses only platform-provided metadata and makes no network request.
- **Regional emergency safeguard.** Emergency short numbers are resolved with libphonenumber for the detected device region before user rules are evaluated. Exact rules, temporary firewalls, schedules, and broad block-all policies cannot override this safeguard.

### Changed

- **Region-aware normalization.** The runtime normalizer now takes its fallback region from the device configuration, rather than relying on a fixed country. This improves local-format parsing, country rules, and emergency short-number recognition while preserving fully offline operation.
- **Explainable decisions.** The decision output now reports `SUCCESS`, `FAILED`, or `UNKNOWN` caller-number verification status, making diagnostics and the decision simulator more transparent.

### Fixed

- Restored the previously unused failed-verification risk factor by connecting it to Android Telecom's native verification status.
- Replaced the narrow hard-coded emergency-number list with localized emergency-number detection.

### Quality

- Added regression coverage for a regional emergency number under a broad block policy and for verification-driven risk scoring and decision explainability.
- No new permissions, analytics SDKs, cloud services, or data collection were introduced.

## [1.4.0] - 2026-08-24

### Added

- Production-grade local call-firewall foundation with rule matching, risk scoring, behavior signals, reputation tracking, temporary protection controls, diagnostics, and a decision simulator.

[1.25.0]: https://github.com/Alaa91H/BlackList/compare/v1.24.0...v1.25.0
[1.24.0]: https://github.com/Alaa91H/BlackList/compare/v1.23.0...v1.24.0
[1.23.0]: https://github.com/Alaa91H/BlackList/compare/v1.22.0...v1.23.0
[1.22.0]: https://github.com/Alaa91H/BlackList/compare/v1.21.0...v1.22.0
[1.21.0]: https://github.com/Alaa91H/BlackList/compare/v1.20.0...v1.21.0
[1.20.0]: https://github.com/Alaa91H/BlackList/compare/v1.19.0...v1.20.0
[1.19.0]: https://github.com/Alaa91H/BlackList/compare/v1.18.0...v1.19.0
[1.18.0]: https://github.com/Alaa91H/BlackList/compare/v1.17.0...v1.18.0
[1.17.0]: https://github.com/Alaa91H/BlackList/compare/v1.16.0...v1.17.0
[1.16.0]: https://github.com/Alaa91H/BlackList/compare/v1.15.0...v1.16.0
[1.15.0]: https://github.com/Alaa91H/BlackList/compare/v1.14.0...v1.15.0
[1.14.0]: https://github.com/Alaa91H/BlackList/compare/v1.13.0...v1.14.0
[1.13.0]: https://github.com/Alaa91H/BlackList/compare/v1.12.0...v1.13.0
[1.12.0]: https://github.com/Alaa91H/BlackList/compare/v1.11.0...v1.12.0
[1.11.0]: https://github.com/Alaa91H/BlackList/compare/v1.10.0...v1.11.0
[1.10.0]: https://github.com/Alaa91H/BlackList/compare/v1.9.0...v1.10.0
[1.9.0]: https://github.com/Alaa91H/BlackList/compare/v1.8.0...v1.9.0
[1.8.0]: https://github.com/Alaa91H/BlackList/compare/v1.7.0...v1.8.0
[1.7.0]: https://github.com/Alaa91H/BlackList/compare/v1.6.0...v1.7.0
[1.6.0]: https://github.com/Alaa91H/BlackList/compare/v1.5.0...v1.6.0
[1.5.0]: https://github.com/Alaa91H/BlackList/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/Alaa91H/BlackList/releases/tag/v1.4.0
