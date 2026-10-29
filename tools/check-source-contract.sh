#!/usr/bin/env bash

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# VIDEO00 (#227): the video player moved verbatim from MediaComponents.kt into
# VideoPlayer.kt, VideoPlaybackPanels.kt and FullscreenVideoGestures.kt; media pins
# read the four files together, so negative pins cover all four.
FAIL=0

# SUBSHELL_WORKFLOW02A_DIRECT_SOURCE_CONTRACT_MEMO_BOOTSTRAP_V1
SOURCE_CONTRACT_MEMO_OWNER=0
SOURCE_CONTRACT_MEMO_TMP=''

source_contract_memo_cleanup() {
    if [[ "$SOURCE_CONTRACT_MEMO_OWNER" -eq 1 &&
          -n "$SOURCE_CONTRACT_MEMO_TMP" ]]; then
        rm -rf "$SOURCE_CONTRACT_MEMO_TMP"
    fi
}

source_contract_memo_bootstrap() {
    local memo_root="${SUBSHELL_CONTRACT_MEMO_ROOT:-}"
    local memo_dir="${SUBSHELL_CONTRACT_MEMO_DIR:-}"
    local real_bash="${SUBSHELL_CONTRACT_REAL_BASH:-}"
    local memo_wrapper="$ROOT/tools/contract-runtime/bash"
    local memo_tmp

    if [[ -n "$memo_root" || -n "$memo_dir" || -n "$real_bash" ]]; then
        if [[ -n "$memo_root" &&
              -n "$memo_dir" &&
              -n "$real_bash" &&
              "$memo_root" == "$ROOT" &&
              -d "$memo_dir" &&
              -x "$real_bash" &&
              ":$PATH:" == *":$ROOT/tools/contract-runtime:"* ]]; then
            return 0
        fi

        printf '%s\n' \
            'FAIL=SOURCE_CONTRACT_MEMO_ENV_INCOMPLETE_OR_FOREIGN'
        return 1
    fi

    test -x "$memo_wrapper" || {
        printf '%s\n' \
            'FAIL=SOURCE_CONTRACT_MEMO_WRAPPER_MISSING'
        return 1
    }

    real_bash="$(command -v bash 2>/dev/null || true)"
    [[ -n "$real_bash" && -x "$real_bash" ]] || {
        printf '%s\n' \
            'FAIL=SOURCE_CONTRACT_REAL_BASH_UNAVAILABLE'
        return 1
    }

    memo_tmp="$(
        mktemp -d \
            "${TMPDIR:-/tmp}/subshell-source-contract-memo.XXXXXX"
    )" || {
        printf '%s\n' \
            'FAIL=SOURCE_CONTRACT_MEMO_TMPDIR_CREATE_FAILED'
        return 1
    }

    mkdir -p "$memo_tmp/memo" || {
        rm -rf "$memo_tmp"
        printf '%s\n' \
            'FAIL=SOURCE_CONTRACT_MEMO_DIR_CREATE_FAILED'
        return 1
    }

    : > "$memo_tmp/memo-events.tsv"

    export SUBSHELL_CONTRACT_REAL_BASH="$real_bash"
    export SUBSHELL_CONTRACT_MEMO_ROOT="$ROOT"
    export SUBSHELL_CONTRACT_MEMO_DIR="$memo_tmp/memo"
    export SUBSHELL_CONTRACT_MEMO_EVENTS="$memo_tmp/memo-events.tsv"
    export PATH="$ROOT/tools/contract-runtime:$PATH"
    hash -r

    SOURCE_CONTRACT_MEMO_OWNER=1
    SOURCE_CONTRACT_MEMO_TMP="$memo_tmp"
    trap source_contract_memo_cleanup EXIT
}

source_contract_memo_report() {
    local hits misses failures

    [[ "$SOURCE_CONTRACT_MEMO_OWNER" -eq 1 ]] || return 0

    hits="$(
        awk -F '\t' \
            '$1 == "HIT" {n++} END {print n+0}' \
            "$SUBSHELL_CONTRACT_MEMO_EVENTS"
    )"
    misses="$(
        awk -F '\t' \
            '$1 == "MISS" {n++} END {print n+0}' \
            "$SUBSHELL_CONTRACT_MEMO_EVENTS"
    )"
    failures="$(
        awk -F '\t' \
            '$1 == "FAIL_NOT_CACHED" {n++} END {print n+0}' \
            "$SUBSHELL_CONTRACT_MEMO_EVENTS"
    )"

    echo "SOURCE_CONTRACT_MEMO_HITS=$hits"
    echo "SOURCE_CONTRACT_MEMO_MISSES=$misses"
    echo "SOURCE_CONTRACT_MEMO_FAILURES_NOT_CACHED=$failures"
    echo 'SOURCE_CONTRACT_MEMO_POLICY=SUCCESS_ONLY_PER_DIRECT_GATE'
}

source_contract_memo_bootstrap || exit 1

pass() { printf 'PASS=%s\n' "$1"; }
fail() { printf 'FAIL=%s\n' "$1"; FAIL=1; }

settings_navigation_labels_ui_present() {
    rg -Fq 'NavigationLabelMode.entries.forEach' "$SETTINGS" ||
        {
            rg -Fq 'options = NavigationLabelMode.entries' "$SETTINGS" &&
            rg -Fq 'choiceDialogKey = "navigation-labels"' "$SETTINGS"
        }
}

settings_interaction_side_product_copy_present() {
    rg -Fq 'Default used by controls set to Follow and floating actions' "$SETTINGS" ||
        {
            rg -Fq 'description = "Preferred side for thumb controls and floating actions"' "$SETTINGS" &&
            rg -Fq 'NavigationSettingsPreview(' "$SETTINGS"
        }
}

settings_comment_action_ui_present() {
    rg -Fq 'CommentActionPreference.entries.forEach' "$SETTINGS" ||
        {
            rg -Fq '"comment-actions" -> SettingsSingleChoiceDialog(' "$SETTINGS" &&
            rg -Fq 'options = CommentActionPreference.entries' "$SETTINGS" &&
            rg -Fq 'state.updateCommentActionPreference(preference)' "$SETTINGS"
        }
}

