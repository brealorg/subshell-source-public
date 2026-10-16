# Issue #22 — comment author avatars

Issue #22 reports that comments show no avatars (snoovatars): the comment
row drew a generated letter avatar because Reddit comment listings carry no
avatar image.

## Data path

```text
comment listing (t1)  →  author_fullname ("t2_…") per comment
  → RedditCommentPage.authorFullnames (username → fullname, transient)
  → RedditCommentsUiState.authorFullnames (accumulated per post)
  → PostScreen: state.ensureAuthorAvatars(map)
  → IdentityMetadataState: skip known / in-flight, batch the rest
  → IdentityMetadataRepository.refreshAuthorAvatars
  → GET /api/user_data_by_account_ids?ids=t2_a,…&raw_json=1  (≤ 100 ids)
  → username → https avatar (null = account without avatar)
```

Design decisions:

- The fullname map is never part of `Comment` or the cache codec (positional
  payloads). Cached threads show letter avatars until the thread is refreshed
  from Reddit.
- The avatar map is process-local, bounded (`MAX_AUTHOR_AVATARS`), keyed by
  username and not account-scoped: avatars are public data. Requests use the
  active account's token and need the `privatemessages` scope, which the
  daily-driver sign-in grants; without it the lookup is skipped silently.
- `404` from the batch endpoint means "no id matched" and is an empty result.
  Other failures leave the authors unresolved so a later thread retries.

## Setting

`show_comment_avatars` (default on, portable like the other comment
presentation settings; older backups restore the default). Off keeps the
letter avatar. The Comments settings preview mirrors the toggle with the
default profile silhouette, since it has no network.

## Runtime evidence

```text
tag=subshell-issue22
author-avatars post=<id> authors=N known_before=A known_after=B
```

## Failure classification

- Primary: the batch lookup, the state cache or the row wiring is lost, or
  the setting no longer round-trips.
- Predecessor: an older comment/identity contract is red while issue #22
  assertions pass.
- Release-blocking: only when a release being prepared cannot ship because of it.
