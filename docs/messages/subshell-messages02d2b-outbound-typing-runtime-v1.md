# MESSAGES02D2B — outbound Direct Chat typing runtime

D2B adds the first Matrix typing write path to Subshell Direct Chat.

Transport:

- `PUT /_matrix/client/v3/rooms/{roomId}/typing/{userId}`;
- `typing=true` carries a bounded 15-second Matrix timeout;
- `typing=false` omits the timeout and explicitly stops typing;
- the writer reuses the C6/C7 shared first-party Reddit web JWT and memory-only Matrix session cache;
- it requires an already-verified warm Matrix identity and room from the foreground live reader;
- it performs no room discovery and never guesses a Direct Chat room;
- writes are serialized so a later stop cannot overtake an earlier start.

Cadence:

- a genuine composer edit starts typing;
- the initial restored-draft emission is ignored;
- repeated edits are network-suppressed except for a 5-second keepalive;
- 2.5 seconds without an edit sends an explicit stop;
- clearing the composer sends stop immediately;
- pressing Send sends stop immediately;
- leaving/stopping the Direct Chat live screen sends stop when typing was active;
- failed writes are not blindly retried; the next genuine edit may retry.

Privacy/feature boundary:

- runtime logs contain only desired boolean, accepted/failed, sanitized reason and HTTP status;
- no account, username, Matrix user ID, room ID, token or message body is logged;
- no read-receipt write, Seen UI or presence is added;
- normal text/media message write paths are unchanged.

D2B remains uncommitted until the other Reddit client visibly confirms Subshell's typing state.
