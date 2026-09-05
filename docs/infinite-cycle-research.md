# Continuous Improvement Cycle Research

## Platform constraints

Android's official call-screening guidance supports blocking, rejecting as if the user rejected the call, silencing the ringtone, and optionally excluding a screened call from the device call log. Android 10+ can screen calls from numbers not in the address book without requiring READ_CALL_LOG solely for screening/caller-ID behavior. Source: https://developer.android.com/develop/connectivity/telecom/dialer-app/screen-calls

The product must continue to distinguish supported ringtone silence from guaranteed suppression of every system UI, missed-call artifact, or OEM-specific behavior.

## Shizuku capability boundary

The Shizuku project describes a broker model in which a privileged process is started through root or ADB and apps receive a Binder to request system APIs. It also documents that ADB permissions vary by Android version and that callers should check whether Shizuku is running and whether the required permission is granted before using an API. Source: https://github.com/RikkaApps/Shizuku

The future adapter should therefore be capability-detected, permission-checked, auditable, optional, and fail closed for the requested privileged operation without becoming a hidden bypass or shell-command executor.

## Competitor signals

CallShield describes a priority-ordered, testable checker pipeline with manual and contact whitelist layers, contacts-only mode, verification signals, explicit blocks, recent-dialed/answered/repeated-call allowances, campaign detection, local heuristics, and offline or bundled spam data. Source: https://github.com/SysAdminDoc/CallShield

The strongest reusable product ideas for BlackList are not remote lookups; they are a stable precedence contract, isolated checkers, explainable decisions, bounded local datasets, and regression tests for every layer.

## Current BlackList baseline

BlackList v1.27.0 already includes offline CallScreeningService enforcement, emergency and whitelist precedence, exact/prefix/suffix/contains/range/country/international/hidden/unknown rules, per-rule schedules, block or silence enforcement, call-log and SMS pickers, local reputation and behavioral signals, first-time and repeated-caller policies, encrypted backups, Room version 15, capability-health concepts, and CI-driven signed releases.

## Candidate next-cycle priorities

1. Add contact-group rules using a local immutable contact/group snapshot, with fail-open behavior when permission or group data is unavailable.
2. Improve behavioral policy semantics by separating recent-dialed callback allowance, answered-caller trust, repeated-call escalation, and campaign evidence rather than conflating all signals.
3. Add a bounded, explainable caller-name rule only when Android exposes caller information locally; never depend on network enrichment in the screening hot path.
4. Build the optional Shizuku/Root capability adapter as a detection and audit layer first, with no destructive system mutation until a public, tested API path is demonstrated.
5. Add decision-trace, migration, backup, performance, and privacy regression tests before each release.
