# CROSSPOST01A1A — Native crosspost creation foundation

## Baseline and scope

Base: `370ca263a4a43d7fcaf7fded5cfcdaed1fcb9cc3`.
Lane: `dev2/crosspost01a1-create-flow`.

This is the first creation slice of the CROSSPOST01 parity goal. Existing CROSSPOST01
read/projection, cache, embedded source cards and canonical routing remain unchanged.
The Reddit wrapper remains the canonical outer `Post`.

This slice adds an explicit optional source fullname to the existing authenticated
submit pipeline. It does not add a menu entry or enable crossposting from the composer.
UI_ENTRY=NOT_ADDED
LIVE_CREATE_READY=NO

## Identity and wire contract

An explicit source must normalize to `t3_` plus 1–16 ASCII base-36 characters.
A bare ID, local ID, URL or comment fullname is not silently converted to a post.
The selected source, authenticated account, destination community, title, flags and
post-flair selection remain request inputs. Source and created wrapper are distinct.

Native form: `/api/submit`, `kind=crosspost`, `crosspost_fullname=<source fullname>`.
There is no substitute `kind=link`, copied `url`, copied body, media upload or
`resubmit=true`. Destination `flair_id` and optional bound `flair_text` reuse the
existing post-flair path. These are post flairs, not author/user flairs.

The account remains pinned separately from the request-key hash by the coordinator.
For crossposts the hash gains a kind discriminator and normalized source fullname.
For all legacy requests with `crosspostFullname=null`, the exact hash input is unchanged.
Tests freeze pre-change self/link keys instead of regenerating expected keys with the
new implementation.

The existing authorization owner, non-retrying write client, final-dispatch marker,
submit mutex and structured error mapping are retained. No OAuth scope is added.
Crosspost authorization completion returns to IDLE and requires an explicit submit;
it never automatically replays the publication. Existing text/link/media behavior
on authorization completion is unchanged.

## Outcome and persistence

The existing pending-attempt store persists source/account/destination/request identity
before dispatch, alongside the existing dispatch timestamp. A literal pre-crosspost
JSON fixture remains readable with the same legacy request key. No preferences are
cleared and no draft/backup/cache migration is introduced here.

A reported created fullname must be a valid post fullname and differ from the source.
A failure to establish that distinction becomes malformed/OUTCOME_UNKNOWN through
existing error handling. Carrying the requested source in `SubmittedTextPost` is not
proof that a listing readback has verified the server's `crosspost_parent`.

UNKNOWN_CROSSPOST_RECONCILIATION=FAIL_CLOSED
The existing reconciliation reader does not yet expose parent-fullname evidence.
Crosspost reconciliation therefore remains OUTCOME_UNKNOWN without attempting a
parent-blind text/link candidate match. Pending state and duplicate-dispatch guards
remain intact; there is no retry or false success. This is deliberate interim behavior,
not completed source-aware reconciliation.

## Required successor before UI publication

The next slice must resolve source eligibility and destination crosspost permission
through available Reddit data; pin source/account/community through composer edits;
persist source selection in drafts, process state and portable backup as appropriate;
reuse destination title/flair requirements; and review before explicit publication.
It must also implement source-aware uncertain-outcome reconciliation and navigation
based on the created wrapper, not the source. Do not expose the new submit field in
an ordinary composer until these obligations have dedicated coverage.

No menu/composer UI, draft/backup schema, live write, media projection, subscription,
source/destination eligibility check, release version, checkpoint or land is included.

## Verification

New tests cover canonical/exclusive identity, legacy hash goldens, native form,
wrapper/source separation, authenticated validation, refusal to inspect parent-blind
reconciliation candidates, pending-attempt persistence and explicit post-auth publish.
Coordinator tests use deterministic in-memory fakes, never real account preferences.

The source contract is registered in `tools/check-source-contract.sh` and does not
recursively run predecessor contracts. The delivery runner runs this contract and
the existing CROSSPOST01 predecessor once each, then one full DEV2 JVM unit suite,
classifying focused failures separately from predecessor/other-suite failures.
An Android build or test pass may only be reported from the local repository's output.

## Source basis and external wire verification

Repository basis: the uploaded `crosspost01a1-source-context.zip`, containing tracked
files and SHA-256 values at the baseline above. The native wire shape was separately
checked against PRAW's primary `Submission.crosspost` implementation
(`praw/models/reddit/submission.py`: `kind=crosspost`, `crosspost_fullname`, API submit).
This is external implementation evidence, not a claim that Reddit's current rendered
API documentation fully describes this endpoint variant. No Reddit request was made.

## CROSSPOST01A1B successor binding

The initial blanket refusal above is superseded by A1B on the same creation branch.
`UNKNOWN_CROSSPOST_RECONCILIATION=FAIL_CLOSED` remains the invariant for a missing,
invalid, conflicting, ambiguous or non-matching **direct outer** `crosspost_parent`.
A1B allows a unique verified created-wrapper candidate and carries explicit parent
proof to the coordinator before clearing the pending attempt. A1A's test now supplies
real parent-blind candidates of every kind instead of forbidding list inspection.
The source contract checks the stronger parent-and-wrapper guard, not the obsolete
implementation-level early return. No legacy wire/hash/persistence assertion is removed.
See `subshell-crosspost01a1b-source-reconciliation-contract-v1.md` for the successor.
