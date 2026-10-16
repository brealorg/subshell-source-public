# ERROR01A4 — Reddit write and mutation error contract

ERROR01A4 migrates Reddit writes and account mutations onto the shared ERROR01 structured failure and presentation model without flattening feature-specific behavior into one generic error string.

## Covered write surfaces

The phase covers comment submit, text/media post submit, comment edit/delete, post edit/delete/manage, community join/leave, and custom-feed create/update/delete. The existing authorization owners, submit reconciliation, mutation ownership and feature-specific retry callbacks remain authoritative.

Each authenticated writer preserves structured failure information whenever the transport provides it: HTTP status and retry timing, network transport category, protocol category, bounded provider code/message and local failure category. Coordinators carry those details into their UI state instead of dropping the cause at the transport boundary.

## Presentation policy

Write failures use `ErrorPresentation` and `ErrorNotice` with product-specific titles and explanations. Technical metadata is rendered through `FailureDetails.toSafeTechnicalDetail()` rather than embedded into the primary message. Provider messages never bypass `SafeFailureText`; legacy provider fields may remain in transport-facing state for compatibility but active UI does not render them directly.

Composer field validation remains feature-owned and inline. ERROR01A4 only replaces submit/upload/network/write failure presentation; it does not turn ordinary title/body/flair validation into a global error notice.

## Ambiguous mutation outcomes

A transport interruption after a write has been dispatched is not equivalent to a confirmed failure. Comment/post submission keeps the existing outcome-reconciliation flow. Destructive or duplicate-prone mutations do not gain a blind Retry action when the final Reddit state is unknown. Users are directed to check or refresh the authoritative state before repeating the mutation.

Comment delete keeps its existing `OUTCOME_NOT_CONFIRMED` check-again path. A failure during the post-delete readback is classified as unconfirmed rather than redispatching `/api/del`. Post deletion only permits a direct retry for failures known to occur before request dispatch (DNS/connect) or an explicit rate-limit rejection; timeout/IO/protocol and other ambiguous failures do not gain a blind delete retry. Community membership and custom-feed mutations preserve their outcome-unknown classification and avoid automatic duplicate mutation retries. Post-management mutations only expose shared Retry for safe load failures; mutation failures retain feature-owned re-entry.

Custom-feed create/update/delete controls are blocked after an outcome-unknown result until authoritative read-side reconciliation resolves the state. ERROR01A4 schedules that reconciliation automatically and never redispatches the mutation. If Reddit confirms the requested state, the normal success path completes; if Reddit confirms the previous state, the control becomes available again. Closing an ambiguous custom-feed editor keeps the safe read reconciliation alive and refreshes the feed list in the background.

Community membership uses the same rule. An outcome-unknown join/leave schedules authoritative subscription refreshes while preserving the last known community list. A successful subscription read clears the ambiguity, and a failed read is not treated as fresh: reopening Community or Subs may revalidate without requiring the user to first clear the old error.

## Endpoint ownership

Authorization remains owned by the existing write capabilities. ERROR01A4 does not add scopes, replay stored destructive requests after process death, or change callback routing. Account changes continue to fail closed.

The shared presentation layer owns readable copy and sanitized technical details. Each feature continues to own the action that actually retries, reconciles, reloads or dismisses the operation.

## Regression boundary

ERROR01A3 media/backup behavior remains unchanged, including the Fragment 1.9 Activity Result compatibility guard. ERROR01A4 also remains compatible with existing composer reconciliation, write authorization owner, community-membership owner and custom-feed owner contracts.

## Deferred

ERROR01A5 is the audit gate. It will scan the broader codebase for raw `Throwable.message`, active direct provider-message rendering, string-only canonical errors, lost structured failure causes and other paths that can bypass the shared error policy.
