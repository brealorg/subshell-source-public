# NOTIFY01A1R1 — compact notification settings

## Purpose

NOTIFY01A1R1 is a presentation-only successor to NOTIFY01A1.

The first A1 preview rendered the master notification control and every granular
category as separate full-size settings cards. That consumed excessive vertical
space and placed the category switches below their labels.

R1 keeps the notification policy unchanged and replaces only the settings
presentation.

## Hierarchy

The Notifications section now contains:

1. one master `Inbox notifications` surface with its switch trailing right;
2. a compact `Notify me about` label;
3. one grouped surface containing five compact rows:
   - Replies;
   - Mentions;
   - Private messages;
   - Reddit notices;
   - Modmail.

Each category switch is trailing right. Category rows do not repeat decorative
icons.

## Master semantics

The master description is:

`Check Reddit every 15 minutes for new notifications`

The master remains the existing opt-in setting and Android permission owner.

When the master is off, category values remain persisted but their controls are
disabled and visually de-emphasized. Re-enabling the master therefore restores
the user's previous category selection.

## Runtime boundary

R1 does not modify:

- activity-kind mapping;
- granular preference defaults;
- event-history reconciliation;
- notification posting policy;
- WorkManager scheduling;
- the 15-minute interval;
- the existing Inbox Android notification channel;
- tap routing;
- read-state behavior.

The existing NOTIFY01A1 runtime remains authoritative.
