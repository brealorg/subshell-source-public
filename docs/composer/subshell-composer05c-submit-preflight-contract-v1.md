# COMPOSER05C submit preflight contract v1

## Goal

Connected Reddit submissions must verify the selected community's current post requirements before entering the sending state whenever Reddit makes those requirements available.

## Policy acquisition

`GET /api/v1/{subreddit}/post_requirements` is a submit-scoped Reddit endpoint. The composer policy loader records whether authoritative post requirements are loaded, require submit authorization, or are temporarily unavailable.

The regular subreddit `/about` response remains useful for general post capabilities and as a conservative fallback, but it is not treated as the authoritative source for moderator-defined post validation rules when `post_requirements` is available.

## Submit state machine

A connected-account Post action performs local draft validation first. If that passes, the composer starts a fresh community-policy request and enters a `Checking…` preflight state. It does not call the Reddit submit transport while the fresh policy request is pending.

When the fresh policy arrives:

- authoritative requirements are validated locally before submit;
- missing submit scope starts capability authorization without binding or sending the draft;
- unavailable requirements fall back to Reddit submit so structured server validation remains the safety net.

Only after preflight resolves successfully may the existing write coordinator enter `Posting…` and call Reddit.

## Field routing

If preflight finds an invalid field, the existing field-level routing is used. Required flair therefore scrolls into view, enters error state, shows `Post flair is required`, and opens the flair picker without entering the sending state.

If Reddit still returns a structured validation failure, the composer refreshes community policy. A server-reported flair requirement is retained as a local hint for the active draft/community so a retry cannot repeat the same unnecessary submit.

## Stale work prevention

Each composer policy request has a monotonically increasing revision. A pending submit preflight accepts only the policy revision requested by that submit or a newer one. Editing the draft while preflight is pending invalidates the pending request by request identity.
