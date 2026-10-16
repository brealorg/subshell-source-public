package app.subshell.prototype.ui

import app.subshell.prototype.ui.screens.profileDestinationScrollSeedOrNull
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Next01A6PublicUserContentWorkspacePolicyTest {
    @Test
    fun publicPostsAndCommentsUseExpandedSplit() {
        listOf(0, 1).forEach { tab ->
            assertTrue(
                useExpandedPublicUserContentWorkspaceListDetail(
                    expandedWindow = true,
                    supportedOrigin = true,
                    selectedTab = tab,
                    tabletLayoutMode = TabletLayoutMode.SPLIT,
                ),
            )
        }
    }

    @Test
    fun nonPublicOrNonContentTabsAreNotClaimed() {
        assertFalse(
            useExpandedPublicUserContentWorkspaceListDetail(
                expandedWindow = true,
                supportedOrigin = false,
                selectedTab = 0,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )

        (2..7).forEach { tab ->
            assertFalse(
                useExpandedPublicUserContentWorkspaceListDetail(
                    expandedWindow = true,
                    supportedOrigin = true,
                    selectedTab = tab,
                    tabletLayoutMode = TabletLayoutMode.SPLIT,
                ),
            )
        }
    }

    @Test
    fun compactAndTabletSingleRemainUnchanged() {
        assertFalse(
            useExpandedPublicUserContentWorkspaceListDetail(
                expandedWindow = false,
                supportedOrigin = true,
                selectedTab = 0,
                tabletLayoutMode = TabletLayoutMode.SPLIT,
            ),
        )

        assertFalse(
            useExpandedPublicUserContentWorkspaceListDetail(
                expandedWindow = true,
                supportedOrigin = true,
                selectedTab = 0,
                tabletLayoutMode = TabletLayoutMode.SINGLE,
            ),
        )
    }

    @Test
    fun ownProfileAndYouBlockedWorkspaceAreExcluded() {
        assertFalse(
            supportsExpandedPublicUserContentOrigin(
                username = "alice",
                activeAccountUsername = "alice",
                blockedWorkspace = false,
            ),
        )

        assertFalse(
            supportsExpandedPublicUserContentOrigin(
                username = "bob",
                activeAccountUsername = "alice",
                blockedWorkspace = true,
            ),
        )

        assertTrue(
            supportsExpandedPublicUserContentOrigin(
                username = "bob",
                activeAccountUsername = "alice",
                blockedWorkspace = false,
            ),
        )
    }

    @Test
    fun publicWorkspaceReusesCanonicalProfileAndStableRouteIdentity() {
        val app = appSource()
        val profile = source("ui/screens/UserProfileScreen.kt")

        assertTrue(
            app.contains(
                "NEXT01A6_SHARED_PUBLIC_USER_PROFILE_HOST_V1",
            ),
        )
        assertTrue(
            app.contains(
                "NEXT01A6_PUBLIC_USER_ROUTE_TWO_PANE_V1",
            ),
        )
        assertTrue(
            app.contains(
                "NEXT01A6_PUBLIC_USER_POST_TWO_PANE_V1",
            ),
        )
        assertTrue(
            app.contains(
                "state.replaceCurrentUserContentSelection(",
            ),
        )

        assertEquals(
            2,
            "public-user-workspace-${'$'}{"
                .toRegex(RegexOption.LITERAL)
                .findAll(app)
                .count(),
        )

        // #224 (adapt-07): main-destination splits hide the list header too.
        assertTrue(
            "showListHeader = false,"
                .toRegex(RegexOption.LITERAL)
                .findAll(app)
                .count() >= 2,
        )

        assertTrue(
            profile.contains(
                "showBackButton: Boolean = true",
            ),
        )
        // #223: the profile's Back moved into RouteTopBar, still behind showBackButton.
        assertTrue(
            profile.contains("if (showBackButton) {") ||
                profile.contains("onBack = if (showBackButton) onBack else null"),
        )

        assertEquals(
            2,
            "UserProfileScreen("
                .toRegex(RegexOption.LITERAL)
                .findAll(app)
                .count(),
        )
    }

    @Test
    fun unvisitedDestinationInheritsCurrentProfileViewport() {
        val current =
            FeedScrollAnchor(
                itemKey = "profile-header",
                fallbackIndex = 1,
                pixelOffset = 73,
            )

        assertEquals(
            current,
            profileDestinationScrollSeedOrNull(
                currentAnchor = current,
                existingTargetAnchor = null,
            ),
        )
    }

    @Test
    fun visitedDestinationKeepsItsOwnScrollHistory() {
        val current =
            FeedScrollAnchor(
                itemKey = "profile-header",
                fallbackIndex = 1,
                pixelOffset = 73,
            )
        val existing =
            FeedScrollAnchor(
                itemKey = "tabs",
                fallbackIndex = 2,
                pixelOffset = 11,
            )

        assertEquals(
            null,
            profileDestinationScrollSeedOrNull(
                currentAnchor = current,
                existingTargetAnchor = existing,
            ),
        )
    }

    @Test
    fun sharedProfileAnchorResolvesAcrossDestinationShapes() {
        val anchor =
            FeedScrollAnchor(
                itemKey = "profile-header",
                fallbackIndex = 1,
                pixelOffset = 61,
            )

        val postKeys =
            listOf(
                "context-header",
                "profile-header",
                "tabs",
                "profile-post-a",
            )
        val commentKeys =
            listOf(
                "context-header",
                "profile-header",
                "tabs",
                "profile-comment-b",
            )

        assertEquals(1, anchor.resolveIndex(postKeys))
        assertEquals(1, anchor.resolveIndex(commentKeys))
        assertEquals(61, anchor.resolveOffset(commentKeys))
    }

    @Test
    fun profileSelectionUsesSingleScrollContinuityOwner() {
        val profile =
            source("ui/screens/UserProfileScreen.kt")

        assertTrue(
            profile.contains(
                "NEXT01A6_PROFILE_DESTINATION_SCROLL_CONTINUITY_V1",
            ),
        )
        assertTrue(
            profile.contains(
                "state.feedScrollAnchorSnapshot()[targetKey]",
            ),
        )
        assertTrue(
            profile.contains(
                "state.captureFeedScrollAnchor(",
            ),
        )
        assertEquals(
            2,
            "selectProfileDestination(selectedTab)"
                .toRegex(RegexOption.LITERAL)
                .findAll(profile)
                .count(),
        )
    }

    /** SubshellApp plus the composables it extracts into under ui/app. */
    private fun appSource(): String {
        val extracted = File("src/main/java/app/subshell/prototype/ui/app")
            .listFiles { file -> file.extension == "kt" }
            .orEmpty()
            .sortedBy { file -> file.name }
            .joinToString("") { file -> file.readText() }
        return source("ui/SubshellApp.kt") + extracted
    }

    private fun source(relative: String): String {
        val path =
            File("src/main/java/app/subshell/prototype/$relative")

        check(path.isFile) {
            "Missing source fixture: ${path.path}"
        }

        return path.readText()
    }
}
