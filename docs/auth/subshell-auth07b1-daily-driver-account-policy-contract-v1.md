# AUTH07B1 daily-driver account policy

AUTH07B1 changes normal Reddit account connection from a core-read grant
into one explicit daily-driver grant covering the Reddit capabilities
currently implemented by Subshell.

The exact account-connect scope set is:

- account
- edit
- flair
- history
- identity
- modposts
- mysubreddits
- privatemessages
- read
- report
- save
- submit
- subscribe
- vote

At the AUTH07B1 checkpoint, `save`, `vote`, and `report` were intentionally excluded
because the capability architecture did not require those grants. VOTE01 added
`vote`, HOMEACTION01 added `save`, SAFETY01A2 adds `report`, and USERACTION01A1
adds `account` for real Reddit user blocking. Existing authenticated accounts are
still upgraded only when a missing capability is actually requested.

Daily-driver authorization is account-connect policy, not another feature
capability. SAFETY01A2 separately adds a `CONTENT_SAFETY` feature capability so
older accounts can receive missing safety grants through the existing owner-bound
upgrade path. USERACTION01A1 extends that owner with `account` and the already-used
`privatemessages` scope for Block and enemy-relationship removal.

The normal runtime remains based on the core-read configuration. Existing
feed, profile, comment, subscription and capability consumers therefore
retain their established scope boundaries.

A separate connect session manager uses the daily-driver account
configuration only for normal new-account authorization.

The session manager that begins an account authorization also completes its
callback. Pending OAuth state is bound to the exact client, redirect URI and
scope fingerprint, so a daily-driver authorization must not be consumed
through the narrower core-read session manager.

Existing authenticated accounts are not silently reauthorized and their
stored grants are not automatically widened. Capability-specific
authorization remains available as a compatibility path for older accounts
whose stored tokens do not grant a required capability.

The account switcher exposes product state rather than OAuth transport
details. The active connected account is `Current account`; another
connected account is `Connected`. RedReader compatibility remains an explicit local setup concern and is presented
under Settings → Accounts → Connection.

AUTH07B1 does not change Home versus Popular landing behavior.
Subscription-aware landing is AUTH07B2.

AUTH07B1 does not implement first-run Client ID onboarding.
That remains AUTH07C.
