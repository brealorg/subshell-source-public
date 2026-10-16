# subshell REDDIT01B persisted authorization owner contract V1

## Scope

REDDIT01B introduces a generic, account-pinned capability authorizer and a process-safe logical authorization owner.

OAuth pending authorization and stored callback records carry an optional `authorizationOwnerKey`.

The owner key is propagated from authorization start through callback storage and is validated during callback completion.

Legacy pending authorizations and callbacks remain compatible because the owner field defaults to null.

The owner is separate from OAuth scope identity. Multiple user operations may share the same OAuth scope fingerprint while remaining distinct logical callback owners.

REDDIT01B does not migrate existing write consumers to the new authorizer. Existing behavior remains unchanged.

Consumer migration and replacement of the callback priority chain are deferred to REDDIT01C.
