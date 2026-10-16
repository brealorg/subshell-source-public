# VOTE01 live Reddit voting

VOTE01 enables authenticated Reddit voting on shared post surfaces and comments inside the post thread.
The provider boundary is Reddit's absolute mutation: `POST /api/vote` with canonical `t3_...` post
or `t1_...` comment fullnames and `dir` of `1`, `-1`, or `0`.

## OAuth and mutation semantics

The explicit `POST_VOTE` capability requires core read scopes plus `vote`, and the normal daily-driver
grant includes `vote` from this successor forward. At the VOTE01 checkpoint, `save` remained excluded.
HOMEACTION01 is the explicit successor that later adds `save` without changing VOTE01 transport or vote
semantics. Existing accounts are not silently widened; an account whose stored grant lacks `vote` enters
the owner-bound authorization flow on the first vote attempt.

Voting is optimistic. The UI projects the requested absolute vote immediately and updates the displayed
score by the delta between the server-provided authoritative vote and the effective vote. It must not add
the effective vote directly to an already authoritative Reddit score. A failed vote restores the prior
local projection; success keeps the confirmed absolute state. Vote mutations use the non-retrying write
transport. A transport failure may offer a user-owned Retry because repeating the same absolute `dir`
converges on the same vote state rather than creating a duplicate object.

Reddit comment `likes` is projected into `Comment.vote`, and the comment cache appends that field while
retaining compatibility with prior 13/14/15/17/19-field payloads.

## V4 shared vote controls

The same shared vote-control component is used for post cards and comment rows. Presentation is selected
through a persisted appearance preference:

- `Comfortable` is the default. It uses thumbs with one net score inside the vote control. Comment headers therefore omit the score so Comfortable never renders the same score twice.
- `Compact` uses arrow controls without an inline score. Comment headers retain the score beside the time;
  post action rows retain a small score beside the compact control.

Vote colour is a separate persisted preference:

- `Theme accent` is the default and uses `SubshellOrangeSoft` (`#FFB68C`) for either selected direction,
- Since #218 (2026-10-09, owner decision) that is the dark-theme tone; light themes use `SubshellVoteNeutralLight` (`#914300`), and Red & blue use `#C23500` / `#3A5BD9` there, so a cast vote reaches 4.5:1 on the light surfaces. A cast vote also shows a filled icon (outlined when not voted).
  independent of Android dynamic colour.
- `Red & blue` uses `#FF4500` for selected upvotes and `#7193FF` for selected downvotes.

The layout and colour preferences are included in settings backup/restore. They do not alter Reddit
transport, optimistic score semantics, or readback.

Vote-control enabled/pressed styling is target-bound. An in-flight `t1_` or `t3_` target may affect only
that exact control; unrelated controls must not dim, activate, or inherit selection styling. Selection
colour remains visible for the selected direction while its own request is in flight.

At the VOTE01 checkpoint, Save remained separately disabled for authenticated accounts, so enabling vote
actions could not accidentally enable the legacy local save mutation path. HOMEACTION01 supersedes only
that Save boundary with its own owner-bound live Save/Unsave capability. Profile comment cards remain
navigation/history surfaces and do not gain vote controls in VOTE01.

VOTE01 preserves ERROR01A5's fail-closed source-contract aggregation and includes policy coverage for OAuth
scope, request shape, direction validation, optimistic score semantics, comment readback, layout persistence,
and exact-target visual isolation.
