# MESSAGES02C6R2 — Matrix-confirmed media echo

Consecutive Direct Chat Matrix media writes must not wait for Reddit's slower legacy inbox projection before appearing in the open conversation. After Matrix returns HTTP 200 + `event_id`, subshell keeps the stable `mxc://reddit.com/<mediaId>` identity and renders a server-confirmed local echo using the Matrix download URL.

The echo is replaced only when the Reddit inbox projection contains the same unique Matrix media ID. A previous outgoing GIF can therefore never falsely confirm a newer media send. Existing read-only inbox refreshes remain in place and no write retry is added.
