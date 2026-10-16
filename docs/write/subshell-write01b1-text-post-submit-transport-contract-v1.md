# subshell WRITE01-B1 authenticated text-post submit transport contract V1

WRITE01-B1 adds a typed transport for publishing one Reddit self post after
ACCOUNT01 has established one authoritative active account selection.
It does not connect the transport to Create Post and does not contact Reddit
during automated proof.

## Version and predecessors

- `versionCode = 91`
- `versionName = 0.65.5-write01b1-text-post-submit-transport-dev`
- predecessor: ACCOUNT01 commit beginning `34be9ad`
- required write grant: exactly `identity mysubreddits read submit`

## Request boundary

The transport sends an HTTPS OAuth request to `POST /api/submit` with an
`application/x-www-form-urlencoded` body containing exactly:

- `api_type=json`
- `raw_json=1`
- `kind=self`
- `sr=<canonical bare community name>`
- `title=<trimmed one-line title>`
- `text=<normalized Markdown body>`
- `sendreplies=<true or false>`
- `nsfw=<true or false>`
- `spoiler=<true or false>`

Community names accept bare, `r/`, or `/r/` forms and are normalized to a
lowercase bare name. Titles are non-empty, one line, and at most 300
characters. A text-post body may be empty; CRLF and CR line endings become
LF and the client boundary is 40,000 characters. NUL characters are rejected.

Subsequent composer integration also uses this boundary for hybrid link
submissions. Hybrid link submissions preserve optional body text by sending
`kind=link`, a normalized `url`, and non-empty normalized `text` in the same
`POST /api/submit` request. An empty link body omits `text`.

## Account and replay safety

The request carries the account stable ID captured before submission. The
existing submit capability resolves or refreshes only that account's
`identity mysubreddits read submit` session. The authenticated identity must reconstruct
the same provider-qualified stable ID before any request is sent.

Text-post POSTs use the dedicated non-retrying OkHttp write client with
`retryOnConnectionFailure(false)`. One submitter instance allows one in-flight
request. A concurrent second call fails locally without network traffic.

DNS and connect failures are safe for an explicit user retry because the
request did not reach the endpoint. Timeouts, generic I/O failures, HTTP
408/421/425/5xx, protocol failures, and malformed successful responses are
unknown outcomes and are not retryable automatically. The transport has no
client idempotency key that could make replay safe.

## Response and failures

A successful response must provide one consistent Reddit post identity through
`json.data.name`, `json.data.id`, or both. The canonical result is a `t3_`
fullname plus the normalized request community, title, body, and an optional
sanitized Reddit permalink. Conflicting response identities are malformed.

Provider error tuples are reduced to sanitized categories. Provider messages
and response bodies never enter result state:

- rate limit
- validation
- unavailable community
- permission
- submission restricted
- challenge required
- unknown API failure

Structured retry timing comes from `Retry-After` or `X-Ratelimit-Reset`.
HTTP 401/403 requires authorization. HTTP 429 is rate-limited. Ambiguous write
failures remain non-retryable until a later canonical read can establish
whether the post exists.

## Runtime proof

The Android runtime lane uses localhost HTTPS only and proves:

1. exact `POST /api/submit` path and nine form fields;
2. `kind=self`, bearer authorization, User-Agent, and content type;
3. canonical `t3` response mapping and sanitized permalink handling;
4. account-stable-ID pinning with no request for a mismatched account;
5. sanitized API rate-limit mapping and structured retry timing;
6. unknown-outcome handling for HTTP 503 and malformed 2xx responses;
7. one-in-flight duplicate protection;
8. automatic connection retry disabled.

The proof does not open the target app auth vault, use a live OAuth client ID,
contact Reddit, or create a remote post.
All user-facing app strings remain English, and WRITE01-B1 adds no user-facing UI.

## Deferred

- Create Post composer wiring
- authorization and sending state in the UI
- Room/feed writeback and canonical refresh
- controlled live self-post proof in `r/subshell`
- media upload flows
