package app.subshell.prototype.data.cache

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// SUBSHELL_PROTOCLEAN01A3_LEGACY_PERSISTED_FIXTURE_RETIREMENT_V1
internal object ProtoClean01LegacyFixtureSignatures {
    val postIds: List<String> = listOf(
        "video-lab-a",
        "video-lab-b",
        "video-lab-c",
        "video-lab-d",
        "video-lab-e",
        "video-lab-f",
        "video-lab-g",
        "video-lab-h",
        "video-lab-i",
        "video-lab-j",
        "video-lab-k",
        "video-lab-l",
        "video-lab-m",
        "video-lab-n",
        "video-lab-o",
        "video-lab-p",
        "video-lab-q",
        "video-lab-r",
        "video-lab-s",
        "video-lab-t",
        "video-lab-u",
        "video-lab-v",
        "video-lab-w",
        "video-lab-x",
        "video-lab-y",
        "video-lab-z",
        "media03-dash-adaptive",
        "media03-hls-adaptive",
        "media03-broken-primary-fallback",
        "media03-split-av",
        "media03-broken-audio-fallback",
        "terminal-widget",
        "packetmoss-dns-map",
        "m3-terminal",
        "compose-state-snapshot",
        "compose-lazy-list-notes",
        "compose-theme-probe",
        "root-backup-diff",
        "root-terminal-session",
        "root-failover-checklist",
        "community-navigation",
        "mock-data-first",
        "compose-baseline-fresh",
        "tap-target-thread",
        "gif-swipe-handoff",
        "community-gallery",
        "shelluser-dns-notes",
        "density-crosspost",
        "shelluser-terminal-desk",
        "offline-resume-video",
        "old-design-reference",
        "controversial-comment-marathon",
        "home-server",
        "gif-high-quality",
        "gif-homemade",
        "gif-pixel-art",
        "spritewitch-palette-test",
        "spritewitch-nearest-neighbour",
        "pixel-gallery",
        "linux-desk",
        "tildehome-dotfiles",
        "tildehome-keyboard-layer",
        "open-reader",
        "tiny-command",
        "shelluser-android-backup",
        "crosspost-reader",
        "shelluser-cable-labels",
        "subshell-poll",
        "spoiler-rack",
        "nsfw-cable-horror",
    )

    val genericCommentPayloadSignatures: List<String> = listOf(
        "The health check stays readable if `adb devices` is an explicit gate before the package query.",
        "My honest reaction when the failover worked on the first pull test 😮‍💨👌",
        "The important bit is density. Material can work for a reader, but only if every surface earns the space it consumes. 🔥",
        "This feels like its own app instead of a reskin. A dedicated subreddit destination is the strongest change here.",
        "Friendly reminder: keep screenshots readable and mark spoilers before posting. Thanks! 💚",
        "The vertical homemade fixture is painfully accurate 😂",
    )

    val profileCommentIds: List<String> = listOf(
        "pc-root-backup",
        "pc-root-terminal",
        "pc-root-failover",
        "pc-shelluser-failover",
        "pc-shelluser-widget",
        "pc-shelluser-command",
        "pc-shelluser-offline",
        "pc-shelluser-material",
        "pc-shelluser-crosspost",
        "pc-packetmoss-dns",
        "pc-packetmoss-rack",
        "pc-packetmoss-offline",
        "pc-tildehome-command",
        "pc-tildehome-dotfiles",
        "pc-tildehome-terminal",
        "pc-spritewitch-gallery",
        "pc-spritewitch-palette",
        "pc-spritewitch-motion",
        "pc-compose-snapshot",
        "pc-compose-tap-target",
        "pc-compose-backstack",
    )

    val communityPayloadSignatures: List<String> = listOf(
        "A fast, local-first Reddit client prototype shaped in public.",
        "Twenty-six deterministic local video fixtures for viewport ownership, playback, audio and lifecycle validation.",
        "Provider-neutral adaptive, split-track and fallback fixtures for the subshell media engine.",
        "News, apps, devices and the wonderfully messy Android ecosystem.",
        "Open source, kernels, desktops and tiny shell victories.",
        "Servers, networking and projects that began as one spare Raspberry Pi.",
        "Tiny canvases, deliberate pixels and very large colour palettes.",
        "Switches, keycaps and keyboards louder than your opinions.",
        "Own your services, own your data, own the maintenance window.",
        "Warm rooms, quiet corners and places you would rather be.",
    )

