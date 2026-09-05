# BlackList Continuous Cycle — v1.28 Priority Decision

## Baseline

The current released baseline is v1.27.0. It includes local CallScreeningService enforcement, emergency and whitelist precedence, explicit rule types, per-rule schedules, block/silence actions, local behavior policies, encrypted backups, Room schema version 15, and CI-backed signed releases.

## Prioritization matrix

| Candidate | User value | Privacy/compatibility risk | Implementation scope | Decision |
|---|---:|---:|---:|---|
| Contact-group blocking rules | High | Medium | Medium | **Selected for v1.28.0** |
| Better answered/recently-dialed trust | High | Medium | Medium | Next cycle |
| Caller-name rules | Medium | Medium/High across OEMs | Medium | After stable local source contract |
| Shizuku/Root adapter | High for advanced users | High | High | Capability detection and audit only; no destructive operation |
| Cloud reputation lookup | Medium | High and conflicts with offline-first | Medium | Rejected for core product |
| Hidden API/Accessibility bypass | High apparent power | Very high policy and reliability risk | High | Rejected |

## Selected scope

v1.28.0 will add a first-class `CONTACT_GROUP` rule. A rule stores a stable Android group identifier and a user-visible title. Group membership is loaded outside the screening hot path into an immutable normalized-number snapshot. The call engine checks the snapshot locally and applies the rule's existing block or silence enforcement and schedule.

The feature fails open when READ_CONTACTS is missing, the group cannot be read, or a provider query fails. Emergency numbers, temporary allows, explicit whitelist entries, and explicit number rules remain higher priority. Encrypted backups validate and preserve the group identifier and title, while older backups restore without group rules.

The same release will add an optional capability descriptor for Shizuku/Root availability and permission state only. It will not execute shell commands, mutate Telecom databases, use hidden APIs, or claim that root can bypass Android call-screening guarantees.

## Acceptance criteria

1. Group rules can be created, displayed, scheduled, silenced, blocked, backed up, restored, and deleted.
2. Group membership is refreshed away from CallScreeningService and normalized once per snapshot.
3. A missing permission or failed provider query never turns a group rule into a global block.
4. Tests cover matching, multiple groups, non-matches, permission failure, whitelist/emergency precedence, migration, and backup compatibility.
5. CI passes unit tests, lint, debug assembly, and release validation before v1.28.0 is tagged.