settings_comment_follow_semantics_present() {
    rg -Fq 'Follow uses Interaction side above' "$SETTINGS" ||
        {
            rg -Fq '"comment-actions" -> SettingsSingleChoiceDialog(' "$SETTINGS" &&
            rg -Fq 'options = CommentActionPreference.entries' "$SETTINGS" &&
            rg -Fq 'value = if (state.commentActionPreference == CommentActionPreference.FOLLOW)' "$SETTINGS" &&
            rg -Fq 'state.interactionSide.followLabel()' "$SETTINGS"
        }
}

run_required_contract() {
    local script="$1"
    local success_label="${2:-}"
    local failure_label
    failure_label="CHILD_CONTRACT_$(basename "$script" .sh | tr '[:lower:]-' '[:upper:]_')"

    printf 'RUNNING_SUBSHELL_CONTRACT=%s\n' "$script"
    if bash "$ROOT/$script"; then
        if test -n "$success_label"; then
            pass "$success_label"
        fi
    else
        fail "$failure_label"
    fi
}

require_file() {
    if test -f "$ROOT/$1"; then
        pass "FILE_$1"
    else
        fail "MISSING_$1"
    fi
}

for FILE in \
    app/src/main/java/app/subshell/prototype/MainActivity.kt \
    app/src/main/java/app/subshell/prototype/media/VideoPlaybackViewModel.kt \
    app/src/main/java/app/subshell/prototype/media/VideoMediaCache.kt \
    app/src/main/java/app/subshell/prototype/media/VideoNetworkPolicy.kt \
    app/src/main/java/app/subshell/prototype/media/RemoteImageLoader.kt \
    app/src/main/java/app/subshell/prototype/media/VideoTrackDiagnostics.kt \
    app/src/main/java/app/subshell/prototype/media/VideoTrackSelection.kt \
    app/src/main/java/app/subshell/prototype/model/Models.kt \
    app/src/main/java/app/subshell/prototype/data/SampleData.kt \
    app/src/main/java/app/subshell/prototype/data/SubshellRepository.kt \
    app/src/main/java/app/subshell/prototype/data/RepositoryPaging.kt \
    app/src/main/java/app/subshell/prototype/data/FakeSubshellRepository.kt \
    app/src/main/java/app/subshell/prototype/data/LocalMutationReplay.kt \
    app/src/main/java/app/subshell/prototype/data/error/FailureDetails.kt \
    app/src/main/java/app/subshell/prototype/data/error/FailureDetailItems.kt \
    app/src/main/java/app/subshell/prototype/data/error/RedditWriteFailureDetails.kt \
    app/src/main/java/app/subshell/prototype/backup/BackupFailures.kt \
    app/src/main/java/app/subshell/prototype/data/network/NetworkModels.kt \
    app/src/main/java/app/subshell/prototype/data/network/NetworkEndpoint.kt \
    app/src/main/java/app/subshell/prototype/data/network/SubshellHttpClient.kt \
    app/src/main/java/app/subshell/prototype/data/network/reddit/RedditCommentDtos.kt \
    app/src/main/java/app/subshell/prototype/data/network/reddit/RedditCommentNetworkDataSource.kt \
    app/src/main/java/app/subshell/prototype/data/network/reddit/RedditCommentSubmitDtos.kt \
    app/src/main/java/app/subshell/prototype/data/network/reddit/RedditCommentSubmitNetworkDataSource.kt \
    app/src/main/java/app/subshell/prototype/data/network/reddit/RedditMediaUploadNetworkDataSource.kt \
    app/src/main/java/app/subshell/prototype/data/network/reddit/RedditListingDtos.kt \
    app/src/main/java/app/subshell/prototype/data/network/reddit/RedditListingNetworkDataSource.kt \
    app/src/main/java/app/subshell/prototype/data/network/reddit/RedditSubscriptionNetworkDataSource.kt \
    app/src/main/java/app/subshell/prototype/data/network/reddit/RedditCommunityDiscoveryNetworkDataSource.kt \
    app/src/main/java/app/subshell/prototype/data/network/reddit/RedditPeopleSearchNetworkDataSource.kt \
    app/src/main/java/app/subshell/prototype/data/network/reddit/RedditPostFeedNetworkSource.kt \
    app/src/main/java/app/subshell/prototype/data/network/reddit/RedditPostVoteNetworkDataSource.kt \
    app/src/main/java/app/subshell/prototype/data/network/reddit/RedditPostSaveNetworkDataSource.kt \
    app/src/main/java/app/subshell/prototype/data/auth/AuthModels.kt \
    app/src/main/java/app/subshell/prototype/data/auth/AuthFailureDetails.kt \
    app/src/main/java/app/subshell/prototype/data/auth/AuthStorage.kt \
    app/src/main/java/app/subshell/prototype/data/auth/OAuthConfiguration.kt \
    app/src/main/java/app/subshell/prototype/data/auth/OAuthHttpClient.kt \
    app/src/main/java/app/subshell/prototype/data/auth/OAuthSessionManager.kt \
    app/src/main/java/app/subshell/prototype/data/auth/reddit/RedditOAuth.kt \
    app/src/main/java/app/subshell/prototype/data/auth/reddit/RedditCommentSubmitter.kt \
    app/src/main/java/app/subshell/prototype/data/auth/reddit/RedditPostVoter.kt \
    app/src/main/java/app/subshell/prototype/data/auth/reddit/RedditPostSaver.kt \
    app/src/main/java/app/subshell/prototype/data/auth/reddit/RedditMediaUploadSource.kt \
    app/src/main/java/app/subshell/prototype/data/account/AccountSessionDirectory.kt \
    app/src/main/java/app/subshell/prototype/data/account/ConnectAccountFlow.kt \
    app/src/main/java/app/subshell/prototype/data/account/CommentWriteFlow.kt \
    app/src/main/java/app/subshell/prototype/data/account/PostVoteFlow.kt \
    app/src/main/java/app/subshell/prototype/data/account/PostSaveFlow.kt \
    app/src/main/java/app/subshell/prototype/data/auth/AuthConnectFlowController.kt \
    app/src/main/java/app/subshell/prototype/data/auth/OAuthIdentityClient.kt \
    app/src/main/java/app/subshell/prototype/data/auth/AuthAccountSessionCoordinator.kt \
    app/src/main/java/app/subshell/prototype/data/auth/AuthCommentWriteCapability.kt \
    app/src/main/java/app/subshell/prototype/data/auth/AuthenticatedRedditBootstrap.kt \
    app/src/main/java/app/subshell/prototype/data/auth/AuthenticatedRedditSubscriptions.kt \
    app/src/main/java/app/subshell/prototype/data/auth/AuthenticatedRedditCommunityDiscovery.kt \
    app/src/main/java/app/subshell/prototype/data/auth/reddit/RedditAccountIdentityClient.kt \
    app/src/main/java/app/subshell/prototype/data/auth/reddit/RedditCommentLoader.kt \
    app/src/main/java/app/subshell/prototype/data/auth/reddit/RedditSubscriptionsLoader.kt \
    app/src/main/java/app/subshell/prototype/data/auth/reddit/RedditCommunityDiscoveryLoader.kt \
    app/src/main/java/app/subshell/prototype/data/auth/reddit/RedditBootstrapper.kt \
    app/src/main/java/app/subshell/prototype/auth/AuthBrowserLauncher.kt \
    app/src/main/java/app/subshell/prototype/auth/RedReaderCompatibilityLoginActivity.kt \
    app/src/main/java/app/subshell/prototype/auth/AuthCallbackActivity.kt \
    app/src/main/java/app/subshell/prototype/auth/AuthApplicationRuntime.kt \
    app/src/main/java/app/subshell/prototype/auth/RedditCompatibilityConfigurationController.kt \
    app/src/main/java/app/subshell/prototype/auth/AuthConnectViewModel.kt \
    app/src/main/java/app/subshell/prototype/auth/CommentWriteCoordinator.kt \
    app/src/main/java/app/subshell/prototype/auth/PostVoteCoordinator.kt \
    app/src/main/java/app/subshell/prototype/auth/PostSaveCoordinator.kt \
    app/src/main/AndroidManifest.xml \
    app/src/release/AndroidManifest.xml \
    app/src/main/java/app/subshell/prototype/data/sync/PostFeedSync.kt \
    app/src/main/java/app/subshell/prototype/data/PersistentSubshellRepository.kt \
    app/src/main/java/app/subshell/prototype/data/cache/SubshellCacheCodec.kt \
    app/src/main/java/app/subshell/prototype/data/cache/SubshellCacheDatabase.kt \
    app/src/main/java/app/subshell/prototype/data/cache/RoomSubshellCacheStore.kt \
    app/src/main/java/app/subshell/prototype/ui/SubshellApp.kt \
    app/src/main/java/app/subshell/prototype/ui/DrawerDynamicGroups.kt \
    app/src/main/java/app/subshell/prototype/ui/SubshellState.kt \
    app/src/main/java/app/subshell/prototype/ui/SearchFoundation.kt \
    app/src/main/java/app/subshell/prototype/ui/FeedPostRoute.kt \
    app/src/main/java/app/subshell/prototype/ui/RedditTarget.kt \
    app/src/main/java/app/subshell/prototype/ui/RedditLinkHandling.kt \
    app/src/main/java/app/subshell/prototype/ui/SubshellRouteStack.kt \
    app/src/main/java/app/subshell/prototype/ui/FeedScrollAnchor.kt \
    app/src/main/java/app/subshell/prototype/ui/FeedPaging.kt \
    app/src/main/java/app/subshell/prototype/ui/SubshellProcessState.kt \
    app/src/main/java/app/subshell/prototype/ui/SubshellSettings.kt \
    app/src/main/java/app/subshell/prototype/ui/PostSystemActions.kt \
    app/src/main/java/app/subshell/prototype/ui/MediaPostActions.kt \
    app/src/main/java/app/subshell/prototype/ui/VideoDownloadUi.kt \
    app/src/main/java/app/subshell/prototype/ui/CodeFont.kt \
    app/src/main/java/app/subshell/prototype/ui/PostComposer.kt \
    app/src/main/java/app/subshell/prototype/ui/CommentComposerState.kt \
    app/src/main/java/app/subshell/prototype/ui/theme/Theme.kt \
    app/src/main/java/app/subshell/prototype/ui/CommunityPostSorting.kt \
    app/src/main/java/app/subshell/prototype/backup/SettingsBackup.kt \
    app/src/main/java/app/subshell/prototype/backup/SettingsBackupStore.kt \
    app/src/main/java/app/subshell/prototype/backup/SettingsBackupVault.kt \
    app/src/main/java/app/subshell/prototype/backup/SettingsBackupRepository.kt \
    app/src/main/java/app/subshell/prototype/ui/error/ErrorPresentation.kt \
    app/src/main/java/app/subshell/prototype/ui/error/RedditReadErrorPresentations.kt \
    app/src/main/java/app/subshell/prototype/ui/error/MediaErrorPresentations.kt \
    app/src/main/java/app/subshell/prototype/ui/error/BackupErrorPresentations.kt \
    app/src/main/java/app/subshell/prototype/ui/error/RedditWriteErrorPresentations.kt \
    app/src/main/java/app/subshell/prototype/ui/error/ViewportErrorHost.kt \
    app/src/main/java/app/subshell/prototype/ui/components/Common.kt \
    app/src/main/java/app/subshell/prototype/ui/components/ErrorNotice.kt \
    app/src/main/java/app/subshell/prototype/ui/components/ViewportErrorTray.kt \
    app/src/main/java/app/subshell/prototype/ui/components/PagingComponents.kt \
    app/src/main/java/app/subshell/prototype/ui/components/MediaComponents.kt \
    app/src/main/java/app/subshell/prototype/ui/components/MarkdownText.kt \
    app/src/main/java/app/subshell/prototype/ui/screens/CreatePostScreen.kt \
    app/src/main/java/app/subshell/prototype/ui/screens/PostFlairPickerSheet.kt \
    app/src/main/java/app/subshell/prototype/ui/screens/CommentComposerScreen.kt \
    app/src/main/java/app/subshell/prototype/ui/screens/HomeScreen.kt \
    app/src/main/java/app/subshell/prototype/ui/screens/SearchScreen.kt \
    app/src/main/java/app/subshell/prototype/ui/screens/MediaViewerScreen.kt \
    app/src/main/java/app/subshell/prototype/ui/screens/PostScreen.kt \
    app/src/main/java/app/subshell/prototype/ui/screens/CommunityScreen.kt \
    app/src/main/java/app/subshell/prototype/ui/screens/SettingsScreen.kt \
    app/src/main/java/app/subshell/prototype/ui/screens/BackupRestoreScreen.kt \
    app/src/main/java/app/subshell/prototype/ui/screens/BackupFolderOnboarding.kt \
    app/src/main/res/drawable/ic_subshell_in_app.xml \
    app/src/main/res/drawable/ic_subshell_foreground.xml \
    app/src/main/res/drawable/ic_subshell_monochrome.xml \
    app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml \
    app/src/main/res/values/colors.xml \
    app/src/main/res/xml/backup_rules.xml \
    app/src/main/res/xml/data_extraction_rules.xml \
    app/src/main/res/xml/subshell_network_security_config.xml \
    app/src/main/res/xml/subshell_file_paths.xml \
    app/src/main/res/font/jetbrains_mono_regular.ttf \
    app/src/main/res/font/iosevka_compact_regular.ttf \
    app/src/main/res/font/recursive_mono_casual_regular.ttf \
    third_party/fonts/JETBRAINS_MONO_OFL.txt \
    third_party/fonts/IOSEVKA_OFL.txt \
    third_party/fonts/RECURSIVE_OFL.txt \
    app/src/test/java/app/subshell/prototype/SampleDataContractTest.kt \
    app/src/test/java/app/subshell/prototype/data/FakeSubshellRepositoryContractTest.kt \
    app/src/test/java/app/subshell/prototype/data/RepositoryPagingPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/LocalMutationReplayPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/error/Error01a1ErrorPresentationPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/error/Error01a2CoreRedditReadErrorPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/error/Error01a3MediaBackupErrorPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/error/Error01a4RedditWriteErrorPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/error/Error01a5FinalErrorAuditPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/auth/reddit/Vote01PostVotePolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/auth/reddit/HomeAction01PostActionsPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/screens/MediaViewerGesturePolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/error/Error01a22ViewportErrorHostPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/network/NetworkPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/network/reddit/RedditCommunityDiscoveryNetworkDataSourceTest.kt \
    app/src/test/java/app/subshell/prototype/data/network/reddit/RedditPeopleSearchNetworkDataSourceTest.kt \
    app/src/test/java/app/subshell/prototype/data/network/reddit/RedditCommentSubmitPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/account/CommentWriteFlowPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/auth/AuthPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/AccountSessionIntegrationPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/ActiveAccountSelectionPersistencePolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/Auth03ConnectAccountPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/auth/Auth06bRedReaderCompatibilityPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/auth/Auth06cLiveReadOnlyPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/auth/reddit/RedditCommunityDiscoveryLoaderTest.kt \
    app/src/test/java/app/subshell/prototype/data/auth/reddit/RedditPeopleSearchLoaderTest.kt \
    app/src/test/java/app/subshell/prototype/data/auth/reddit/RedditComposerPolicyLoaderTest.kt \
    app/src/test/java/app/subshell/prototype/data/auth/reddit/RedditMediaUploadSourcePolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/network/reddit/RedditMediaUploadNetworkPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/sync/PostFeedSyncPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/data/cache/SubshellCacheCodecPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/SettingsBackupContractTest.kt \
    app/src/test/java/app/subshell/prototype/PostComposerContractTest.kt \
    app/src/test/java/app/subshell/prototype/ui/CommentComposerStatePolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/NavigationLifecycleStateTest.kt \
    app/src/test/java/app/subshell/prototype/ui/FeedPostRoutePolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/RedditTargetResolverTest.kt \
    app/src/test/java/app/subshell/prototype/ui/RedditLinkHandlingPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/Search01FoundationPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/Search01StateIntegrationPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/Search01BWorkspacePolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/Search01CFederatedProvidersPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/Search01C2LivePeoplePolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/SubshellRouteStackPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/FeedScrollAnchorPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/SubshellProcessStatePolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/DrawerDynamicGroupsPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/SubsExploreShortcutPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/CommunityFavoritesPersistenceTest.kt \
    app/src/test/java/app/subshell/prototype/media/VideoPlaybackOwnershipPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/media/VideoMediaCachePolicyTest.kt \
    app/src/test/java/app/subshell/prototype/media/VideoNetworkPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/media/VideoTrackDiagnosticsTest.kt \
    app/src/test/java/app/subshell/prototype/media/VideoTrackSelectionTest.kt \
    app/src/debug/assets/media03/subtitles/video-lab-a-en.vtt \
    app/src/debug/assets/media03/subtitles/video-lab-a-nb.vtt \
    app/src/test/java/app/subshell/prototype/ui/screens/Subs04cCommunityNavigationDiscoveryPolicyTest.kt \
    app/src/test/java/app/subshell/prototype/ui/screens/Subs04c2CommunitiesTerminologyDrawerIaPolicyTest.kt \
    tools/check-auth04-contract.sh \
    tools/verify-auth04-app-link-manifest.py \
    tools/generate-auth04-assetlinks.py \
    tools/check-auth05b-contract.sh \
    tools/validate-auth05-production-identity.py \
    tools/check-auth05c-contract.sh \
    tools/verify-auth05c-application-id.py \
    tools/check-auth05d2-contract.sh \
    tools/verify-auth05d2-release-signing.py \
    tools/check-auth05e2-contract.sh \
    tools/verify-auth05e2-production-app-link.py \
    app/src/main/res/drawable-nodpi/gif_high_quality.gif \
    app/src/main/res/drawable-nodpi/gif_homemade.gif \
    app/src/main/res/drawable-nodpi/gif_pixel_art.gif \
    app/src/main/res/drawable-nodpi/pixel_gallery_campfire.gif \
    app/src/main/res/drawable-nodpi/pixel_gallery_terminal.gif \
    app/src/main/res/drawable-nodpi/pixel_gallery_cat.gif \
    app/src/main/res/drawable-nodpi/pixel_gallery_moon.gif
