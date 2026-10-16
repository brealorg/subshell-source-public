# MODRESPONSE01A3 — Saved Responses

Issue #14, slice 3 of 3. Reusable moderator response templates, usable in
both remove dialogs and independent of removal reasons.

## Model and storage

`ModeratorSavedResponse(id, title ≤ 60, body ≤ 10 000, updatedAtEpochMs)`,
at most 50, ordered by title. Stored device-locally in the app preferences
under `moderator_saved_responses` (JSON, unknown keys ignored, invalid rows
dropped) by `ModeratorSavedResponseStore`; shared by every account on the
device because templates are text, not account state. Portable since
BACKUP04A4 (backup schema v8, opt-in, off by default; see
`docs/backup/subshell-backup03-portability-policy-v1.md`).

`ModeratorResponseState` (in `SubshellState`): `save(title, body)` adds or
replaces the response with the same title (case-insensitive, newest spelling
wins), `delete(id)`; every change is persisted.

## Dialog

In the removal response section: "Use saved response…" opens a picker
(explicit choice; the text lands in the editable message field, so it can be
changed before sending), each row has a delete action; "Save as response…"
saves the current message text under a title. Nothing here sends anything.

## Failure classification

- Primary: a template is sent without landing in the editable draft, the
  list is unbounded or not persisted, or a saved response is created
  implicitly.
- Predecessor: MODRESPONSE01A1/A2 or MOD01 contracts red while these pass.
- Release-blocking: only when a release being prepared cannot ship because of it.
