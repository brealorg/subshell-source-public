# PROFILEUSER01 — public profile correctness

## Product contract

Public Reddit user profiles distinguish four different product states instead of treating every
empty response as proof that the user has no activity.

When Subshell has direct public evidence for a post or comment author and the matching Reddit
profile listing later loads successfully but is empty, the matching tab explains that **profile
privacy may be enabled**. The copy is deliberately probabilistic because Reddit does not expose a
public profile-curation flag in the user `about`, `overview`, `submitted`, or `comments` response
schemas observed during PROFILEUSER01 diagnostics.

When no matching public activity evidence exists, empty authenticated listings use the neutral
labels **No public profile posts** and **No public profile comments**. Transport/protocol failures
remain error states and are not converted into privacy messages.

Opening a user from a post records post evidence. Tapping a comment avatar or username records
comment evidence while the remaining comment header and disclosure control own collapse/expand.
Evidence is local runtime context only; it is not persisted as a claim about the Reddit account.

## Unavailable profiles

A public-user request that resolves to HTTP 404 from identity, submitted posts, or comments is treated
as an unavailable profile rather than a generic empty or privacy-curated listing. The UI intentionally
does not claim whether the account is deleted, suspended, nonexistent, shadowbanned, or otherwise
unavailable because Reddit does not expose a reliable public distinction for those cases. The profile
header suppresses stale remote identity imagery and Follow controls while preserving the normal profile
card structure. Unavailable profiles use the generic empty Reddit-style profile avatar and explicit
unknown (`—`) karma/account-age/follower values rather than stale identity data. Tabs and listing refresh
controls are replaced by one shared red ERROR01 **Profile unavailable** `ErrorNotice`, matching the
error family used for unavailable deleted comment authors. The unavailable banner keeps the neutral
background without a duplicate status chip. Other HTTP, transport, and protocol failures
remain ordinary error states and are not reclassified.

## Identity visuals

A Reddit-provided avatar is preferred for available profiles. When no usable avatar exists, including
for an unavailable profile, the profile screen uses the generic empty Reddit-style avatar rather than
the legacy white-face `SnooAvatar`. Replacing that legacy fallback elsewhere in Subshell remains separate
follow-on work. A Reddit-provided banner is preferred. Missing or failed banner imagery uses an intentional
`No profile banner` placeholder; unavailable profiles keep the neutral background without a duplicate
status chip because the shared red ErrorNotice owns the error presentation.

## Diagnostic boundary

The temporary PROFILEUSER01 schema-snoop and runtime diagnostic logging are probe-only and must not
ship in the product candidate.
