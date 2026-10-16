# REDDIT01C3A composer authorization owner contract v1

REDDIT01C3A migrates composer flair and composer post-requirements authorization onto persisted logical authorization ownership.

`COMPOSER_FLAIR` uses the existing composer-flair OAuth configuration and scope set.

`COMPOSER_REQUIREMENTS` uses the existing comment-write OAuth configuration because post-requirements loading requires no broader grant than the existing comment-write capability.

The existing `ConfiguredAuthCommentWriteCapability` instances remain available to predecessor consumers and post-management transport/authorization paths. REDDIT01C3A adds owner-bound authorization adapters for the two composer authorization flows.

`AuthConnectViewModel` routes `COMPOSER_FLAIR` and `COMPOSER_REQUIREMENTS` persisted callbacks and resume state by authorization owner before the predecessor priority chain.

Ownerless predecessor callbacks remain temporarily supported by the existing composer fallback routing.

REDDIT01C3A does not broaden or otherwise change Reddit OAuth scopes.

Custom-feed write, inbox authorization, and post-management authorization are intentionally deferred. Custom-feed and inbox capabilities use specialized capability interfaces, while post management selects among edit, flair, and moderation capabilities by operation.
