# Subshell BACKUP03 portability policy v1

`BACKUP03A1_PORTABILITY_POLICY_V1`

This policy defines which state belongs in Subshell's user-controlled Backup & Restore payload.
It is intentionally narrower than "everything persisted" or "everything account-scoped".

The governing rule is:

> A backup payload contains durable user intent that should follow the user between
> installations. It does not silently become a transport for device history, caches,
> authentication material, transient UI state, or mixed-source projections.

## Classification semantics

- `PORTABLE` — included by default in Subshell Backup & Restore.
- `PRIVACY_SENSITIVE_OPT_IN` — may become portable only through a separately designed,
  explicit opt-in boundary.
- `DEVICE_LOCAL` — remains on the current installation and is not part of Subshell
  Backup & Restore.
- `NEVER_BACKUP` — must not enter a Subshell backup payload.
- `OPEN` — policy gap requiring a separate slice before behavior changes.

Absence versus explicit empty is significant for portable account modules:
an absent module means legacy/not supplied/no-op, while a present empty value is authoritative.

## PORTABLE

`CLASSIFICATION=PORTABLE`

| State | Ownership key | Policy |
| --- | --- | --- |
| `SubshellSettings` | app | Portable by default through the existing curated settings storage map. |
| `joinedCommunityIdsByAccount` | normalized Reddit username | Portable by default; schema v3 semantics remain authoritative. |
| `favoriteCommunityNames` | account stable ID | Portable by default through schema v4. |
| `favoriteCustomFeedPaths` | account stable ID | Portable by default through schema v4. |
| `savedSearches` | account stable ID | Portable by default through schema v10 (issue #206): searches the reader chose to keep; only while the Saved searches setting is on is there anything to back up. |

Future account modules must preserve their native ownership key unless a deliberate,
tested migration changes that identity. Disconnected-account rows should remain
recoverable where their ownership can be represented safely.

## PRIVACY_SENSITIVE_OPT_IN

`CLASSIFICATION=PRIVACY_SENSITIVE_OPT_IN`

| State | Reason |
| --- | --- |
| `hiddenCommunityNames` | Reveals communities the user intentionally concealed; BACKUP04A1 permits portability only through an explicit device-local opt-in that defaults off. |
| `communityMutes` | Reveals filtering choices and contains time-sensitive expiry semantics; BACKUP04A2 permits portability only through its own explicit device-local opt-in that defaults off. |
| `postComposerDrafts` | Contains unpublished user content; BACKUP04A3 permits only text/link post drafts through a separate explicit device-local opt-in. Media drafts and local URI attachments remain device-local. |
| `userTags` | Private notes and coloured tags on Reddit usernames (issue #34); personal judgements about other people. BACKUP04A5 permits portability only through its own explicit device-local opt-in that defaults off. |

No item in this class is part of schema v4 merely because it is persisted locally.

## DEVICE_LOCAL

`CLASSIFICATION=DEVICE_LOCAL`

The following are installation history or transient continuity state and do not belong
in Subshell Backup & Restore:

- `recentCommunityVisits`
- `recentSearches` / search history
- `searchIndexEntries` / search interaction index
- active account selection and current account UI routing
- route stack, scroll anchors, current process snapshot and other UI continuity state
- comment collapse/read-navigation continuity
- the app lock and its relock delay (issue #38, `subshell_app_lock`): a security
  choice for this device, like the hidden-subscription unlock requirement
- other bounded recent-history state whose purpose is local convenience rather than
  durable user configuration

These values may still have their own local persistence; that does not make them portable.

## NEVER_BACKUP

`CLASSIFICATION=NEVER_BACKUP`

The following must never be serialized into a Subshell user backup:

- OAuth access tokens
- OAuth refresh tokens
- pending OAuth authorization/callback material
- encrypted OAuth sessions
- OAuth client selection/configuration, client IDs, secrets and authentication fingerprints
- Matrix/web-session tokens or token fingerprints
- Room/offline/reconstructable caches
- trending/discovery search caches
- notification posting/reconciliation bookkeeping
- inbox dismissed IDs and clear-boundary bookkeeping
- transport cursors, retry bookkeeping and other reconstructable runtime metadata
- `AccountPostActions` as one aggregate payload

`AccountPostActions` is explicitly excluded as an aggregate because it mixes different
sources of truth and semantics: saved IDs, hidden IDs, read IDs, votes, poll votes and
followed users. Any future portability proposal must first split such state by semantic
owner and prove why the individual field is durable user intent rather than a server
projection, cache, or device-local history.

Authentication material remains outside the backup model even if a future storage
implementation changes its on-disk location.

## Schema-v4 boundary

`BACKUP03C1_FAVORITES_SCHEMA_V4_CODEC_STORE_V1`

`BACKUP03C_FAVORITES_SCHEMA_V4_COMPLETE_V1`

`SCHEMA_V4_STATUS=RESOLVED_BACKUP03C`

Schema v4 introduces exactly two new optional portable modules:

1. `favoriteCommunityNamesByAccount`, account-scoped by stable account ID;
2. `favoriteCustomFeedPathsByAccount`, account-scoped by stable account ID.

The JSON field names are `favoriteCommunities` and `favoriteCustomFeeds`.

For both modules:

- null/absent means legacy/not supplied and restore is a no-op;
- present empty map is authoritative empty at module scope;
- an explicit account row with an empty set is authoritative empty for that account;
- account keys keep their native stable account ID ownership and are never remapped
  through usernames;
- malformed account rows and malformed array values are ignored without making the
  full backup unrestorable;
- account rows, value counts, account-key length and value length are bounded before
  persistence/restore;
- values are trimmed, case-insensitively deduplicated and deterministically ordered.

The BACKUP03A1 wording invariants remain binding for successor compatibility:

- favorite communities, account-scoped by stable account ID;
- favorite custom feeds, account-scoped by stable account ID;
- `null` / absent module = legacy backup, leave current state unchanged;
- present empty map or present account with empty set = authoritative empty state;
- do not remap one account's row to another account;
- do not add privacy-sensitive, device-local, auth, or cache state in the same change.

BACKUP03C1 established codec/store plumbing. BACKUP03C2 completes the portable
favorites feature end to end:

- all known account sessions are captured, with both favorite modules keyed by native
  stable account ID and joined communities still keyed by normalized username;
- manual saves, automatic history and pre-restore safety snapshots carry all four
  default-portable modules;
- settings, joined communities, favorite communities and favorite custom feeds are
  persisted through one `SharedPreferences.Editor.commit()` restore boundary;
- a failed durable commit prevents every live-state projection;
- `null` favorite modules remain legacy/no-op, explicit empty account rows clear that
  account, and an explicit empty favorite module clears its persisted module keys;
- restore preview reports favorite-community and favorite-custom-feed account deltas
  without remapping ownership;
- privacy-sensitive, device-local, authentication, cache and mixed action state remain
  outside this schema change.

## Schema-v5 hidden communities privacy-sensitive opt-in

`BACKUP04A1_HIDDEN_COMMUNITIES_SCHEMA_V5_OPT_IN_V1`

`SCHEMA_V5_STATUS=RESOLVED_BACKUP04A1`

Schema v5 adds exactly one privacy-sensitive optional module:

- `hiddenCommunityNamesByAccount`, serialized as `hiddenCommunities`.

The boundary is deliberately stricter than the default-portable schema-v4 modules:

- the device-local Backup & Restore preference defaults off;
- the preference itself is not serialized into any backup payload;
- repository save/history/safety paths omit the module while opt-in is off;
- enabling opt-in affects newly created snapshots only; existing backups are not rewritten;
- stable account IDs remain opaque native ownership keys and are not remapped through
  usernames or case-folded into favorite-community preference identity;
- null/absent means legacy or opted-out and restore is a no-op;
- present empty map is authoritative empty at module scope when privacy-sensitive
  restore is enabled;
- an explicit account row with an empty set is authoritative empty for that account;
- imported schema-v4 and older backups remain compatible and decode this module as absent;
- restore preview labels hidden-community state as privacy-sensitive;
- a present hidden-community module is applied only while the same device-local opt-in
  is enabled;
- hidden-community live state is projected only after the existing single durable
  `SharedPreferences.Editor.commit()` succeeds;
- the existing device-unlock requirement controls ordinary viewing of hidden names and
  never enables backup portability implicitly.

Timed community mutes, drafts, post hidden IDs, authentication material, caches,
device-local history and Android system backup remain outside BACKUP04A1.


## Schema-v6 community mutes privacy-sensitive opt-in

`BACKUP04A2_COMMUNITY_MUTES_SCHEMA_V6_OPT_IN_V1`

`SCHEMA_V6_STATUS=RESOLVED_BACKUP04A2`

Schema v6 adds exactly one additional privacy-sensitive optional module:

- `communityMutesByAccount`, serialized as structured `communityMutes` rows.

The boundary is independent from hidden-community portability:

- the device-local muted-community Backup & Restore preference defaults off;
- the preference itself is not serialized into a backup payload;
- enabling hidden-community backup does not enable muted-community backup, and vice versa;
- repository save/history/safety paths omit community mutes while the muted-community opt-in is off;
- enabling opt-in affects newly created snapshots only; existing backups are not rewritten;
- stable account IDs remain opaque native ownership keys and are not remapped through usernames or case-folded;
- each portable mute stores a normalized community name plus `expiresAtEpochMillis`;
- a null expiry is an indefinite mute;
- timed expiries are absolute wall-clock epoch milliseconds, not remaining-duration values;
- restore uses the receiving device's current wall clock, drops timed entries already expired there,
  preserves future absolute expiries unchanged, and performs no clock-skew compensation;
- null/absent means legacy or opted-out and restore is a no-op;
- present empty map is authoritative empty at module scope when muted-community restore is enabled;
- an explicit account row with an empty list is authoritative empty for that account;
- disconnected-account rows are preserved under their native stable IDs without remapping;
- restore preview labels muted-community state as privacy-sensitive and discloses absolute-expiry behavior;
- muted-community persisted state joins the existing single `SharedPreferences.Editor.commit()` restore boundary;
- live mute projection and the next-expiry scheduler update only after that durable commit succeeds.

The separate `muted_community_direct_view_mode` preference is not added by BACKUP04A2.
Drafts, post hidden IDs, authentication material, caches, device-local history and Android
system backup remain outside this schema change.


## Schema-v7 post composer text/link drafts privacy-sensitive opt-in

`BACKUP04A3_POST_COMPOSER_DRAFTS_SCHEMA_V7_OPT_IN_V1`

`SCHEMA_V7_STATUS=RESOLVED_BACKUP04A3`

Schema v7 adds exactly one additional privacy-sensitive optional module:

- `postComposerDraftsByAccount`, serialized as structured `postComposerDrafts` rows.

The portable boundary is deliberately a subset of the native composer draft store:

- only post composer drafts whose type is `TEXT` or `LINK`, whose `media` list is empty,
  and whose opaque stable account owner is nonblank are portable;
- media post drafts, galleries, images, GIFs, video drafts and every local attachment URI
  remain device-local and are never serialized into schema v7;
- historical native drafts with a null/blank account owner remain device-local because
  they cannot be safely remapped to another account;
- comment composer drafts, message/chat drafts, search drafts and transient field drafts
  are outside BACKUP04A3;
- the device-local post-draft Backup & Restore preference defaults off and is not serialized;
- hidden-community, muted-community and post-draft opt-ins remain independent;
- enabling the opt-in affects newly created snapshots only;
- stable account IDs are opaque case-preserving ownership keys and are never username-remapped;
- portable drafts preserve draft ID, community, type, title, body, link URL, input mode,
  flair metadata, NSFW/spoiler/reply flags and creation/update timestamps;
- portable draft input is bounded to the native 100-draft ceiling plus a bounded portable
  content-character budget; malformed or oversized imported draft rows are ignored;
- null/absent module means legacy or opted-out and restore is a no-op;
- present empty module removes only portable text/link drafts and preserves all native
  nonportable drafts;
- explicit empty account row removes only portable text/link drafts owned by that account;
- when a non-empty module omits an account, that account's existing drafts remain unchanged;
- device-local nonportable or unaffected drafts win any imported draft-ID collision;
- restore preserves nonportable drafts first and fills remaining native draft capacity from
  restored portable drafts in newest-first order;
- the merged native draft JSON is staged into the same existing
  `SharedPreferences.Editor.commit()` restore transaction as the other backup modules;
- live draft-list projection occurs only after that durable commit succeeds;
- restore preview labels post drafts as privacy-sensitive and states that media drafts remain local.

Android system backup remains deny-all. No media-copy/export format is introduced by BACKUP04A3.

## Schema-v8 moderator saved responses opt-in

`BACKUP04A4_MODERATOR_SAVED_RESPONSES_SCHEMA_V8_OPT_IN_V1`

`SCHEMA_V8_STATUS=RESOLVED_BACKUP04A4`

Schema v8 adds exactly one additional optional module:

- `moderatorSavedResponses`, serialized as structured rows `{id, title, body, updatedAtEpochMs}`.

Boundary and semantics:

- the module is device-wide, not per account: saved responses are moderator-authored
  text templates shared by every account on the device, so there is no account key and
  no username remapping;
- the device-local saved-responses Backup & Restore preference
  (`privacy_moderator_saved_responses_backup`) defaults off and is not serialized;
- hidden-community, muted-community, post-draft and saved-response opt-ins remain independent;
- enabling the opt-in affects newly created snapshots only;
- rows are bounded to the native ceiling (50 responses, title ≤ 60, body ≤ 10 000);
  malformed rows (blank id/title/body, negative timestamp) are ignored and duplicate ids
  keep the newest `updatedAtEpochMs`;
- null/absent module means legacy (schema ≤ 7) or opted-out and restore is a no-op;
- present empty module authoritatively clears the saved responses on this device;
- a present non-empty module replaces the device's saved responses as a whole;
- the encoded JSON is staged into the same existing `SharedPreferences.Editor.commit()`
  restore transaction as the other backup modules;
- live list projection occurs only after that durable commit succeeds;
- restore preview shows the module as one "This device" row.

Android system backup remains deny-all. No new export format is introduced by BACKUP04A4.

## Schema-v9 user notes and tags privacy-sensitive opt-in

`BACKUP04A5_USER_TAGS_SCHEMA_V9_OPT_IN_V1`

`SCHEMA_V9_STATUS=RESOLVED_BACKUP04A5`

Schema v9 adds exactly one additional optional module:

- `userTags`, serialized as `{stableAccountId: {username: {label, color, note}}}`.

Boundary and semantics:

- tags exist only for signed-in Reddit accounts; the local session has none and
  capture covers every signed-in account in the session catalog (the active one from
  memory, the others from storage);
- stable account IDs are opaque ownership keys, trimmed but not case-folded (as for
  hidden communities) and never remapped through usernames; usernames are lowercased;
- the device-local Backup & Restore preference (`privacy_user_tags_backup`) defaults
  off, is independent of the other opt-ins and is not serialized;
- enabling the opt-in affects newly created snapshots only;
- rows are normalized like the native store (label one line ≤ 24, note ≤ 500, a tag
  with neither is dropped, non-username keys are dropped, an unknown colour becomes
  grey) and bounded to 2 000 tags per account and the shared 64-account ceiling;
- null/absent module means legacy (schema ≤ 8) or opted-out and restore is a no-op;
- present empty module authoritatively clears the tags of every account on this device;
- a present non-empty module replaces the tags of each listed account; accounts not
  listed keep theirs;
- the encoded tags are staged into the same existing `SharedPreferences.Editor.commit()`
  restore transaction as the other backup modules;
- live projection (the active account only) occurs after that durable commit succeeds;
- restore preview counts one value per tagged username, so an edited tag shows as −1/+1.

Android system backup remains deny-all. No new export format is introduced by BACKUP04A5.

## Android system backup / device transfer

`BACKUP03B_ANDROID_SYSTEM_BACKUP_DENY_ALL_V1`

`SYSTEM_BACKUP_STATUS=RESOLVED_BACKUP03B`

Subshell's user-controlled Backup & Restore is the only supported portability mechanism.

The Android platform backup boundary is deny-all:

- the manifest sets `android:allowBackup="false"` to disable platform cloud backup;
- the Android 11-and-lower `fullBackupContent` policy excludes the root of every
  supported backup domain;
- the Android 12+ `cloud-backup` policy excludes the root of every supported domain;
- the Android 12+ `device-transfer` policy independently excludes the root of every
  supported domain;
- device-protected storage domains are denied as defense-in-depth even though Subshell
  does not currently rely on them for portable state;
- auth/session storage remains in `noBackupFilesDir`;
- caches and persistent downloads remain outside the portability model.

This deliberately prevents raw Android migration of `subshell_preferences.xml`,
`subshell_backup_preferences.xml`, `subshell_external_poll_handoff.xml`,
`filesDir/settings_snapshots`, inbox/notification bookkeeping, drafts, recents and any
future storage added to normal backup-eligible app domains.

The explicit historical exclusions for `subshell_device_preferences.xml` and the Room
cache remain in the XML as redundant predecessor-compatible fences. They document the
older narrower policy while the root-domain exclusions enforce the stronger BACKUP03B
policy.

A connected user-selected backup folder is outside this platform backup boundary and
continues to be managed by Subshell's Backup & Restore feature.

## Schema-v10 saved searches

`ISSUE206_SAVED_SEARCHES_SCHEMA_V10_V1`

`SCHEMA_V10_STATUS=RESOLVED_ISSUE206`

Schema v10 adds exactly one additional optional module:

- `savedSearches`, serialized as `{stableAccountId: [{query, scope, area, community?}]}`,
  newest first, at most 50 per account.

Boundary and semantics:

- saved searches exist only for signed-in Reddit accounts and only while the
  device's "Saved searches" setting is on; capture covers every signed-in account in
  the session catalog (the active one from memory, the others from storage);
- owner decision: the module is PORTABLE, not an opt-in — a reader saves a search to
  keep it; `recentSearches` and `searchIndexEntries` stay DEVICE_LOCAL;
- stable account IDs are opaque ownership keys, trimmed but not case-folded;
- an absent module (a backup from schema v9 or older) leaves saved searches unchanged;
  a present empty module clears them; a present non-empty module replaces only the
  listed accounts; a module that is not an object is corrupt and leaves them unchanged;
- rows with an unknown scope or area, a blank query, or a community search without a
  valid community are dropped;
- the encoded JSON is staged into the same `SharedPreferences.Editor.commit()` restore
  transaction as the other modules, and the live list changes only after that commit.

## Non-goals

BACKUP03A1 changes no payload schema, codec, restore transaction, SharedPreferences
persistence behavior, Android backup rules, auth storage, device data, or remote state.
