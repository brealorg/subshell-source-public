# subshell Feed/Post V2 canonical route stack contract v1

## FEEDPOST-V2B scope

FEEDPOST-V2B replaces navigation decisions reconstructed from nullable side fields with one canonical typed route stack. The stack always has `Main(Home)` as its root and represents the visible hierarchy with explicit `Main`, `Community`, `Search`, `Post`, `User`, `Conversation`, `Secondary`, `Media`, and `PostComposer` entries.

`SubshellState.navigateBack()` performs one stack pop. Route-specific close functions are typed guards around the same pop operation. Nested Post → User, Post → Community, and Post → Media flows no longer require dedicated return variables or a separate community frame stack.

The existing public screen state (`selectedPost`, `selectedSubreddit`, `selectedUser`, `searchVisible`, and related values) remains as a read-only compatibility projection derived from the canonical stack. Screen rendering selects the current `SubshellRoute`, not a prioritized chain of nullable fields.

Main-destination navigation resets the stack to Home plus the selected main destination. Opening a secondary destination intentionally discards contextual routes, matching the previous drawer behavior, and Back returns to the associated main destination.

FEEDPOST-V2A typed post origins remain the process-restoration boundary for this phase. Full saveable serialization of every stack entry is deferred to FEEDPOST-V2E. Per-feed scroll anchors remain deferred to FEEDPOST-V2C.
