package io.github.brealorg.subshell.model

/** What a tap on "Random NSFW community" found. */
sealed interface RandomNsfwCommunityPick {
    data class Found(val name: String) : RandomNsfwCommunityPick

    /**
     * #263: Reddit answered both reads and marked nothing it offered NSFW.
     * Seen for a reader whose Reddit search shows no NSFW communities at
     * all, so trying again does not help. The counts are how many
     * communities each read returned (for the diagnostics log).
     */
    data class NoneOffered(
        val popularCommunities: Int,
        val searchedCommunities: Int,
    ) : RandomNsfwCommunityPick

    /**
     * ISSUE293_LANGUAGE_FILTER_V1: Reddit offered NSFW communities, but every
     * one is known to be in a language the reader left out.
     */
    data object NoneInShownLanguages : RandomNsfwCommunityPick

    /** Reddit could not be asked (a read failed or no loader). */
    data object Failed : RandomNsfwCommunityPick
}
