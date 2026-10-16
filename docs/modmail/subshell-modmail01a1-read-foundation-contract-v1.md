# MODMAIL01A1 — New Modmail read foundation

## Product boundary

MODMAIL01A1 adds a read-only New Modmail surface under `Inbox → Mod` without mixing moderator mail into personal Inbox Activity or the authoritative moderation queue.

The Mod surface has two secondary destinations:

- `Queue` — the existing authoritative `/r/mod/about/modqueue` workflow from MOD01.
- `Modmail` — New Modmail conversations owned by the active Reddit moderator account.

`Mod` itself remains conditional on the active account moderating at least one community. `Modmail` then reports whether that moderator account has any communities with Reddit mail permission.

## Authorization

Modmail is an incremental capability, not part of the daily-driver OAuth grant.

- authorization owner: `MODMAIL_READ`
- wire key: `modmail_read`
- OAuth scope: `modmail`
- account-pinned authorization
- RedReader-compatible authorization starts with a fresh web account session
- missing `modmail` is feature-owned authorization state and must not invalidate the base Reddit account session

## Read transport

A1 uses only New Modmail read endpoints:

- `GET /api/mod/conversations/subreddits`
- `GET /api/mod/conversations`
- `GET /api/mod/conversations/{conversation_id}`

Conversation detail is requested with `markRead=false`; A1 does not mutate Reddit read state.

The UI exposes subreddit, participant, subject, preview, unread metadata, relative time, and a read-only chronological conversation thread.

## Explicitly deferred

A1 does not implement any Modmail write operation. In particular it does not send replies, internal notes, archive/unarchive, mute/unmute, highlight/unhighlight, or other conversation mutations.

Those operations are successor slices after the read/thread model is runtime accepted.

## Account and state ownership

Modmail state is scoped to the active account stable ID. Switching accounts must not project the previous account's conversations or permissions into the new account. Authorization completion must resolve back to the account that initiated the capability grant.

MODMAIL01A1 does not alter the personal Activity read/dismiss lifecycle and does not convert Modmail conversations into `InboxItem` records.
