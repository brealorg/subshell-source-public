# MOD01A2.2 — Conditional Inbox Mod tab and unified moderation queue

## Product boundary

MOD01A2.2 places actionable moderator work in a dedicated Inbox destination without
mixing reports into ordinary personal Activity.

For an authenticated account that moderates at least one community, Inbox exposes:

- **Activity** — personal Reddit replies, mentions, and system activity;
- **Messages** — private-message conversations;
- **Mod** — the account-scoped moderation queue.

The Mod tab is absent when the active account moderates no communities. Account
switching therefore changes the available Inbox destinations authoritatively.

## Authoritative queue

The Mod tab reads Reddit's aggregate moderator queue through
`GET /r/mod/about/modqueue`. This is the same listing family used by the existing
per-community Mod tools surface, but its projection accepts queue entries from all
communities moderated by the active account.

The aggregate queue:

- keeps Reddit `after` pagination;
- preserves each item's real subreddit identity;
- contains both posts and comments;
- remains separate from `InboxItem`, Inbox read receipts, and Activity lifecycle
  dismissal state;
- uses the existing MOD01A2 optimistic approve/remove policy and MOD01A2.1 removal
  reason flow.

## Navigation

Tapping a moderation item resolves the parent post through the existing authenticated
Reddit target resolver.

- A post opens that post.
- A comment parses the parent post ID from its Reddit permalink and opens the exact
  comment fullname.
- Expanded Inbox list/detail replaces the current Inbox-owned detail rather than
  pushing an unrelated route.

Moderator queue items do not create Inbox read receipts and are not projected as
personal Activity.

## Presentation

The Mod tab shows:

- a badge with the current number of queue entries;
- the number of moderated communities represented by the active account;
- subreddit, author, report count, content summary, and post/comment identity;
- direct **Approve** and **Remove** actions;
- the existing optional removal reason and mod-note dialog;
- pull-to-refresh, Reddit pagination, loading, failure, and empty states.

The Inbox Message composer FAB is hidden while Mod is selected because moderator work
is not a private-message composition context.

## Runtime hardening: MOD01A2.2.1

The runtime acceptance pass exposed two presentation/session-affinity edges which are
closed before MOD01 is checkpointed:

- moderator comment previews sanitize Reddit image-processing placeholders. A pure
  image placeholder is presented as **Image comment**; text plus an image placeholder
  keeps the text and adds **· Image**;
- missing Inbox `privatemessages` scope is a feature-owned authorization requirement,
  not a broken base Reddit session. The account remains connected;
- Inbox capability authorization is pinned to the selected account and requests a
  fresh RedReader compatibility web session. This prevents a stale Reddit WebView
  cookie from another configured account from silently selecting the wrong identity;
- callback/session replacement retains the existing account-identity checks. A token
  for another Reddit identity is not rebound to the selected account.

These changes do not alter Mod-tab visibility, moderation queue ownership, Activity
read lifecycle, or moderator action semantics.

## Deferred

MOD01A2.2 does not add:

- moderator notifications/background polling;
- Modmail;
- unmoderated/spam/reports sub-filters;
- moderation log;
- removal messages sent to authors.