do
    require_file "$FILE"
done

# LOWUI4 (OCB-225) and LOWUI4C (OCB-108): the Library screen and the whole
# video download system are retired (owner-approved); Settings can only delete
# what an older build downloaded. The dead predecessor branches went with
# step 3 (2026-10-14).
if [[ ! -f "$ROOT/app/src/main/java/app/subshell/prototype/ui/screens/LibraryScreen.kt" ]] &&
   rg -Fq 'LOWUI4_LIBRARY_RETIRED_V1' "$ROOT/app/src/main/java/app/subshell/prototype/ui/SubshellState.kt" &&
   ! rg -q 'SecondaryDestination\.LIBRARY|fun LibraryScreen\(' "$ROOT/app/src/main/java"; then
    pass LIBRARY_STAYS_RETIRED
else
    fail LIBRARY_STAYS_RETIRED
fi

if [[ ! -f "$ROOT/app/src/main/java/app/subshell/prototype/media/VideoDownloadStore.kt" &&
      ! -f "$ROOT/app/src/main/java/app/subshell/prototype/media/VideoDownloadQueue.kt" ]] &&
   ! rg -q 'VideoDownloadWorker|VideoDownloadStore|resolvePlaybackSource' "$ROOT/app/src/main/java" &&
   rg -Fq 'LOWUI4C_LEGACY_DOWNLOADS_CAN_ONLY_BE_DELETED_V1' "$ROOT/app/src/main/java/app/subshell/prototype/media/LegacyVideoDownloads.kt" &&
   rg -Fq 'fun deleteAll(): LegacyVideoDownloadDeleteResult' "$ROOT/app/src/main/java/app/subshell/prototype/media/LegacyVideoDownloads.kt" &&
   rg -Fq 'LOWUI4C_VIDEO_STREAMS_ONLY_V1' "$ROOT/app/src/main/java/app/subshell/prototype/media/VideoPlayback.kt" &&
   rg -Fq 'title = { Text("Delete all downloaded videos?") }' "$ROOT/app/src/main/java/app/subshell/prototype/ui/screens/SettingsScreen.kt" &&
   rg -Fq 'oldDownloadsAreCountedAndDeleted' "$ROOT/app/src/test/java/app/subshell/prototype/media/LowUi4cLegacyVideoDownloadsTest.kt"; then
    pass MEDIA05_DOWNLOAD_SYSTEM_RETIRED_LEGACY_DELETE_KEPT
