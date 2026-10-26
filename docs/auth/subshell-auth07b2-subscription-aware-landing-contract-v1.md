# AUTH07B2 subscription-aware landing contract v1

## Goal

An authenticated Reddit account should enter the most useful main listing without treating incomplete subscription data as authoritative.

## Authoritative subscription state

Automatic landing decisions use only a matching subscription state that has completed a Reddit refresh, except the cold start below.

A cached result has `fromCache = true` and is never sufficient for deciding that an account has zero joined communities. Loading, mismatched, unavailable, and failed subscription states do not guess between Home and Popular.

## Landing policy

After authenticated account activation:

- an authoritative empty subscription list selects `Popular` with `Hot`;
- an authoritative non-empty subscription list selects `Home` with `Best`;
- the automatic decision is one-shot for that account activation.

### Cold start of the account already active (#257)

When the app starts with an authenticated account already active (not a sign-in, not an account switch, also not a switch back to that account later in the same process), a matching cached list that is **not empty** (`loaded`, `fromCache`, no failure) selects `Home` with `Best` at once; Subshell does not wait for Reddit's refresh (it cost ~0.9 s on every start). A missing or empty cache still waits for the authoritative answer, so the cache still never decides that an account has zero communities. Accepted cost: an account that left every community on another device lands on Home once instead of Popular (`ISSUE257_COLD_START_LANDS_ON_CACHED_SUBSCRIPTIONS_V1`).

After the landing, Home and community feeds say `Loading from Reddit…` until their stored page has been read, so a feed still being read is never shown as `No cached Reddit posts` with `Refresh from Reddit` (`ISSUE257_HOME_SAYS_LOADING_UNTIL_ITS_STORE_IS_READ_V1`).

The deterministic landing operation owns both source and sort. It does not inherit a sort from the previously active account.

## User intent

Explicit feed-source, feed-sort, listing-time-period, or custom-feed selection cancels the pending automatic landing decision. A later subscription response must not override that explicit choice.

## Explicit Home with zero subscriptions

If the user explicitly chooses Home after an authoritative empty subscription result, Subshell stays on Home.

The screen presents `Your Home feed is empty` and offers `Browse Popular` and `Find communities`. It does not present the generic `Reddit returned no posts` state and does not expose `Refresh from Reddit` as the primary resolution.

## Boundaries

AUTH07B2 does not change OAuth scopes, account identity, Reddit network endpoints, Room schema, profile comments, or first-run Client ID onboarding.

All user-facing strings remain English.
