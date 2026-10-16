#!/usr/bin/env bash
set -u
set -o pipefail

# STEP3_BOUNDARY_GUARDS_V1 (2026-10-09, owner-approved): boundaries that a
# behaviour test cannot express ("this must not exist"), gathered from orphan
# checkers no gate ran, so they stop a land. Each block names the checker it
# came from. Add a guard here only when it is a real boundary, not wording.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
M="$ROOT/app/src/main/java/app/subshell/prototype"

fail() { echo "FAIL=$1"; echo 'RESULT=SUBSHELL_BOUNDARY_GUARDS_CONTRACT_FAIL'; exit 1; }
pass() { echo "PASS=$1"; }

MUTE_FILES=(
    "$M/ui/CommunityMutes.kt"
    "$M/ui/components/CommunityMuteSheet.kt"
    "$M/ui/components/MutedCommunitiesSheet.kt"
)
for file in "${MUTE_FILES[@]}" \
    "$M/ui/screens/EasterEggTerminal.kt" "$M/ui/screens/EasterEggTerminalEngine.kt" \
    "$M/ui/screens/HelpScreen.kt" "$M/ui/screens/CommunitiesScreen.kt" \
    "$M/model/Models.kt" "$M/data/cache/SubshellCacheCodec.kt" \
    "$M/data/network/reddit/RedditListingDtos.kt" "$M/ui/components/MediaComponents.kt" \
    "$M/MainActivity.kt"; do
    [[ -f "$file" ]] || fail "BOUNDARY_GUARD_FILE_MISSING_${file#"$ROOT"/}"
done
pass BOUNDARY_GUARD_FILES_PRESENT

# From mute01a1/mute01a2: muting is local; expiry needs no background
# scheduler, and mutes use no network, OAuth scope or Room schema.
! rg -q 'WorkManager|CoroutineWorker|AlarmManager' "${MUTE_FILES[@]}" \
    "$M/ui/SubshellState.kt" "$M/ui/screens/HomeScreen.kt" ||
    fail MUTE_HAS_NO_BACKGROUND_SCHEDULER
! rg -q 'listOf\("api",|oauth|NetworkDataSource|RoomDatabase|Migration\(' "${MUTE_FILES[@]}" ||
    fail MUTE_IS_LOCAL_ONLY
pass MUTE_LOCAL_BOUNDARY

# From mute01a2, widened to the tree: the volume icons are the auto-mirrored
# ones; the deprecated non-mirrored icons do not come back.
! rg -q 'androidx\.compose\.material\.icons\.rounded\.Volume(Off|Up)\b|Icons\.Rounded\.Volume(Off|Up)\b' \
    "$ROOT/app/src/main" ||
    fail VOLUME_ICONS_ARE_AUTO_MIRRORED
pass VOLUME_ICONS_ARE_AUTO_MIRRORED

# From egg01: the help-screen terminal is a toy; it has no network, Reddit
# session, app state or navigation.
! rg -q 'OkHttp|Retrofit|RedditHttp|NetworkDataSource|AuthenticatedReddit|SubshellState|navigate\(' \
    "$M/ui/screens/EasterEggTerminal.kt" "$M/ui/screens/EasterEggTerminalEngine.kt" "$M/ui/screens/HelpScreen.kt" ||
    fail TERMINAL_IS_ISOLATED
pass TERMINAL_IS_ISOLATED

# From polls01a1: polls are read-only; no poll write transport exists.
! rg -q -i 'api/poll|graphql|gql|poll.*submit|poll.*create|post.*poll.*vote' \
    "$M/data/network/reddit/RedditListingDtos.kt" "$M/ui/components/MediaComponents.kt" \
    "$M/data/cache/SubshellCacheCodec.kt" "$M/model/Models.kt" ||
    fail POLLS_HAVE_NO_WRITE_TRANSPORT
pass POLLS_HAVE_NO_WRITE_TRANSPORT

# From update01a1a: updates are offered through the site; the app never
# installs packages itself or uses Play Core in-app updates.
! rg -q 'REQUEST_INSTALL_PACKAGES|AppUpdateManager|com\.google\.android\.play.*app.?update' \
    "$ROOT/app/src/main" "$ROOT/app/build.gradle.kts" ||
    fail UPDATES_HAVE_NO_SELF_INSTALLER
pass UPDATES_HAVE_NO_SELF_INSTALLER

# From next01a5: a Subscriptions row opens through the route-aware selection,
# never the old direct open that grows the back stack.
! grep -Fq 'state.openSubreddit(community.name)' "$M/ui/screens/CommunitiesScreen.kt" ||
    fail SUBSCRIPTIONS_ROWS_USE_ROUTE_AWARE_OPEN
pass SUBSCRIPTIONS_ROWS_USE_ROUTE_AWARE_OPEN

# From subs04a1: community discovery has no hard-coded topic chips.
! grep -Fq 'DISCOVERY_TOPICS' "$M/ui/screens/CommunitiesScreen.kt" &&
! grep -Fq 'label = { Text("Popular") }' "$M/ui/screens/CommunitiesScreen.kt" &&
! grep -Fq 'Technology", "Gaming", "Science", "DIY' "$M/ui/screens/CommunitiesScreen.kt" ||
    fail DISCOVERY_HAS_NO_STATIC_TOPICS
pass DISCOVERY_HAS_NO_STATIC_TOPICS

# From issue22: comment avatars are looked up, not stored; the comment model
# and the positional cache codec carry no avatar fields.
! grep -Fq 'authorAvatar' "$M/model/Models.kt" &&
! grep -Fq 'authorFullname' "$M/data/cache/SubshellCacheCodec.kt" ||
    fail COMMENT_AVATARS_STAY_OUT_OF_MODEL_AND_CODEC
pass COMMENT_AVATARS_STAY_OUT_OF_MODEL_AND_CODEC

# From subs04a0: the activity wires community membership (state, action and
# authorization); a JVM test does not start MainActivity.
grep -Fq 'communityMembershipUiState.collectAsState()' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::updateCommunityMembership' "$M/MainActivity.kt" &&
grep -Fq '::launchCommunityMembershipAuthorization' "$M/MainActivity.kt" &&
grep -Fq 'beginCommunityMembershipAuthorization()' "$M/MainActivity.kt" ||
    fail ACTIVITY_WIRES_COMMUNITY_MEMBERSHIP
pass ACTIVITY_WIRES_COMMUNITY_MEMBERSHIP

# From subs04b: the activity hands Reddit community discovery (state and
# loader) to SubshellApp; CommunitiesScreen and CommunityEntryPoints default
# both, so the compiler does not prove it, and a JVM test does not start
# MainActivity.
rg -U -q 'redditCommunityDiscoveryState by\s*authConnectViewModel\.redditCommunityDiscoveryUiState\.collectAsState\(\)' \
    "$M/MainActivity.kt" &&
grep -Fq 'redditCommunityDiscoveryState = redditCommunityDiscoveryState,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::loadRedditCommunityDiscovery' "$M/MainActivity.kt" ||
    fail ACTIVITY_WIRES_COMMUNITY_DISCOVERY
pass ACTIVITY_WIRES_COMMUNITY_DISCOVERY

# From moderation02a5a6/a5a7: the activity hands the moderator team runtime
# (permission changes and removals with their authorizations) to SubshellApp;
# every field has a default, so the compiler does not prove it.
grep -Fq 'moderatorTeamRuntime = moderatorTeamRuntimeEntryPoints(' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::updateModeratorPermissions' "$M/MainActivityEntryPoints.kt" &&
grep -Fq 'authConnectViewModel::removeModerator' "$M/MainActivityEntryPoints.kt" &&
grep -Fq 'launchRedditModeratorPermissionAuthorization,' "$M/MainActivityEntryPoints.kt" &&
grep -Fq 'launchRedditModeratorRemovalAuthorization,' "$M/MainActivityEntryPoints.kt" &&
grep -Fq 'authConnectViewModel::inviteModerator' "$M/MainActivityEntryPoints.kt" &&
grep -Fq 'launchRedditModeratorInviteAuthorization,' "$M/MainActivityEntryPoints.kt" ||
    fail ACTIVITY_WIRES_MODERATOR_TEAM