else
    fail MEDIA05_DOWNLOAD_SYSTEM_RETIRED_LEGACY_DELETE_KEPT
fi

: > /tmp/subshell-v5-source-boundary.txt
rg -n 'com\.rubenmayayo|\bBoost\b|\bMorphe\b' \
    "$ROOT/app/src/main/java" "$ROOT/app/src/main/res/values" \
    >> /tmp/subshell-v5-source-boundary.txt || true
rg -n 'https?://' "$ROOT/app/src/main/res/values" \
    >> /tmp/subshell-v5-source-boundary.txt || true
if test -s /tmp/subshell-v5-source-boundary.txt; then
    fail SOURCE_ISOLATION
    sed -n '1,40p' /tmp/subshell-v5-source-boundary.txt
else
    pass SOURCE_ISOLATION
fi

for DESTINATION in HOME SUBS INBOX YOU; do
    if rg -q "$DESTINATION" "$ROOT/app/src/main/java"; then
        pass "DESTINATION_$DESTINATION"
    else
        fail "DESTINATION_$DESTINATION"
    fi
done

DATA="$ROOT/app/src/main/java/app/subshell/prototype/data/SampleData.kt"
MODELS="$ROOT/app/src/main/java/app/subshell/prototype/model/Models.kt"
SETTINGS="$ROOT/app/src/main/java/app/subshell/prototype/ui/screens/SettingsScreen.kt"
BACKUP_SCREEN="$ROOT/app/src/main/java/app/subshell/prototype/ui/screens/BackupRestoreScreen.kt"
BACKUP_STORE="$ROOT/app/src/main/java/app/subshell/prototype/backup/SettingsBackupStore.kt"
BACKUP_MODEL="$ROOT/app/src/main/java/app/subshell/prototype/backup/SettingsBackup.kt"
BACKUP_REPOSITORY="$ROOT/app/src/main/java/app/subshell/prototype/backup/SettingsBackupRepository.kt"
BACKUP_VAULT="$ROOT/app/src/main/java/app/subshell/prototype/backup/SettingsBackupVault.kt"
BACKUP_ONBOARDING="$ROOT/app/src/main/java/app/subshell/prototype/ui/screens/BackupFolderOnboarding.kt"
SETTINGS_MODEL="$ROOT/app/src/main/java/app/subshell/prototype/ui/SubshellSettings.kt"
HOME_SCREEN_FILE="$ROOT/app/src/main/java/app/subshell/prototype/ui/screens/HomeScreen.kt"

