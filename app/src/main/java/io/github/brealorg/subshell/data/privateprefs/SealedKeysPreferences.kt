package io.github.brealorg.subshell.data.privateprefs

import android.content.Context
import android.content.SharedPreferences
import io.github.brealorg.subshell.data.cache.CachePayloadSeal
import java.util.concurrent.ConcurrentHashMap

/**
 * PRIVATEPREFS01_PRIVATE_SETTINGS_ARE_SEALED_V1: the settings file also holds what is about the
 * reader and not about the app: the communities they hide or mute, their private notes on
 * users, saved searches, post drafts and the moderator's saved responses. [SealedKeysPreferences]
 * looks like the file (it is a [SharedPreferences]) and stores those keys, and only those, sealed
 * under the cache key (CachePayloadSeal). Everything else passes straight through.
 *
 * The rules:
 * - A value an older build wrote in plain text is read as it is; [sealLegacyValues] seals it.
 * - A value that cannot be opened reads as missing, and is never lost by a write: the first write
 *   over it keeps the old value under [UNREADABLE_KEY_PREFIX] (like the moderator's responses
 *   already did for text they could not read).
 * - A value that cannot be sealed is not written (the in-memory state keeps it, the next change
 *   writes again: LOWUI1_FAILED_PREFERENCE_WRITE_IS_NOT_A_CRASH_V1). Never plain text.
 * - A sealed value is a text with a type marker, so a set comes back as a set.
 */
