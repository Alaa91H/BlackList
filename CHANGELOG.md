# Changelog

All notable changes to BlackList are documented in this file. The project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.5.0] - 2026-08-27

### Added

- **Network verification awareness.** On Android 10 and later, BlackList now preserves Android Telecom's caller-number verification outcome as part of the immutable call event. A failed verification increases the local risk score and is retained in the explainable decision record. This uses only platform-provided metadata and makes no network request.
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

[1.5.0]: https://github.com/Alaa91H/BlackList/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/Alaa91H/BlackList/releases/tag/v1.4.0