pass ACTIVITY_WIRES_MODERATOR_TEAM

# From crosspost01a1e: the activity hands the crosspost review state, Review,
# Publish and dropping a review to SubshellApp; every field of
# PostComposerWriteRuntimeEntryPoints has a default, so the compiler does not
# prove it.
grep -Fq 'authConnectViewModel.crosspostReviewUiState.collectAsState()' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::reviewCrosspostDraft,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::publishReviewedCrosspost,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::invalidateCrosspostReview,' "$M/MainActivity.kt" ||
    fail ACTIVITY_WIRES_CROSSPOST_CREATE
pass ACTIVITY_WIRES_CROSSPOST_CREATE

# From moderation02a4/a5a1-a5a3: the activity hands the mod log and user
# management (reads, writes and their authorizations) to SubshellApp; every
# field of ModeratorToolsEntryPoints has a default, so the compiler does not
# prove the wiring.
grep -Fq 'moderatorTools = moderatorToolsEntryPoints(' "$M/MainActivity.kt" &&
grep -Fq 'launchRedditModeratorLogAuthorization = ::launchRedditModeratorLogAuthorization,' "$M/MainActivity.kt" &&
grep -Fq '::launchRedditModeratorUserManagementAuthorization,' "$M/MainActivity.kt" &&
grep -Fq 'onOpenModeratorLog = authConnectViewModel::openModeratorLog,' "$M/MainActivityEntryPoints.kt" &&
grep -Fq 'onAppendModeratorLog = authConnectViewModel::appendModeratorLog,' "$M/MainActivityEntryPoints.kt" &&
grep -Fq 'authConnectViewModel::openModeratorUserManagement,' "$M/MainActivityEntryPoints.kt" &&
grep -Fq 'onMutateModeratorUser = authConnectViewModel::mutateModeratorUser,' "$M/MainActivityEntryPoints.kt" &&
grep -Fq 'authConnectViewModel::mutateMutedModeratorUser,' "$M/MainActivityEntryPoints.kt" &&
grep -Fq 'authConnectViewModel::mutateApprovedModeratorUser,' "$M/MainActivityEntryPoints.kt" &&
grep -Fq 'authConnectViewModel::appendMutedModeratorUserManagement,' "$M/MainActivityEntryPoints.kt" &&
grep -Fq 'authConnectViewModel::appendApprovedModeratorUserManagement,' "$M/MainActivityEntryPoints.kt" &&
grep -Fq 'launchRedditModeratorLogAuthorization,' "$M/MainActivityEntryPoints.kt" &&
grep -Fq 'launchRedditModeratorUserManagementAuthorization,' "$M/MainActivityEntryPoints.kt" ||
    fail ACTIVITY_WIRES_MODERATOR_TOOLS
pass ACTIVITY_WIRES_MODERATOR_TOOLS

# From moderation02a2: what users and moderators wrote in a report stays in
# memory; the reports dialog, its coordinator and the report parser never log
# it or write it anywhere (no saved state, preferences, files or database).
for file in "$M/ui/components/ModeratorReports.kt" "$M/auth/ModeratorReportsCoordinator.kt" \
    "$M/data/network/reddit/RedditModeratorReportParser.kt"; do
    [[ -f "$file" ]] || fail "MODERATOR_REPORTS_FILE_MISSING_${file#"$ROOT"/}"
    ! grep -Eq 'println\(|Log\.[diwev]\(|rememberSaveable|SharedPreferences|writeText\(|RoomDatabase' "$file" ||
        fail MODERATOR_REPORTS_ARE_NOT_LOGGED_OR_PERSISTED
done
pass MODERATOR_REPORTS_ARE_NOT_LOGGED_OR_PERSISTED

# From deeplink01: MainActivity takes in a forwarded Reddit link both on a
# cold start and through onNewIntent (starting the whole activity in the unit
# suite ran the test JVM out of memory).
grep -Fq 'consumeRedditViewIntent(launchIntent, delivery = "cold")' "$M/MainActivity.kt" &&
grep -Fq '!consumeRedditViewIntent(intent, delivery = "warm")' "$M/MainActivity.kt" ||
    fail ACTIVITY_TAKES_IN_REDDIT_LINKS
pass ACTIVITY_TAKES_IN_REDDIT_LINKS

# From error01a3: backup failures are kept and shown by kind; an exception's
# own text (paths, provider details) is never stored or shown.
! grep -Eq 'error\.message|Throwable\.message' \
    "$M/backup/SettingsBackupRepository.kt" \
    "$M/ui/screens/BackupRestoreScreen.kt" \
    "$M/ui/screens/BackupFolderOnboarding.kt" ||
    fail BACKUP_FAILURES_KEEP_NO_EXCEPTION_TEXT
pass BACKUP_FAILURES_KEEP_NO_EXCEPTION_TEXT

# From error01a5: nowhere in the app is an exception's own text shown or
# stored (it can carry paths, URLs, tokens or provider details); failures
# travel as FailureDetails and safe, written copy.
! grep -rEiq '(^|[^A-Za-z0-9_])(error|exception|throwable)\??\.message\b' "$M" ||
    fail NO_RAW_EXCEPTION_TEXT_IN_APP
pass NO_RAW_EXCEPTION_TEXT_IN_APP

# From error01a5 (checker tooling, not the app): source-contract runs its
# children through run_required_contract, which records a child's failure;
# a bare `bash tools/check-…` line would let a failing child pass unnoticed.
SOURCE_CONTRACT="$ROOT/tools/check-source-contract.sh"
grep -Fq 'run_required_contract()' "$SOURCE_CONTRACT" &&
grep -Fq 'CHILD_CONTRACT_' "$SOURCE_CONTRACT" &&
! grep -Eq '^[[:space:]]*bash[[:space:]]+("?\$ROOT/)?tools/check-[A-Za-z0-9_-]+-contract\.sh' "$SOURCE_CONTRACT" ||
    fail SOURCE_CONTRACT_PROPAGATES_CHILD_FAILURES
pass SOURCE_CONTRACT_PROPAGATES_CHILD_FAILURES

# From profile01a5: the comment screens and their text/media rendering draw
# what the data layer gives them; they hold no HTTP client, endpoint or
# Reddit data source, and no web address of their own (the comment text
# projection has exactly one: the Reddit chat image host it recognises).
PRESENTATION_FILES=(
    "$M/ui/screens/UserProfileScreen.kt"
    "$M/ui/screens/PostScreen.kt"
    "$M/ui/components/MarkdownText.kt"
    "$M/ui/components/CommentRemoteMedia.kt"
)
PROJECTION="$M/model/CommentTextProjection.kt"
for file in "${PRESENTATION_FILES[@]}" "$PROJECTION"; do
    [[ -f "$file" ]] || fail "PRESENTATION_FILE_MISSING_${file#"$ROOT"/}"
    ! grep -Eq 'OkHttp|SubshellHttpClient|NetworkEndpoint|RedditCommentNetworkDataSource' "$file" ||
        fail PRESENTATION_HAS_NO_NETWORK
done
pass PRESENTATION_HAS_NO_NETWORK
for file in "${PRESENTATION_FILES[@]}"; do
    ! grep -Eq 'https?://' "$file" || fail NO_RAW_WEB_ENDPOINTS_IN_PRESENTATION_SOURCE
done
[[ "$(grep -Ec 'https?://' "$PROJECTION")" == "1" ]] &&
grep -Fq 'https://matrix\\.redditspace\\.com' "$PROJECTION" ||
    fail NO_RAW_WEB_ENDPOINTS_IN_PRESENTATION_SOURCE
pass NO_RAW_WEB_ENDPOINTS_IN_PRESENTATION_SOURCE

