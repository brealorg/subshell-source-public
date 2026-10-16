# WRITE01-A3B — comment authorization, submit, and thread update

WRITE01-A3B connects the English comment composer to the authenticated comment-write
capability and the typed `/api/comment` transport established by WRITE01-A1 and
WRITE01-A2.

## Contract

- The active authenticated account is pinned before authorization or submission.
- A missing `submit` grant produces an explicit **Grant comment access** action.
- OAuth callback completion is routed to the comment-write flow only when the stored
  callback or pending authorization matches the comment-write scope fingerprint.
- A successful grant resumes only the exact in-memory pinned request. After process
  recreation, the draft remains available but no comment is submitted automatically.
- The submission state is typed as idle, authorization required, authorizing, awaiting
  callback, sending, failed, outcome unknown, or succeeded.
- The draft is preserved during authorization, retryable failure, and outcome unknown.
- The draft is cleared only after a confirmed successful response.
- `t1_` comment replies and `t3_` post replies remain typed and account scoped.
- One write is in flight at a time. Ambiguous outcomes are never retried automatically.
- Confirmed comments are merged into the active account/post/sort Room cache before the
  visible thread revision is advanced.
- An account switch before authorization or submission fails closed.
- Remote comments are text-only in this phase. Existing image/GIF metadata is preserved,
  but the user must remove the attachment before posting.
- Authenticated accounts keep **Add comment** and **Reply** available while unrelated
  vote/save mutations remain outside this phase.
- All user-facing app strings remain English.

## Runtime boundary

The deterministic Android proof uses localhost HTTPS only. It proves explicit
authorization, callback resumption, exact form submission, Room-thread writeback,
rate-limit metadata, outcome-unknown handling, and account-switch fail-closed behavior.

WRITE01-A3B does not perform a live Reddit comment. The controlled production proof is
deferred to WRITE01-A3C.
