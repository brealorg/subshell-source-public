# NOTIFY01A2 — read-state to system-notification reconciliation

## Problem

Before A2, the Android notification center persisted only a global set of
integer notification IDs.

Those IDs were derived from `eventKey` alone. The persisted state therefore
could not answer:

- which Reddit account owns the notification;
- which Inbox item owns the notification;
- which Modmail conversation owns the notification;
- whether several events belong to the same conversation.

Consequently, the only safe cancellation operation was global cancellation
when the notification master setting was turned off.

## Posted notification identity

A2 persists one typed record for every successfully posted notification:

- notification kind: Inbox or Modmail;
- account stable ID;
- Inbox item ID or Modmail conversation ID;
- event key;
- Android notification ID.

Android notification tags are account-scoped. PendingIntent request codes are
also derived from account + event identity.

Two accounts receiving the same Reddit event key therefore do not overwrite
each other's notification or PendingIntent.

## Inbox reconciliation

An Inbox target is considered settled read when either:

1. Reddit already projects `unread = false`; or
2. the item is locally seen and no individual or mark-all read mutation is
   still pending.

Optimistic read projection does not cancel the Android notification. A failed
Reddit read mutation can therefore roll back without having prematurely
destroyed the notification.

All posted events sharing the same Inbox item ID are cancelled together. This
matters for private-message/direct-chat threads where successive incoming
messages have different event keys but the same Inbox conversation identity.

## Modmail reconciliation

A Modmail notification is cancelled after its exact conversation is projected
as read.

All posted events for that account + conversation are cancelled together.

## Wiring

A2 reads the existing `StateFlow` values exposed by `AuthConnectViewModel`
through a dedicated Activity-owned reconciliation runtime.

The two collectors are started in `MainActivity.onCreate` before Compose
`setContent` and are cancelled from `onDestroy`.

The runtime preserves the original account + `contentRevision` trigger
semantics while executing notification reconciliation on `Dispatchers.IO`.

This ownership is deliberately outside the Compose tree. `MainActivity` already
has a very large `setContent` lambda, so notification side effects must not add
generated Compose bytecode to that method.

A2 does not add notification callbacks through Inbox UI, `SubshellApp`,
conversation presentation, or Modmail presentation.

## Upgrade behavior

Pre-A2 persisted notification IDs contain no account or target ownership.
They cannot be safely mapped after upgrade.

The first A2 reconciliation therefore cancels those legacy unscoped
notifications and removes the legacy ID set. New A2 notifications use the
typed account-target registry.

## Runtime boundary

A2 does not change:

- Reddit read/write endpoints;
- Inbox optimistic mutation behavior;
- Modmail read mutation behavior;
- notification category preferences;
- notification channel;
- WorkManager worker;
- 15-minute interval;
- notification content or deep-link target data;
- Inbox or Modmail presentation.

The existing master-off action remains the only global notification
cancellation operation.