# From search02a12/search01a1: search and the app state behind it draw what
# the data layer gives them — no HTTP client, network type, trending service
# or web address of their own.
for file in "$M/ui/screens/SearchScreen.kt" "$M/ui/SearchFoundation.kt" "$M/ui/SubshellState.kt" "$M/ui/SubshellApp.kt"; do
    [[ -f "$file" ]] || fail "SEARCH_PRESENTATION_FILE_MISSING_${file#"$ROOT"/}"
    ! grep -Eq 'okhttp3|data\.network|trends\.google\.com|https?://' "$file" ||
        fail SEARCH_PRESENTATION_HAS_NO_NETWORK
done
pass SEARCH_PRESENTATION_HAS_NO_NETWORK

# From profile01a2: the activity hands the signed-in user-posts feed
# (refresh and next page) to SubshellApp; the parameters have defaults, so
# the compiler does not prove the wiring.
grep -Fq 'authConnectViewModel::refreshRedditUserSubmitted' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::appendRedditUserSubmitted' "$M/MainActivity.kt" ||
    fail ACTIVITY_WIRES_USER_SUBMITTED_FEED
pass ACTIVITY_WIRES_USER_SUBMITTED_FEED

# From messages02c4r2: comment writes use only the OAuth API; no Reddit chat
# token, web cookie or legacy compose endpoint (the message side follows).
! rg -n 'chat:write|chat_write|Cookie|cookie|web-session-derived|listOf\("api", "compose"\)' \
    "$M/data/auth/reddit/RedditCommentSubmitter.kt" \
    "$M/data/network/reddit/RedditCommentSubmitNetworkDataSource.kt" >/dev/null ||
    fail COMMENT_WRITES_USE_ONLY_THE_OAUTH_API
pass COMMENT_WRITES_USE_ONLY_THE_OAUTH_API

# From messages02c/02c1: a private-message reply (also a direct chat's text
# reply) uses only the OAuth api/comment path; no Reddit chat token, web
# cookie or legacy compose endpoint.
! rg -n 'chat:write|chat_write|matrix\.redditspace\.com|Cookie|cookie|web-session-derived|/api/compose|listOf\("api", "compose"\)' \
    "$M/data/account/MessageReplyFlow.kt" \
    "$M/data/network/reddit/RedditMessageReplyNetworkDataSource.kt" \
    "$M/data/auth/reddit/RedditMessageReplyWriter.kt" \
    "$M/auth/MessageReplyCoordinator.kt" >/dev/null ||
    fail MESSAGE_REPLIES_USE_ONLY_THE_OAUTH_API
pass MESSAGE_REPLIES_USE_ONLY_THE_OAUTH_API

# From messages02c: the activity hands the reply state, the send and its reset
# to SubshellApp; MessagingEntryPoints' fields have defaults, so the compiler
# does not prove the wiring.
grep -Fq 'authConnectViewModel.messageReplyUiState.collectAsState()' "$M/MainActivity.kt" &&
grep -Fq 'onSendRedditMessageReply = authConnectViewModel::sendRedditMessageReply,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::resetRedditMessageReplyState,' "$M/MainActivity.kt" ||
    fail ACTIVITY_WIRES_MESSAGE_REPLY
pass ACTIVITY_WIRES_MESSAGE_REPLY

# From messages02c4r2, 02c, 02c5r3 and 02e2a2: a message reply, a Direct Chat
# image, a reaction, a redaction or a first message is sent once; neither the
# writers nor the coordinator has a wait or retry loop of its own (the
# transport is non-retrying, WriteTransportPolicyTest).
! rg -n 'delay\(|retry\(|retryWhen|repeat\(|while[[:space:]]*\(' \
    "$M/data/auth/reddit/RedditMessageReplyWriter.kt" \
    "$M/auth/MessageReplyCoordinator.kt" \
    "$M/data/auth/reddit/RedditChatMatrixMediaWriter.kt" \
    "$M/data/auth/reddit/RedditChatMatrixReactionWriter.kt" \
    "$M/data/auth/reddit/RedditChatMatrixRedactionWriter.kt" \
    "$M/data/auth/reddit/RedditChatMatrixStartWriter.kt" >/dev/null ||
    fail MESSAGE_REPLY_WRITER_HAS_NO_RETRY_LOOP
pass MESSAGE_REPLY_WRITER_HAS_NO_RETRY_LOOP

# From messages02c7 and 02e2a: the activity hands Direct Chat (live read,
# typing, delete, reactions, the first message) to SubshellApp; every field of
# MessagingEntryPoints has a default, so the compiler does not prove it.
grep -Fq 'directChatLiveState = directChatLiveState,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::startRedditDirectChatLive,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::stopRedditDirectChatLive,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::updateRedditDirectChatTyping,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::deleteRedditDirectChatMessage,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::reactRedditDirectChatMessage,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::resetRedditDirectChatReactionFailure,' "$M/MainActivity.kt" &&
grep -Fq 'onStartRedditDirectChat = authConnectViewModel::startRedditDirectChat,' "$M/MainActivity.kt" ||
    fail ACTIVITY_WIRES_DIRECT_CHAT
pass ACTIVITY_WIRES_DIRECT_CHAT

# From messages02c5r3 (owner approved): the Reddit chat token is minted in a
# WebView that reads no files or content URIs, talks only to reddit.com, and
# keeps the token in memory (nothing in this file stores it).
CHAT_TOKEN="$M/chat/RedditChatWebSessionTokenProvider.kt"
grep -Fq 'settings.allowFileAccess = false' "$CHAT_TOKEN" &&
grep -Fq 'settings.allowContentAccess = false' "$CHAT_TOKEN" &&
grep -Fq 'return host == "reddit.com" || host.endsWith(".reddit.com")' "$CHAT_TOKEN" &&
grep -Fq "fetch('/svc/shreddit/token'" "$CHAT_TOKEN" &&
! rg -n 'SharedPreferences|DataStore|openFileOutput' "$CHAT_TOKEN" >/dev/null ||
    fail CHAT_WEB_TOKEN_WEBVIEW_IS_LOCKED_DOWN
pass CHAT_WEB_TOKEN_WEBVIEW_IS_LOCKED_DOWN

# From messages02c5: an image pasted into the comment composer is copied into
# the app before it is attached (the paste arrives through a content receiver
# no JVM test can drive).
grep -Fq 'requestMedia(pastedImageKeptInApp(context, pasted))' "$M/ui/screens/CommentComposerScreen.kt" ||
    fail COMMENT_COMPOSER_KEEPS_PASTED_IMAGES
pass COMMENT_COMPOSER_KEEPS_PASTED_IMAGES

# From messages02c3/c5r2: an image or GIF pasted from the keyboard into a
# conversation reaches the same attachment path as the picker; the editor
# takes it through a content receiver no JVM test can drive.
grep -Fq 'onMediaReceived = ::requestMedia,' "$M/ui/screens/ConversationScreen.kt" &&
grep -Fq '.contentReceiver(receiveContentListener)' "$M/ui/components/PostStyleComposerEditor.kt" ||
    fail CONVERSATION_COMPOSER_TAKES_PASTED_MEDIA
pass CONVERSATION_COMPOSER_TAKES_PASTED_MEDIA

# From wiki01a1/a2: Subshell only reads community wikis — nothing edits a
# wiki page or its settings, or asks for the wiki edit grant — and a wiki is
# never stored in the cache.
! grep -rEq 'api/wiki/edit|wiki/settings|"wikiedit"' "$M" || fail COMMUNITY_WIKI_IS_READ_ONLY
pass COMMUNITY_WIKI_IS_READ_ONLY
! grep -rq 'CommunityWiki' "$M/data/cache" || fail COMMUNITY_WIKI_IS_NOT_PERSISTED
pass COMMUNITY_WIKI_IS_NOT_PERSISTED

