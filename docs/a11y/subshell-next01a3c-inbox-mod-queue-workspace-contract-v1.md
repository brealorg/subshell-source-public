# NEXT01A3C — Inbox Mod Queue canonical adaptive workspace

## Purpose

NEXT01A3C follows accepted Activity (A3A) and Messages (A3B).

The Mod primary workspace contains two secondary destinations:

- Queue
- Modmail

A3C covers Queue only. Modmail remains explicitly deferred.

## Secondary navigation ownership

Before A3C, Queue / Modmail selection was owned only by local
`InboxScreen` `rememberSaveable` state.

A3C introduces optional controlled Mod-section state, while retaining that
local state as the compatibility fallback.

`SubshellApp` owns the controlled section with `rememberSaveable` keyed by
active account, matching the primary Inbox tab ownership established by A3A.

This allows the parent host to distinguish:

- Mod + Queue -> adaptive list/detail
- Mod + Modmail -> existing Modmail presentation

Notification-driven Modmail navigation updates both the local compatibility
state and the controlled parent section.

## Queue workspace

On expanded width with Tablet mode `Split`, selecting Mod + Queue opens the
Queue list/detail workspace immediately.

The left pane is the existing production moderation Queue.

The right pane initially displays `Select a queue item`.

## Queue detail

The existing moderation target resolver and
`replaceCurrentModeratorQueueDetail()` remain the detail engine.

Queue entries may resolve to a Post or an exact comment target. The resolved
Post/comments surface opens on the right while Queue remains on the left.

Selecting another Queue item replaces the current Inbox-owned detail rather
than pushing another detail route.

## Modmail boundary

A3C does not make Modmail adaptive.

Selecting Modmail leaves the Queue workspace and uses the existing Modmail
surface.

The current `modmailThreadOpen` special case remains unchanged.

Adaptive Modmail list/detail is NEXT01A3D.

## Unchanged systems

A3C does not change:

- Activity behavior from A3A;
- Messages behavior from A3B;
- moderation transport;
- moderator action writes;
- removal reasons;
- Modmail transport or writes;
- Modmail notification routing;
- route models;
- Room or persistent data;
- OAuth;
- Compact layout;
- Tablet mode `Single`.

## Runtime acceptance

With Tablet mode `Split`:

1. Open Inbox -> Mod -> Queue.
2. Queue immediately uses two panes.
3. Right pane displays `Select a queue item`.
4. If Queue has an item, open it and verify Post/comment detail appears right.
5. If another Queue item exists, select it and verify only detail changes.
6. Back from detail returns to Queue plus placeholder.
7. Select Modmail: Queue split must disappear and existing Modmail remains.
8. Select Queue again: Queue split returns.
9. Switch Queue -> Activity -> Messages -> Mod and confirm all prior workspaces remain.
10. Tablet mode `Single` retains the existing full-surface Mod presentation.

If the live moderation queue is empty, Queue detail runtime acceptance is
recorded as deferred rather than falsely marked pass.

Final tablet visual QA remains deferred.
