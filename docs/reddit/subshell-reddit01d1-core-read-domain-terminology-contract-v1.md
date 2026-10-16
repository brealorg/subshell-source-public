# REDDIT01D1 core-read and Reddit domain terminology contract v1

REDDIT01D1 begins retirement of the predecessor `ReadOnly` architecture vocabulary.

The canonical base OAuth scope set is `coreReadScopes`. It remains exactly `identity`, `mysubreddits`, and `read`.

The active Reddit listing domain uses `RedditFeedSort`, `RedditMainFeed`, and `RedditCommentSort`.

Active feed mapping helpers use Reddit terminology rather than read-only terminology.

REDDIT01D1 is nomenclature-only. It does not alter OAuth grants, network endpoints, cache identities, paging behavior, comment sorting, account identity, callback routing, or mutation capabilities.

Historical AUTH06C milestone names, proof scripts, test class names, result tokens, contract labels, and historical documentation remain intact where they describe the predecessor milestone.

Later REDDIT01D phases retire `AuthReadOnly*`, `RedditReadOnly*` loader names, `LiveReadOnly*` UI state names, and active `readOnly*` runtime wiring.

Unrelated functional descriptions such as read-only diagnostics or a genuinely non-mutable poll are outside this rename.