internal class SealedKeysPreferences(
    private val delegate: SharedPreferences,
    sealOf: () -> CachePayloadSeal,
    private val isSealedKey: (String) -> Boolean = ::isPrivatePreferenceKey,
) : SharedPreferences {
    private val seal by lazy(sealOf)

    // Opening a value costs a Keystore call. The sealed text is unique to its value, so the opened
    // text is remembered under it; a write remembers what it wrote, and warm() fills it for the
    // values already stored (off the main thread, with the other start-up preferences).
    private val opened = ConcurrentHashMap<String, String>()
    private val latest = ConcurrentHashMap<String, String>()

    /** Remembers [plain] for [stored] and forgets what was remembered for the key before. */
    private fun remember(key: String, stored: String, plain: String) {
        val memo = memoKey(key, stored)
        latest.put(key, memo)?.takeIf { it != memo }?.let(opened::remove)
        opened[memo] = plain
    }

    /** Opens every sealed value once, so the first reads on the main thread find them in memory. */
    fun warm() {
        delegate.all.forEach { (key, value) ->
            if (isSealedKey(key) && value is String && value.startsWith(CachePayloadSeal.SEALED_PREFIX)) {
                openedOrNull(key, value)
            }
        }
    }

    /**
     * PRIVATEPREFS03_PRIVATE_DATA_STATUS_V1: how many sealed values open, how many do not (the key is
     * gone or the value is damaged) and how many old copies were kept after a write over one that did
     * not. Counts only; no value leaves this function.
     */
    fun status(): PrivateDataStatus {
        var opens = 0
        var cannotOpen = 0
        var kept = 0
        delegate.all.forEach { (key, value) ->
            when {
                key.startsWith(UNREADABLE_KEY_PREFIX) -> kept += 1
                !isSealedKey(key) || value !is String -> Unit
                value.startsWith(CachePayloadSeal.SEALED_PREFIX) ->
                    if (openedOrNull(key, value) != null) opens += 1 else cannotOpen += 1
                value.startsWith(CachePayloadSeal.UNAVAILABLE_PREFIX) -> cannotOpen += 1
            }
        }
        return PrivateDataStatus(opens, cannotOpen, kept)
    }

    /** Seals the values an older build stored in plain text. Returns how many. */
    fun sealLegacyValues(): Int {
        if (!seal.encrypts) return 0
        val editor = delegate.edit()
        var sealed = 0
        delegate.all.forEach { (key, value) ->
            if (!isSealedKey(key)) return@forEach
            val plain = when {
                value is Set<*> -> SET_MARKER + value.filterIsInstance<String>().sorted().joinToString("\n")
                value is String && !value.startsWith(CachePayloadSeal.SEALED_PREFIX) &&
                    !value.startsWith(CachePayloadSeal.UNAVAILABLE_PREFIX) -> TEXT_MARKER + value
                else -> return@forEach
            }
            val stored = seal.seal(plain, placeOf(key))
            if (stored.startsWith(CachePayloadSeal.UNAVAILABLE_PREFIX)) return@forEach
            remember(key, stored, plain)
            editor.putString(key, stored)
            sealed += 1
        }
        if (sealed > 0) editor.apply()
        return sealed
    }

    override fun getAll(): MutableMap<String, *> {
        val result = HashMap<String, Any?>()
        delegate.all.forEach { (key, value) ->
            if (isSealedKey(key) && value is String) {
                readable(key, value)?.let { result[key] = it }
            } else {
                result[key] = value
            }
        }
        return result
    }

    override fun getString(key: String, defValue: String?): String? {
        if (!isSealedKey(key)) return delegate.getString(key, defValue)
        val stored = storedString(key) ?: return defValue
        return readable(key, stored) as? String ?: defValue
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        if (!isSealedKey(key)) return delegate.getStringSet(key, defValues)
        val stored = storedString(key)
        if (stored == null) {
            return storedSet(key)?.toMutableSet() ?: defValues
        }
        @Suppress("UNCHECKED_CAST")
        return (readable(key, stored) as? Set<String>)?.toMutableSet() ?: defValues
    }

    override fun getInt(key: String, defValue: Int): Int = delegate.getInt(key, defValue)

    override fun getLong(key: String, defValue: Long): Long = delegate.getLong(key, defValue)

    override fun getFloat(key: String, defValue: Float): Float = delegate.getFloat(key, defValue)

    override fun getBoolean(key: String, defValue: Boolean): Boolean = delegate.getBoolean(key, defValue)

    override fun contains(key: String): Boolean = delegate.contains(key)

    override fun edit(): SharedPreferences.Editor = SealingEditor(delegate.edit())

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = delegate.registerOnSharedPreferenceChangeListener(listener)

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = delegate.unregisterOnSharedPreferenceChangeListener(listener)

    private fun storedString(key: String): String? =
        try {
            delegate.getString(key, null)
        } catch (_: ClassCastException) {
            null
        }

    private fun storedSet(key: String): Set<String>? =
        try {
            delegate.getStringSet(key, null)
        } catch (_: ClassCastException) {
            null
        }

    /** The value as the caller wants it: a [String] or a [Set] of strings; null when it cannot be opened. */
    private fun readable(key: String, stored: String): Any? {
        if (!stored.startsWith(CachePayloadSeal.SEALED_PREFIX)) return stored
        val plain = openedOrNull(key, stored) ?: return null
        return when {
            plain.startsWith(TEXT_MARKER) -> plain.substring(TEXT_MARKER.length)
            plain.startsWith(SET_MARKER) ->
                plain.substring(SET_MARKER.length).split('\n').filter(String::isNotEmpty).toSet()
            else -> null
        }
    }

    private fun openedOrNull(key: String, stored: String): String? {
        opened[memoKey(key, stored)]?.let { return it }
        val plain = try {
            seal.open(stored, placeOf(key))
        } catch (_: RuntimeException) {
            return null
        }
        remember(key, stored, plain)
        return plain
    }

    private fun memoKey(key: String, stored: String): String = key + '\u0000' + stored

    private inner class SealingEditor(
        private val inner: SharedPreferences.Editor,
    ) : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            when {
                !isSealedKey(key) -> inner.putString(key, value)
                value == null -> remove(key)
                else -> putSealed(key, TEXT_MARKER + value)
            }
            return this
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
            when {
                !isSealedKey(key) -> inner.putStringSet(key, values)
                values == null -> remove(key)
                else -> putSealed(key, SET_MARKER + values.sorted().joinToString("\n"))
            }
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            inner.putInt(key, value)
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            inner.putLong(key, value)
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            inner.putFloat(key, value)
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            inner.putBoolean(key, value)
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            inner.remove(key)
            // A removed value leaves nothing behind: not the copy kept when it could not be opened either.
            if (isSealedKey(key)) inner.remove(UNREADABLE_KEY_PREFIX + key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            inner.clear()
            return this
        }

        override fun commit(): Boolean = inner.commit()

        override fun apply() = inner.apply()

        private fun putSealed(key: String, plain: String) {
            keepWhatCannotBeOpened(key)
            val stored = seal.seal(plain, placeOf(key))
            if (stored.startsWith(CachePayloadSeal.UNAVAILABLE_PREFIX)) return
            remember(key, stored, plain)
            inner.putString(key, stored)
        }

        /** The first write over a sealed value that cannot be opened keeps it, so it is not lost. */
        private fun keepWhatCannotBeOpened(key: String) {
            val current = storedString(key) ?: return
            if (!current.startsWith(CachePayloadSeal.SEALED_PREFIX) || openedOrNull(key, current) != null) return
            val keptKey = UNREADABLE_KEY_PREFIX + key
            if (!delegate.contains(keptKey)) inner.putString(keptKey, current)
        }
    }

    private fun placeOf(key: String): String = "prefs/$key"

    companion object {
        const val UNREADABLE_KEY_PREFIX = "unreadable_sealed:"
        private const val TEXT_MARKER = "T:"
        private const val SET_MARKER = "S:"

        const val SETTINGS_PREFERENCES_NAME = "subshell_preferences"
        const val DEVICE_PREFERENCES_NAME = "subshell_device_preferences"
        const val REDDIT_FAVORITES_PREFERENCES_NAME = "subshell_reddit_favorites"
        const val CONVERSATION_REPLIES_PREFERENCES_NAME = "subshell_conversation_replies"

        /** Which keys of each file are about the reader; a file not listed here has none. */
        private val SEALED_KEYS_BY_FILE: Map<String, (String) -> Boolean> = mapOf(
            SETTINGS_PREFERENCES_NAME to ::isPrivatePreferenceKey,
            DEVICE_PREFERENCES_NAME to { key -> DEVICE_SEALED_PREFIXES.any(key::startsWith) },
            REDDIT_FAVORITES_PREFERENCES_NAME to { key -> key.startsWith("names.") },
            CONVERSATION_REPLIES_PREFERENCES_NAME to { key -> key.startsWith("target:") },
        )

        private val lock = Any()
        private val shared = HashMap<Pair<Context, String>, SealedKeysPreferences>()

        /** The settings file, with the private keys sealed; used wherever the file was read before. */
        fun of(context: Context): SharedPreferences = instance(context)

        /** The file [name], sealed where it holds something about the reader; one object per file. */
        fun named(context: Context, name: String): SharedPreferences = instance(context, name)

        /** The one per process and file, so the memory of opened values is shared; the start-up job warms it. */
        fun instance(context: Context, name: String = SETTINGS_PREFERENCES_NAME): SealedKeysPreferences {
            val appContext = context.applicationContext
            return synchronized(lock) {
                shared.getOrPut(appContext to name) {
                    SealedKeysPreferences(
                        delegate = appContext.getSharedPreferences(name, Context.MODE_PRIVATE),
                        sealOf = { CachePayloadSeal.production() },
                        isSealedKey = requireNotNull(SEALED_KEYS_BY_FILE[name]) { "No sealed keys for $name" },
                    )
                }
            }
        }

        /** Whether [key] is stored sealed in the file [fileName] (a file without sealed keys: never). */
        fun isSealedKey(fileName: String, key: String): Boolean = SEALED_KEYS_BY_FILE[fileName]?.invoke(key) == true

        /** The counts of [status] over every file that has sealed keys. */
        fun statusOf(context: Context): PrivateDataStatus =
            allInstances(context).map(SealedKeysPreferences::status).fold(PrivateDataStatus(0, 0, 0)) { sum, next ->
                PrivateDataStatus(
                    sum.opens + next.opens,
                    sum.cannotOpen + next.cannotOpen,
                    sum.keptCopies + next.keptCopies,
                )
            }

        /** Every file that has sealed keys, for the start-up job. */
        fun allInstances(context: Context): List<SealedKeysPreferences> =
            SEALED_KEYS_BY_FILE.keys.map { name -> instance(context, name) }
    }
}

