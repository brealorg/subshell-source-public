package io.github.brealorg.subshell.data.privateprefs

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRIVATEPREFS02_NO_CODE_READS_A_SEALED_FILE_RAW_V1: a file that holds sealed keys is opened through
 * SealedKeysPreferences. Code that opens it directly would read an `e1:` text where a set or a
 * string is expected. A file listed here is opened raw on purpose (it reads only keys that are not
 * sealed, or only preloads the file).
 */
class PrivatePrefs02NoRawAccessTest {
    private val sealedFileNames = listOf(
        "\"subshell_preferences\"",
        "SUBSHELL_PREFERENCES",
        "COMMUNITY_POST_WATCH_PREFERENCES",
        "DEVICE_PREFERENCES_NAME",
        "REDDIT_FAVORITES_PREFERENCES",
        "\"subshell_conversation_replies\"",
    )

    // Why each is raw: the preload only fills Android's own file cache; the others read booleans.
    private val allowed = setOf(
        "data/privateprefs/SealedKeysPreferences.kt",
        "ui/app/StartupPreferences.kt",
        "auth/SharedAuthRuntime.kt",
        "communityposts/CommunityPostWorker.kt",
        "ui/RedditLinkHandling.kt",
        "inbox/InboxBackgroundSync.kt",
    )

    @Test
    fun noCodeOpensASealedFileRawExceptWhereItIsListed() {
        val root = File("src/main/java/io/github/brealorg/subshell")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> file.relativeTo(root).path !in allowed }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val opensRaw = line.contains("getSharedPreferences(") &&
                        sealedFileNames.any { name -> line.contains(name) }
                    if (opensRaw) "${file.relativeTo(root).path}:${index + 1}" else null
                }
            }
            .toList()

        assertTrue("These open a sealed preferences file raw: $offenders", offenders.isEmpty())
    }
}
