# NOTIFY01A1 — granular notification policy

## Purpose

NOTIFY01A1 adds user-selectable notification categories over Subshell's existing
Inbox/Modmail background notification runtime.

It does not create a new notification transport.

## Existing runtime retained

- one 15-minute connected WorkManager job;
- one Inbox notification channel;
- one shared Inbox/Modmail notification center;
- first-run unread baselining;
- bounded event-history deduplication;
- account-bound exact notification targets;
- Android notification permission handling;
- lock-screen public redaction.

## Granular policy

The master `Inbox notifications` setting remains opt-in and remains the outer
gate.

When enabled:

- Replies — `ActivityKind.REPLY` and `ActivityKind.POST_REPLY`;
- Mentions — `ActivityKind.MENTION`;
- Private messages — `ActivityKind.MESSAGE`;
- Reddit notices — `ActivityKind.MOD`;
- Modmail — existing Modmail unread-event path.

All granular categories default to enabled, preserving existing behavior when
the master switch is enabled.

## Baseline semantics

Granular filtering happens after background event-history reconciliation and
before notification posting.

Events discovered while a category is disabled therefore still enter event
history. Re-enabling that category cannot emit an old backlog.

## Boundaries

NOTIFY01A1 does not:

- create another background worker;
- create another Android notification channel;
- change the 15-minute polling interval;
- mutate Reddit read state in the background;
- alter Inbox or Conversation presentation;
- alter exact-target tap routing;
- alter Modmail read/write semantics.

Read-state/system-notification cancellation reconciliation is deferred to
NOTIFY01A2.
