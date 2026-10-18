package app.subshell.prototype.auth

import app.subshell.prototype.data.error.FailureDetails
import app.subshell.prototype.data.account.ModeratorUserManagementFailureKind
import app.subshell.prototype.data.account.ModeratorUserManagementFailureSection
import app.subshell.prototype.data.account.ModeratorUserManagementUiState
import app.subshell.prototype.data.account.RedditApprovedUser
import app.subshell.prototype.data.account.RedditModeratorUserApprovalRequest
import app.subshell.prototype.data.account.RedditModeratorUserBanRequest
import app.subshell.prototype.data.account.RedditModeratorUserMuteRequest
import app.subshell.prototype.data.account.RedditBannedUser
import app.subshell.prototype.data.account.RedditMutedUser
import app.subshell.prototype.data.account.normalizeRedditSafetyUsername
import app.subshell.prototype.data.auth.AuthBrowserLaunchResult
import app.subshell.prototype.data.auth.AuthCommentWriteCapability
import app.subshell.prototype.data.auth.AuthCommentWriteCapabilityResult
import app.subshell.prototype.data.auth.AuthenticatedRedditApprovedUsersReader
import app.subshell.prototype.data.auth.AuthenticatedRedditApprovedUsersResult
import app.subshell.prototype.data.auth.AuthenticatedRedditBannedUsersReader
import app.subshell.prototype.data.auth.AuthenticatedRedditBannedUsersResult
import app.subshell.prototype.data.auth.AuthenticatedRedditMutedUsersReader
import app.subshell.prototype.data.auth.AuthenticatedRedditMutedUsersResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserApprovalWriter
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserMutationResult
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserWriter
import app.subshell.prototype.data.auth.AuthenticatedRedditModeratorUserMuteWriter
import app.subshell.prototype.data.auth.OAuthClientMode
import app.subshell.prototype.data.normalizeRedditCommunityName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import app.subshell.prototype.data.auth.AUTH_CALLBACK_CANCELLED_TOKEN

