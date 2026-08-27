# Changelog

All notable changes to BlackList are documented in this file. The project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[1.12.0]: https://github.com/Alaa91H/BlackList/compare/v1.11.0...v1.12.0
[1.11.0]: https://github.com/Alaa91H/BlackList/compare/v1.10.0...v1.11.0
[1.10.0]: https://github.com/Alaa91H/BlackList/compare/v1.9.0...v1.10.0
[1.9.0]: https://github.com/Alaa91H/BlackList/compare/v1.8.0...v1.9.0
[1.8.0]: https://github.com/Alaa91H/BlackList/compare/v1.7.0...v1.8.0
[1.7.0]: https://github.com/Alaa91H/BlackList/compare/v1.6.0...v1.7.0
[1.6.0]: https://github.com/Alaa91H/BlackList/compare/v1.5.0...v1.6.0
[1.5.0]: https://github.com/Alaa91H/BlackList/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/Alaa91H/BlackList/releases/tag/v1.4.0
