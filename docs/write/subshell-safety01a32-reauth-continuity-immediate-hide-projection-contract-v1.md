# SAFETY01A3.2 — reauth continuity and immediate Hide/Unhide projection

## Runtime evidence

After A3.1, the Content Safety error path no longer crashes. First-use authorization for the new Reddit `report` scope can still return with `ACCOUNT_CHANGED` after a fresh RedReader login even when the user authenticated the same Reddit identity. After dismissing the warning, subsequent Hide requests succeed and the post appears in authoritative You → Hidden.

Successful Hide also remains visible in the current Home listing until refresh. The remote write and Hidden readback are correct; the remaining problem is optimistic list projection.

## Same-identity authorization continuity

Capability authorization remains pinned to an account stable ID. If capability-session replacement for that pinned record fails because the local account record was rebuilt during fresh login, the authorizer may try the currently active authenticated stable ID. The existing account coordinator remains the authority that decides whether the upgraded Reddit session belongs to that account. A genuinely different identity therefore still fails closed.

`ContentSafetyCoordinator` additionally pins the Reddit username when the mutation begins. A returned stable-ID change is accepted only when the authorization username matches that pending Reddit identity, case-insensitively. If the currently active username is available, it must match as well. The report reason remains transient.

A successful owner-bound callback may bridge a transient account-directory snapshot gap for the single resumed mutation; the writer still performs its normal account-scoped `ensureAuthorized` check before the Reddit write.

## Immediate Hide/Unhide projection

Normal list `PostCard`s now leave their current list immediately when optimistic hidden state differs from that list's authoritative hidden hint:

- Home/community/search/profile-visible list: Hide removes the card immediately.
- You → Hidden: Unhide removes the card immediately.
- failure rollback restores the card automatically.

The explicit post-detail card opts out of suppression so hiding an already-open discussion does not destroy the viewport beneath the user.

## Source of truth

You → Hidden remains authoritative Reddit account history. No authenticated local Hidden truth is restored and no Room schema is changed.

## Iteration build policy

A3.2 uses the faster DEV2 iteration path: one static successor contract pass, focused JVM tests, then incremental `assembleDebug`/install while excluding the already-run Gradle `subshellContracts` task. The APK application ID is checked before installation. Full canonical gates remain mandatory at checkpoint/land.