# ISSUE270: the screen's actions are "Back up now" and "Restore from file"
# (were "Save current setup" and "Import backup file").
if rg -Fq 'title = "Back up now"' "$BACKUP_SCREEN" &&
   rg -Fq 'title = "Restore from file"' "$BACKUP_SCREEN" &&
   rg -Fq 'Restore' "$BACKUP_SCREEN" &&
   rg -Fq 'Before restoring' "$BACKUP_STORE" &&
   rg -Fq 'RECENT_CHANGE_SNAPSHOT_LIMIT = 20' "$BACKUP_MODEL" &&
   rg -Fq 'DAILY_HISTORY_DAYS = 30' "$BACKUP_MODEL" &&
   rg -Fq 'WEEKLY_HISTORY_DAYS = 365' "$BACKUP_MODEL" &&
   rg -Fq 'SAFETY_SNAPSHOT_LIMIT = 10' "$BACKUP_MODEL" &&
   rg -Fq 'ActivityResultContracts.OpenDocumentTree' "$BACKUP_SCREEN" "$BACKUP_ONBOARDING" &&
   rg -Fq 'takePersistableUriPermission' "$BACKUP_REPOSITORY" &&
   rg -Fq '.subshell-settings.json' "$BACKUP_VAULT" &&
   rg -Fq 'manifest.json' "$BACKUP_VAULT" &&
   rg -Fq 'AUTOMATIC_BACKUP_DEBOUNCE_MILLIS = 30_000L' "$ROOT/app/src/main/java" &&
   rg -Fq 'fun captureSettings(): SubshellSettings' "$ROOT/app/src/main/java" &&
   rg -Fq 'fun restoreSettings(settings: SubshellSettings)' "$ROOT/app/src/main/java" &&
   rg -Fq 'val storageKeys = setOf(' "$SETTINGS_MODEL" &&
   ! rg -qi 'token|credential|password|account|cache' "$SETTINGS_MODEL"; then
    pass RETAINED_INTERNAL_AND_EXTERNAL_SETTINGS_HISTORY
