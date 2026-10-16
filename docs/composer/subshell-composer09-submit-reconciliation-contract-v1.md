# COMPOSER09 submit resilience and outcome reconciliation

COMPOSER09 treats a missing or unreadable response after the final Reddit submit dispatch as an unknown write outcome, not as proof of failure.

A pending attempt is synchronously persisted before the authenticated submit pipeline runs. The attempt is synchronously marked with a dispatch timestamp immediately before the final `/api/submit` or gallery submit request. Media staging, lease acquisition, binary upload, and poster upload happen before this final-dispatch boundary.

Confirmed success and confirmed failures clear the matching attempt. Unknown outcomes retain the attempt. Process recreation restores the unresolved state without issuing another write.

Reconciliation is read-only. `Check again` reads `/user/{username}/submitted?sort=new`, limits the candidate window, and accepts only one strong match for the pinned account, username, community, normalized title, normalized body, post kind, flags, dispatch-time window, and link destination when applicable. Zero matches and multiple plausible matches remain unknown.

COMPOSER09 never automatically replays an uncertain final submit. A new copy is not submitted as part of reconciliation.

## OAuth history scope

The direct `/user/{username}/submitted` reconciliation read requires Reddit history access. Text-post authorization therefore uses `liveTextPostWriteScopes`, which adds `history` to the existing comment-write scope set. Comment writing retains its narrower `liveCommentWriteScopes` set and does not gain history access.

An account whose existing text-post grant predates this requirement must explicitly grant post access again before a new final submit can be dispatched. COMPOSER09 does not silently broaden an existing OAuth grant.
