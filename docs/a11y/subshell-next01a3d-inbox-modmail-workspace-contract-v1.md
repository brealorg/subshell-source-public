# NEXT01A3D — Inbox Modmail canonical adaptive workspace

## Purpose

NEXT01A3D completes the Inbox adaptive-workspace sequence after:

- A3A Activity
- A3B Messages
- A3C Mod Queue

A3D covers `Mod -> Modmail`.

## Split workspace

On an expanded window with Tablet mode `Split`, Modmail is a list/detail
workspace from entry.

The left pane remains the production Modmail list and queue-filter surface.

The right pane initially displays `Select a Modmail conversation`.

Selecting a conversation opens the existing Modmail thread presentation in the
right pane.

Selecting another conversation replaces the selected thread through the
existing Modmail coordinator state; no route type is added.

Back closes `selectedConversationId` through the existing Modmail close action
and returns the right pane to the placeholder.

## Shared thread presentation

The former fullscreen-only thread block in `InboxScreen` is extracted into one
shared `InboxModmailThreadSurface`.

The surface retains the existing:

- thread header;
- Archive / unread / triage actions;
- message rows;
- Reply / Internal note composer;
- refresh behavior;
- account-pinned callbacks.

Compact and Tablet mode `Single` continue to use this same renderer as a
fullscreen Modmail thread with status/navigation-bar padding.

Split uses the same renderer in the detail pane without duplicate system-bar
padding.

## Split list ownership

Before A3D, selecting a Modmail conversation changed `InboxScreen` refresh and
paging ownership from the list to the selected thread because the thread was a
fullscreen replacement.

In A3D Split, the left pane remains the list even while a thread is selected.

Therefore:

- pull-to-refresh on the left refreshes the Modmail list;
- list pagination remains available;
- thread-loading presentation belongs to the right pane;
- thread refresh remains inside the right-hand thread surface.

Compact and Tablet Single preserve the predecessor fullscreen behavior.

## Shell behavior

The existing `modmailThreadOpen` shell exception is retained.

It suppresses normal main chrome only when the selected thread is using the
legacy fullscreen presentation.

In expanded Split Modmail, `modmailThreadOpen` no longer vetoes the expanded
main shell, allowing the Inbox master pane and Modmail detail pane to coexist.

## Notification continuity

MODMAIL06 exact notification routing remains unchanged.

A notification may select Mod -> Modmail and open the exact conversation.

In Split the exact thread appears in the right detail pane.

In Compact or Tablet Single the same target opens through the existing
fullscreen thread presentation.

## Unchanged systems

NEXT01A3D does not change:

- Modmail transport;
- Modmail OAuth scopes;
- read/unread writes;
- archive writes;
- triage writes;
- Reply/Internal note writes;
- notification identity;
- notification reconciliation;
- routing models;
- Room or other persistent data;
- Activity A3A;
- Messages A3B;
- Queue A3C.

## Runtime acceptance

With Tablet mode `Split`:

1. Open Inbox -> Mod -> Modmail.
2. Modmail list is left and `Select a Modmail conversation` is right.
3. Open a conversation.
4. Existing thread UI appears right while the Modmail list stays left.
5. Select a second conversation directly from the left.
6. Only the right thread changes.
7. Back closes the selected thread and returns the placeholder.
8. Pull/refresh on the left continues to refresh the list.
9. Switch Modmail -> Queue -> Modmail; both A3C and A3D workspaces return.
10. Activity and Messages remain unchanged.
11. Tablet mode `Single` opens Modmail conversations fullscreen as before.
12. Compact opens Modmail conversations fullscreen as before.
13. If a Modmail notification is available, tapping it focuses the exact
    conversation in the right pane while Split is active.

Final tablet visual QA remains deferred.