# From wiki01a2: the activity hands the wiki state, its loads and its
# authorization to SubshellApp; the parameters have defaults, so the
# compiler does not prove the wiring.
grep -Fq 'redditCommunityWikiState = redditCommunityWikiState,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::loadRedditCommunityWikiIndex' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::loadRedditCommunityWikiPage' "$M/MainActivity.kt" &&
grep -Fq '::launchCommunityWikiAuthorization,' "$M/MainActivity.kt" ||
    fail ACTIVITY_WIRES_COMMUNITY_WIKI
pass ACTIVITY_WIRES_COMMUNITY_WIKI

# From communityinfo01a1/a3: community information (sidebar, rules,
# moderators) is read live and never stored; the activity hands its state and
# its loader to SubshellApp (the parameters have defaults, so the compiler
# does not prove the wiring).
! grep -rq 'CommunityInfo' "$M/data/cache" || fail COMMUNITY_INFO_IS_NOT_PERSISTED
pass COMMUNITY_INFO_IS_NOT_PERSISTED
grep -Fq 'authConnectViewModel.redditCommunityInfoUiState.collectAsState()' "$M/MainActivity.kt" &&
grep -Fq 'redditCommunityInfoState = redditCommunityInfoState,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::loadRedditCommunityInfo' "$M/MainActivity.kt" ||
    fail ACTIVITY_WIRES_COMMUNITY_INFO
pass ACTIVITY_WIRES_COMMUNITY_INFO

# From settings02c, widened to the backup package: settings backups are local
# files and the chosen folder; no process and no network.
! rg -q 'ProcessBuilder|Runtime\.getRuntime|java\.net|okhttp|retrofit' \
    "$M/backup" "$M/ui/screens/BackupRestoreScreen.kt" ||
    fail BACKUP_HAS_NO_PROCESS_OR_NETWORK
pass BACKUP_HAS_NO_PROCESS_OR_NETWORK

# From inbox01b21 and modmail06: the background inbox sync only reads; it never
# marks a message or a modmail conversation read (the worker builds its own
# runtime, so no test can fake it).
! rg -q 'markRead|markAllRead|read_message|read_all_messages|inboxReadMutationWriter|setReadState|redditModmailWriter' \
    "$M/inbox/InboxBackgroundSync.kt" ||
    fail BACKGROUND_INBOX_SYNC_NEVER_MARKS_READ
pass BACKGROUND_INBOX_SYNC_NEVER_MARKS_READ

# From notify01a1 and inbox01b22: the background sync (its worker builds its
# own runtime) posts only the new events it decided on, for the inbox and for
# modmail, and only through InboxNotificationCenter.
test "$(grep -cF 'newEventKeys = decision.newEventKeys,' "$M/inbox/InboxBackgroundSync.kt")" -eq 2 &&
! rg -q 'NotificationManager|NotificationCompat|\.notify\(' "$M/inbox/InboxBackgroundSync.kt" ||
    fail BACKGROUND_SYNC_POSTS_ONLY_NEW_EVENTS
pass BACKGROUND_SYNC_POSTS_ONLY_NEW_EVENTS

# From notify01a2: the activity starts the notification read reconciliation
# with the inbox and modmail states and stops it when destroyed; the runtime is
# its one owner.
grep -Fq 'startNotificationReadReconciliation(' "$M/MainActivity.kt" &&
grep -Fq 'inboxStates = authConnectViewModel.redditInboxUiState,' "$M/MainActivity.kt" &&
grep -Fq 'modmailStates = authConnectViewModel.redditModmailUiState,' "$M/MainActivity.kt" &&
grep -Fq 'notificationReadReconciliationJob.cancel()' "$M/MainActivity.kt" &&
! grep -Fq 'InboxNotificationCenter.reconcile' "$M/MainActivity.kt" ||
    fail ACTIVITY_RUNS_NOTIFICATION_READ_RECONCILIATION
pass ACTIVITY_RUNS_NOTIFICATION_READ_RECONCILIATION

# From inbox01b22: a tapped inbox or modmail notification is taken in on a cold
# and a warm start and handed to SubshellApp.
grep -Fq 'consumeInboxNotificationIntent(launchIntent)' "$M/MainActivity.kt" &&
grep -Fq 'consumeModmailNotificationIntent(launchIntent)' "$M/MainActivity.kt" &&
grep -Fq '!consumeInboxNotificationIntent(intent) &&' "$M/MainActivity.kt" &&
grep -Fq '!consumeModmailNotificationIntent(intent) &&' "$M/MainActivity.kt" &&
grep -Fq 'inboxNotificationTarget = inboxNotificationTarget,' "$M/MainActivity.kt" ||
    fail ACTIVITY_TAKES_IN_NOTIFICATION_TARGETS
pass ACTIVITY_TAKES_IN_NOTIFICATION_TARGETS

# From inbox01b22: the activity asks for the notification permission when
# SubshellApp requests it and reads only its own answer.
grep -Fq 'onRequestInboxNotificationPermission = ::requestInboxNotificationPermission,' "$M/MainActivity.kt" &&
grep -Fq 'if (requestCode != INBOX_NOTIFICATION_PERMISSION_REQUEST_CODE) return' "$M/MainActivity.kt" &&
grep -Fq 'android.permission.POST_NOTIFICATIONS' "$ROOT/app/src/main/AndroidManifest.xml" ||
    fail ACTIVITY_ASKS_FOR_THE_NOTIFICATION_PERMISSION
pass ACTIVITY_ASKS_FOR_THE_NOTIFICATION_PERMISSION

# From modmail01a1-06: the activity hands New Modmail (reads, replies, archive,
# read state, triage, authorization, the notification target) to SubshellApp;
# every field of ModmailEntryPoints has a default, so the compiler does not
# prove it.
grep -Fq 'authConnectViewModel.redditModmailUiState.collectAsState()' "$M/MainActivity.kt" &&
grep -Fq 'redditModmail = ModmailEntryPoints(' "$M/MainActivity.kt" &&
grep -Fq 'onLoadRedditModmail = authConnectViewModel::loadRedditModmail,' "$M/MainActivity.kt" &&
grep -Fq 'onAppendRedditModmail = authConnectViewModel::appendRedditModmail,' "$M/MainActivity.kt" &&
grep -Fq 'onSelectRedditModmailListMode = authConnectViewModel::selectRedditModmailListMode,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::openRedditModmailConversation,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::sendRedditModmailMessage,' "$M/MainActivity.kt" &&
grep -Fq 'onSetRedditModmailArchived = authConnectViewModel::setRedditModmailArchived,' "$M/MainActivity.kt" &&
grep -Fq 'onSetRedditModmailUnread = authConnectViewModel::setRedditModmailUnread,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::verifyRedditModmailReadState,' "$M/MainActivity.kt" &&
grep -Fq 'onApplyRedditModmailTriage = authConnectViewModel::applyRedditModmailTriage,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::closeRedditModmailConversation,' "$M/MainActivity.kt" &&
grep -Fq 'onAuthorizeRedditModmail = ::launchRedditModmailAuthorization,' "$M/MainActivity.kt" &&
grep -Fq 'modmailNotificationTarget = modmailNotificationTarget,' "$M/MainActivity.kt" &&
grep -Fq 'takeModmailNotificationTarget(candidate)' "$M/MainActivity.kt" ||
    fail ACTIVITY_WIRES_MODMAIL
pass ACTIVITY_WIRES_MODMAIL

# From mod01a1-a22: the activity hands the moderation queue (reads, actions,
# retries, removal reasons and their authorizations) to SubshellApp; every
# field of ModerationQueueEntryPoints has a default.
grep -Fq 'redditModeration = ModerationQueueEntryPoints(' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel.redditModerationUiState.collectAsState()' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel.moderationActionUiState.collectAsState()' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel.removalReasonUiState.collectAsState()' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::loadRedditModQueue' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::appendRedditModQueue' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::resolveRedditModerationTarget' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::moderateRedditQueueItem' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::retryRedditModerationAction' "$M/MainActivity.kt" &&
grep -Fq '::launchRedditModerationActionAuthorization' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::loadRedditRemovalReasons' "$M/MainActivity.kt" &&
grep -Fq '::launchRedditRemovalReasonAuthorization' "$M/MainActivity.kt" ||
    fail ACTIVITY_WIRES_MODERATION_QUEUE
