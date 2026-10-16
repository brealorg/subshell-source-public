# CROSSPOST01A1D — process restore continuity contract v1

## Scope

A source-bound crosspost draft must survive Android process-state serialization without losing,
flattening, or replacing the selected Reddit source identity.

`ComposerCrosspostSource.fullname` remains the only source identity. Display metadata
(community, author, title, kind and sensitivity) is restored only as the pinned source capsule.

## Required behavior

- A valid `CROSSPOST` composer record serializes the complete source capsule.
- Restore recreates `ComposerCrosspostSource` and preserves draft ID, account owner,
  destination, edited wrapper title, selected destination flair, NSFW and spoiler state.
- A `CROSSPOST` record without a complete valid source capsule fails closed.
- Source capsule tags attached to a non-crosspost composer record fail closed rather than
  being downgraded to or smuggled through an ordinary text draft.
- Source NSFW/spoiler requirements remain monotonic through restore.
- Ordinary TEXT/LINK/MEDIA process snapshots remain backward-compatible and do not gain
  synthetic crosspost source fields.
- Existing persistent draft isolation from A1C is unchanged.
- The redundant per-test `Json` instance warning introduced by A1C is removed.

## Explicit boundary

This slice does not add a Crosspost menu action, source-card UI, review screen, or live write.
The generic composer submit fences from A1C remain closed.

`UI_ENTRY=NOT_ADDED`
`LIVE_CREATE_READY=NO`
`PROCESS_STATE_SOURCE_SERIALIZATION=FULFILLED_BY_A1D`
`FRESH_SOURCE_ACCESS_CHECK=PENDING_VISIBLE_FLOW`
`DEPLOY_REQUIRED=NO`