private val SEALED_KEY_PREFIXES = listOf(
    // The communities, notes, searches and drafts of the reader.
    "hidden_community_names:",
    "community_mutes:",
    "user_tags:",
    "saved_searches:",
    "moderator_saved_responses",
    "post_composer_drafts",
    "post_composer_crosspost_drafts",
    "composer09_text_post_submit_attempt",
    // What the reader joined, favours, watches and visited.
    "joined_community_ids.",
    "favorite_community_names.",
    "favorite_custom_feed_paths:",
    "recent_community_visits.",
    "community_post_watch:",
    // What the reader did to posts (votes, saved, hidden, read, followed, polls); the boolean
    // "hide read" under the same prefix is not a string or a set and passes through.
    "account_post_actions.",
    // Settings that are histories or lists: where the reader has been, and what they filter.
    "comment_visit_history",
    "community_sort_preferences",
    "content_filter_",
)

private val DEVICE_SEALED_PREFIXES = listOf("search_history.v1.", "search_index.v1.")

/**
 * The keys that are about the reader. A test checks each one against the code that writes it, so a
 * renamed key cannot leave a value in plain text unnoticed.
 */
internal fun isPrivatePreferenceKey(key: String): Boolean = SEALED_KEY_PREFIXES.any { key.startsWith(it) }

/** What [SealedKeysPreferences.status] counted: values that open, values that do not, kept old copies. */
internal data class PrivateDataStatus(val opens: Int, val cannotOpen: Int, val keptCopies: Int) {
    /** The line the Diagnostics screen shows. */
    fun line(): String = when {
        opens == 0 && cannotOpen == 0 -> "Nothing private is stored sealed on this phone yet."
        cannotOpen == 0 -> "Private data on this phone: $opens sealed values, all of them open."
        else -> "Private data on this phone: $opens sealed values open, $cannotOpen cannot be opened " +
            "(the key is gone or the value is damaged). Old copies kept: $keptCopies."
    }
}
