# subshell WRITE01-B2 text-post composer contract v1

WRITE01-B2 connects the existing Create Post composer to the typed WRITE01-B1
Reddit text-post transport. It does not redesign the composer and it does not
add remote link, poll, image, GIF, gallery, or video publishing.

## Account and authorization boundary

- A request pins the active authenticated account by stable account ID.
- The current valid draft is re-pinned when the user chooses **Grant post access**.
- The shared `submit` capability is requested explicitly and a successful callback
  resumes exactly one pending request.
- An account switch before authorization or submission fails closed.
- No bearer token, OAuth code, or provider payload is exposed to Compose UI state.

## Draft and completion boundary

- The composer keeps its existing saved-draft behavior while closed or on failure.
- A successful response clears the draft only when the active account and the
  normalized text draft still match the submitted request key.
- Edits made while an already submitted request is in flight remain as a draft.
- An unambiguous success opens the created post using the returned Reddit post ID.
- Outcome-unknown failures never expose automatic retry; the UI tells the user to
  check Reddit before trying again.
- The remote **Post** action remains available while idle so invalid input can be
  validated on tap without network activity. The first validation error is then
  shown inline.
- The composer subtitle distinguishes **New draft** from **Draft saved**.

## Supported remote content

Authenticated remote submission is limited to a text post with:

- community
- title, at most 300 characters
- Markdown body, at most 40,000 characters
- no media attachment

Local fixture-account publishing remains unchanged. Unsupported authenticated
post kinds remain visible but fail validation with an English explanation.

## Runtime proof

The focused Android test uses in-memory account, authorization, and submitter
fakes. It proves authorization resume, request re-pinning, automatic submit after
grant, exact-account enforcement, matching-draft clearing, edited-draft
preservation, and opening the returned post. It does not launch the app activity,
contact Reddit, mutate the installed OAuth vault, or create a remote post.

All user-facing app strings remain English.