pass ACTIVITY_WIRES_MODERATION_QUEUE

# From modmail02/04a1/05/06: a modmail reply, archive, read-state or triage
# change is sent once; the writer has no wait or retry loop of its own.
! rg -n 'delay\(|retry\(|retryWhen|repeat\(|while[[:space:]]*\(' \
    "$M/data/auth/reddit/RedditModmailWriter.kt" >/dev/null ||
    fail MODMAIL_WRITER_HAS_NO_RETRY_LOOP
pass MODMAIL_WRITER_HAS_NO_RETRY_LOOP

# From profile01a6/savedcomments01a3: the activity hands the account's post
# history (upvoted, downvoted, hidden), its saved history, the saved-comment
# target and the profile-history authorization to SubshellApp.
grep -Fq 'authConnectViewModel.redditAccountHistoryUiState.collectAsState()' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel.redditSavedHistoryUiState.collectAsState()' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::refreshRedditAccountHistory,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::appendRedditAccountHistory,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::refreshRedditSavedHistory,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::appendRedditSavedHistory,' "$M/MainActivity.kt" &&
grep -Fq 'authConnectViewModel::resolveRedditSavedCommentTarget,' "$M/MainActivity.kt" &&
grep -Fq '::launchProfileHistoryAuthorization,' "$M/MainActivity.kt" ||
    fail ACTIVITY_WIRES_PROFILE_HISTORY
pass ACTIVITY_WIRES_PROFILE_HISTORY

# From useraction01a2: the activity hands block/unblock and the account's
# authoritative blocked list (refresh, next page, authorization) to SubshellApp.
grep -Fq 'onSetUserBlocked = authConnectViewModel::setUserBlocked,' "$M/MainActivity.kt" &&
grep -Fq 'onRefreshBlockedAccounts = authConnectViewModel::refreshBlockedAccounts,' "$M/MainActivity.kt" &&
grep -Fq 'onAuthorizeBlockedAccounts = ::launchBlockedAccountsAuthorization,' "$M/MainActivity.kt" &&
grep -Fq 'onAppendBlockedAccounts = authConnectViewModel::appendBlockedAccounts,' "$M/MainActivity.kt" ||
    fail ACTIVITY_WIRES_BLOCKED_ACCOUNTS
pass ACTIVITY_WIRES_BLOCKED_ACCOUNTS

# From savedcomments01a2: the account's saved history is device-local (auth
# material and device-local state are never portable), so its cache lives in
# noBackupFilesDir.
rg -U -q 'noBackupFilesDir,\s*"reddit-saved-history-v1"' \
    "$M/auth/ReadLoaderRuntimeParts.kt" ||
    fail SAVED_HISTORY_CACHE_IS_DEVICE_LOCAL
pass SAVED_HISTORY_CACHE_IS_DEVICE_LOCAL

# From media-audio-r27: one Media3 player builds video playback (the shared
# coordinator; the composer preview has its own), and no other platform
# player or Compose PlayerSurface appears.
test "$(rg -l 'ExoPlayer\.Builder' "$M" | sed "s#^$M/##" | sort | tr '\n' ' ')" = \
    'media/VideoPlayback.kt ui/screens/CreatePostScreenV4.kt ' &&
! rg -q '\bVideoView\b|android\.media\.MediaPlayer' "$M" &&
! rg -q 'media3\.ui\.compose\.PlayerSurface' "$M/ui/components" ||
    fail ONE_MEDIA3_PLAYER
pass ONE_MEDIA3_PLAYER

# From media-audio-r27: the activity owns the one video player through a
# ViewModel, hands it to the app, tells it about configuration changes, and
# only the ViewModel releases it.
grep -Fq 'ViewModelProvider(this)[VideoPlaybackViewModel::class.java]' "$M/MainActivity.kt" &&
grep -Fq 'videoPlaybackCoordinator = videoPlaybackViewModel.coordinator,' "$M/MainActivity.kt" &&
grep -Fq 'videoPlaybackViewModel.coordinator.onHostConfigurationChanged(newConfig)' "$M/MainActivity.kt" &&
rg -U -q 'override fun onCleared\(\) \{\s*coordinator\.release\(\)' "$M/media/VideoPlaybackViewModel.kt" &&
! rg -U -q 'onDispose \{[^}]*coordinator\.release\(\)' "$M/media/VideoPlayback.kt" ||
    fail ACTIVITY_OWNS_ONE_VIDEO_PLAYER
pass ACTIVITY_OWNS_ONE_VIDEO_PLAYER

# From media-audio-r27: the activity keeps its player across rotation and
# resizing, and never locks the orientation.
test "$(grep -c 'android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout"' "$ROOT/app/src/main/AndroidManifest.xml")" = 1 &&
! grep -q 'android:screenOrientation=' "$ROOT/app/src/main/AndroidManifest.xml" ||
    fail MANIFEST_KEEPS_THE_PLAYER_ACROSS_CONFIGURATION
pass MANIFEST_KEEPS_THE_PLAYER_ACROSS_CONFIGURATION

# From media-audio-r27: video telemetry and the network policy never read a
# URL, request headers or the device's network identity.
! rg -q 'dataSpec\.(uri|httpRequestHeaders)|getResponseHeaders\(|WebSettings|HttpEngine|Referer' \
    "$M/media/VideoMediaCache.kt" &&
! rg -q 'WifiInfo|TelephonyManager|LinkProperties|linkAddresses|getLinkProperties|dataSpec\.(uri|httpRequestHeaders)' \
    "$M/media/VideoNetworkPolicy.kt" ||
    fail VIDEO_TRANSPORT_HAS_NO_URL_OR_NETWORK_IDENTITY
pass VIDEO_TRANSPORT_HAS_NO_URL_OR_NETWORK_IDENTITY

# From media-audio-r27 and net01: the release build trusts only the system CAs
# and no cleartext; only the debug manifest names the debug (test CA)
# configuration, which allows no cleartext either; no build trusts user CAs.
grep -Fq 'android:usesCleartextTraffic="false"' "$ROOT/app/src/main/AndroidManifest.xml" &&
grep -Fq 'android:networkSecurityConfig="@xml/subshell_network_security_config"' "$ROOT/app/src/main/AndroidManifest.xml" &&
! rg -q 'subshell_debug_network_security_config' "$ROOT/app/src/main" &&
grep -Fq '<certificates src="system" />' "$ROOT/app/src/main/res/xml/subshell_network_security_config.xml" &&
test "$(grep -c '<certificates ' "$ROOT/app/src/main/res/xml/subshell_network_security_config.xml")" = 1 &&
! grep -q 'cleartextTrafficPermitted="true"' "$ROOT/app/src/main/res/xml/subshell_network_security_config.xml" &&
grep -Fq 'cleartextTrafficPermitted="false"' "$ROOT/app/src/debug/res/xml/subshell_debug_network_security_config.xml" &&
! rg -q 'src="user"' "$ROOT/app/src" ||
    fail RELEASE_MANIFEST_HAS_NO_TEST_TRUST
pass RELEASE_MANIFEST_HAS_NO_TEST_TRUST

# From media-audio-r27: the video cache (its size and contents) is
# device-local and never part of the portable settings.
! grep -Eiq 'cache' "$M/ui/SubshellSettings.kt" ||
    fail VIDEO_CACHE_STAYS_OUT_OF_BACKUPS
pass VIDEO_CACHE_STAYS_OUT_OF_BACKUPS

