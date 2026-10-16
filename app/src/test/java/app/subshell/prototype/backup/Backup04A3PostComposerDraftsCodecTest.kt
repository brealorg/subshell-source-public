package app.subshell.prototype.backup

import app.subshell.prototype.ui.SubshellSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Backup04A3PostComposerDraftsCodecTest {
    @Test
    fun schemaSevenRoundTripsTextAndLinkWithoutAnyMediaUriField() {
        val snapshot = snapshot(
            mapOf(
                " Reddit:Case " to listOf(
                    draft("text-1", "TEXT", "Text title", body = "private body", updated = 3_000L),
                    draft(
                        "link-1",
                        "LINK",
                        "Link title",
                        linkUrl = "https://example.com/story",
                        updated = 2_000L,
                    ),
                ),
                "local:empty" to emptyList(),
            ),
        )

        val encoded = SettingsSnapshotCodec.encode(snapshot)
        assertTrue(encoded.contains("\"schemaVersion\": $SETTINGS_BACKUP_SCHEMA_VERSION"))
        assertTrue(encoded.contains("\"postComposerDrafts\""))
        assertTrue(encoded.contains("\"Reddit:Case\""))
        assertFalse(encoded.contains("\"media\""))
        assertFalse(encoded.contains("content://"))

        val decoded = SettingsSnapshotCodec.decode(encoded)
        assertEquals(2, decoded.postComposerDraftsByAccount?.get("Reddit:Case")?.size)
        assertEquals(emptyList<PortablePostComposerDraft>(), decoded.postComposerDraftsByAccount?.get("local:empty"))
        assertEquals(
            "https://example.com/story",
            decoded.postComposerDraftsByAccount
                ?.get("Reddit:Case")
                ?.first { it.id == "link-1" }
                ?.linkUrl,
        )
    }

    @Test
    fun schemaSixIgnoresDraftModuleAndExplicitEmptyModuleRoundTrips() {
        val encoded = SettingsSnapshotCodec.encode(snapshot(emptyMap()))
        assertEquals(
            emptyMap<String, List<PortablePostComposerDraft>>(),
            SettingsSnapshotCodec.decode(encoded).postComposerDraftsByAccount,
        )

        val schemaSix = encoded.replace("\"schemaVersion\": $SETTINGS_BACKUP_SCHEMA_VERSION", "\"schemaVersion\": 6")
        assertNull(SettingsSnapshotCodec.decode(schemaSix).postComposerDraftsByAccount)
    }

    @Test
    fun malformedMediaLikeAndOwnerRowsAreBoundedOrIgnored() {
        val raw = mapOf(
            "" to listOf(draft("blank-owner", "TEXT", "drop")),
            "Owner" to listOf(
                draft("media-type", "MEDIA", "drop"),
                draft("good", "TEXT", "keep"),
                draft("good", "LINK", "duplicate newer", updated = 9_000L),
            ),
        )
        val normalized = normalizePortablePostComposerDraftsByAccount(raw)
        assertFalse(normalized.containsKey(""))
        assertEquals(listOf("good"), normalized.getValue("Owner").map { it.id })
        assertEquals("duplicate newer", normalized.getValue("Owner").single().title)
    }

    private fun draft(
        id: String,
        type: String,
        title: String,
        body: String = "",
        linkUrl: String = "",
        updated: Long = 2_000L,
    ) = PortablePostComposerDraft(
        id = id,
        community = "subshell",
        type = type,
        title = title,
        body = body,
        linkUrl = linkUrl,
        inputMode = "MARKDOWN",
        nsfw = false,
        spoiler = false,
        sendReplies = true,
        createdAtEpochMs = 1_000L,
        updatedAtEpochMs = updated,
    )

    private fun snapshot(
        drafts: Map<String, List<PortablePostComposerDraft>>?,
    ) = SettingsSnapshot(
        id = "00000000-0000-4000-8000-0000000000a3",
        name = "BACKUP04A3",
        createdAtEpochMillis = 1_000L,
        appVersion = "test",
        kind = SnapshotKind.MANUAL,
        deviceId = "device",
        settings = SubshellSettings(),
        postComposerDraftsByAccount = drafts,
    )
}
