# AUTH07B2 subscription-aware landing contract v1

## Goal

An authenticated Reddit account should enter the most useful main listing without treating incomplete subscription data as authoritative.

## Authoritative subscription state

Automatic landing decisions use only a matching subscription state that has completed a Reddit refresh.

A cached result has `fromCache = true` and is never sufficient for deciding that an account has zero joined communities. Loading, mismatched, unavailable, and failed subscription states do not guess between Home and Popular.

## Landing policy

After authenticated account activation:

- an authoritative empty subscription list selects `Popular` with `Hot`;
- an authoritative non-empty subscription list selects `Home` with `Best`;
- the automatic decision is one-shot for that account activation.

The deterministic landing operation owns both source and sort. It does not inherit a sort from the previously active account.

## User intent

Explicit feed-source, feed-sort, listing-time-period, or custom-feed selection cancels the pending automatic landing decision. A later subscription response must not override that explicit choice.

## Explicit Home with zero subscriptions

If the user explicitly chooses Home after an authoritative empty subscription result, Subshell stays on Home.

The screen presents `Your Home feed is empty` and offers `Browse Popular` and `Find communities`. It does not present the generic `Reddit returned no posts` state and does not expose `Refresh from Reddit` as the primary resolution.

## Boundaries

AUTH07B2 does not change OAuth scopes, account identity, Reddit network endpoints, Room schema, profile comments, or first-run Client ID onboarding.

All user-facing strings remain English.
