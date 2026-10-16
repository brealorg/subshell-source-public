# CROSSPOST01A1B — Source-verified uncertain-outcome reconciliation

## Input and purpose

Base HEAD `370ca263a4a43d7fcaf7fded5cfcdaed1fcb9cc3`, branch
`dev2/crosspost01a1-create-flow`, with the exact qualified A1A dirty candidate.
Input evidence: the supplied A1A result archive, including 1,964 tests with zero
failures/errors/skips and SHA-256 manifests. No remote-head assumptions.

A1B replaces the deliberate A1A blanket refusal with a source-aware readback of an
already-dispatched pending attempt. This is still a foundation slice, not a new UI.
UI_ENTRY=NOT_ADDED
LIVE_CREATE_READY=NO

## Evidence and matching

Reuse the existing authenticated GET `/user/{username}/submitted`, limit 25 by default
(maximum 100 in the existing reader), sort new and raw_json=1. No new endpoint or scope.
Only the returned outer `crosspost_parent` supplies parent proof. The source card's
`crosspost_parent_list`, title, URL and body never synthesize that identity. A crosspost
marker (even invalid or only a preview) classifies the candidate as CROSSPOST before
media hints, so it cannot accidentally reconcile as an ordinary text/link/media post.

Match exactly one candidate against canonical source, a distinct valid wrapper identity,
authenticated username, target community, title, existing dispatch window (-30..+300
seconds), NSFW/spoiler flags, and requested destination flair template ID/text when
present. The timestamp is the persisted original dispatch time, not the read time.
Missing rows, missing/invalid parent, wrong parent, wrong account/target/flags/flair,
invalid wrapper, and multiple matching rows remain OUTCOME_UNKNOWN. The bounded listing
cannot prove non-publication; absence is not permission to retry.

A source body or media destination may be inherited in a crosspost listing. It is not
compared to the empty create-request body, and is not copied into the new wrapper's
submitted-content model. The result projects the created wrapper's path and an explicit
`verifiedCrosspostFullname`; submit ACKs do not receive that proof automatically.

## Pending-attempt and account boundaries

Before a crosspost read, the capability-authenticated username must match the supplied
account-directory username. The coordinator already pins the pending attempt's account
before dispatching the read; A1B checks the active account again when the read completes.

Before clearing pending state, require the result account, verified parent, distinct
wrapper identity, target/title, empty wrapper body and wrapper route to agree. Failed or
unverified crosspost readbacks are converted to OUTCOME_UNKNOWN, retain the stored
attempt, and keep the duplicate-submit guard. Cancellation propagates and retains state.
Success clears the pending attempt and increments completion once. A repeated read with
no pending attempt cannot create another completion.

No automatic resubmit, write replay, authorization replay, new network write, source
replacement or media upload is introduced. Existing direct submit and legacy request
hashes/pending serialization are unchanged. The ordinary non-crosspost matcher retains
its current body/link checks. The only new kind prevents crossposts from masquerading
as ordinary posts in that matcher.

## Predecessor update

A1A's temporary no-list-inspection assertion is obsolete, not its safety requirement.
It is replaced by explicit candidates of every kind with no parent evidence; none may
succeed. The A1A source contract now checks the stronger source+wrapper guard. Other A1A
and CROSSPOST01 assertions remain in place and run independently, without recursive tests.

## Verification and remaining obligations

Three new classes / 26 methods cover listing projection (including literal JSON), exact
source matching and rejection, wrapper projection, stored-attempt recreation, success,
account drift, forged/incomplete evidence, failed reads and cancellation. The runner
verifies all expected methods in one full DEV2 unit execution and classifies new,
predecessor and other-suite failures separately. Compile errors are not test PASS.

Local script operation has no Reddit reads/writes, deploy, checkpoint, land or push.
Runtime Reddit readback is not claimed from deterministic unit evidence.
Source eligibility, destination crosspost permission, source-aware draft/process/backup
persistence, composer entry/review, and runtime publication remain the creation-UI
successor. No current code claims these are implemented by A1B.
