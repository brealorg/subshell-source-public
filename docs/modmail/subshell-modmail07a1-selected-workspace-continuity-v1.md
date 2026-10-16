# MODMAIL07A1 — selected workspace continuity

MODMAIL07A1 refines the already-landed adaptive Modmail workspace.

## Product contract

On expanded split layouts:

- the currently selected Modmail conversation is visually identifiable in
  the conversation pane;
- the detail thread does not render a redundant visual Back button;
- system Back still clears/closes the selected Modmail conversation.

On compact/full-screen layouts:

- the existing visual Back button remains;
- the existing full-screen thread presentation remains unchanged.

## Functional boundary

MODMAIL07A1 does not change:

- Reddit Modmail transport;
- authorization;
- reply or internal-note write semantics;
- archive, unread, triage, mute, or highlight semantics;
- notification routing;
- persistence;
- thread/message/composer data flow.

The existing INBOXM3A1 presentation and NEXT01A3D adaptive workspace are
preserved and refined rather than replaced.
