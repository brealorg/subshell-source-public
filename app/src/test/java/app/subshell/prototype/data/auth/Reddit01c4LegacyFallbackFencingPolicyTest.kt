package app.subshell.prototype.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Reddit01c4LegacyFallbackFencingPolicyTest {
    @Test
    fun noAuthorizationRecordIsNotLegacyFallback() {
        val resolution =
            resolveAuthCapabilityAuthorizationRouting(emptyList())

        assertEquals(
            AuthCapabilityAuthorizationRoutingKind.NONE,
            resolution.kind,
        )
        assertNull(resolution.route)
    }

    @Test
    fun ownerlessRecordsAreExplicitlyLegacy() {
        val resolution =
            resolveAuthCapabilityAuthorizationRouting(
                listOf(
                    AuthCapabilityAuthorizationRouteMetadata(
                        ownerKey = null,
                        contextKey = null,
                    ),
                    AuthCapabilityAuthorizationRouteMetadata(
                        ownerKey = null,
                        contextKey = null,
                    ),
                ),
            )

        assertEquals(
            AuthCapabilityAuthorizationRoutingKind.LEGACY_OWNERLESS,
            resolution.kind,
        )
        assertNull(resolution.route)
    }

    @Test
    fun matchingOwnedRecordsResolveDeterministically() {
        val resolution =
            resolveAuthCapabilityAuthorizationRouting(
                listOf(
                    AuthCapabilityAuthorizationRouteMetadata(
                        ownerKey = "post_manage",
                        contextKey = "moderation",
                    ),
                    AuthCapabilityAuthorizationRouteMetadata(
                        ownerKey = "post_manage",
                        contextKey = "moderation",
                    ),
                ),
            )

        assertEquals(
            AuthCapabilityAuthorizationRoutingKind.OWNED,
            resolution.kind,
        )
        assertEquals(
            AuthCapabilityAuthorizationRoute(
                owner = AuthCapabilityAuthorizationOwner.POST_MANAGE,
                contextKey = "moderation",
            ),
            resolution.route,
        )
    }

    @Test
    fun mixedLegacyAndOwnedRecordsFailClosed() {
        val resolution =
            resolveAuthCapabilityAuthorizationRouting(
                listOf(
                    AuthCapabilityAuthorizationRouteMetadata(
                        ownerKey = null,
                        contextKey = null,
                    ),
                    AuthCapabilityAuthorizationRouteMetadata(
                        ownerKey = "comment_edit",
                        contextKey = null,
                    ),
                ),
            )

        assertEquals(
            AuthCapabilityAuthorizationRoutingKind.INVALID,
            resolution.kind,
        )
        assertNull(resolution.route)
    }

    @Test
    fun mismatchedOwnedRecordsFailClosed() {
        val resolution =
            resolveAuthCapabilityAuthorizationRouting(
                listOf(
                    AuthCapabilityAuthorizationRouteMetadata(
                        ownerKey = "comment_edit",
                        contextKey = null,
                    ),
                    AuthCapabilityAuthorizationRouteMetadata(
                        ownerKey = "comment_delete",
                        contextKey = null,
                    ),
                ),
            )

        assertEquals(
            AuthCapabilityAuthorizationRoutingKind.INVALID,
            resolution.kind,
        )
        assertNull(resolution.route)
    }

    @Test
    fun malformedOwnershipMetadataFailsClosed() {
        val unknownOwner =
            resolveAuthCapabilityAuthorizationRouting(
                listOf(
                    AuthCapabilityAuthorizationRouteMetadata(
                        ownerKey = "unknown_owner",
                        contextKey = null,
                    ),
                ),
            )

        val contextWithoutOwner =
            resolveAuthCapabilityAuthorizationRouting(
                listOf(
                    AuthCapabilityAuthorizationRouteMetadata(
                        ownerKey = null,
                        contextKey = "moderation",
                    ),
                ),
            )

        val malformedContext =
            resolveAuthCapabilityAuthorizationRouting(
                listOf(
                    AuthCapabilityAuthorizationRouteMetadata(
                        ownerKey = "post_manage",
                        contextKey = "bad-context!",
                    ),
                ),
            )

        assertEquals(
            AuthCapabilityAuthorizationRoutingKind.INVALID,
            unknownOwner.kind,
        )
        assertEquals(
            AuthCapabilityAuthorizationRoutingKind.INVALID,
            contextWithoutOwner.kind,
        )
        assertEquals(
            AuthCapabilityAuthorizationRoutingKind.INVALID,
            malformedContext.kind,
        )
    }
}