# From safety01a3: the activity hands Hide/Unhide, Report, the safety state,
# its retry and its authorization to SubshellApp (the SubshellApp parameters
# default to {}, so the compiler does not prove this wiring).
grep -Fq 'authConnectViewModel.contentSafetyUiState.collectAsState()' "$M/MainActivity.kt" &&
grep -Fq 'onSetPostHidden = authConnectViewModel::setPostHidden,' "$M/MainActivity.kt" &&
grep -Fq 'onReportContent = authConnectViewModel::reportContent,' "$M/MainActivity.kt" &&
grep -Fq 'onRetryContentSafety = authConnectViewModel::retryContentSafetyMutation,' "$M/MainActivity.kt" &&
grep -Fq 'onAuthorizeContentSafety = ::launchContentSafetyAuthorization,' "$M/MainActivity.kt" ||
    fail ACTIVITY_WIRES_CONTENT_SAFETY
pass ACTIVITY_WIRES_CONTENT_SAFETY

# From safety01a2: a hide or a report is sent once; a lost answer is "outcome
# unknown", never re-sent (the client half is WriteTransportPolicyTest).
! rg -n 'delay\(|retry\(|retryWhen|repeat\(|while[[:space:]]*\(' \
    "$M/data/auth/reddit/RedditContentSafetyWriter.kt" \
    "$M/data/network/reddit/RedditContentSafetyNetworkDataSource.kt" >/dev/null ||
    fail CONTENT_SAFETY_WRITER_HAS_NO_RETRY_LOOP
pass CONTENT_SAFETY_WRITER_HAS_NO_RETRY_LOOP

# From reddit01c3c1 (owner approved, auth storage): the pending and callback
# records and the session manager hold an owner and a context key, never a
# post-manage write request; the waiting change stays in the route's own
# persisted state, so a callback can never carry a destructive request.
! rg -q 'PostManageMutationRequest' \
    "$M/data/auth/AuthModels.kt" \
    "$M/data/auth/OAuthSessionManager.kt" \
    "$M/data/auth/AuthStorage.kt" ||
    fail AUTH_RECORDS_NEVER_HOLD_A_WRITE_REQUEST
pass AUTH_RECORDS_NEVER_HOLD_A_WRITE_REQUEST

# From protoclean01/01a2: the prototype media fixtures live only in the debug
# source set. Main code never names one by resource id (that breaks only the
# release compile, which a land does not run), and main holds none of the
# fixture files. The retired prototype clips and the old ic_subshell icon
# stay gone (quality01c-resource, retired 2026-10-14).
! rg -q 'R\.(raw|drawable)\.subshell_(video_lab_|media_r27_av)' "$M" &&
[[ -z "$(find "$ROOT/app/src/main/res" -path '*/raw*/*' \( -name 'subshell_*' -o -name 'homelab_*' \) 2>/dev/null)" ]] &&
[[ ! -e "$ROOT/app/src/main/res/drawable/ic_subshell.xml" ]] &&
[[ -z "$(find "$ROOT/app/src/main/res" -path '*/drawable*/*' -name 'subshell_video_lab_*' 2>/dev/null)" ]] &&
[[ ! -e "$ROOT/app/src/main/assets/media03" ]] ||
    fail NO_DEBUG_FIXTURES_IN_MAIN
pass NO_DEBUG_FIXTURES_IN_MAIN

# From protoclean01a31: SubshellState's default repository is the sample
# fixture backend, so the app builds it only through rememberSubshellState
# (the fixture-free persistent repository), and the runtime, the restore
# catalog and the state factory never reach for the fixture backend.
! rg -q --glob '*.kt' --glob '!SubshellState.kt' --glob '!RememberSubshellState.kt' \
    '(^|[^[:alnum:]_])SubshellState\(' "$M" &&
grep -Fq 'remember(context) { createPersistentSubshellRepository(context) }' "$M/ui/RememberSubshellState.kt" &&
! rg -q 'createFakeSubshellRepository|FakeSubshellRepository\(' \
    "$M/auth/AuthApplicationRuntime.kt" "$M/ui/SubshellProcessState.kt" "$M/ui/RememberSubshellState.kt" ||
    fail SUBSHELL_STATE_IS_BUILT_ONLY_BY_ITS_FACTORY
pass SUBSHELL_STATE_IS_BUILT_ONLY_BY_ITS_FACTORY

# From auth07a: Add account and reconnect ask the browser for a fresh
# sign-in window (the RedReader window clears Reddit's cookies, the Auth Tab
# is ephemeral; Step3FreshSignInWindowTest). MainActivity passes the flag
# through; its parameter defaults to false, so the compiler does not prove it.
grep -Fq 'freshAccountSession = launch.freshAccountSession && propagateFreshAccountSession,' "$M/MainActivity.kt" &&
rg -U -q '\{ authConnectViewModel\.beginConnect\(\) \},\s*propagateFreshAccountSession = true,' "$M/MainActivity.kt" &&
rg -U -q '\{ authConnectViewModel\.beginReconnect\(accountStableId\) \},\s*propagateFreshAccountSession = true,' "$M/MainActivity.kt" ||
    fail ACTIVITY_ASKS_FOR_A_FRESH_SIGN_IN_WINDOW
pass ACTIVITY_ASKS_FOR_A_FRESH_SIGN_IN_WINDOW


# From followpost01a1b: the activity hands following a post (send, retry,
# authorization) to the app.
grep -Fq 'app.subshell.prototype.ui.LocalPostFollowRuntime provides' "$M/MainActivity.kt" &&
grep -Fq 'onSetFollowed = authConnectViewModel::followPost,' "$M/MainActivity.kt" &&
grep -Fq 'onRetry = authConnectViewModel::retryPostFollow,' "$M/MainActivity.kt" &&
grep -Fq 'onAuthorize = ::launchPostFollowAuthorization,' "$M/MainActivity.kt" ||
    fail ACTIVITY_WIRES_POST_FOLLOW
pass ACTIVITY_WIRES_POST_FOLLOW

# From userstatus01a1: the activity hands the account's online visibility
# (read, change after confirmation, cancel) to Settings.
grep -Fq 'onOpen = authConnectViewModel::openOnlineVisibility,' "$M/MainActivity.kt" &&
grep -Fq 'onClose = authConnectViewModel::closeOnlineVisibility,' "$M/MainActivity.kt" &&
grep -Fq 'onRefresh = authConnectViewModel::refreshOnlineVisibility,' "$M/MainActivity.kt" &&
grep -Fq 'onPropose = authConnectViewModel::proposeOnlineVisibility,' "$M/MainActivity.kt" &&
grep -Fq 'onConfirm = authConnectViewModel::confirmOnlineVisibility,' "$M/MainActivity.kt" &&
grep -Fq 'onCancel = authConnectViewModel::cancelOnlineVisibilityConfirmation,' "$M/MainActivity.kt" ||
    fail ACTIVITY_WIRES_ONLINE_VISIBILITY
pass ACTIVITY_WIRES_ONLINE_VISIBILITY

# UPDATE07 (owner): every activity start, not just a cold start, asks for an
# update check once the last one is stale.
grep -F -A5 'override fun onStart() {' "$M/MainActivity.kt" |
    grep -Fq 'UpdateCheckScheduler.enqueueForegroundCheckIfStale(applicationContext)' ||
    fail ACTIVITY_CHECKS_FOR_UPDATES_ON_EVERY_START
pass ACTIVITY_CHECKS_FOR_UPDATES_ON_EVERY_START

# From net01 (owner): the shared HTTP client neither logs requests nor keeps a
# disk cache of Reddit's answers.
! rg -q 'HttpLoggingInterceptor|logging-interceptor|println\(|Log\.|Cache\(' \
    "$M/data/network/SubshellHttpClient.kt" ||
    fail HTTP_CLIENT_HAS_NO_LOGGING_OR_DISK_CACHE
pass HTTP_CLIENT_HAS_NO_LOGGING_OR_DISK_CACHE

