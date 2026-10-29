package app.subshell.prototype.model

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

    /** Reddit could not be asked (a read failed or no loader). */
    data object Failed : RandomNsfwCommunityPick
}
