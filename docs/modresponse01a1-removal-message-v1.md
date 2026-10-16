# MODRESPONSE01A1 — moderator removal responses (data and writer)

Issue #14, slice 1 of 3. Adds the optional removal response to the
moderation write path; no UI yet.

## Reddit contract

`POST /api/v1/modactions/removal_link_message` (posts) and
`…/removal_comment_message` (comments), form `api_type=json&json={"item_id":
[fullname], "message", "title", "type"}`. `type` is one of `public`,
`public_as_subreddit`, `private`, `private_exposed`. Reddit decides
distinguish/sticky: the public types leave a distinguished, stickied
comment on a post (a distinguished reply on a comment), the private types go
as modmail with the moderator hidden or exposed. `title` is ignored for the
public types. The item must already be removed. Verified against PRAW's
recorded exchanges.

## Transaction

```text
remove (api/remove)  →  attach reason (optional)  →  send response (optional)
```

Each step is one write over the non-retrying client and is reported on its
own: `Updated(removalReasonWarning, removalMessageOutcome)`, with
`RedditRemovalStepOutcome` = `NOT_REQUESTED | SENT | FAILED`. Only the
removal decides success; a failed response is a warning on a successful
removal and is never retried (the removal must not repeat). A failed removal
sends nothing else. A response is never implied by a reason: it is an
explicit `RedditRemovalMessage` on the request, valid only with `REMOVE`.

## Failure classification

- Primary: the response write is retried, sent before the removal, sent
  without an explicit message, or its failure is folded into the removal.
- Predecessor: MOD01A2/MOD01A21/MODERATION02 contracts red while these pass.
- Release-blocking: only when a release being prepared cannot ship because of it.
