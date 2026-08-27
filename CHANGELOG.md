# Changelog

All notable changes to BlackList are documented in this file. The project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[1.8.0]: https://github.com/Alaa91H/BlackList/compare/v1.7.0...v1.8.0
[1.7.0]: https://github.com/Alaa91H/BlackList/compare/v1.6.0...v1.7.0
[1.6.0]: https://github.com/Alaa91H/BlackList/compare/v1.5.0...v1.6.0
[1.5.0]: https://github.com/Alaa91H/BlackList/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/Alaa91H/BlackList/releases/tag/v1.4.0
