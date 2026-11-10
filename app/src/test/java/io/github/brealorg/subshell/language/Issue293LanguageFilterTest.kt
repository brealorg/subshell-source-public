package io.github.brealorg.subshell.language

import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.PostKind
import io.github.brealorg.subshell.ui.SubshellSettings
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.screens.shownLanguagesSummary
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** #293: hide communities and search results the device is sure are in a left-out language. ISSUE293_LANGUAGE_FILTER_V1. */
class Issue293LanguageFilterTest {
    private val norwegian = "Et sted for mennesker som vil diskutere aksjer og fond i Norge"
    private val english = "A place for people who want to talk about stocks and funds"

    /** Norwegian text is Bokmål, English is English, both sure; "mumble" is unsure. */
    private val detector = LanguageDetector { text ->
        when {
            text.contains("mumble") -> "de" to 0.4f
            text.contains("Norge") -> "nb" to 0.97f
            else -> "en" to 0.99f
        }
    }

    @Before
    fun setUp() {
        ContentLanguages.clear()
        ContentLanguages.detector = detector
        ContentLanguages.shownLanguages = emptySet()
    }

    @After
    fun tearDown() {
        ContentLanguages.clear()
        ContentLanguages.detector = null
        ContentLanguages.shownLanguages = emptySet()
    }

    @Test
    fun aLanguageIsKnownOnlyForLongEnoughTextAndASureGuess() {
        assertEquals("no", knownLanguageOf(norwegian, detector))
        assertEquals("en", knownLanguageOf(english, detector))
        // Short text gave confident nonsense on the phone (CaptionPolska → sv 0.88).
        assertNull(knownLanguageOf("Norge.", detector))
        assertNull(knownLanguageOf("mumble mumble mumble mumble mumble", detector))
        assertEquals("pt", normalizedLanguageTag("pt-BR"))
        assertEquals("no", normalizedLanguageTag("nn"))
        assertEquals("zh", normalizedLanguageTag("zh-Hans"))
        assertNull(normalizedLanguageTag("und"))
    }

    @Test
    fun redditsOwnLanguageCountsOnlyWhenItAgreesWithTheDevice() {
        val unsure = LanguageDetector { "de" to 0.6f }
        val text = "Deutsche Memes nur zum Spaß und für alle hier"
        assertNull(knownLanguageOf(text, unsure))
        assertEquals("de", knownLanguageOf(text, unsure, hint = "de"))
        // Reddit says English (its usual mistake): no agreement, still unknown.
        assertNull(knownLanguageOf(text, unsure, hint = "en"))
        // Below the agreeing floor nothing counts.
        assertNull(knownLanguageOf(text, LanguageDetector { "de" to 0.4f }, hint = "de"))
        // Region and script forms of Reddit's lang agree too.
        assertEquals("pt", knownLanguageOf(text, LanguageDetector { "pt" to 0.6f }, hint = "pt_BR"))
    }

    @Test
    fun theTextToldIsNameAndDescriptionAndTitleAndBody() {
        assertEquals(
            "MangaDE. Das deutsche Subreddit für Manga",
            communityLanguageText("MangaDE", "Das deutsche Subreddit für Manga"),
        )
        assertEquals("Deutsch. Tüdelü und Fallera", communityLanguageText("Deutsch", "Tüdelü und Fallera"))
        assertEquals("Only a name", communityLanguageText("Only a name", null))
        assertEquals("Title", postLanguageText(" Title ", ""))
        assertEquals("Title. " + "b".repeat(300), postLanguageText("Title", "b".repeat(400)))
        assertEquals("3 results hidden by language", io.github.brealorg.subshell.ui.components.languageHiddenSummaryText(3, false))
        assertEquals("Showing 1 result in other languages", io.github.brealorg.subshell.ui.components.languageHiddenSummaryText(1, true))
    }

    @Test
    fun hintsReachTheDetection() = runBlocking {
        ContentLanguages.detector = LanguageDetector { "de" to 0.6f }
        ContentLanguages.shownLanguages = setOf("en")
        ContentLanguages.detectAll(
            listOf(
                communityLanguageKey("withHint") to "Deutsche Memes nur zum Spaß und für alle hier",
                communityLanguageKey("withoutHint") to "Deutsche Memes nur zum Spaß und für alle hier",
            ),
            hints = mapOf(communityLanguageKey("withHint") to "de"),
        )
        assertTrue(ContentLanguages.hidesCommunity("withHint"))
        assertFalse(ContentLanguages.hidesCommunity("withoutHint"))
    }

    @Test
    fun onlyAKnownLeftOutLanguageIsHidden() {
        assertFalse(languageHides("no", emptySet()))
        assertFalse(languageHides(null, setOf("en")))
        assertFalse(languageHides("en", setOf("en", "no")))
        assertTrue(languageHides("de", setOf("en", "no")))
    }

