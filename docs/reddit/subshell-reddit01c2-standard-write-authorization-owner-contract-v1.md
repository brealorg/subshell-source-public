# REDDIT01C2 standard write authorization owner contract v1

REDDIT01C2 migrates the ordinary Reddit write authorization consumers onto persisted logical authorization ownership.

The migrated owners are `COMMENT_WRITE`, `TEXT_POST_WRITE`, `POST_EDIT`, and `POST_DELETE`.

`COMMENT_WRITE` uses the existing comment-write OAuth configuration. `TEXT_POST_WRITE` uses the existing text-post configuration. `POST_EDIT` and `POST_DELETE` intentionally share the existing comment-edit OAuth scope configuration while retaining distinct logical authorization owners.

The existing `ConfiguredAuthCommentWriteCapability` instances remain the transport capabilities for comment submission, text-post submission, post editing, and post deletion. REDDIT01C2 adds owner-bound authorization adapters only for authorization lifecycle ownership.

`AuthConnectViewModel` routes persisted callback and resume state by authorization owner before the predecessor priority chain. The predecessor priority chain remains temporarily as compatibility fallback for ownerless predecessor authorization records.

REDDIT01C2 does not broaden or otherwise change Reddit OAuth scopes.

REDDIT01C1 comment edit/delete ownership remains intact.

The REDDIT01C1D user-visible unconfirmed delete outcome remains intact.

Remaining special authorization consumers, including post management, composer authorization, custom-feed write, and inbox authorization, are deferred to later REDDIT01C work.
