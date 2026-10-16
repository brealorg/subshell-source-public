# subshell WRITE01-A2 authenticated comment submit transport contract V1

WRITE01-A2 adds a typed transport for posting one text comment to Reddit after
WRITE01-A1 has granted and pinned the active account's comment capability. It
does not expose the operation in the composer and does not send a live Reddit
comment during automated proof.

## Version and predecessor

- `versionCode = 87`
- `versionName = 0.65.1-write01a2-comment-submit-transport-dev`
- predecessor: WRITE01-A1 commit `5ea0eadf470fdaf4d4833bca20bf4928d6ba5a25`
- preserved default grant: exactly `identity mysubreddits read`
- required comment grant: `identity mysubreddits read submit`

## Request boundary

The transport sends an HTTPS OAuth request to `POST /api/comment` with an
`application/x-www-form-urlencoded` body containing exactly:

- `api_type=json`
- `raw_json=1`
- `thing_id=<t3 post fullname or t1 comment fullname>`
- `text=<trimmed raw Markdown>`

Parent fullnames accept only `t1_` or `t3_` plus a bounded alphanumeric
identifier. Comment text must be non-empty and at most 10,000 characters.
The shared HTTP boundary applies a bounded encoded form body, bounded JSON
response, cancellation, bearer authorization, and the selected canonical
User-Agent.

## Account and replay safety

The caller supplies the account stable ID captured before submission.
WRITE01-A1 resolves or refreshes only that account's `identity mysubreddits read submit`
session. The returned identity must reconstruct the same provider-qualified
stable ID before any request is sent.

Comment POSTs use a dedicated OkHttp client with
`retryOnConnectionFailure(false)`. One submitter instance permits one in-flight
request. A concurrent second request fails locally without network traffic.

DNS and connect failures are safe for explicit retry because the request did
not reach the endpoint. Timeouts, generic I/O failures, HTTP 408/421/425/5xx,
and malformed successful responses are classified as an unknown outcome and
are not automatically retryable, because Reddit exposes no client idempotency
key for `/api/comment`.

## Response and failures

A successful JSON response must contain exactly one `kind=t1` thing. It is
mapped to the existing canonical `Comment` model, including fullname,
parent fullname, author, body, score, creation time, depth, OP/moderator state,
and awards.

`json.errors` tuples are reduced to sanitized typed categories. Provider
messages and response bodies never enter UI-facing result state:

- rate limit
- validation
- unavailable or locked parent
- permission
- unknown API failure

Retry timing uses structured `Retry-After` or `X-Ratelimit-Reset` metadata.
A 401 or 403 requires authorization. HTTP 429 is rate-limited. Other ambiguous
write failures remain non-retryable until a later thread refresh can establish
whether the comment exists.

## Runtime proof

The deterministic Android lane uses localhost HTTPS only. It proves:

1. exact `POST /api/comment` path, form fields, bearer, User-Agent and content type;
2. canonical mapping of a returned `t1` comment;
3. account-stable-ID pinning with no request for a mismatched account;
4. sanitized Reddit API rate-limit mapping and structured retry timing;
5. unknown-outcome handling for malformed 2xx responses;
6. one-in-flight duplicate protection;
7. automatic connection retry disabled.

The proof does not open the target app auth vault, use a live OAuth client ID,
contact Reddit, or create a remote comment.

## Deferred to WRITE01-A3

- composer authorization and submit UI
- draft, sending, failure and success state
- thread cache writeback or bounded canonical refresh
- replies selected from visible post/comment UI
- controlled manual live Reddit proof
