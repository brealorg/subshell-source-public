# REDDIT01C3C2 post-manage authorization owner contract v1

REDDIT01C3C2 migrates post-management authorization onto persisted logical ownership.

All post-management authorization uses the logical owner `POST_MANAGE`.

The persisted authorization sub-context selects the required OAuth capability:

- `flair` for flair access and flair changes;
- `moderation` for NSFW and spoiler changes;
- `edit` for reply-notification changes.

The context selects an OAuth configuration; it does not encode or persist the mutation payload.

The existing edit, composer-flair, and post-moderation capabilities remain available as ownerless predecessor callback fallbacks.

New authorization starts through owner-and-context-bound capability adapters and therefore persists `POST_MANAGE` plus the appropriate context before the browser opens.

Callback and process-resume routing dispatch `POST_MANAGE` through the common persisted-owner router.

When the original in-memory `PostManageMutationRequest` is still present and matches the persisted context and account, successful authorization may continue the already-requested operation.

When process death has removed the in-memory mutation request, Subshell completes the OAuth grant but does not reconstruct, guess, persist, or automatically replay the post mutation. The user may invoke the operation again and the newly granted scope will then be used.

A context mismatch never causes a mutation replay.

Ownerless predecessor post-management callbacks retain the previous in-memory compatibility path.

REDDIT01C3C2 does not change the OAuth scope matrix or Reddit mutation endpoints.
