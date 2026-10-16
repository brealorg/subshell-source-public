package app.subshell.prototype.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Crosspost01A1CDestinationPolicyTest {
    private val draft = crosspost01A1CDraft()
    private val policy = crosspost01A1CPolicy()
    private val destination = requireNotNull(policy.crosspostDestination)
    private fun validate(p: ComposerCommunityPolicy = policy, d: PostComposerDraft = draft) =
        validateCrosspostComposerDraftIssue(d, "owner", true, p)

    @Test
    fun explicitCrosspostPermissionIsIndependentOfOrdinaryPostTypeChoices() {
        assertNull(validate())
        assertNull(validate(policy.copy(allowedPostTypes = setOf(ComposerPostType.TEXT))))
        assertNull(validate(policy.copy(allowedPostTypes = setOf(ComposerPostType.LINK))))
    }

    @Test
    fun unavailablePolicyAndExplicitDenialBlockWhileUnknownCapabilityDefersToPublish() {
        assertNotNull(validate(policy.copy(crosspostDestination = null)))
        assertNotNull(validate(policy.copy(loadedFromReddit = false)))
        assertNotNull(
            validate(
                policy.copy(
                    crosspostDestination =
                        destination.copy(allowCrossposts = false),
                ),
            ),
        )
        assertNull(
            validate(
                policy.copy(
                    crosspostDestination =
                        destination.copy(allowCrossposts = null),
                ),
            ),
        )
    }

    @Test
    fun accountSwitchAndDestinationSwitchInvalidatePreviousPolicy() {
        assertNotNull(validate(policy.copy(community = "other")))
        assertNotNull(validate(policy.copy(crosspostDestination = destination.copy(community = "other"))))
        assertNotNull(validate(policy.copy(crosspostDestination = destination.copy(accountStableId = "other"))))
        assertNotNull(validate(d = draft.copy(accountStableId = "other")))
        assertNotNull(validateCrosspostComposerDraftIssue(draft, "owner", false, policy))
        assertNotNull(validateCrosspostComposerDraftIssue(draft, "", true, policy))
    }

    @Test
    fun postingBanAndExplicitDenialWinEvenForModeratorHints() {
        for (banned in listOf(null, true)) {
            assertNotNull(validate(policy.copy(crosspostDestination = destination.copy(userIsBanned = banned, userIsModerator = true))))
        }
        assertNotNull(validate(policy.copy(crosspostDestination = destination.copy(userCanSubmit = false, userIsModerator = true))))
    }

    @Test
    fun restrictedAndPrivateDestinationsRequireAnExplicitPostingGrant() {
        for (type in listOf("restricted", "private")) {
            val restricted = destination.copy(subredditType = type)
            assertNotNull(validate(policy.copy(crosspostDestination = restricted)))
            assertNull(validate(policy.copy(crosspostDestination = restricted.copy(userIsContributor = true))))
            assertNull(validate(policy.copy(crosspostDestination = restricted.copy(userIsModerator = true))))
            assertNull(validate(policy.copy(crosspostDestination = restricted.copy(userCanSubmit = true))))
        }
        assertNotNull(validate(policy.copy(crosspostDestination = destination.copy(subredditType = "user"))))
    }

    @Test
    fun authorizationRequiredBlocksWhileUnavailableRequirementsDeferToReddit() {
        assertNotNull(
            validate(
                policy.copy(
                    postRequirementsStatus =
                        ComposerPostRequirementsStatus.AUTHORIZATION_REQUIRED,
                ),
            ),
        )

        assertNull(
            validate(
                policy.copy(
                    postRequirementsStatus =
                        ComposerPostRequirementsStatus.UNAVAILABLE,
                ),
            ),
        )

        assertNull(
            validate(
                policy.copy(
                    postRequirementsStatus =
                        ComposerPostRequirementsStatus.NOT_CHECKED,
                ),
            ),
        )

        assertNull(
            validate(
                policy.copy(
                    postRequirementsStatus =
                        ComposerPostRequirementsStatus.LOADED,
                ),
            ),
        )
    }

    @Test
    fun titleAndRequiredBodyRulesRemainEnforcedWithoutInventingAReplacementBody() {
        assertNotNull(validate(d = draft.copy(title = "")))
        assertNotNull(validate(policy.copy(titleMinLength = 100)))
        assertNotNull(validate(policy.copy(titleMaxLength = 3)))
        assertNotNull(validate(policy.copy(titleRequiredStrings = listOf("[Tag]"))))
        assertNotNull(validate(policy.copy(titleBlockedStrings = listOf("Original"))))
        assertNotNull(validate(policy.copy(bodyRequired = true)))
        assertNull(validate(policy.copy(bodyAllowed = false)))
    }

    @Test
    fun unsupportedSourceDomainRequirementsDoNotGuessFromPermalink() {
        assertNotNull(validate(policy.copy(domainWhitelist = listOf("reddit.com"))))
        assertNotNull(validate(policy.copy(domainBlacklist = listOf("example.com"))))
    }

    @Test
    fun requiredFlairMustBeLoadedAuthorizedAndActuallySelected() {
        assertNotNull(validate(policy.copy(flairRequired = true)))
        assertNotNull(validate(policy.copy(flairAuthorizationRequired = true)))
        val option = ComposerFlair("news", "News")
        val selected = draft.copy(flair = option)
        val loaded = policy.copy(flairRequired = true, flairOptions = listOf(option))
        assertNull(validate(loaded, selected))
        assertNotNull(validate(loaded.copy(flairOptionsLoaded = false), selected))
    }

    @Test
    fun staleUnknownDuplicateAndNonEditableFlairChangesAreRejected() {
        val option = ComposerFlair("news", "News")
        val selected = draft.copy(flair = option)
        assertNotNull(validate(policy, selected))
        assertNotNull(validate(policy.copy(flairOptions = listOf(option, option)), selected))
        assertNotNull(validate(policy.copy(flairOptions = listOf(option)), selected.copy(flair = option.copy(text = "Fake"))))
        assertNull(validate(policy.copy(flairOptions = listOf(option.copy(editable = true))), selected.copy(flair = option.copy(text = "Custom", editable = true))))
    }

    @Test
    fun destinationNsfwAndSourceIntegrityRemainRequired() {
        val adult = policy.copy(crosspostDestination = destination.copy(nsfw = true))
        assertNotNull(validate(adult))
        assertNull(validate(adult, draft.copy(nsfw = true)))
        assertNotNull(validate(d = draft.copy(crosspostSource = null)))
        assertNotNull(validate(d = draft.copy(community = "")))
        assertNotNull(validate(d = draft.copy(body = "hidden replacement")))
    }
}
