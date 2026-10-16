# WRITE02F manage own posts

WRITE02F adds one owner-management surface for authenticated Reddit posts of every post kind.

The canonical PostCard overflow menu shows `Manage post` only when WRITE02A ownership policy identifies the post as owned by the active authenticated account. Feed entry uses the existing origin-aware post route and opens the management screen immediately.

The screen loads fresh post metadata from Reddit `/api/info` instead of extending the cached `Post` model solely for reply-notification state. It reads current flair text, NSFW status, spoiler status, and `send_replies`. If the session already grants `flair`, it also loads `/r/{community}/api/link_flair_v2`.

Supported writes are:
- post flair through `/r/{community}/api/selectflair` with the `flair` grant;
- reply notifications through `/api/sendreplies` with the `edit` grant;
- NSFW through `/api/marknsfw` and `/api/unmarknsfw` with the `modposts` grant;
- spoiler through `/api/spoiler` and `/api/unspoiler` with the `modposts` grant.

The broader `modposts` OAuth grant is a dedicated on-demand capability and is not added to ordinary edit or flair authorization. Every mutation uses the non-retrying write call factory.

After server acknowledgement, the management snapshot updates immediately and SubshellState reconciles flair, NSFW, and spoiler values into the routed post and active paging snapshots. Reply-notification state remains management-session metadata and is reloaded from Reddit whenever the screen opens.

WRITE02F does not replace, reorder, upload, or mutate the published media asset. It does not change titles, destination links, communities, authors, or moderator-only post actions.