    @Test
    fun nothingIsToldWhileTheFilterIsOff() = runBlocking {
        ContentLanguages.detectAll(listOf(communityLanguageKey("AksjerNorge") to norwegian))
        assertNull(ContentLanguages.detected(communityLanguageKey("AksjerNorge")))

        ContentLanguages.shownLanguages = setOf("en")
        ContentLanguages.detectAll(
            listOf(
                communityLanguageKey("AksjerNorge") to norwegian,
                communityLanguageKey("stocks") to english,
                postLanguageKey("t3_abc") to "Hva synes dere om Norge og nissene på melkekartongen",
            ),
        )
        assertEquals("no", ContentLanguages.detected(communityLanguageKey("r/aksjernorge")))
        assertEquals("no", ContentLanguages.detected(postLanguageKey("abc")))
        assertTrue(ContentLanguages.hidesCommunity("AksjerNorge"))
        assertFalse(ContentLanguages.hidesCommunity("stocks"))
        assertFalse(ContentLanguages.hidesCommunity("neverSeen"))
    }

    @Test
    fun offIsTheDefaultAndTheChoiceIsStoredAndRestored() {
        assertEquals(emptySet<String>(), SubshellSettings().shownLanguages)
        assertEquals("shown_languages", SubshellSettings.SHOWN_LANGUAGES)
        val stored = SubshellSettings(shownLanguages = setOf("no", "en")).toStorageMap()
        assertEquals("en,no", stored[SubshellSettings.SHOWN_LANGUAGES])
        assertEquals(setOf("en", "no"), SubshellSettings.fromStorageMap(stored).shownLanguages)
        // Unknown codes are dropped; a file from before the option is off.
        val odd = stored + (SubshellSettings.SHOWN_LANGUAGES to "en, xx ,NO")
        assertEquals(setOf("en", "no"), SubshellSettings.fromStorageMap(odd).shownLanguages)
        val legacy = stored - SubshellSettings.SHOWN_LANGUAGES
        assertEquals(emptySet<String>(), SubshellSettings.fromStorageMap(legacy).shownLanguages)
        assertEquals("Off", shownLanguagesSummary(emptySet()))
        assertEquals("English, Norwegian", shownLanguagesSummary(setOf("no", "en")))
        assertEquals("English, Norwegian +2", shownLanguagesSummary(setOf("no", "en", "sv", "da")))
    }

    @Test
    fun theStateHidesByTheChosenLanguages() = runBlocking {
        val persisted = linkedMapOf<String, String>()
        val state = SubshellState(persistPreference = { key, value -> persisted[key] = value })
        ContentLanguages.shownLanguages = setOf("en")
        ContentLanguages.detectAll(
            listOf(
                communityLanguageKey("AksjerNorge") to norwegian,
                postLanguageKey("t3_abc") to "Hva synes dere om Norge og nissene på melkekartongen",
            ),
        )
        val post = Post(
            id = "t3_abc", community = "norge", author = "a", age = "1h", title = "x",
            score = 1, comments = 0, kind = PostKind.TEXT,
        )

        // Off: nothing hidden even though the language is known.
        assertFalse(state.languageHidesCommunity("AksjerNorge"))
        assertFalse(state.languageHidesSearchPost(post))

        state.updateShownLanguages(setOf("en", "xx"))
        assertEquals(setOf("en"), state.shownLanguages)
        assertEquals("en", persisted[SubshellSettings.SHOWN_LANGUAGES])
        assertTrue(state.languageHidesCommunity("AksjerNorge"))
        assertTrue(state.languageHidesSearchPost(post))

        state.updateShownLanguages(setOf("en", "no"))
        assertFalse(state.languageHidesCommunity("AksjerNorge"))
    }

    @Test
    fun aListAlreadyOnScreenIsToldOnceTheFilterIsOn() = runBlocking {
        val state = SubshellState()
        val entries = listOf(
            communityLanguageKey("AksjerNorge") to norwegian,
            communityLanguageKey("short") to "Hei",
        )
        // Off: nothing asked, nothing changes.
        state.detectLanguages(entries)
        assertFalse(ContentLanguages.told(communityLanguageKey("AksjerNorge")))
        assertEquals(0, state.languageDetectionRevision)

        state.updateShownLanguages(setOf("en"))
        assertEquals(setOf("en"), ContentLanguages.shownLanguages)
        state.detectLanguages(entries)
        assertEquals(1, state.languageDetectionRevision)
        assertTrue(state.languageHidesCommunity("AksjerNorge"))
        // The unsure answer is remembered, so the same list asks nothing again.
        assertTrue(ContentLanguages.told(communityLanguageKey("short")))
        assertNull(ContentLanguages.detected(communityLanguageKey("short")))
        state.detectLanguages(entries)
        assertEquals(1, state.languageDetectionRevision)
    }
}
