# CROSSPOST01A1C — source-bound draft and destination-policy foundation

## Scope

Extends qualified A1A native submit and A1B source-verified reconciliation. No new
endpoint, OAuth scope, write transport, replay, UI entry, live submit, deploy, or
release operation. A1C is NOT the completed crosspost composer.

## Source-bound draft

`ComposerPostType.CROSSPOST` is explicit. The ordinary type picker remains
`TEXT`, `LINK`, `MEDIA` through `ComposerPostType.standaloneTypes`. A selected
post supplies its own canonical `t3_…` fullname, not a guessed URL/id and not its
nested parent's identity. Display metadata (community, author, title, kind) does
not prove that the source is still accessible. Source NSFW/spoiler flags are
retained, including sensitivity inherited by a selected wrapper.

The draft has an account owner and immutable source snapshot. A fresh draft has
no implicit destination. Destination changes clear the old community's post
flair. Owner, draft id, source, type and sensitivity cannot silently change during
editing; replacement body/link/media is prohibited. Request construction is pure
and delegates to the existing `TextPostWriteRequest` with `crosspostFullname`.
Generic V2/V4 submit/local preview paths reject source-bound drafts.

## Native storage and compatibility

The existing `ComposerDraftStore` stores ordinary drafts in
`post_composer_drafts.v1` and crossposts in `post_composer_crosspost_drafts.v1`.
This is one store and one SharedPreferences editor, not a parallel submit store.
Old builds only read the former key and must never reinterpret CROSSPOST as TEXT.
Malformed source records are rejected individually without dropping unrelated
valid records. Records with a crosspost source in an ordinary type/key are not
restored as ordinary posts. Both keys are encoded before either is staged.
The global 100-draft limit, account ownership and id de-duplication remain.

Literal pre-A1C JSON is a compatibility fixture. Current-encoder suffix stripping
is not used to manufacture that legacy record. Portable backup still supports its
existing TEXT/LINK schema only: crossposts are excluded rather than exported
lossily, and existing nonportable crossposts survive portable draft restore.

## Destination evidence and requirements

The existing account-scoped about loader maps nullable `crosspostable` with
`allow_post_crossposts` as a compatibility fallback, plus `subreddit_type`,
`user_is_banned`, `user_can_submit`, `user_is_contributor`,
`user_is_moderator` and `over18` into a policy bound to account and destination.
Absent values remain unknown; no extra read or implicit permission is invented.
These JSON mappings are test fixtures, not evidence of a live response.

Request assembly requires authenticated draft ownership, matching loaded policy,
confirmed absence of a posting ban, no explicit submission denial, and a supported
destination type. An explicit destination crosspost denial blocks the request.
Missing crosspost-capability evidence is treated as unknown rather than denied and
is deferred to Reddit at the explicit Publish boundary. Private/restricted targets
still need a positive posting grant. Unknown target types remain blocked. Posting-requirement authorization remains
blocking when the account lacks the required scope. If the detailed requirements
endpoint is unavailable or not checked, validation follows the existing composer
preflight fallback and is deferred to Reddit at the explicit Publish boundary. Title/body rules and required post flair are checked;
selected flair must belong to current destination options and respect editability.
A required replacement body is unsupported. Source-domain requirements remain
blocked because the source display snapshot does not establish the original URL.
Destination NSFW status cannot be ignored when building the request.

## UI obligations remain — A1D

SOURCE_AWARE_UI=PENDING
PROCESS_STATE_SOURCE_SERIALIZATION=PENDING
FRESH_SOURCE_ACCESS_CHECK=PENDING
DESTINATION_REFRESH_AND_REVIEW=PENDING
PORTABLE_CROSSPOST_BACKUP=NOT_SUPPORTED
UI_ENTRY=NOT_ADDED
LIVE_CREATE_READY=NO

Generic draft reopening is blocked for CROSSPOST until source-aware routing and
process-state serialization are added. Native store recreation is not equivalent
to an Android process-death runtime proof. Source availability, destination
freshness, review UI, explicit publish dispatch, and navigation to the new wrapper
must be wired and qualified before exposure to users. No existing source or
reconciliation guarantee is relaxed by A1C.

## Qualification

New suites cover source/owner immutability, native request identity, destination
permission and requirements, current flair membership, real store recreation,
legacy JSON, malformed records, shared-editor staging, downgrade isolation and
portable-backup isolation. Existing composer picker coverage asserts the same
three ordinary choices instead of incorrectly treating enum membership as UI.
Run this contract, A1B, A1A, original CROSSPOST01, and one full DEV2 unit suite.
Classify current failures, predecessor failures and other suite failures separately;
release blocking is not evaluated by a no-release foundation operation.

## A1E successor qualification

A1E replaces the temporary blanket draft-reopen prohibition with source-aware
CROSSPOST routing and authenticated same-owner validation. The generic V2/V4
request builders and local post builder still reject source-bound drafts. The
portable backup boundary and source/owner immutability remain unchanged.
The PENDING values above record A1C's historical scope; A1E's own contract covers
the newly exposed entry, fresh source reads, review and explicit publish.