    val userPayloadSignatures: List<String> = listOf(
        "Local subshell test account. Android, Linux and a home lab with more checkpoints than spare cables.",
        "Homelab diagrams, tiny status screens and calm DNS failovers.",
        "Linux, keyboards and distraction-free desks.",
        "Pixel art loops made one frame at a time.",
        "Android UI experiments and long comment threads.",
        "Linux, Android and a home lab that is always one cable away from becoming a learning opportunity.",
    )

    val inboxPayloadSignatures: List<String> = listOf(
        "A dedicated subreddit destination is the strongest change here.",
        "@root the failover checklist you shared worked perfectly.",
        "I collected the remaining spacing issues in one short list.",
        "The weekly support thread is now live.",
    )
}

internal val ProtoClean01Migration5To6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val signatures = ProtoClean01LegacyFixtureSignatures

        db.execSQL(
            """
            CREATE TEMP TABLE IF NOT EXISTS `protoclean01a3_affected_queries` (
                `query_key` TEXT NOT NULL PRIMARY KEY
            )
            """.trimIndent(),
        )
        db.execSqlWithStrings(
            """
            INSERT OR IGNORE INTO `protoclean01a3_affected_queries` (`query_key`)
            SELECT DISTINCT `query_key`
            FROM `cached_query_entries`
            WHERE `post_id` IN (${sqlPlaceholders(signatures.postIds.size)})
            """.trimIndent(),
            signatures.postIds,
        )

        db.execSQL(
            """
            CREATE TEMP TABLE IF NOT EXISTS `protoclean01a3_affected_subscription_accounts` (
                `account_stable_id` TEXT NOT NULL PRIMARY KEY
            )
            """.trimIndent(),
        )
        db.execSqlWithStrings(
            """
            INSERT OR IGNORE INTO `protoclean01a3_affected_subscription_accounts` (`account_stable_id`)
            SELECT DISTINCT `account_stable_id`
            FROM `cached_subscriptions`
            WHERE ${sqlContainsAny("payload", signatures.communityPayloadSignatures.size)}
            """.trimIndent(),
            encodedCacheFieldSignatures(signatures.communityPayloadSignatures),
        )

        db.execSQL(
            """
            CREATE TEMP TABLE IF NOT EXISTS `protoclean01a3_affected_discovery_keys` (
                `cache_key` TEXT NOT NULL PRIMARY KEY
            )
            """.trimIndent(),
        )
        db.execSqlWithStrings(
            """
            INSERT OR IGNORE INTO `protoclean01a3_affected_discovery_keys` (`cache_key`)
            SELECT DISTINCT `cache_key`
            FROM `cached_community_discovery_entries`
            WHERE ${sqlContainsAny("payload", signatures.communityPayloadSignatures.size)}
            """.trimIndent(),
            encodedCacheFieldSignatures(signatures.communityPayloadSignatures),
        )

        val directMutationKeys = signatures.postIds.flatMap { postId ->
            listOf("saved:$postId", "vote:$postId", "poll:$postId")
        }
        val commentMutationPrefixes = signatures.postIds.map { postId ->
            "comment:$postId:"
        }
        db.execSqlWithStrings(
            """
            DELETE FROM `local_mutations`
            WHERE `mutation_key` IN (${sqlPlaceholders(directMutationKeys.size)})
               OR (${sqlStartsWithAny("mutation_key", commentMutationPrefixes.size)})
            """.trimIndent(),
            directMutationKeys + commentMutationPrefixes,
        )

        db.execSqlWithStrings(
            """
            DELETE FROM `cached_comments`
            WHERE `post_id` IN (${sqlPlaceholders(signatures.postIds.size)})
               OR (
                    `post_id` = '__all_comments__'
                    AND (${sqlContainsAny("payload", signatures.genericCommentPayloadSignatures.size)})
               )
            """.trimIndent(),
            signatures.postIds + encodedCacheFieldSignatures(signatures.genericCommentPayloadSignatures),
        )
        db.execSqlWithStrings(
            """
            DELETE FROM `cached_profile_comments`
            WHERE `post_id` IN (${sqlPlaceholders(signatures.postIds.size)})
               OR `comment_id` IN (${sqlPlaceholders(signatures.profileCommentIds.size)})
            """.trimIndent(),
            signatures.postIds + signatures.profileCommentIds,
        )

        db.execSqlWithStrings(
            """
            DELETE FROM `cached_query_entries`
            WHERE `post_id` IN (${sqlPlaceholders(signatures.postIds.size)})
            """.trimIndent(),
            signatures.postIds,
        )
        db.execSqlWithStrings(
            """
            DELETE FROM `cached_posts`
            WHERE `post_id` IN (${sqlPlaceholders(signatures.postIds.size)})
            """.trimIndent(),
            signatures.postIds,
        )
        db.execSQL(
            """
            UPDATE `cached_queries`
            SET `last_successful_refresh_epoch_ms` = 0,
                `next_cursor` = NULL,
                `terminal` = 0
            WHERE `query_key` IN (
                SELECT `query_key` FROM `protoclean01a3_affected_queries`
            )
            """.trimIndent(),
        )

        db.execSqlWithStrings(
            """
            DELETE FROM `cached_communities`
            WHERE ${sqlContainsAny("payload", signatures.communityPayloadSignatures.size)}
            """.trimIndent(),
            encodedCacheFieldSignatures(signatures.communityPayloadSignatures),
        )
        db.execSqlWithStrings(
            """
            DELETE FROM `cached_subscriptions`
            WHERE ${sqlContainsAny("payload", signatures.communityPayloadSignatures.size)}
            """.trimIndent(),
            encodedCacheFieldSignatures(signatures.communityPayloadSignatures),
        )
        db.execSQL(
            """
            UPDATE `cached_subscription_lists`
            SET `last_successful_refresh_epoch_ms` = 0
            WHERE `account_stable_id` IN (
                SELECT `account_stable_id`
                FROM `protoclean01a3_affected_subscription_accounts`
            )
            """.trimIndent(),
        )

        db.execSqlWithStrings(
            """
            DELETE FROM `cached_community_discovery_entries`
            WHERE ${sqlContainsAny("payload", signatures.communityPayloadSignatures.size)}
            """.trimIndent(),
            encodedCacheFieldSignatures(signatures.communityPayloadSignatures),
        )
        db.execSQL(
            """
            UPDATE `cached_community_discovery_lists`
            SET `last_successful_refresh_epoch_ms` = 0
            WHERE `cache_key` IN (
                SELECT `cache_key`
                FROM `protoclean01a3_affected_discovery_keys`
            )
            """.trimIndent(),
        )

        db.execSqlWithStrings(
            """
            DELETE FROM `cached_users`
            WHERE ${sqlContainsAny("payload", signatures.userPayloadSignatures.size)}
            """.trimIndent(),
            encodedCacheFieldSignatures(signatures.userPayloadSignatures),
        )
        db.execSqlWithStrings(
            """
            DELETE FROM `cached_inbox`
            WHERE ${sqlContainsAny("payload", signatures.inboxPayloadSignatures.size)}
            """.trimIndent(),
            encodedCacheFieldSignatures(signatures.inboxPayloadSignatures),
        )

        db.execSQL("DROP TABLE IF EXISTS `protoclean01a3_affected_queries`")
        db.execSQL("DROP TABLE IF EXISTS `protoclean01a3_affected_subscription_accounts`")
        db.execSQL("DROP TABLE IF EXISTS `protoclean01a3_affected_discovery_keys`")
    }
}

private fun SupportSQLiteDatabase.execSqlWithStrings(
    sql: String,
    values: List<String>,
) {
    require(values.isNotEmpty()) { "PROTOCLEAN01A3 SQL values cannot be empty" }
    execSQL(sql, values.toTypedArray())
}

private fun encodedCacheFieldSignatures(values: List<String>): List<String> =
    values.map { value -> "${value.length}:$value" }

private fun sqlPlaceholders(count: Int): String {
    require(count > 0) { "PROTOCLEAN01A3 SQL placeholder count must be positive" }
    return List(count) { "?" }.joinToString(", ")
}

private fun sqlContainsAny(
    column: String,
    count: Int,
): String {
    require(count > 0) { "PROTOCLEAN01A3 payload signature count must be positive" }
    return List(count) { "instr(`$column`, ?) > 0" }.joinToString(" OR ")
}

private fun sqlStartsWithAny(
    column: String,
    count: Int,
): String {
    require(count > 0) { "PROTOCLEAN01A3 prefix signature count must be positive" }
    return List(count) { "instr(`$column`, ?) = 1" }.joinToString(" OR ")
}
