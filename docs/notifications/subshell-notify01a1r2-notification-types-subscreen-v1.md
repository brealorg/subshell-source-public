# NOTIFY01A1R2 — notification types subscreen

## Purpose

NOTIFY01A1R2 replaces the five-category notification settings wall on
`Comments & notifications` with progressive disclosure.

The parent screen now owns only:

- the existing `Inbox notifications` master switch;
- one `Notification types` navigation row with a dynamic summary.

The five granular controls move to a dedicated `Notification types` child
screen.

## Navigation

`NOTIFICATION_TYPES` is a Settings child destination. It is intentionally not
part of `settingsRootDestinations`.

Opening it from `Comments & notifications` returns to that screen through the
existing Settings return-destination contract.

## Child screen

The child screen contains the existing five categories:

- Replies;
- Mentions;
- Private messages;
- Reddit notices;
- Modmail.

The categories remain in one compact surface with dividers. Each entire row is
a switch touch target, while the trailing Material switch is presentation-only
inside that row.

When the master notification setting is off, the child controls remain visible
but disabled and visually de-emphasized. Their persisted values are unchanged.

## Search

Granular search entries remain indexed. A match for an individual category
surfaces the `Notification types` gateway on the parent settings screen.

## Runtime boundary

R2 changes Settings navigation and presentation only.

It does not modify:

- notification category mapping;
- notification preference storage;
- default preference values;
- background event history;
- notification posting;
- notification channels;
- WorkManager scheduling;
- the 15-minute background interval;
- tap routing;
- Reddit read state.

NOTIFY01A1 remains the authoritative notification runtime and policy.
