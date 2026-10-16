# subshell REDDIT01C1 owner-bound shared-scope migration contract V1

## Scope

REDDIT01C1 begins consumer migration onto the generic REDDIT01B capability authorizer.

Comment edit and comment delete intentionally share the same Reddit OAuth configuration and `edit` scope, but they now use distinct persisted logical authorization owners:

- `COMMENT_EDIT`
- `COMMENT_DELETE`

The existing shared comment-edit capability remains available to transport clients and to consumers that have not yet migrated.

No Reddit OAuth scope is added or removed by REDDIT01C1.

`AuthConnectViewModel` routes the two migrated owners from the persisted authorization owner before entering the legacy callback priority chain.

This proves that operations sharing an OAuth scope can remain distinct callback owners.

Migration of comment write, text post, post edit/delete/manage, composer capabilities, custom feeds and inbox is deferred to later REDDIT01C phases.

Removal of the legacy callback priority chain is deferred until all capability consumers have migrated.