# From followpost01a1a: a follow or unfollow is sent once; the writer has no
# wait or retry loop of its own.
! rg -n 'delay\(|retry\(|retryWhen|repeat\(|while[[:space:]]*\(' \
    "$M/data/auth/reddit/RedditPostFollower.kt" >/dev/null ||
    fail POST_FOLLOWER_HAS_NO_RETRY_LOOP
pass POST_FOLLOWER_HAS_NO_RETRY_LOOP

# From auth06b: the RedReader callback is a debug/compatibility route. The
# release manifest names no `redreader` route, and in the main manifest the
# callback activity itself is not exported — only its alias takes the
# browser's callback (Step3RedReaderCallbackManifestTest; the debug manifest
# exports the activity for its test scheme, so a unit test cannot see this).
test -f "$ROOT/app/src/release/AndroidManifest.xml" &&
! grep -qi 'redreader' "$ROOT/app/src/release/AndroidManifest.xml" &&
rg -U -q 'android:name="app\.subshell\.prototype\.auth\.AuthCallbackActivity"[^>]*android:exported="false"' \
    "$ROOT/app/src/main/AndroidManifest.xml" ||
    fail RELEASE_MANIFEST_HAS_ONLY_THE_PRODUCTION_CALLBACK
pass RELEASE_MANIFEST_HAS_ONLY_THE_PRODUCTION_CALLBACK

# From auth06b: the RedReader login WebView runs Reddit's login page only —
# no mixed content, no file or content access, no JavaScript bridge, no new
# windows.
LOGIN_WEBVIEW="$M/auth/RedReaderCompatibilityLoginActivity.kt"
test -f "$LOGIN_WEBVIEW" &&
grep -Fq 'settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW' "$LOGIN_WEBVIEW" &&
grep -Fq 'settings.allowFileAccess = false' "$LOGIN_WEBVIEW" &&
grep -Fq 'settings.allowContentAccess = false' "$LOGIN_WEBVIEW" &&
grep -Fq 'settings.setSupportMultipleWindows(false)' "$LOGIN_WEBVIEW" &&
! grep -q 'addJavascriptInterface' "$LOGIN_WEBVIEW" ||
    fail COMPATIBILITY_LOGIN_WEBVIEW_IS_LOCKED_DOWN
pass COMPATIBILITY_LOGIN_WEBVIEW_IS_LOCKED_DOWN

# From auth06b (BACKUP03B): Android's own backup takes nothing — backup is
# off, and both rule files deny every domain (cloud and device transfer).
BACKUP_RULES="$ROOT/app/src/main/res/xml/backup_rules.xml"
EXTRACTION_RULES="$ROOT/app/src/main/res/xml/data_extraction_rules.xml"
grep -Fq 'android:allowBackup="false"' "$ROOT/app/src/main/AndroidManifest.xml" &&
grep -Fq '<cloud-backup>' "$EXTRACTION_RULES" &&
grep -Fq '<device-transfer>' "$EXTRACTION_RULES" ||
    fail SYSTEM_BACKUP_DENIES_ALL
for domain in root file database sharedpref external device_root device_file device_database device_sharedpref; do
    grep -Fq "<exclude domain=\"$domain\" path=\".\" />" "$BACKUP_RULES" &&
    test "$(grep -Fc "<exclude domain=\"$domain\" path=\".\" />" "$EXTRACTION_RULES")" = 2 ||
        fail "SYSTEM_BACKUP_DENIES_ALL_$domain"
done
pass SYSTEM_BACKUP_DENIES_ALL

# From auth06b: the RedReader-compatible client ID is typed in by the reader
# and kept in no-backup storage; the build, the manifests and the code that
# configures it never carry one.
CLIENT_ID_FILES=(
    "$ROOT/app/build.gradle.kts"
    "$ROOT/app/src/main/AndroidManifest.xml"
    "$ROOT/app/src/release/AndroidManifest.xml"
    "$M/auth/AuthApplicationRuntime.kt"
    "$M/auth/RedditCompatibilityConfigurationController.kt"
    "$M/ui/screens/SettingsScreen.kt"
)
for file in "${CLIENT_ID_FILES[@]}"; do
    [[ -f "$file" ]] || fail "COMPATIBILITY_CLIENT_ID_FILE_MISSING_${file#"$ROOT"/}"
done
! rg -n 'AUTH06B_[A-Z0-9_]*CLIENT_ID|REDREADER_CLIENT_ID|buildConfigField\([^\n]*CLIENT_ID' \
    "${CLIENT_ID_FILES[@]}" >/dev/null ||
    fail COMPATIBILITY_CLIENT_ID_IS_NEVER_BUNDLED
pass COMPATIBILITY_CLIENT_ID_IS_NEVER_BUNDLED

# From auth06c: searching, community discovery and reading comments only read;
# their sources have no write transport (the writers live elsewhere and never
# retry: WriteTransportPolicyTest).
READ_LISTING_SOURCES=(
    "$M/data/network/reddit/RedditListingNetworkDataSource.kt"
    "$M/data/network/reddit/RedditPostFeedNetworkSource.kt"
    "$M/data/network/reddit/RedditCommunityDiscoveryNetworkDataSource.kt"
    "$M/data/auth/reddit/RedditCommunityDiscoveryLoader.kt"
    "$M/data/network/reddit/RedditCommentNetworkDataSource.kt"
    "$M/data/auth/reddit/RedditCommentLoader.kt"
)
for file in "${READ_LISTING_SOURCES[@]}"; do
    [[ -f "$file" ]] || fail "READ_LISTING_SOURCE_MISSING_${file#"$ROOT"/}"
done
! rg -n 'postJson|postForm|postEmptyForm|patchJson|nonRetryingWrite|RedditCommentSubmitter|RedditCommentSubmitNetworkDataSource' \
    "${READ_LISTING_SOURCES[@]}" >/dev/null ||
    fail READ_LISTING_SOURCES_HAVE_NO_WRITE_TRANSPORT
pass READ_LISTING_SOURCES_HAVE_NO_WRITE_TRANSPORT

# From quality01-p0: Media3 unstable API is opted in per declaration
# (@AndroidxOptIn(UnstableApi::class)); never for a whole file, and our own
# declarations are never marked @UnstableApi. Lint's UnsafeOptInUsageError
# (land lint profile) catches a missing opt-in, not an over-broad one.
! rg -q '@file:(androidx\.annotation\.|kotlin\.)?OptIn\(' "$M" &&
! rg -q '^\s*@(androidx\.media3\.common\.util\.)?UnstableApi\b' "$M" ||
    fail MEDIA3_OPT_IN_IS_NEVER_FILE_WIDE
pass MEDIA3_OPT_IN_IS_NEVER_FILE_WIDE

# From quality01d-final-lint: the release launcher icon is the adaptive icon
# in mipmap-anydpi-v26 (minSdk 26) with background, foreground and a
# monochrome layer (themed icons on Android 13+); a v33 copy would shadow it.
# Lint's MonochromeLauncherIcon cannot see it: lintDebug checks the debug
# manifest, which swaps in the lane's own icon.
LAUNCHER_ICON="$ROOT/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml"
[[ ! -d "$ROOT/app/src/main/res/mipmap-anydpi-v33" ]] &&
grep -Fq '<background android:drawable=' "$LAUNCHER_ICON" &&
grep -Fq '<foreground android:drawable=' "$LAUNCHER_ICON" &&
grep -Fq '<monochrome android:drawable=' "$LAUNCHER_ICON" ||
    fail LAUNCHER_ICON_IS_V26_WITH_A_MONOCHROME_LAYER
pass LAUNCHER_ICON_IS_V26_WITH_A_MONOCHROME_LAYER

# From auth07c1: a saved or removed RedReader Client ID reloads the auth host
# by restarting the activity task (no process kill). MainActivity, the route
# host and SettingsScreen pass the reload on through parameters that default
# to {}, so the compiler does not prove it; the Settings dialog cannot be
# driven under Robolectric (a text field in an AlertDialog never goes idle).
# The onboarding save is Step3RedditOnboardingComposeTest.
rg -U -q 'onRedditConfigurationChanged =\n\s*::reloadRedditConfigurationHost,' "$M/MainActivity.kt" &&
rg -U -q 'private fun reloadRedditConfigurationHost\(\) \{\n(\s*//[^\n]*\n)*\s*startActivity\(\n\s*Intent\.makeRestartActivityTask\(componentName\),\n\s*\)\n\s*\}' \
    "$M/MainActivity.kt" &&