else
    fail RETAINED_INTERNAL_AND_EXTERNAL_SETTINGS_HISTORY
fi

for KIND in TEXT IMAGE VIDEO GIF GALLERY LINK CROSSPOST POLL; do
    if rg -q "^[[:space:]]*$KIND," "$MODELS" && rg -q "PostKind\.$KIND" "$DATA"; then
        pass "POST_KIND_$KIND"
    else
        fail "POST_KIND_$KIND"
    fi
done

for PREVIEW in LOADING OFFLINE ERROR EMPTY; do
    if rg -q "FeedPreviewState\.$PREVIEW" "$HOME_SCREEN_FILE"; then
        pass "FEED_STATE_$PREVIEW"
    else
        fail "FEED_STATE_$PREVIEW"
    fi
done

# The product name is lowercase "subshell" (owner, #223); the launcher label
# comes from app_name, which no test reads.
if rg -q '<string name="app_name">subshell</string>' "$ROOT/app/src/main/res/values/strings.xml"; then
    pass LOWERCASE_PRODUCT_NAME
else
    fail LOWERCASE_PRODUCT_NAME
fi

# DATA01: the ui never reads the sample fixtures; posts and people come from
# the repository through SubshellState.
if ! rg -n --glob '*.kt' 'SampleData' "$ROOT/app/src/main/java/app/subshell/prototype/ui" >/tmp/subshell-data01-ui-sampledata.txt; then
    pass DATA01_UI_DIRECT_SAMPLEDATA_ACCESS_RETIRED
else
    fail DATA01_UI_DIRECT_SAMPLEDATA_ACCESS_RETIRED
    sed -n '1,40p' /tmp/subshell-data01-ui-sampledata.txt 2>/dev/null || true
fi

QUALITY01E_COMMON="$ROOT/app/src/main/java/app/subshell/prototype/ui/components/Common.kt"

quality01e_post_action_modifier_line="$(
    awk '
        /fun PostActionRow\(/ { in_signature = 1 }
        in_signature && /modifier: Modifier = Modifier,/ {
            print
            exit
        }
    ' "$QUALITY01E_COMMON" |
    sed 's/^[[:space:]]*//'
)"

quality01e_post_action_previous_line="$(
    awk '
        /fun PostActionRow\(/ { in_signature = 1 }
        in_signature {
            previous = current
            current = $0
        }
        in_signature && /modifier: Modifier = Modifier,/ {
            print previous
            exit
        }
    ' "$QUALITY01E_COMMON" |
    sed 's/^[[:space:]]*//'
)"

quality01e_post_action_next_line="$(
    awk '
        /fun PostActionRow\(/ { in_signature = 1 }
        in_signature && /modifier: Modifier = Modifier,/ {
            getline
            print
            exit
        }
    ' "$QUALITY01E_COMMON" |
    sed 's/^[[:space:]]*//'
)"

DATA03_DATABASE="$ROOT/app/src/main/java/app/subshell/prototype/data/cache/SubshellCacheDatabase.kt"
DATA03_RUNTIME_TEST="$ROOT/app/src/androidTest/java/app/subshell/prototype/data/cache/PersistentCacheInstrumentedTest.kt"
DATA03_MIGRATION_TEST="$ROOT/app/src/androidTest/java/app/subshell/prototype/data/cache/SubshellCacheMigrationInstrumentedTest.kt"
DATA03_MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"

# CACHEDOWN1 (owner 2026-10-07): a newer build never drops the cache on an
# upgrade; only a downgrade (an older build over a newer schema) rebuilds it.
if ! rg -F 'fallbackToDestructiveMigration' "$ROOT/app/src/main/java" |
       rg -vF 'fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)' |
       rg -q . ; then
    pass CACHE_UPGRADES_NEVER_DROP_DATA
else
    fail CACHE_UPGRADES_NEVER_DROP_DATA
fi

data03_on_upgrade_database_parameter="$(
    awk '
        /override fun onUpgrade\(/ {
            getline
            gsub(/^[[:space:]]+|[[:space:]]+$/, "")
            print
            exit
        }
    ' "$DATA03_MIGRATION_TEST"
)"

# The cache is schema 6 and every step from 1 is registered (no unit test runs
# 1 -> 5; ProtoClean01LegacyFixtureMigrationTest runs 5 -> 6).
if rg -Fq 'version = 6' "$DATA03_DATABASE" &&
   rg -U -q '\.addMigrations\(\s*Migration1To2,\s*Migration2To3,\s*Migration3To4,\s*Migration4To5,\s*ProtoClean01Migration5To6,\s*\)' "$DATA03_DATABASE"; then
    pass CACHE_MIGRATIONS_STAY_REGISTERED
