package io.github.brealorg.subshell.language

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ISSUE293_LANGUAGE_FILTER_V1 (#293): the language of a community or a search
 * result, told by the device's own TextClassifier (no network, no library).
 * Measured on the Pixel 6 (2026-11-09, worklog/issue293-language): Reddit's own
 * community `lang` is unreliable (155 of 566 clearly foreign communities say
 * `en`), the device is right when it is sure and the text has some length, and
 * confidently wrong on very short text. So a language is only known when the
 * text is at least [LANGUAGE_MIN_TEXT_LENGTH] characters and the top guess
 * reaches [LANGUAGE_MIN_CONFIDENCE]; anything else is unknown and stays shown.
 */
internal const val LANGUAGE_MIN_TEXT_LENGTH = 30
internal const val LANGUAGE_MIN_CONFIDENCE = 0.8f

/**
 * When Reddit's own community `lang` names the same language as the device's
 * top guess, two independent sources agree and a lower confidence is enough.
 * In the measurement Reddit's mistakes were almost all `en` on foreign
 * communities, where the device's guess was not English, so they never agree.
 */
internal const val LANGUAGE_AGREEING_HINT_MIN_CONFIDENCE = 0.5f

/** The reader's choice of languages; only these are offered in Settings. */
internal val SELECTABLE_LANGUAGES: List<String> = listOf(
    "en", "no", "sv", "da", "fi", "is", "de", "nl", "fr", "es", "pt", "it", "pl", "cs", "hu",
    "ro", "el", "tr", "ru", "uk", "ar", "he", "hi", "id", "vi", "th", "ja", "ko", "zh",
)

/** The primary language of a BCP 47 tag; Norwegian Bokmål and Nynorsk are Norwegian. */
internal fun normalizedLanguageTag(tag: String): String? {
    val primary = tag.trim().substringBefore('-').substringBefore('_').lowercase()
    if (primary.isEmpty() || primary == "und") return null
    return when (primary) {
        "nb", "nn" -> "no"
        else -> primary
    }
}

internal fun interface LanguageDetector {
    /** The most likely language tag and its confidence, or null. */
    fun detect(text: String): Pair<String, Float>?
}

/**
 * The language of [text], or null when it is too short or the guess is not sure.
 * [hint] is Reddit's own `lang` for a community, when there is one.
 */
internal fun knownLanguageOf(text: String, detector: LanguageDetector, hint: String? = null): String? {
    val clean = text.trim().replace(Regex("\\s+"), " ")
    if (clean.length < LANGUAGE_MIN_TEXT_LENGTH) return null
    val (tag, confidence) = detector.detect(clean.take(LANGUAGE_DETECTION_MAX_TEXT)) ?: return null
    val language = normalizedLanguageTag(tag) ?: return null
    return when {
        confidence >= LANGUAGE_MIN_CONFIDENCE -> language
        confidence >= LANGUAGE_AGREEING_HINT_MIN_CONFIDENCE &&
            hint?.let(::normalizedLanguageTag) == language -> language
        else -> null
    }
}

/** Hidden only when the reader chose languages and this one is known and not among them. */
internal fun languageHides(detected: String?, shownLanguages: Set<String>): Boolean =
    shownLanguages.isNotEmpty() && detected != null && detected !in shownLanguages

internal fun communityLanguageKey(name: String): String =
    "community:" + name.trim().removePrefix("r/").lowercase()

internal fun postLanguageKey(id: String): String =
    "post:" + id.trim().lowercase().removePrefix("t3_")

/**
 * A community's title (or name) and its description. Measured on the Pixel 6
 * (2026-11-09): the description alone made the device less sure, not more
 * ("DeutschInfluencerNSFW. Postet alles über…" was German, without the name
 * it was unsure), so the name stays in front.
 */
internal fun communityLanguageText(title: String?, description: String?): String =
    listOfNotNull(title?.trim(), description?.trim())
        .filter(String::isNotEmpty)
        .joinToString(". ")

/** A post's title and the start of its text: more words, surer answers. */
internal fun postLanguageText(title: String, body: String): String =
    listOf(title.trim(), body.trim().take(POST_LANGUAGE_BODY_CHARS))
        .filter(String::isNotEmpty)
        .joinToString(". ")

/**
 * What the device found for the communities and posts read from Reddit, kept in
 * memory for this process. Detection runs only while the filter is on
 * ([shownLanguages], mirrored from the setting), so a reader who never turns it on pays
 * nothing; items read before it was turned on are unknown and stay shown.
 */
internal object ContentLanguages {
    /** Mirrored from the setting; empty = off. */
    @Volatile
    var shownLanguages: Set<String> = emptySet()

    val active: Boolean
        get() = shownLanguages.isNotEmpty()

    @Volatile
    var detector: LanguageDetector? = null

    private val known = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > LANGUAGE_CACHE_ENTRIES
    }

    /** Android 10 and later; on older versions nothing is known and nothing is hidden. */
    fun install(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            detector = TextClassifierLanguageDetector(context.applicationContext)
        }
    }

    /** The known language, or null when unknown (unsure, too short, or not told yet). */
    fun detected(key: String): String? = synchronized(known) { known[key] }?.takeIf(String::isNotEmpty)

    /** True once the device was asked, even when the answer was "unsure". */
    fun told(key: String): Boolean = synchronized(known) { known.containsKey(key) }

    fun hidesCommunity(name: String): Boolean =
        languageHides(detected(communityLanguageKey(name)), shownLanguages)

    /**
     * Finds the language of each (key, text) not yet known, off the main
     * thread; [hints] are Reddit's community `lang` by key.
     */
    suspend fun detectAll(entries: List<Pair<String, String>>, hints: Map<String, String> = emptyMap()) {
        if (!active || entries.isEmpty()) return
        val detector = detector ?: return
        withContext(Dispatchers.IO) {
            entries.forEach { (key, text) ->
                if (synchronized(known) { known.containsKey(key) }) return@forEach
                val language = try {
                    knownLanguageOf(text, detector, hints[key])
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                // An unsure answer is kept too (as ""), so it is not asked again.
                synchronized(known) { known[key] = language.orEmpty() }
            }
        }
    }

    internal fun clear() {
        synchronized(known) { known.clear() }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private class TextClassifierLanguageDetector(private val context: Context) : LanguageDetector {
    override fun detect(text: String): Pair<String, Float>? {
        val classifier = context.getSystemService(TextClassificationManager::class.java)
            ?.textClassifier
            ?: return null
        val result = classifier.detectLanguage(TextLanguage.Request.Builder(text).build())
        if (result.localeHypothesisCount == 0) return null
        val locale = result.getLocale(0)
        return locale.toLanguageTag() to result.getConfidenceScore(locale)
    }
}

private const val LANGUAGE_DETECTION_MAX_TEXT = 500
private const val POST_LANGUAGE_BODY_CHARS = 300
private const val LANGUAGE_CACHE_ENTRIES = 4_000
