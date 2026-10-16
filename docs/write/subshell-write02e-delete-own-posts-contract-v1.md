# WRITE02E delete own posts

WRITE02E deletes posts owned by the active authenticated Reddit account.

The ownership gate is `OwnContentActionPolicy.canDelete`. It applies to self/text posts and other post kinds, so image, GIF, video, gallery, link, and text posts share the same delete capability while edit remains text/self-post only.

The write boundary uses Reddit `POST /api/del` with a canonical `t3_` fullname. It reuses the existing edit authorization grant and `SubshellNetworkTransport.nonRetryingWriteCallFactory`.

Deletion always requires an explicit confirmation dialog. No local post is removed before a remote success acknowledgement.

After acknowledgement, `SubshellState.applyDeletedPost` records a session tombstone, removes the post from current paging snapshots and PostCard surfaces, clears post-scoped local UI state, and pops an open post route through the existing route stack. Existing feed origin and scroll-anchor behavior therefore owns the return position.

WRITE02E does not add moderator removal, edit media, replace media, or delete comments.
