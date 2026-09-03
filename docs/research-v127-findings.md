# v1.27.x Research Findings

## Android platform constraints

The Android `CallScreeningService` receives incoming calls before ringing and requires the screening app to respond within five seconds. Local, preloaded policy snapshots are therefore appropriate for the hot path; Room and ContactsProvider lookups should not occur during the response-critical operation. Source: https://developer.android.com/reference/android/telecom/CallScreeningService

`ContactsContract.Groups` exposes per-account contact groups and group metadata such as title and summary counts. A group-based rule should therefore use stable local group identifiers where possible, while treating account and synchronization changes as invalidation events. Source: https://developer.android.com/reference/android/provider/ContactsContract.Groups

## Open-source competitor signals

The open-source Calls Blocker project advertises rules for contact groups, callers never previously called by the user, repeated calls with a configurable time window, schedules, block suggestions, and test screening. Source: https://github.com/ryosoftware/calls-blocker

## Initial product gap

BlackList already has in-memory repeated-call and burst signals, optional contact enrichment, per-rule schedules, and a local deterministic engine. The v1.27.x gap is an explicit user-configurable policy layer for first-time callers and repeated-call escalation, plus persistent contact-group selection and explainable precedence. The implementation must fail open when contacts permission or group data is unavailable, preserve emergency/whitelist precedence, and keep screening decisions local and bounded.
