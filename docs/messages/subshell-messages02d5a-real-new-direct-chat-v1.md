# MESSAGES02D5A — real new Direct Chat

## Scope

D5A replaces the Inbox `Message` prototype with a text-only Reddit Direct Chat start flow.

Fresh Reddit Web HAR evidence established the brand-new room sequence:

1. `POST /_matrix/client/v3/user_directory/search`
2. `GET /_matrix/client/v3/rooms?include=state,timeline&seq=y&type=direct&with_user=...`
3. if and only if the direct-room lookup is definitively empty:
   `POST /_matrix/client/v3/createRoom`
   with `{"invite":[target],"preset":"reddit_dm"}`
4. `PUT /_matrix/client/v3/rooms/{room}/send/m.room.message/{txn}`
   with `{"body":"...","msgtype":"m.text"}`

The HAR showed the first-send room ID appearing first in the successful `createRoom` response, not
in an earlier sync or lookup. The resulting membership state had the creator joined and the peer
invited with `is_direct=true`.

A follow-up structural probe of that same Reddit Web capture established the exact successful
no-existing-room response: all three observed direct-room lookups returned `{"rooms":null}`.
D5A therefore treats an explicit JSON `null` in the `rooms` field as definitively empty. Missing
`rooms`, unexpected scalar/container shapes, malformed room entries and ambiguous multiple-room
results still fail closed.

## Product behavior

- Inbox `Message` opens a real recipient + text composer.
- Recipient suggestions reuse the existing Reddit people-search UI state.
- The Matrix writer independently resolves the selected username through Reddit's Matrix user
  directory before room lookup/creation.
- Existing room cache is reused when already verified.
- A non-empty direct-room lookup must be fully parseable and unambiguous. Unknown shapes fail
  closed rather than creating a duplicate room.
- A created or resolved room is placed in the existing memory-only Direct Chat session cache before
  first send so C7/D2B/D3B can use the warm room in the same process.
- D5A is text-only. Media remains excluded from the initial chat request.
- No read receipt or Seen write is introduced.

## Write safety

The Matrix client has `retryOnConnectionFailure(false)`.

Directory search and direct-room lookup are reads. `createRoom` and first message send are writes.
An IOException at either write boundary is classified as `OUTCOME_UNKNOWN`; D5A never automatically
retries those operations. The UI blocks another send until the user refreshes/checks Inbox.

HTTP 401/403 invalidates the first-party token/cache. A definite 404 during message send invalidates
the cached room but is not automatically retried.

## Runtime proof required

Before checkpointing:

- start a chat with a recipient that has no existing Direct Chat;
- confirm sanitized log sequence:
  `stage=directory result=accepted`,
  `stage=room result=created`,
  `stage=send result=accepted`;
- confirm the chat request and first text are visible in Reddit Web / another first-party client;
- separately verify an existing/warm Direct Chat does not create a duplicate room if convenient.

No commit or landing is part of the preview operation.

<!-- SUBSHELL_MESSAGES02D5A_RUNTIME_PROOF_20260829 -->
## Runtime verification

On 2026-08-29, DEV1 runtime verification started a Direct Chat with a Reddit account that did not
already appear in the test account's chat list and sent the first text exactly once. The new chat
and first text then appeared in Reddit Web, providing cross-client end-to-end proof of the
directory lookup -> no-existing-room projection -> room creation -> first-message path.

The intended internal `subshell-matrix-chat-start` stage trace was not captured because the
interactive logcat wrapper exited early under zsh and the relevant app log events were no longer
present in the retained logcat buffer. This limitation is recorded explicitly rather than
repeating the write solely to obtain a prettier trace. The cross-client Reddit Web result is the
runtime acceptance evidence.

The installed runtime-proven DEV1 APK SHA256 was
`cd0e7084d2662f11fb7abb2934e8c9886e1770cf949e33a1fa61cc1d24bbb92e`.
