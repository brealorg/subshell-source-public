# MODRESPONSE01A2 — removal response in the remove dialog

Issue #14, slice 2 of 3. Both remove dialogs (Moderator tools sheet and the
Inbox mod queue) share `RemovalResponseSection` and `RemovalResponseDraft`
(`ui/components/RemovalResponseSection.kt`).

## One transaction

1. Remove content (existing).
2. Select a removal reason or none (existing).
3. "Send a removal message" — off by default; selecting a reason never sends.
   When on, the body is pre-filled from the selected reason's message until
   the moderator edits it; an edited body survives reason changes.
4. Presentation: public comment as you / as the subreddit (Reddit posts a
   distinguished, stickied comment on a post, a distinguished reply on a
   comment), private modmail with the moderator hidden / shown. Default
   `private`; the last type chosen is remembered per account on this device
   (`mod_removal_message_type:<account>`, never portable). Reddit decides
   distinguish/sticky; no separate toggles.
5. Confirm ("Remove and send") builds the `RedditRemovalMessage` (title =
   reason title, or "Removed"); an empty body blocks confirm.

The dialog body scrolls; the reasons list keeps its bounded height.

## Editing lives outside the AlertDialog (r2)

Measured on device: a text field inside the wrap-content AlertDialog with a
scrolling body jumped 151 px on the first keystroke — Gboard's suggestion
strip changed the IME height, the dialog re-measured (1743 → 1894 px), the
scroll viewport grew and `ScrollState` clamped. The remove dialog now only
summarizes the response (type, first lines, "Edit"); the text is edited in
`RemovalResponseEditor`, a bounded card dialog (82 % height, margins,
`imePadding`) whose field takes the remaining height, so a keyboard change
only shortens the field from the bottom. Turning the switch on opens the
editor directly.

## Partial success

`removalPartialSuccessMessage(reasonWarning, messageOutcome)` names every
failed optional step on a successful removal ("…but the removal message was
not sent."); it is shown in both queues.

## Failure classification

- Primary: a reason sends a message by itself, the draft is not reviewable
  before the write, the confirm ignores an empty body, or a failed message
  reports as full success.
- Predecessor: MOD01A2/MOD01A21/INBOX contracts red while these pass.
- Release-blocking: only when a release being prepared cannot ship because of it.