! rg -q 'killProcess\(|System\.exit\(' "$M/MainActivity.kt" &&
rg -U -q 'onRedditConfigurationChanged =\n\s*onRedditConfigurationChanged,' "$M/ui/app/SecondaryRouteHost.kt" &&
rg -U -q '"Reddit connection configuration saved"\n\s*onRedditConfigurationChanged\(\)' "$M/ui/screens/SettingsScreen.kt" &&
rg -U -q '"Reddit connection configuration removed"\n\s*onRedditConfigurationChanged\(\)' "$M/ui/screens/SettingsScreen.kt" ||
    fail CONFIGURATION_CHANGE_RELOADS_THE_HOST
pass CONFIGURATION_CHANGE_RELOADS_THE_HOST

# From auth07c1: the user-supplied Client ID is never written to a log
# (Log.*, print/println or the app's debug-log helpers), anywhere in main.
! rg -q '(^|[^A-Za-z0-9_])(Log\.[A-Za-z]+\([^)]*clientId|println\([^)]*clientId|print\([^)]*clientId|[A-Za-z]*DebugLog\([^)]*clientId)' \
    "$M" ||
    fail CLIENT_ID_IS_NEVER_LOGGED
pass CLIENT_ID_IS_NEVER_LOGGED

# From auth07c2: the onboarding screen reaches the Client ID only through
# RedditCompatibilityConfigurationController; it never touches auth storage,
# tokens or the OAuth identity itself.
! rg -q 'app\.subshell\.prototype\.data\.auth|OAuthTokenSet|accessToken|refreshToken|RedditOAuth\.|EncryptedAuthSessionStore' \
    "$M/ui/screens/RedditOnboardingScreen.kt" ||
    fail ONBOARDING_HAS_NO_AUTH_STORAGE_OR_TOKENS
pass ONBOARDING_HAS_NO_AUTH_STORAGE_OR_TOKENS

# From auth01/02/03 (owner approved, auth storage): the backup package never
# names the auth layer or a token; a backup can never carry a sign-in. (The
# ui and data/cache halves of this rule stay in check-source-contract.sh's
# AUTH01–AUTH03 blocks.)
! rg -n --glob '*.kt' 'data\.auth|StoredOAuthSession|OAuthTokenSet|StoredAuthAccountRe(cord|gistry)|accessToken|refreshToken|authorizationCode' \
    "$M/backup" >/dev/null ||
    fail AUTH_NEVER_REACHES_BACKUP
pass AUTH_NEVER_REACHES_BACKUP

# From auth01: sign-in uses the Auth Tab or the scoped RedReader
# compatibility window only; the launcher, the callback activity and the auth
# data layer never open a WebView of their own.
! rg -n 'WebView|android\.webkit' \
    "$M/auth/AuthBrowserLauncher.kt" \
    "$M/auth/AuthCallbackActivity.kt" \
    "$M/data/auth" >/dev/null ||
    fail SIGN_IN_HAS_NO_WEBVIEW_OUTSIDE_COMPATIBILITY
pass SIGN_IN_HAS_NO_WEBVIEW_OUTSIDE_COMPATIBILITY

# From auth01 (owner approved, auth storage): the sign-in vault is AES-GCM
# with a 256-bit Android Keystore key (randomized), written atomically to the
# no-backup folder, never to SharedPreferences or a database. Robolectric has
# no Android Keystore, so no test can drive the real cipher;
# Step3AuthVaultFilesTest covers the folder and file names.
grep -Fq 'ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"' "$M/data/auth/AuthStorage.kt" &&
grep -Fq 'AUTH_CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"' "$M/data/auth/AuthStorage.kt" &&
grep -Fq '.setKeySize(256)' "$M/data/auth/AuthStorage.kt" &&
grep -Fq '.setRandomizedEncryptionRequired(true)' "$M/data/auth/AuthStorage.kt" &&
grep -Fq 'File(appContext.noBackupFilesDir, AUTH_DIRECTORY_NAME)' "$M/data/auth/AuthStorage.kt" &&
grep -Fq 'AtomicFile(File(targetDirectory, name))' "$M/data/auth/AuthStorage.kt" &&
! rg -n 'SharedPreferences|Room|database' "$M/data/auth/AuthStorage.kt" >/dev/null ||
    fail AUTH_VAULT_IS_KEYSTORE_AES_GCM_AND_NOT_BACKED_UP
pass AUTH_VAULT_IS_KEYSTORE_AES_GCM_AND_NOT_BACKED_UP

# From auth01/02: the token client, the session manager, the identity
# client and the account coordinator never log or print a token, and the
# identity client keeps no token in a field (each call uses its own).
! rg -n 'println|Log\.|accessToken.*toString|refreshToken.*toString' \
    "$M/data/auth/OAuthHttpClient.kt" \
    "$M/data/auth/OAuthSessionManager.kt" \
    "$M/data/auth/reddit/RedditAccountIdentityClient.kt" \
    "$M/data/auth/AuthAccountSessionCoordinator.kt" >/dev/null &&
! rg -n '(private|internal)? *var .*[Tt]oken' "$M/data/auth/reddit/RedditAccountIdentityClient.kt" >/dev/null ||
    fail AUTH_NEVER_LOGS_OR_KEEPS_TOKENS
pass AUTH_NEVER_LOGS_OR_KEEPS_TOKENS

# From auth01/03: the activities never see a token or an authorization code;
# the callback activity hands the URI to OAuthCallbackProcessor and the
# result to the connect flow as a token-free result string.
! rg -n 'accessToken|refreshToken|authorizationCode' \
    "$M/MainActivity.kt" \
    "$M/auth/AuthCallbackActivity.kt" \
    "$M/auth/RedReaderCompatibilityLoginActivity.kt" >/dev/null ||
    fail ACTIVITIES_NEVER_HANDLE_TOKENS
pass ACTIVITIES_NEVER_HANDLE_TOKENS

# From auth01/03: no OAuth client ID or client secret value is committed to
# the Reddit adapter, the build script or the runtime factory (values come
# from the local selection or the external configuration; auth04/auth05d2
# keep the build-time and signing halves).
! rg -n 'clientId = "[^"$]+' "$M/data/auth/reddit/RedditOAuth.kt" >/dev/null &&
! rg -n 'auth03-test-client|clientSecret.*[^"]".+"' \
    "$ROOT/app/build.gradle.kts" \
    "$M/auth/AuthApplicationRuntime.kt" >/dev/null ||
    fail NO_OAUTH_CLIENT_VALUES_IN_SOURCE
pass NO_OAUTH_CLIENT_VALUES_IN_SOURCE

# From #229: the activity hands the composer's mention search (its own
# state and the search) to SubshellApp, and the post route asks it for the
# active account; every link has a default, so the compiler does not prove it.
grep -Fq 'authConnectViewModel.redditMentionSearchUiState.collectAsState()' "$M/MainActivity.kt" &&
grep -Fq 'onSearchMentions = authConnectViewModel::loadRedditMentionSearch,' "$M/MainActivity.kt" &&
grep -Fq 'mentionSearchState = redditCommunity.redditMentionSearchState,' "$M/ui/app/PostRouteHost.kt" &&
grep -Fq 'redditCommunity.onSearchMentions(state.activeAccountId, query)' "$M/ui/app/PostRouteHost.kt" ||
    fail ACTIVITY_WIRES_MENTION_SEARCH
pass ACTIVITY_WIRES_MENTION_SEARCH

echo 'RESULT=SUBSHELL_BOUNDARY_GUARDS_CONTRACT_PASS'
