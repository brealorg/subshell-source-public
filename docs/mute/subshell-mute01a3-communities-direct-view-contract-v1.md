# MUTE01A3 — Communities entry and direct-view mode

Joined community rows gain a dedicated mute/edit action while retaining normal navigation.

`SHOW_POSTS` is the backward-compatible default. `ABOUT_ONLY` preserves community identity, description, mute status and controls but replaces the complete Posts section before post cards are composed. Direct post/comment deep links remain unchanged.

Feeds settings expose both modes with a dedicated AnimatedContent preview. The mode is a device-global UI preference; mute records and expiry remain account-scoped.

No Reddit write, OAuth scope, Room migration, WorkManager job, cache deletion, main mutation, DEV1 source mutation or remote operation is introduced.