// MODERATION02A5A1_ACCOUNT_COMMUNITY_PINNED_USER_MANAGEMENT_COORDINATOR_V1
internal class ModeratorUserManagementCoordinator(
    private val capability: AuthCommentWriteCapability?,
    private val reader: AuthenticatedRedditBannedUsersReader?,
    private val writer: AuthenticatedRedditModeratorUserWriter?,
    private val mutedReader: AuthenticatedRedditMutedUsersReader? = null,
    private val muteWriter: AuthenticatedRedditModeratorUserMuteWriter? = null,
    private val approvedReader: AuthenticatedRedditApprovedUsersReader? = null,
    private val approvalWriter: AuthenticatedRedditModeratorUserApprovalWriter? = null,
    private val clientMode: OAuthClientMode?,
    private val activeAccountStableId: () -> String?,
    private val activeAccountUsername: () -> String?,
) {
    private val mutationMutex = Mutex()
    private val mutableUiState = MutableStateFlow(ModeratorUserManagementUiState())
    val uiState: StateFlow<ModeratorUserManagementUiState> = mutableUiState.asStateFlow()

    fun open(accountStableId: String, rawCommunityName: String) {
        val account = accountStableId.trim()
        val community = runCatching { normalizeRedditCommunityName(rawCommunityName) }.getOrNull()
        if (account.isEmpty() || community == null || activeAccountStableId()?.trim() != account) {
            mutableUiState.value = ModeratorUserManagementUiState(
                visible = true,
                accountStableId = account.takeIf(String::isNotEmpty),
                communityName = community,
                failureKind = ModeratorUserManagementFailureKind.SESSION,
                failureDetails = null,
                failureSection = null,
            )
            return
        }
        val current = mutableUiState.value
        mutableUiState.value = if (current.matches(account, community)) {
            current.copy(visible = true)
        } else {
            ModeratorUserManagementUiState(
                visible = true,
                accountStableId = account,
                communityName = community,
            )
        }
    }

    suspend fun load(accountStableId: String, forceRefresh: Boolean = false) {
        val account = accountStableId.trim()
        val current = mutableUiState.value
        val community = current.communityName ?: return
        if (!current.visible || !current.matches(account, community) ||
            activeAccountStableId()?.trim() != account
        ) return
        // LOW11_LISTS_SKIPPED_DURING_A_WRITE_LOAD_AFTER_IT_V1 (OCB-591)
        if (current.mutationInProgress) {
            listsSkippedDuringWrite += UserList.BANNED
            return
        }
        if (current.loading || current.authorizing) return
        if (current.loaded && !forceRefresh && !current.authorizationRequired) return

        val configuredReader = reader ?: run {
            mutableUiState.value = current.copy(
                failureKind = ModeratorUserManagementFailureKind.CLIENT_CONFIGURATION,
                failureDetails = null,
                failureSection = ModeratorUserManagementFailureSection.BANNED,
            )
            return
        }
        mutableUiState.value = current.withFailureClearedFor(ModeratorUserManagementFailureSection.BANNED).copy(
            loading = true,
            authorizationRequired = false,
            verifiedUsername = null,
            verifiedBanned = null,
        )
        applyReadResult(
            account,
            community,
            safeRead(configuredReader, account, community, null),
            append = false,
        )
    }

    suspend fun append(accountStableId: String) {
        val account = accountStableId.trim()
        val current = mutableUiState.value
        val community = current.communityName ?: return
        val cursor = current.nextCursor ?: return
        val configuredReader = reader ?: return
        if (!current.visible || !current.matches(account, community) ||
            activeAccountStableId()?.trim() != account || current.loading ||
            current.authorizing || current.mutationInProgress || current.authorizationRequired
        ) return

        mutableUiState.value = current.withFailureClearedFor(ModeratorUserManagementFailureSection.BANNED).copy(loading = true)
        applyReadResult(
            account,
            community,
            safeRead(configuredReader, account, community, cursor),
            append = true,
        )
    }

    suspend fun loadMuted(accountStableId: String, forceRefresh: Boolean = false) {
        val account = accountStableId.trim()
        val current = mutableUiState.value
        val community = current.communityName ?: return
        if (!current.visible || !current.matches(account, community) ||
            activeAccountStableId()?.trim() != account
        ) return
        // LOW11_LISTS_SKIPPED_DURING_A_WRITE_LOAD_AFTER_IT_V1 (OCB-591)
        if (current.mutationInProgress) {
            listsSkippedDuringWrite += UserList.MUTED
            return
        }
        if (current.mutedLoading || current.authorizing || current.authorizationRequired) return
        if (current.mutedLoaded && !forceRefresh) return

        val configuredReader = mutedReader ?: run {
            mutableUiState.value = current.copy(
                failureKind = ModeratorUserManagementFailureKind.CLIENT_CONFIGURATION,
                failureDetails = null,
                failureSection = ModeratorUserManagementFailureSection.MUTED,
            )
            return
        }
        mutableUiState.value = current.withFailureClearedFor(ModeratorUserManagementFailureSection.MUTED).copy(
            mutedLoading = true,
            verifiedMutedUsername = null,
            verifiedMuted = null,
        )
        applyMutedReadResult(
            account,
            community,
            safeMutedRead(configuredReader, account, community, null),
            append = false,
        )
    }

    suspend fun appendMuted(accountStableId: String) {
        val account = accountStableId.trim()
        val current = mutableUiState.value
        val community = current.communityName ?: return
        val cursor = current.mutedNextCursor ?: return
        val configuredReader = mutedReader ?: return
        if (!current.visible || !current.matches(account, community) ||
            activeAccountStableId()?.trim() != account || current.mutedLoading ||
            current.authorizing || current.mutationInProgress || current.authorizationRequired
        ) return

        mutableUiState.value = current.withFailureClearedFor(ModeratorUserManagementFailureSection.MUTED).copy(mutedLoading = true)
        applyMutedReadResult(
            account,
            community,
            safeMutedRead(configuredReader, account, community, cursor),
            append = true,
        )
    }

    suspend fun loadApproved(accountStableId: String, forceRefresh: Boolean = false) {
        val account = accountStableId.trim()
        val current = mutableUiState.value
        val community = current.communityName ?: return
        if (!current.visible || !current.matches(account, community) ||
            activeAccountStableId()?.trim() != account
        ) return
        // LOW11_LISTS_SKIPPED_DURING_A_WRITE_LOAD_AFTER_IT_V1 (OCB-591)
        if (current.mutationInProgress) {
            listsSkippedDuringWrite += UserList.APPROVED
            return
        }
        if (current.approvedLoading || current.authorizing || current.authorizationRequired) return
        if (current.approvedLoaded && !forceRefresh) return

        val configuredReader = approvedReader ?: run {
            mutableUiState.value = current.copy(
                failureKind = ModeratorUserManagementFailureKind.CLIENT_CONFIGURATION,
                failureDetails = null,
                failureSection = ModeratorUserManagementFailureSection.APPROVED,
            )
            return
        }
        mutableUiState.value = current.withFailureClearedFor(ModeratorUserManagementFailureSection.APPROVED).copy(
            approvedLoading = true,
            verifiedApprovedUsername = null,
            verifiedApproved = null,
        )
        applyApprovedReadResult(
            account,
            community,
            safeApprovedRead(configuredReader, account, community, null),
            append = false,
        )
    }

    suspend fun appendApproved(accountStableId: String) {
        val account = accountStableId.trim()
        val current = mutableUiState.value
        val community = current.communityName ?: return
        val cursor = current.approvedNextCursor ?: return
        val configuredReader = approvedReader ?: return
        if (!current.visible || !current.matches(account, community) ||
            activeAccountStableId()?.trim() != account || current.approvedLoading ||
            current.authorizing || current.mutationInProgress || current.authorizationRequired
        ) return

        mutableUiState.value = current.withFailureClearedFor(ModeratorUserManagementFailureSection.APPROVED).copy(approvedLoading = true)
        applyApprovedReadResult(
            account,
            community,
            safeApprovedRead(configuredReader, account, community, cursor),
            append = true,
        )
    }

    // LOW11_LISTS_SKIPPED_DURING_A_WRITE_LOAD_AFTER_IT_V1 (OCB-591): a list load
    // that came in while a ban/mute/approve ran was dropped for good (the
    // sheet opens with three loads in a row); it runs once the write ends.
    private enum class UserList { BANNED, MUTED, APPROVED }
    private val listsSkippedDuringWrite = mutableSetOf<UserList>()

    private suspend fun loadListsSkippedDuringWrite(accountStableId: String) {
        val skipped = listsSkippedDuringWrite.toList()
        listsSkippedDuringWrite.clear()
        if (UserList.BANNED in skipped) load(accountStableId)
        if (UserList.MUTED in skipped) loadMuted(accountStableId)
        if (UserList.APPROVED in skipped) loadApproved(accountStableId)
    }

    suspend fun mutate(request: RedditModeratorUserBanRequest) {
        mutateBan(request)
        if (!mutableUiState.value.mutationInProgress) loadListsSkippedDuringWrite(request.accountStableId)
    }

    private suspend fun mutateBan(request: RedditModeratorUserBanRequest) {
        val account = request.accountStableId.trim()
        val community = runCatching { normalizeRedditCommunityName(request.communityName) }
            .getOrNull() ?: return
        val username = runCatching { normalizeRedditSafetyUsername(request.username) }
            .getOrNull() ?: return
        val current = mutableUiState.value
        if (!current.visible || !current.matches(account, community) ||
            activeAccountStableId()?.trim() != account ||
            username.equals(activeAccountUsername()?.trim(), ignoreCase = true)
        ) return

        if (!mutationMutex.tryLock()) {
            mutableUiState.value = current.copy(
                failureKind = ModeratorUserManagementFailureKind.ALREADY_MUTATING,
                failureDetails = null,
                failureSection = ModeratorUserManagementFailureSection.BANNED,
                failureRetryable = false,
            )
            return
        }
        try {
            val configuredReader = reader
            val configuredWriter = writer
            if (configuredReader == null || configuredWriter == null) {
                mutableUiState.value = current.copy(
                    failureKind = ModeratorUserManagementFailureKind.CLIENT_CONFIGURATION,
                    failureDetails = null,
                    failureSection = ModeratorUserManagementFailureSection.BANNED,
                )
                return
            }
            mutableUiState.value = current.withFailureClearedFor(ModeratorUserManagementFailureSection.BANNED).copy(
                mutatingUsername = username,
                verifiedUsername = null,
                verifiedBanned = null,
            )

            val preflight = findBannedStatus(configuredReader, account, community, username)
            if (!resultStillFor(account, community)) return
            when (preflight) {
                BannedStatus.AUTHORIZATION_REQUIRED -> {
                    mutableUiState.value = mutableUiState.value.copy(
                        mutatingUsername = null,
                        authorizationRequired = true,
                    )
                    return
                }
                BannedStatus.UNKNOWN -> {
                    mutableUiState.value = statusUnknownState(ModeratorUserManagementFailureSection.BANNED)
                    return
                }
                BannedStatus.BANNED, BannedStatus.NOT_BANNED -> Unit
            }

            val currentlyBanned = preflight == BannedStatus.BANNED
            // LOW10_REBAN_WITH_NEW_TERMS_IS_SENT_V1 (OCB-429): Reddit updates an
            // existing ban when it is posted again; a ban of an already banned
            // user that carries duration, reason, note or message is sent, so
            // the moderator's edits are not dropped behind "Ban confirmed".
            val carriesBanTerms = request.banned && (
                request.durationDays != null ||
                    !request.banReason.isNullOrBlank() ||
                    !request.moderatorNote.isNullOrBlank() ||
                    !request.messageToUser.isNullOrBlank()
                )
            if (currentlyBanned == request.banned && !carriesBanTerms) {
                mutableUiState.value = mutableUiState.value.copy(
                    mutatingUsername = null,
                    verifiedUsername = username,
                    verifiedBanned = currentlyBanned,
                    contentRevision = mutableUiState.value.contentRevision + 1L,
                )
                return
            }

            if (activeAccountStableId()?.trim() != account) return
            val writeResult = try {
                configuredWriter.mutate(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditModeratorUserMutationResult.Failed(
                    ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
            }

            if (!resultStillFor(account, community)) return
            when (writeResult) {
                AuthenticatedRedditModeratorUserMutationResult.AuthorizationRequired -> {
                    mutableUiState.value = mutableUiState.value.copy(
                        mutatingUsername = null,
                        authorizationRequired = true,
                    )
                    return
                }
                is AuthenticatedRedditModeratorUserMutationResult.Failed -> {
                    if (writeResult.kind != ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN) {
                        mutableUiState.value = mutableUiState.value.copy(
                            mutatingUsername = null,
                            failureKind = writeResult.kind,
                            failureDetails = writeResult.details,
                            failureSection = ModeratorUserManagementFailureSection.BANNED,
                            failureRetryable = false,
                            failureHttpStatusCode = writeResult.httpStatusCode,
                        )
                        return
                    }
                }
                AuthenticatedRedditModeratorUserMutationResult.Acknowledged -> Unit
            }

            val readback = findBannedStatus(configuredReader, account, community, username)
            if (!resultStillFor(account, community)) return
            val verified = when (readback) {
                BannedStatus.BANNED -> request.banned
                BannedStatus.NOT_BANNED -> !request.banned
                BannedStatus.AUTHORIZATION_REQUIRED, BannedStatus.UNKNOWN -> false
            }
            if (verified) {
                // MED1_VERIFIED_BANNED_LIST_PROJECTION_V1 (OCB-430): like the
                // muted list, a ban/unban Reddit has confirmed is shown in the
                // banned list at once, so the list and the banner agree.
                val latest = mutableUiState.value
                val others = latest.bannedUsers.filterNot { user ->
                    user.username.equals(username, ignoreCase = true)
                }
                val projectedBannedUsers = if (request.banned) {
                    val confirmedUser = latest.bannedUsers.firstOrNull { user ->
                        user.username.equals(username, ignoreCase = true)
                    } ?: RedditBannedUser(username = username, note = request.banReason)
                    listOf(confirmedUser) + others
                } else {
                    others
                }
                mutableUiState.value = latest.withFailureClearedFor(ModeratorUserManagementFailureSection.BANNED).copy(
                    bannedUsers = projectedBannedUsers,
                    loaded = true,
                    mutatingUsername = null,
                    verifiedUsername = username,
                    verifiedBanned = request.banned,
                    contentRevision = latest.contentRevision + 1L,
                )
            } else {
                mutableUiState.value = mutableUiState.value.copy(
                    mutatingUsername = null,
                    failureKind = ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                    failureDetails = null,
                    failureSection = ModeratorUserManagementFailureSection.BANNED,
                    failureRetryable = false,
                )
            }
        } finally {
            mutationMutex.unlock()
        }
    }

    suspend fun mutateMuted(request: RedditModeratorUserMuteRequest) {
        mutateMute(request)
        if (!mutableUiState.value.mutationInProgress) loadListsSkippedDuringWrite(request.accountStableId)
    }

    private suspend fun mutateMute(request: RedditModeratorUserMuteRequest) {
        val account = request.accountStableId.trim()
        val community = runCatching { normalizeRedditCommunityName(request.communityName) }
            .getOrNull() ?: return
        val username = runCatching { normalizeRedditSafetyUsername(request.username) }
            .getOrNull() ?: return
        val current = mutableUiState.value
        if (!current.visible || !current.matches(account, community) ||
            activeAccountStableId()?.trim() != account ||
            username.equals(activeAccountUsername()?.trim(), ignoreCase = true)
        ) return

        if (!mutationMutex.tryLock()) {
            mutableUiState.value = current.copy(
                failureKind = ModeratorUserManagementFailureKind.ALREADY_MUTATING,
                failureDetails = null,
                failureSection = ModeratorUserManagementFailureSection.MUTED,
                failureRetryable = false,
            )
            return
        }
        try {
            val configuredReader = mutedReader
            val configuredWriter = muteWriter
            if (configuredReader == null || configuredWriter == null) {
                mutableUiState.value = current.copy(
                    failureKind = ModeratorUserManagementFailureKind.CLIENT_CONFIGURATION,
                    failureDetails = null,
                    failureSection = ModeratorUserManagementFailureSection.MUTED,
                )
                return
            }
            mutableUiState.value = current.withFailureClearedFor(ModeratorUserManagementFailureSection.MUTED).copy(
                mutatingUsername = username,
                verifiedUsername = null,
                verifiedBanned = null,
                verifiedMutedUsername = null,
                verifiedMuted = null,
            )

            val preflight = findMutedStatus(configuredReader, account, community, username)
            if (!resultStillFor(account, community)) return
            when (preflight) {
                MutedStatus.AUTHORIZATION_REQUIRED -> {
                    mutableUiState.value = mutableUiState.value.copy(
                        mutatingUsername = null,
                        authorizationRequired = true,
                    )
                    return
                }
                MutedStatus.UNKNOWN -> {
                    mutableUiState.value = statusUnknownState(ModeratorUserManagementFailureSection.MUTED)
                    return
                }
                MutedStatus.MUTED, MutedStatus.NOT_MUTED -> Unit
            }

            val currentlyMuted = preflight == MutedStatus.MUTED
            if (currentlyMuted == request.muted) {
                mutableUiState.value = mutableUiState.value.copy(
                    mutatingUsername = null,
                    verifiedMutedUsername = username,
                    verifiedMuted = currentlyMuted,
                    contentRevision = mutableUiState.value.contentRevision + 1L,
                )
                return
            }

            if (activeAccountStableId()?.trim() != account) return
            val writeResult = try {
                configuredWriter.mutate(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditModeratorUserMutationResult.Failed(
                    ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
            }

            if (!resultStillFor(account, community)) return
            when (writeResult) {
                AuthenticatedRedditModeratorUserMutationResult.AuthorizationRequired -> {
                    mutableUiState.value = mutableUiState.value.copy(
                        mutatingUsername = null,
                        authorizationRequired = true,
                    )
                    return
                }
                is AuthenticatedRedditModeratorUserMutationResult.Failed -> {
                    if (writeResult.kind != ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN) {
                        mutableUiState.value = mutableUiState.value.copy(
                            mutatingUsername = null,
                            failureKind = writeResult.kind,
                            failureDetails = writeResult.details,
                            failureSection = ModeratorUserManagementFailureSection.MUTED,
                            failureRetryable = false,
                            failureHttpStatusCode = writeResult.httpStatusCode,
                        )
                        return
                    }
                }
                AuthenticatedRedditModeratorUserMutationResult.Acknowledged -> Unit
            }

            val readback = findMutedStatus(configuredReader, account, community, username)
            if (!resultStillFor(account, community)) return
            val verified = when (readback) {
                MutedStatus.MUTED -> request.muted
                MutedStatus.NOT_MUTED -> !request.muted
                MutedStatus.AUTHORIZATION_REQUIRED, MutedStatus.UNKNOWN -> false
            }
            if (verified) {
                // MODERATION02A5A2_VERIFIED_MUTED_LIST_PROJECTION_V1
                // This is not optimistic state: the relationship was already confirmed by
                // authoritative readback above. Project it immediately so the list and
                // confirmation banner cannot contradict each other while a hydration read runs.
                val latest = mutableUiState.value
                val projectedMutedUsers = if (request.muted) {
                    val confirmedUser = latest.mutedUsers.firstOrNull { user ->
                        user.username.equals(username, ignoreCase = true)
                    } ?: RedditMutedUser(
                        username = username,
                        note = request.moderatorNote,
                    )
                    listOf(confirmedUser) + latest.mutedUsers.filterNot { user ->
                        user.username.equals(username, ignoreCase = true)
                    }
                } else {
                    latest.mutedUsers.filterNot { user ->
                        user.username.equals(username, ignoreCase = true)
                    }
                }
                mutableUiState.value = latest.withFailureClearedFor(ModeratorUserManagementFailureSection.MUTED).copy(
                    mutedUsers = projectedMutedUsers,
                    mutedLoaded = true,
                    mutatingUsername = null,
                    verifiedMutedUsername = username,
                    verifiedMuted = request.muted,
                    contentRevision = latest.contentRevision + 1L,
                )

                // Hydrate the first page once after the verified projection. A successful
                // response enriches the confirmed row with Reddit's avatar/note/date data.
                // A hydration failure never turns an already-confirmed write into a failure.
                when (
                    val hydration = safeMutedRead(
                        configuredReader,
                        account,
                        community,
                        null,
                    )
                ) {
                    is AuthenticatedRedditMutedUsersResult.Loaded ->
                        applyMutedReadResult(
                            account = account,
                            community = community,
                            result = hydration,
                            append = false,
                        )
                    AuthenticatedRedditMutedUsersResult.AuthorizationRequired,
                    is AuthenticatedRedditMutedUsersResult.Failed,
                    -> Unit
                }
            } else {
                mutableUiState.value = mutableUiState.value.copy(
                    mutatingUsername = null,
                    failureKind = ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                    failureDetails = null,
                    failureSection = ModeratorUserManagementFailureSection.MUTED,
                    failureRetryable = false,
                )
            }
        } finally {
            mutationMutex.unlock()
        }
    }

    suspend fun mutateApproved(request: RedditModeratorUserApprovalRequest) {
        mutateApproval(request)
        if (!mutableUiState.value.mutationInProgress) loadListsSkippedDuringWrite(request.accountStableId)
    }

    private suspend fun mutateApproval(request: RedditModeratorUserApprovalRequest) {
        val account = request.accountStableId.trim()
        val community = runCatching { normalizeRedditCommunityName(request.communityName) }
            .getOrNull() ?: return
        val username = runCatching { normalizeRedditSafetyUsername(request.username) }
            .getOrNull() ?: return
        val current = mutableUiState.value
        if (!current.visible || !current.matches(account, community) ||
            activeAccountStableId()?.trim() != account ||
            username.equals(activeAccountUsername()?.trim(), ignoreCase = true)
        ) return

        if (!mutationMutex.tryLock()) {
            mutableUiState.value = current.copy(
                failureKind = ModeratorUserManagementFailureKind.ALREADY_MUTATING,
                failureDetails = null,
                failureSection = ModeratorUserManagementFailureSection.APPROVED,
                failureRetryable = false,
            )
            return
        }
        try {
            val configuredReader = approvedReader
            val configuredWriter = approvalWriter
            if (configuredReader == null || configuredWriter == null) {
                mutableUiState.value = current.copy(
                    failureKind = ModeratorUserManagementFailureKind.CLIENT_CONFIGURATION,
                    failureDetails = null,
                    failureSection = ModeratorUserManagementFailureSection.APPROVED,
                )
                return
            }
            mutableUiState.value = current.withFailureClearedFor(ModeratorUserManagementFailureSection.APPROVED).copy(
                mutatingUsername = username,
                verifiedUsername = null,
                verifiedBanned = null,
                verifiedMutedUsername = null,
                verifiedMuted = null,
                verifiedApprovedUsername = null,
                verifiedApproved = null,
            )

            val preflight = findApprovedStatus(configuredReader, account, community, username)
            if (!resultStillFor(account, community)) return
            when (preflight) {
                ApprovedStatus.AUTHORIZATION_REQUIRED -> {
                    mutableUiState.value = mutableUiState.value.copy(
                        mutatingUsername = null,
                        authorizationRequired = true,
                    )
                    return
                }
                ApprovedStatus.UNKNOWN -> {
                    mutableUiState.value = statusUnknownState(ModeratorUserManagementFailureSection.APPROVED)
                    return
                }
                ApprovedStatus.APPROVED, ApprovedStatus.NOT_APPROVED -> Unit
            }

            val currentlyApproved = preflight == ApprovedStatus.APPROVED
            if (currentlyApproved == request.approved) {
                projectVerifiedApprovedState(
                    reader = configuredReader,
                    account = account,
                    community = community,
                    username = username,
                    approved = currentlyApproved,
                )
                return
            }

            if (activeAccountStableId()?.trim() != account) return
            val writeResult = try {
                configuredWriter.mutate(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AuthenticatedRedditModeratorUserMutationResult.Failed(
                    ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                )
            }

            if (!resultStillFor(account, community)) return
            when (writeResult) {
                AuthenticatedRedditModeratorUserMutationResult.AuthorizationRequired -> {
                    mutableUiState.value = mutableUiState.value.copy(
                        mutatingUsername = null,
                        authorizationRequired = true,
                    )
                    return
                }
                is AuthenticatedRedditModeratorUserMutationResult.Failed -> {
                    if (writeResult.kind != ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN) {
                        mutableUiState.value = mutableUiState.value.copy(
                            mutatingUsername = null,
                            failureKind = writeResult.kind,
                            failureDetails = writeResult.details,
                            failureSection = ModeratorUserManagementFailureSection.APPROVED,
                            failureRetryable = false,
                            failureHttpStatusCode = writeResult.httpStatusCode,
                        )
                        return
                    }
                }
                AuthenticatedRedditModeratorUserMutationResult.Acknowledged -> Unit
            }

            val readback = findApprovedStatus(configuredReader, account, community, username)
            if (!resultStillFor(account, community)) return
            val verified = when (readback) {
                ApprovedStatus.APPROVED -> request.approved
                ApprovedStatus.NOT_APPROVED -> !request.approved
                ApprovedStatus.AUTHORIZATION_REQUIRED, ApprovedStatus.UNKNOWN -> false
            }
            if (verified) {
                projectVerifiedApprovedState(
                    reader = configuredReader,
                    account = account,
                    community = community,
                    username = username,
                    approved = request.approved,
                )
            } else {
                mutableUiState.value = mutableUiState.value.copy(
                    mutatingUsername = null,
                    failureKind = ModeratorUserManagementFailureKind.OUTCOME_UNKNOWN,
                    failureDetails = null,
                    failureSection = ModeratorUserManagementFailureSection.APPROVED,
                    failureRetryable = false,
                )
            }
        } finally {
            mutationMutex.unlock()
        }
    }

    suspend fun beginAuthorization(): AuthBrowserLaunchResult {
        val current = mutableUiState.value
        val account = current.accountStableId?.trim().orEmpty()
        if (!current.visible || current.communityName == null || account.isEmpty() ||
            activeAccountStableId()?.trim() != account || capability == null || clientMode == null
        ) {
            mutableUiState.value = current.copy(
                authorizing = false,
                authorizationRequired = true,
                failureKind = ModeratorUserManagementFailureKind.CLIENT_CONFIGURATION,
                failureDetails = null,
                failureSection = null,
            )
            return AuthBrowserLaunchResult.NotStarted
        }
        mutableUiState.value = current.copy(
            authorizing = true,
            authorizationRequired = true,
            failureKind = null,
            failureDetails = null,
            failureSection = null,
        )
        return try {
            AuthBrowserLaunchResult.Ready(
                authorizationUrl = capability.beginAuthorization(account),
                clientMode = clientMode,
                freshAccountSession = true,
            )
        } catch (_: Exception) {
            capability.cancelPendingAuthorization()
            mutableUiState.value = mutableUiState.value.copy(
                authorizing = false,
                authorizationRequired = true,
                failureKind = ModeratorUserManagementFailureKind.SESSION,
                failureDetails = null,
                failureSection = null,
                failureRetryable = true,
            )
            AuthBrowserLaunchResult.NotStarted
        }
    }

    fun browserLaunchFailed() {
        capability?.cancelPendingAuthorization()
        mutableUiState.value = mutableUiState.value.copy(
            authorizing = false,
            authorizationRequired = true,
            failureKind = ModeratorUserManagementFailureKind.SESSION,
            failureDetails = null,
            failureSection = null,
            failureRetryable = true,
        )
    }

    suspend fun handleCallbackResultTokenIfOwned(resultToken: String): Boolean {
        val configuredCapability = capability ?: return false
        if (resultToken == AUTH_CALLBACK_CANCELLED_TOKEN) {
            configuredCapability.cancelPendingAuthorization()
            mutableUiState.value = mutableUiState.value.copy(
                authorizing = false,
                authorizationRequired = true,
            )
            return true
        }
        if (!configuredCapability.hasStoredCallback()) return false
        completeAuthorization()
        return true
    }

    suspend fun resumeStoredAuthorizationIfPresent(): Boolean {
        val configuredCapability = capability ?: return false
        return when {
            configuredCapability.hasStoredCallback() -> { completeAuthorization(); true }
            configuredCapability.hasPendingAuthorization() -> true
            else -> false
        }
    }

    fun accountChanged(accountStableId: String) {
        val current = mutableUiState.value
        if (current.accountStableId != null && current.accountStableId != accountStableId.trim()) {
            // LOW8_ACCOUNT_SWITCH_CANCELS_A_PENDING_AUTHORIZATION_V1 (OCB-592), as
            // the permission and removal coordinators do.
            if (current.authorizing) capability?.cancelPendingAuthorization()
            mutableUiState.value = ModeratorUserManagementUiState()
        }
    }

    // MED2_USER_MANAGEMENT_RESULTS_STAY_WITH_THEIR_COMMUNITY_V1 (OCB-432): the
    // sheet can close and open for another community (or account) while a
    // ban/mute/approve preflight, write or read-back runs. Its answer is only
    // applied while the state still belongs to that account and community.
    private fun resultStillFor(account: String, community: String): Boolean =
        mutableUiState.value.matches(account, community) &&
            activeAccountStableId()?.trim() == account

    fun dismiss() {
        mutableUiState.value = mutableUiState.value.copy(visible = false)
    }

    private suspend fun completeAuthorization() {
        val configuredCapability = capability ?: return
        val current = mutableUiState.value
        // LOW8: a callback with no sheet to complete is dropped, not left stored.
        val account = current.accountStableId
            ?: return configuredCapability.cancelPendingAuthorization()
        val community = current.communityName
            ?: return configuredCapability.cancelPendingAuthorization()
        mutableUiState.value = current.copy(authorizing = true)
        when (val result = configuredCapability.completePendingAuthorization()) {
            is AuthCommentWriteCapabilityResult.Upgraded -> {
                if (result.accountStableId != account || activeAccountStableId()?.trim() != account ||
                    !mutableUiState.value.matches(account, community)
                ) {
                    mutableUiState.value = mutableUiState.value.copy(
                        authorizing = false,
                        authorizationRequired = true,
                        failureKind = ModeratorUserManagementFailureKind.SESSION,
                        failureDetails = null,
                        failureSection = null,
                    )
                    return
                }
                mutableUiState.value = mutableUiState.value.copy(
                    authorizing = false,
                    authorizationRequired = false,
                    failureKind = null,
                    failureDetails = null,
                    failureSection = null,
                )
                // Authorization completion reloads only. It never replays a ban/unban.
                // A5A2/A5A3 use the same rule for mute/unmute and approve/unapprove.
                load(account, forceRefresh = true)
                loadMuted(account, forceRefresh = true)
                loadApproved(account, forceRefresh = true)
            }
            is AuthCommentWriteCapabilityResult.Failed -> {
                mutableUiState.value = mutableUiState.value.copy(
                    authorizing = false,
                    authorizationRequired = true,
                    failureKind = ModeratorUserManagementFailureKind.SESSION,
                    failureDetails = null,
                    failureSection = null,
                    failureRetryable = result.retryable,
                )
            }
        }
    }

    private suspend fun safeRead(
        reader: AuthenticatedRedditBannedUsersReader,
        account: String,
        community: String,
        after: String?,
        username: String? = null,
    ): AuthenticatedRedditBannedUsersResult = try {
        reader.load(account, community, after, username)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        AuthenticatedRedditBannedUsersResult.Failed(
            communityName = community,
            kind = ModeratorUserManagementFailureKind.TRANSPORT,
            retryable = true,
        )
    }

    private suspend fun safeMutedRead(
        reader: AuthenticatedRedditMutedUsersReader,
        account: String,
        community: String,
        after: String?,
        username: String? = null,
    ): AuthenticatedRedditMutedUsersResult = try {
        reader.load(account, community, after, username)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        AuthenticatedRedditMutedUsersResult.Failed(
            communityName = community,
            kind = ModeratorUserManagementFailureKind.TRANSPORT,
            retryable = true,
        )
    }

    private suspend fun safeApprovedRead(
        reader: AuthenticatedRedditApprovedUsersReader,
        account: String,
        community: String,
        after: String?,
        username: String? = null,
    ): AuthenticatedRedditApprovedUsersResult = try {
        reader.load(account, community, after, username)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        AuthenticatedRedditApprovedUsersResult.Failed(
            communityName = community,
            kind = ModeratorUserManagementFailureKind.TRANSPORT,
            retryable = true,
        )
    }

    // LOW10_STATUS_READ_FAILURE_KEEPS_ITS_KIND_V1 (OCB-428): a failed status
    // read (network, HTTP 403/5xx, session) was reported as an unexpected
    // response and not retryable; its own kind, retry flag and HTTP status are
    // shown instead.
    private class StatusReadFailure(
        val kind: ModeratorUserManagementFailureKind,
        val retryable: Boolean,
        val httpStatusCode: Int?,
        val details: FailureDetails? = null,
    )
    private var lastStatusReadFailure: StatusReadFailure? = null

    private fun statusUnknownState(section: ModeratorUserManagementFailureSection): ModeratorUserManagementUiState {
        val failure = lastStatusReadFailure
        return mutableUiState.value.copy(
            mutatingUsername = null,
            failureSection = section,
            failureKind = failure?.kind ?: ModeratorUserManagementFailureKind.PROTOCOL,
            failureDetails = failure?.details,
            failureRetryable = failure?.retryable ?: false,
            failureHttpStatusCode = failure?.httpStatusCode,
        )
    }

    private suspend fun findBannedStatus(
        reader: AuthenticatedRedditBannedUsersReader,
        account: String,
        community: String,
        username: String,
    ): BannedStatus {
        lastStatusReadFailure = null
        var cursor: String? = null
        repeat(MAX_BANNED_STATUS_PAGES) { page ->
            // MODUSERS01_USER_FILTER_V1 (#125): the first read asks Reddit for
            // this user only (user=<name>), so one request settles the status in
            // a list of any size. If the filter were ignored, the paged scan goes on.
            when (val result = safeRead(reader, account, community, cursor, username.takeIf { page == 0 })) {
                is AuthenticatedRedditBannedUsersResult.Loaded -> {
                    if (result.communityName != community) return BannedStatus.UNKNOWN
                    if (result.users.any { it.username.equals(username, ignoreCase = true) }) {
                        return BannedStatus.BANNED
                    }
                    cursor = result.nextCursor
                    if (cursor == null) return BannedStatus.NOT_BANNED
                }
                AuthenticatedRedditBannedUsersResult.AuthorizationRequired ->
                    return BannedStatus.AUTHORIZATION_REQUIRED
                is AuthenticatedRedditBannedUsersResult.Failed -> {
                    lastStatusReadFailure = StatusReadFailure(result.kind, result.retryable, result.httpStatusCode, result.details)
                    return BannedStatus.UNKNOWN
                }
            }
        }
        return BannedStatus.UNKNOWN
    }

    private suspend fun findMutedStatus(
        reader: AuthenticatedRedditMutedUsersReader,
        account: String,
        community: String,
        username: String,
    ): MutedStatus {
        lastStatusReadFailure = null
        var cursor: String? = null
        repeat(MAX_MUTED_STATUS_PAGES) { page ->
            // MODUSERS01_USER_FILTER_V1 (#125): the first read asks Reddit for
            // this user only (user=<name>), so one request settles the status in
            // a list of any size. If the filter were ignored, the paged scan goes on.
            when (val result = safeMutedRead(reader, account, community, cursor, username.takeIf { page == 0 })) {
                is AuthenticatedRedditMutedUsersResult.Loaded -> {
                    if (result.communityName != community) return MutedStatus.UNKNOWN
                    if (result.users.any { it.username.equals(username, ignoreCase = true) }) {
                        return MutedStatus.MUTED
                    }
                    cursor = result.nextCursor
                    if (cursor == null) return MutedStatus.NOT_MUTED
                }
                AuthenticatedRedditMutedUsersResult.AuthorizationRequired ->
                    return MutedStatus.AUTHORIZATION_REQUIRED
                is AuthenticatedRedditMutedUsersResult.Failed -> {
                    lastStatusReadFailure = StatusReadFailure(result.kind, result.retryable, result.httpStatusCode, result.details)
                    return MutedStatus.UNKNOWN
                }
            }
        }
        return MutedStatus.UNKNOWN
    }

    private suspend fun findApprovedStatus(
        reader: AuthenticatedRedditApprovedUsersReader,
        account: String,
        community: String,
        username: String,
    ): ApprovedStatus {
        lastStatusReadFailure = null
        var cursor: String? = null
        repeat(MAX_APPROVED_STATUS_PAGES) { page ->
            // MODUSERS01_USER_FILTER_V1 (#125): the first read asks Reddit for
            // this user only (user=<name>), so one request settles the status in
            // a list of any size. If the filter were ignored, the paged scan goes on.
            when (val result = safeApprovedRead(reader, account, community, cursor, username.takeIf { page == 0 })) {
                is AuthenticatedRedditApprovedUsersResult.Loaded -> {
                    if (result.communityName != community) return ApprovedStatus.UNKNOWN
                    if (result.users.any { it.username.equals(username, ignoreCase = true) }) {
                        return ApprovedStatus.APPROVED
                    }
                    cursor = result.nextCursor
                    if (cursor == null) return ApprovedStatus.NOT_APPROVED
                }
                AuthenticatedRedditApprovedUsersResult.AuthorizationRequired ->
                    return ApprovedStatus.AUTHORIZATION_REQUIRED
                is AuthenticatedRedditApprovedUsersResult.Failed -> {
                    lastStatusReadFailure = StatusReadFailure(result.kind, result.retryable, result.httpStatusCode, result.details)
                    return ApprovedStatus.UNKNOWN
                }
            }
        }
        return ApprovedStatus.UNKNOWN
    }

    private suspend fun projectVerifiedApprovedState(
        reader: AuthenticatedRedditApprovedUsersReader,
        account: String,
        community: String,
        username: String,
        approved: Boolean,
    ) {
        // MODERATION02A5A3_VERIFIED_APPROVED_LIST_PROJECTION_V1
        // Projection happens only after authoritative preflight/readback established the state.
        val latest = mutableUiState.value
        val projectedApprovedUsers = if (approved) {
            val confirmedUser = latest.approvedUsers.firstOrNull { user ->
                user.username.equals(username, ignoreCase = true)
            } ?: RedditApprovedUser(username = username)
            listOf(confirmedUser) + latest.approvedUsers.filterNot { user ->
                user.username.equals(username, ignoreCase = true)
            }
        } else {
            latest.approvedUsers.filterNot { user ->
                user.username.equals(username, ignoreCase = true)
            }
        }
        mutableUiState.value = latest.withFailureClearedFor(ModeratorUserManagementFailureSection.APPROVED).copy(
            approvedUsers = projectedApprovedUsers,
            approvedLoaded = true,
            mutatingUsername = null,
            verifiedApprovedUsername = username,
            verifiedApproved = approved,
            contentRevision = latest.contentRevision + 1L,
        )

        // Hydration enriches avatar/date but cannot invalidate an already verified write.
        when (val hydration = safeApprovedRead(reader, account, community, null)) {
            is AuthenticatedRedditApprovedUsersResult.Loaded ->
                applyApprovedReadResult(
                    account = account,
                    community = community,
                    result = hydration,
                    append = false,
                )
            AuthenticatedRedditApprovedUsersResult.AuthorizationRequired,
            is AuthenticatedRedditApprovedUsersResult.Failed,
            -> Unit
        }
    }

    private fun applyReadResult(
        account: String,
        community: String,
        result: AuthenticatedRedditBannedUsersResult,
        append: Boolean,
    ) {
        val latest = mutableUiState.value
        if (!latest.matches(account, community) || activeAccountStableId()?.trim() != account) return
        mutableUiState.value = when (result) {
            is AuthenticatedRedditBannedUsersResult.Loaded ->
                if (result.communityName == community) {
                    latest.withFailureClearedFor(ModeratorUserManagementFailureSection.BANNED).copy(
                        bannedUsers = if (append) {
                            (latest.bannedUsers + result.users).distinctBy { it.username }
                        } else result.users,
                        loading = false,
                        loaded = true,
                        nextCursor = result.nextCursor,
                        authorizing = false,
                        authorizationRequired = false,
                        contentRevision = latest.contentRevision + 1L,
                    )
                } else latest.copy(
                    loading = false,
                    failureKind = ModeratorUserManagementFailureKind.PROTOCOL,
                    failureDetails = null,
                    failureSection = ModeratorUserManagementFailureSection.BANNED,
                )
            AuthenticatedRedditBannedUsersResult.AuthorizationRequired -> latest.withFailureClearedFor(ModeratorUserManagementFailureSection.BANNED).copy(
                loading = false,
                authorizing = false,
                authorizationRequired = true,
            )
            is AuthenticatedRedditBannedUsersResult.Failed -> latest.copy(
                loading = false,
                authorizing = false,
                failureKind = result.kind,
                failureDetails = result.details,
                failureSection = ModeratorUserManagementFailureSection.BANNED,
                failureRetryable = result.retryable,
                failureHttpStatusCode = result.httpStatusCode,
            )
        }
    }

    private fun applyApprovedReadResult(
        account: String,
        community: String,
        result: AuthenticatedRedditApprovedUsersResult,
        append: Boolean,
    ) {
        val latest = mutableUiState.value
        if (!latest.matches(account, community) || activeAccountStableId()?.trim() != account) return
        mutableUiState.value = when (result) {
            is AuthenticatedRedditApprovedUsersResult.Loaded ->
                if (result.communityName == community) {
                    latest.withFailureClearedFor(ModeratorUserManagementFailureSection.APPROVED).copy(
                        approvedUsers = if (append) {
                            (latest.approvedUsers + result.users).distinctBy { it.username }
                        } else result.users,
                        approvedLoading = false,
                        approvedLoaded = true,
                        approvedNextCursor = result.nextCursor,
                        authorizing = false,
                        authorizationRequired = false,
                        contentRevision = latest.contentRevision + 1L,
                    )
                } else latest.copy(
                    approvedLoading = false,
                    failureKind = ModeratorUserManagementFailureKind.PROTOCOL,
                    failureDetails = null,
                    failureSection = ModeratorUserManagementFailureSection.APPROVED,
                )
            AuthenticatedRedditApprovedUsersResult.AuthorizationRequired -> latest.withFailureClearedFor(ModeratorUserManagementFailureSection.APPROVED).copy(
                approvedLoading = false,
                authorizing = false,
                authorizationRequired = true,
            )
            is AuthenticatedRedditApprovedUsersResult.Failed -> latest.copy(
                approvedLoading = false,
                authorizing = false,
                failureKind = result.kind,
                failureDetails = result.details,
                failureSection = ModeratorUserManagementFailureSection.APPROVED,
                failureRetryable = result.retryable,
                failureHttpStatusCode = result.httpStatusCode,
            )
        }
    }

    private fun applyMutedReadResult(
        account: String,
        community: String,
        result: AuthenticatedRedditMutedUsersResult,
        append: Boolean,
    ) {
        val latest = mutableUiState.value
        if (!latest.matches(account, community) || activeAccountStableId()?.trim() != account) return
        mutableUiState.value = when (result) {
            is AuthenticatedRedditMutedUsersResult.Loaded ->
                if (result.communityName == community) {
                    latest.withFailureClearedFor(ModeratorUserManagementFailureSection.MUTED).copy(
                        mutedUsers = if (append) {
                            (latest.mutedUsers + result.users).distinctBy { it.username }
                        } else result.users,
                        mutedLoading = false,
                        mutedLoaded = true,
                        mutedNextCursor = result.nextCursor,
                        authorizing = false,
                        authorizationRequired = false,
                        contentRevision = latest.contentRevision + 1L,
                    )
                } else latest.copy(
                    mutedLoading = false,
                    failureKind = ModeratorUserManagementFailureKind.PROTOCOL,
                    failureDetails = null,
                    failureSection = ModeratorUserManagementFailureSection.MUTED,
                )
            AuthenticatedRedditMutedUsersResult.AuthorizationRequired -> latest.withFailureClearedFor(ModeratorUserManagementFailureSection.MUTED).copy(
                mutedLoading = false,
                authorizing = false,
                authorizationRequired = true,
            )
            is AuthenticatedRedditMutedUsersResult.Failed -> latest.copy(
                mutedLoading = false,
                authorizing = false,
                failureKind = result.kind,
                failureDetails = result.details,
                failureSection = ModeratorUserManagementFailureSection.MUTED,
                failureRetryable = result.retryable,
                failureHttpStatusCode = result.httpStatusCode,
            )
        }
    }
}

private enum class BannedStatus { BANNED, NOT_BANNED, AUTHORIZATION_REQUIRED, UNKNOWN }
private enum class MutedStatus { MUTED, NOT_MUTED, AUTHORIZATION_REQUIRED, UNKNOWN }
private enum class ApprovedStatus { APPROVED, NOT_APPROVED, AUTHORIZATION_REQUIRED, UNKNOWN }
private const val MAX_BANNED_STATUS_PAGES = 10
private const val MAX_MUTED_STATUS_PAGES = 10
private const val MAX_APPROVED_STATUS_PAGES = 10

// ISSUE87_FAILURE_KEPT_PER_LIST_V1: banned, muted and approved shared one
// failure slot, so a later muted/approved read erased a banned-list failure
// (a blank Banned tab with no error or Retry) and Load more in another list
// erased an "Action outcome not confirmed" warning. A failure belongs to the
// list it came from and only that list's read or write clears it; a failure of
// no list (session, configuration) is cleared by any.
private fun ModeratorUserManagementUiState.withFailureClearedFor(
    section: ModeratorUserManagementFailureSection,
): ModeratorUserManagementUiState =
    if (failureKind != null && failureSection != null && failureSection != section) {
        this
    } else {
        copy(
            failureKind = null,
            failureDetails = null,
            failureRetryable = false,
            failureHttpStatusCode = null,
            failureSection = null,
        )
    }
