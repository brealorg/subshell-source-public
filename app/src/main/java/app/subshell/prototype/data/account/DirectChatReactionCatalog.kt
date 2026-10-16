package app.subshell.prototype.data.account

// SUBSHELL_MESSAGES02E2A_R2_REDDIT_REACTION_CATALOG_V1

data class DirectChatReactionAsset(
    val key: String,
    val uri: String,
    val animated: Boolean,
) {
    init {
        require(key.isNotBlank()) { "Direct Chat reaction key cannot be blank" }
        require(uri == "https://i.redd.it/$key") {
            "Direct Chat reaction asset URI must match its Reddit reaction key"
        }
    }
}

internal val redditDirectChatReactionCatalog: List<DirectChatReactionAsset> = listOf(
    DirectChatReactionAsset(key = "8r21ukpfa7081.gif", uri = "https://i.redd.it/8r21ukpfa7081.gif", animated = true),
    DirectChatReactionAsset(key = "ytv3x0sfa7081.png", uri = "https://i.redd.it/ytv3x0sfa7081.png", animated = false),
    DirectChatReactionAsset(key = "2o3aooqfa7081.gif", uri = "https://i.redd.it/2o3aooqfa7081.gif", animated = true),
    DirectChatReactionAsset(key = "foyijyyga7081.gif", uri = "https://i.redd.it/foyijyyga7081.gif", animated = true),
    DirectChatReactionAsset(key = "jvuspmbga7081.gif", uri = "https://i.redd.it/jvuspmbga7081.gif", animated = true),
    DirectChatReactionAsset(key = "79opsq0ha7081.gif", uri = "https://i.redd.it/79opsq0ha7081.gif", animated = true),
    DirectChatReactionAsset(key = "7rc03trga7081.gif", uri = "https://i.redd.it/7rc03trga7081.gif", animated = true),
    DirectChatReactionAsset(key = "mag7v6tfa7081.gif", uri = "https://i.redd.it/mag7v6tfa7081.gif", animated = true),
    DirectChatReactionAsset(key = "iuqmp7ufa7081.gif", uri = "https://i.redd.it/iuqmp7ufa7081.gif", animated = true),
    DirectChatReactionAsset(key = "zn7iubvfa7081.gif", uri = "https://i.redd.it/zn7iubvfa7081.gif", animated = true),
    DirectChatReactionAsset(key = "qzl5vyxfa7081.gif", uri = "https://i.redd.it/qzl5vyxfa7081.gif", animated = true),
    DirectChatReactionAsset(key = "mi2jolzfa7081.gif", uri = "https://i.redd.it/mi2jolzfa7081.gif", animated = true),
    DirectChatReactionAsset(key = "k7ry7t1ga7081.gif", uri = "https://i.redd.it/k7ry7t1ga7081.gif", animated = true),
    DirectChatReactionAsset(key = "tspuf53ga7081.gif", uri = "https://i.redd.it/tspuf53ga7081.gif", animated = true),
    DirectChatReactionAsset(key = "evwks24ga7081.gif", uri = "https://i.redd.it/evwks24ga7081.gif", animated = true),
    DirectChatReactionAsset(key = "dfxygs4ga7081.gif", uri = "https://i.redd.it/dfxygs4ga7081.gif", animated = true),
    DirectChatReactionAsset(key = "ax7wu47ga7081.gif", uri = "https://i.redd.it/ax7wu47ga7081.gif", animated = true),
    DirectChatReactionAsset(key = "uy83aa8ga7081.gif", uri = "https://i.redd.it/uy83aa8ga7081.gif", animated = true),
    DirectChatReactionAsset(key = "t2r5xc9ga7081.gif", uri = "https://i.redd.it/t2r5xc9ga7081.gif", animated = true),
    DirectChatReactionAsset(key = "ksz4fmaga7081.gif", uri = "https://i.redd.it/ksz4fmaga7081.gif", animated = true),
    DirectChatReactionAsset(key = "mp9zclcga7081.gif", uri = "https://i.redd.it/mp9zclcga7081.gif", animated = true),
    DirectChatReactionAsset(key = "9ut7iedga7081.gif", uri = "https://i.redd.it/9ut7iedga7081.gif", animated = true),
    DirectChatReactionAsset(key = "ul2w17ega7081.gif", uri = "https://i.redd.it/ul2w17ega7081.gif", animated = true),
    DirectChatReactionAsset(key = "0ku6twega7081.gif", uri = "https://i.redd.it/0ku6twega7081.gif", animated = true),
    DirectChatReactionAsset(key = "cdnhvqfga7081.gif", uri = "https://i.redd.it/cdnhvqfga7081.gif", animated = true),
    DirectChatReactionAsset(key = "b5s6cohga7081.gif", uri = "https://i.redd.it/b5s6cohga7081.gif", animated = true),
    DirectChatReactionAsset(key = "av9z8iiga7081.gif", uri = "https://i.redd.it/av9z8iiga7081.gif", animated = true),
    DirectChatReactionAsset(key = "00brcfjga7081.gif", uri = "https://i.redd.it/00brcfjga7081.gif", animated = true),
    DirectChatReactionAsset(key = "pleyoikga7081.gif", uri = "https://i.redd.it/pleyoikga7081.gif", animated = true),
    DirectChatReactionAsset(key = "m7uy86lga7081.gif", uri = "https://i.redd.it/m7uy86lga7081.gif", animated = true),
    DirectChatReactionAsset(key = "wbrgz1nga7081.gif", uri = "https://i.redd.it/wbrgz1nga7081.gif", animated = true),
    DirectChatReactionAsset(key = "fsg2a1oga7081.gif", uri = "https://i.redd.it/fsg2a1oga7081.gif", animated = true),
    DirectChatReactionAsset(key = "8zx5ixoga7081.gif", uri = "https://i.redd.it/8zx5ixoga7081.gif", animated = true),
    DirectChatReactionAsset(key = "xjls1pqga7081.gif", uri = "https://i.redd.it/xjls1pqga7081.gif", animated = true),
    DirectChatReactionAsset(key = "d45pfmsga7081.gif", uri = "https://i.redd.it/d45pfmsga7081.gif", animated = true),
    DirectChatReactionAsset(key = "g6akcitga7081.gif", uri = "https://i.redd.it/g6akcitga7081.gif", animated = true),
    DirectChatReactionAsset(key = "t1djdguga7081.gif", uri = "https://i.redd.it/t1djdguga7081.gif", animated = true),
    DirectChatReactionAsset(key = "19b5q4vga7081.gif", uri = "https://i.redd.it/19b5q4vga7081.gif", animated = true),
    DirectChatReactionAsset(key = "3s0glewga7081.gif", uri = "https://i.redd.it/3s0glewga7081.gif", animated = true),
    DirectChatReactionAsset(key = "7url39xga7081.gif", uri = "https://i.redd.it/7url39xga7081.gif", animated = true),
    DirectChatReactionAsset(key = "d2kn6yxga7081.gif", uri = "https://i.redd.it/d2kn6yxga7081.gif", animated = true),
    DirectChatReactionAsset(key = "3i2arwzga7081.gif", uri = "https://i.redd.it/3i2arwzga7081.gif", animated = true),
)

private val redditDirectChatReactionByKey: Map<String, DirectChatReactionAsset> =
    redditDirectChatReactionCatalog.associateBy(DirectChatReactionAsset::key)

internal fun redditDirectChatReactionAsset(key: String): DirectChatReactionAsset? =
    redditDirectChatReactionByKey[key.trim()]
