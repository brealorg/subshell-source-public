package app.subshell.prototype.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtoClean01LegacyFixtureMigrationPolicyTest {
    @Test
    fun legacyPostIdsRemainAClosedPrototypeNamespace() {
        val ids = ProtoClean01LegacyFixtureSignatures.postIds
        assertEquals(70, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { id -> '-' in id })
        assertTrue("video-lab-a" in ids)
        assertTrue("nsfw-cable-horror" in ids)
    }

    @Test
    fun sharedRedditCommunityNamesAreMatchedByFixturePayloadNotByCommunityId() {
        val signatures = ProtoClean01LegacyFixtureSignatures.communityPayloadSignatures
        assertEquals(10, signatures.size)
        assertEquals(signatures.size, signatures.toSet().size)
        assertTrue(signatures.all(String::isNotBlank))
        assertTrue(
            "News, apps, devices and the wonderfully messy Android ecosystem." in signatures,
        )
    }

    @Test
    fun fixtureUserInboxAndCommentSignaturesAreClosedAndExplicit() {
        assertEquals(6, ProtoClean01LegacyFixtureSignatures.userPayloadSignatures.size)
        assertEquals(4, ProtoClean01LegacyFixtureSignatures.inboxPayloadSignatures.size)
        assertEquals(6, ProtoClean01LegacyFixtureSignatures.genericCommentPayloadSignatures.size)
        assertEquals(21, ProtoClean01LegacyFixtureSignatures.profileCommentIds.size)
    }
}