else
    fail CACHE_MIGRATIONS_STAY_REGISTERED
fi

data03_fresh_history_clear_line="$(
    rg -n -F 'freshBackend.clearPageRequestHistory()' "$DATA03_RUNTIME_TEST" |
        head -1 |
        cut -d: -f1
)"
data03_fresh_initial_line="$(
    rg -n -F 'val initial = freshRepository.posts(' "$DATA03_RUNTIME_TEST" |
        head -1 |
        cut -d: -f1
)"
data03_stale_history_clear_line="$(
    rg -n -F 'staleBackend.clearPageRequestHistory()' "$DATA03_RUNTIME_TEST" |
        head -1 |
        cut -d: -f1
)"
data03_stale_initial_line="$(
    rg -n -F 'val staleInitial = staleRepository.posts(' "$DATA03_RUNTIME_TEST" |
        head -1 |
        cut -d: -f1
)"

# The manifest points Android at the deny-all backup rules (their contents are
# boundary guard SYSTEM_BACKUP_DENIES_ALL); without dataExtractionRules,
# Android 12+ device transfer ignores allowBackup.
if rg -Fq 'android:dataExtractionRules="@xml/data_extraction_rules"' "$DATA03_MANIFEST" &&
   rg -Fq 'android:fullBackupContent="@xml/backup_rules"' "$DATA03_MANIFEST"; then
    pass DATABASE_BACKUP_RULES_ARE_WIRED
else
    fail DATABASE_BACKUP_RULES_ARE_WIRED
fi

# NET01/NET02/DATA03 (step 3 slim): the ui tree never reaches the network,
# sync or database layers or their serialization directly.
if ! rg -n --glob '*.kt' 'okhttp3|kotlinx\.serialization|data\.network|data\.sync|RoomDatabase|SubshellCacheDao|CachedPostEntity|RoomSubshellCacheStore' \
       "$ROOT/app/src/main/java/app/subshell/prototype/ui" >/tmp/subshell-ui-data-layer-imports.txt; then
    pass UI_HAS_NO_DATA_LAYER_IMPORTS
else
    fail UI_HAS_NO_DATA_LAYER_IMPORTS
    sed -n '1,40p' /tmp/subshell-ui-data-layer-imports.txt 2>/dev/null || true
fi

# AUTH01-06 (step 3 slim): the ui and the cache never see tokens, sessions,
# authorization codes, client configuration or the production identity.
if ! rg -n --glob '*.kt' 'data\.auth|StoredOAuthSession|OAuthTokenSet|accessToken|refreshToken|authorizationCode|StoredOAuthClientSelection|EncryptedAuthSessionStore|AUTH04_REDDIT_CLIENT_ID|AUTH04_REDDIT_REDIRECT_URI|sha256_cert_fingerprints|Auth05ProductionIdentity|SUBSHELL_FINAL_APPLICATION_ID|SUBSHELL_FINAL_SIGNING_CERT_SHA256' \
       "$ROOT/app/src/main/java/app/subshell/prototype/ui" \
       "$ROOT/app/src/main/java/app/subshell/prototype/data/cache" >/tmp/subshell-ui-cache-auth.txt; then
    pass UI_AND_CACHE_NEVER_SEE_AUTH
else
    fail UI_AND_CACHE_NEVER_SEE_AUTH
    sed -n '1,40p' /tmp/subshell-ui-cache-auth.txt 2>/dev/null || true
fi

# WRITE01 (step 3 slim): the ui never reaches a write transport or a token.
if ! rg -n 'AuthenticatedCommentSubmit|RedditCommentSubmit|postFormJson|api.*comment|AuthenticatedTextPostSubmit|RedditTextPostSubmit|api.*submit|NetworkBearerTokenProvider' \
       "$ROOT/app/src/main/java/app/subshell/prototype/ui" >/tmp/subshell-ui-write-transport.txt; then
    pass UI_HAS_NO_WRITE_TRANSPORT
else
    fail UI_HAS_NO_WRITE_TRANSPORT
    sed -n '1,40p' /tmp/subshell-ui-write-transport.txt 2>/dev/null || true
fi

# WRITE01A2/B1: the comment and text-post writers send only; the loader
# merges an accepted write into Room (CommentWriteFlowPolicyTest).
WRITE_SOURCES="$ROOT/app/src/main/java/app/subshell/prototype/data"
if ! rg -n 'cacheComments|withTransaction|insert.*comment|upsert.*comment' \
       "$WRITE_SOURCES/network/reddit/RedditCommentSubmitNetworkDataSource.kt" \
       "$WRITE_SOURCES/auth/reddit/RedditCommentSubmitter.kt" &&
   ! rg -n 'cache.*Post|withTransaction|insert.*post|upsert.*post' \
       "$WRITE_SOURCES/network/reddit/RedditTextPostSubmitNetworkDataSource.kt" \
       "$WRITE_SOURCES/auth/reddit/RedditTextPostSubmitter.kt"; then
    pass WRITERS_NEVER_WRITE_THE_CACHE
else
    fail WRITERS_NEVER_WRITE_THE_CACHE
fi

# WRITE01A1/A3A/A3B: the comment write capability only authorizes, the
# composer and process state never submit or authorize, and the comment write
# flow stays free of auth and network.
if ! rg -n 'postForm|postJson|api.*comment' "$WRITE_SOURCES/auth/AuthCommentWriteCapability.kt" &&
   ! rg -n 'AuthenticatedCommentSubmit|commentSubmitter|beginAuthorization|ensureAuthorized' \
       "$ROOT/app/src/main/java/app/subshell/prototype/ui/CommentComposerState.kt" \
       "$ROOT/app/src/main/java/app/subshell/prototype/ui/SubshellState.kt" \
       "$ROOT/app/src/main/java/app/subshell/prototype/ui/SubshellProcessState.kt" &&
   ! rg -n 'data\.auth|data\.network' "$WRITE_SOURCES/account/CommentWriteFlow.kt"; then
    pass COMMENT_WRITE_LAYERS_STAY_SEPARATE
