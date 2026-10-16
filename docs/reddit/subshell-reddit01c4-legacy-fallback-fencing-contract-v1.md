# REDDIT01C4 legacy fallback fencing contract v1

REDDIT01C4 closes the final predecessor-routing ambiguity in REDDIT01C.

Persisted authorization routing is classified as `NONE`, `LEGACY_OWNERLESS`, `OWNED`, or `INVALID`.

`LEGACY_OWNERLESS` requires at least one stored pending authorization or callback and requires every present record to have both `authorizationOwnerKey` and `authorizationContextKey` unset.

A missing authorization record is `NONE`, not legacy authorization.

Valid matching owner and context metadata resolves to `OWNED`.

Unknown owners, malformed contexts, context without an owner, mixed ownerless and owned records, conflicting pending/callback routes, and auth-storage read failures resolve to `INVALID`.

Specialized inbox/custom-feed predecessor handling and the generic historical capability priority chain run only for `LEGACY_OWNERLESS`.

An `INVALID` authorization route is never passed to the predecessor priority chain and is never handed to the ownerless account-connect callback flow.

The account-connect flow remains ownerless by design. A valid ownerless account-connect record may pass through the legacy fence, fail to match any predecessor capability, and then continue through the existing account-connect flow.

No OAuth capability, scope set, Reddit endpoint, mutation payload, or successful owned callback route changes in REDDIT01C4.

REDDIT01C4 completes the authorization-consumer migration phase before REDDIT01D naming and architecture cleanup.
