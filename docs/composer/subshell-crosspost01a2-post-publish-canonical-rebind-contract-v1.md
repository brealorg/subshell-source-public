# CROSSPOST01A2 — post-publish canonical rebind

## Problem

Reddit's successful `/api/submit` acknowledgement proves the newly-created wrapper
identity but does not contain the rich `crosspost_parent_list` media projection.
A1E therefore opens a safe synthetic wrapper immediately after Publish. For a video
source this can temporarily render `Video unavailable`, while reopening the same
wrapper later from the destination community renders correctly from Reddit's canonical
listing projection.

## Contract

A successful crosspost continues to use A1E's exact wrapper/source/request fences and
immediately opens the newly-created wrapper. A2 then performs a read-only canonical
post resolution through the existing authenticated Reddit navigation resolver.

Readback may replace the synthetic wrapper only when all of these remain true:

- the current route still shows the wrapper created by this submit;
- canonical wrapper identity matches the submitted wrapper ID;
- destination community and title match;
- NSFW/spoiler flags match;
- the canonical post is a crosspost;
- canonical parent identity matches the pinned source fullname.

If resolution times out, fails, returns malformed/mismatched data, or the user has
already navigated away, the synthetic A1E wrapper remains unchanged. Readback never
reopens a closed post and never steals navigation.

The writer is reset only after the best-effort canonical readback attempt. Resetting the write state earlier can change the LaunchedEffect key and cancel the suspending readback itself. A2 performs no Reddit write, no retry of
the submit, no new OAuth flow, no new network transport and no media-specific
hydration. Rich video/image/gallery data comes only from the existing canonical Reddit
post projection.

## Runtime obligation

Publish a native crosspost whose source contains playable media. The newly-created
wrapper should still open immediately. Its embedded source should become equivalent to
opening that same wrapper later from the destination community, without requiring the
user to leave and reopen it. Back during readback must remain authoritative.