else
    fail COMMENT_WRITE_LAYERS_STAY_SEPARATE
fi

# auth01, auth02 and auth03 were retired on 2026-10-14 (step 3, owner approved;
# docs/quality/retired-checkers.md). These three blocks keep their real rules:
# the build markers, the vault/session/coordinator/flow types and the ui and
# data/cache isolation; the rest lives in check-boundary-guards-contract.sh.

AUTH05_SOURCE_TMP="$(
    mktemp -d "${TMPDIR:-/tmp}/subshell-auth05-source.XXXXXX"
)"

rm -rf "$AUTH05_SOURCE_TMP"

AUTH06B_UI_EVIDENCE="$(mktemp "${TMPDIR:-/tmp}/subshell-auth06b-ui.XXXXXX")"

rm -f "$AUTH06B_UI_EVIDENCE"

if rg -Fq 'SUBS01B8A_DISABLE_COMPOSE_CONTENT_CAPTURE_V1' \
      "$ROOT/app/src/main/java/app/subshell/prototype/MainActivity.kt" &&
   rg -Fq '@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)' \
      "$ROOT/app/src/main/java/app/subshell/prototype/MainActivity.kt" &&
   rg -Fq 'androidx.compose.ui.contentcapture.ContentCaptureManager.isEnabled = false' \
      "$ROOT/app/src/main/java/app/subshell/prototype/MainActivity.kt" &&
   python3 - "$ROOT/app/src/main/java/app/subshell/prototype/MainActivity.kt" <<'PY_SUBS01B8A'
from pathlib import Path
import sys

text = Path(sys.argv[1]).read_text()

marker = "// SUBS01B8A_DISABLE_COMPOSE_CONTENT_CAPTURE_V1"
disable = "androidx.compose.ui.contentcapture.ContentCaptureManager.isEnabled = false"
super_call = "super.onCreate(savedInstanceState)"
# ISSUE278_EVERY_WINDOW_FOLLOWS_THE_UI_SIZE_V1: the content is now set through
# setScaledContent (a ComposeView on the UiScaleContext); either call must
# still come after the disable and super.onCreate.
set_content = "setScaledContent(" if "setScaledContent(" in text else "setContent {"

positions = [text.find(token) for token in (marker, disable, super_call, set_content)]
if any(position < 0 for position in positions):
    raise SystemExit(1)

marker_pos, disable_pos, super_pos, set_content_pos = positions
if not marker_pos < disable_pos < super_pos < set_content_pos:
    raise SystemExit(1)

if text.count(marker) != 1 or text.count(disable) != 1:
    raise SystemExit(1)
PY_SUBS01B8A
then
    pass SUBS01B8A_COMPOSE_CONTENT_CAPTURE_DISABLED_BEFORE_ACTIVITY_CREATION
else
    fail SUBS01B8A_COMPOSE_CONTENT_CAPTURE_DISABLED_BEFORE_ACTIVITY_CREATION
fi

# CAPTURE_SAFE: one pull-to-refresh owner (the shared capture-safe gesture;
# its decisions are CaptureSafeRefreshGesturePolicyTest); no Material
# PullToRefreshBox or nested-scroll pullToRefresh anywhere.
if ! rg -q 'PullToRefreshBox|\.pullToRefresh\(' "$ROOT/app/src/main/java"; then
    pass CAPTURE_SAFE_SHARED_PULL_REFRESH_WITHOUT_NESTED_SCROLL
else
    fail CAPTURE_SAFE_SHARED_PULL_REFRESH_WITHOUT_NESTED_SCROLL
fi

rm -f /tmp/subshell-write01a1-write-endpoints.txt

WRITE01A2_UI_EVIDENCE="/tmp/subshell-write01a2-ui-endpoints.$$"
WRITE01A2_ROOM_EVIDENCE="/tmp/subshell-write01a2-room-writeback.$$"

rm -f "$WRITE01A2_UI_EVIDENCE" "$WRITE01A2_ROOM_EVIDENCE"

WRITE01A3A_REMOTE_EVIDENCE="/tmp/subshell-write01a3a-remote-submit.$$"

rm -f "$WRITE01A3A_REMOTE_EVIDENCE"

WRITE01A3B_UI_LAYER_EVIDENCE="/tmp/subshell-write01a3b-ui-layer.$$"

rm -f "$WRITE01A3B_UI_LAYER_EVIDENCE"

WRITE01B1_UI_EVIDENCE="/tmp/subshell-write01b1-ui-endpoints.$$"
WRITE01B1_ROOM_EVIDENCE="/tmp/subshell-write01b1-room-writeback.$$"

rm -f "$WRITE01B1_UI_EVIDENCE" "$WRITE01B1_ROOM_EVIDENCE"

WRITE01B2_NETWORK_LEAK_EVIDENCE="/tmp/subshell-write01b2-ui-network-leak.$$"

rm -f "$WRITE01B2_NETWORK_LEAK_EVIDENCE"

run_required_contract tools/check-workflow01-contract.sh WORKFLOW01_BASELINE_AWARE_LAND

# Retired checkers and where their rules went: docs/quality/retired-checkers.md.
# STEP3_BOUNDARY_GUARDS_V1: boundaries moved out of orphan checkers so they gate lands.
run_required_contract tools/check-boundary-guards-contract.sh STEP3_BOUNDARY_GUARDS
# STEP3_CONTRACT02_SELFTESTS_GATED_V1: the release runner and verifier selftests run on every land.
run_required_contract tools/check-contract02-selftests-contract.sh STEP3_CONTRACT02_SELFTESTS

source_contract_memo_report

if test "$FAIL" -eq 0; then
    printf '%s\n' 'RESULT=SUBSHELL_DRAWER_BALANCE_R31_SOURCE_CONTRACT_PASS'

else
    printf '%s\n' 'RESULT=SUBSHELL_DRAWER_BALANCE_R31_SOURCE_CONTRACT_FAIL'
fi

exit "$FAIL"
