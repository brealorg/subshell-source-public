# subshell REDDIT01A core-read scope foundation contract V1

## Scope

REDDIT01A establishes the provider-neutral foundation for the unified Reddit capability architecture.

The canonical baseline capability is `CORE_READ`.

Its exact OAuth scopes are:

- `identity`
- `mysubreddits`
- `read`

The scope set is identical for native-production and RedReader-compatibility OAuth client modes.

`CORE_READ` does not include history, save, subscribe, vote, submit, edit, flair, moderation, private-message, wiki, account-management, or other mutation scopes.

Existing authenticated sessions may contain a superset of scopes. Capability checks continue to use subset grant validation so previously granted capabilities are not discarded.

AUTH06C remains the read-only predecessor contract.

Normal authenticated Reddit surfaces do not present the account or feed as read-only. Internal predecessor naming remains unchanged until the later REDDIT01 cleanup phase. REDDIT01A does not rename the existing `ReadOnly` loaders, feed models, phases, or cache APIs.

Further capability authorization consolidation is deferred to REDDIT01B.
