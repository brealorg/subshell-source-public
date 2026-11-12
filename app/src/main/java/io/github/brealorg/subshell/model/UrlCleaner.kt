package io.github.brealorg.subshell.model

import java.net.URI

/**
 * CLEANURL01_TRACKING_PARAMETERS_V1: removes well-known tracking parameters
 * from a web link, locally and without any request. A parameter that is not in
 * the lists below is never touched, so a link that picks a page, a variant or
 * a time (`id=`, `v=`, `variation0=`, `t=`) keeps working. The original link
 * stays in the text the author wrote; only what is opened, copied or shared is
 * cleaned.
 */
internal object UrlCleaner {
    private val trackingExact = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "yclid",
        "twclid", "ttclid", "li_fat_id", "igshid", "igsh", "mc_cid", "mc_eid",
        "_hsenc", "_hsmi", "mkt_tok", "vero_id", "oly_enc_id", "oly_anon_id",
        "wickedid", "spm", "scm", "cmpid", "ocid", "ref_src", "ref_url",
        "sr_share", "share_id",
    )
    private val trackingPrefixes = listOf("utm_", "pk_", "mtm_", "matomo_", "hsa_", "stm_", "_bta")

    /** Parameters that are tracking only on one site (host and its subdomains). */
    private val perHost: Map<String, UrlRule> = mapOf(
        "etsy.com" to UrlRule(
            exact = setOf(
                "ref", "ls", "sts", "content_source", "logging_key", "organic_search_click",
                "pro", "frs", "click_key", "click_sum", "plkey",
            ),
            prefixes = listOf("ga_"),
        ),
        "amazon.com" to AMAZON,
        "amazon.co.uk" to AMAZON,
        "amazon.de" to AMAZON,
        "amazon.se" to AMAZON,
        "amazon.no" to AMAZON,
        "amazon.fr" to AMAZON,
        "amazon.es" to AMAZON,
        "amazon.it" to AMAZON,
        "amazon.ca" to AMAZON,
        "amazon.co.jp" to AMAZON,
        "aliexpress.com" to UrlRule(
            exact = setOf("spm", "algo_pvid", "algo_exp_id", "pdp_ext_f", "pdp_npi", "aff_fcid", "aff_fsk", "aff_platform", "aff_trace_key", "terminal_id", "afSmartRedirect"),
        ),
        "youtube.com" to UrlRule(exact = setOf("feature", "pp", "si")),
        "youtu.be" to UrlRule(exact = setOf("feature", "si")),
        "twitter.com" to UrlRule(exact = setOf("s", "t", "ref_src", "ref_url")),
        "x.com" to UrlRule(exact = setOf("s", "t", "ref_src", "ref_url")),
        "instagram.com" to UrlRule(exact = setOf("igshid", "igsh")),
        "facebook.com" to UrlRule(exact = setOf("fbclid", "ref", "refsrc", "__tn__", "__cft__", "mibextid")),
        "tiktok.com" to UrlRule(exact = setOf("_r", "_t", "is_from_webapp", "sender_device", "share_app_id", "share_link_id", "u_code", "social_share_type", "utm_campaign")),
        "ebay.com" to UrlRule(exact = setOf("_trkparms", "_trksid", "amdata", "mkcid", "mkevt", "mkrid", "campid", "customid", "toolid", "siteid")),
        "linkedin.com" to UrlRule(exact = setOf("trk", "trkinfo", "originalsubdomain", "refid", "trackingid")),
        "spotify.com" to UrlRule(exact = setOf("si", "nd", "dlsi")),
    )

    /** The link without tracking parameters; [url] itself when there is nothing to remove. */
    fun clean(url: String): String {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return url
        }
        val queryStart = trimmed.indexOf('?')
        if (queryStart < 0) return url
        val fragmentStart = trimmed.indexOf('#', startIndex = queryStart).takeIf { it >= 0 } ?: trimmed.length
        val query = trimmed.substring(queryStart + 1, fragmentStart)
        val host = hostOf(trimmed) ?: return url
        val rule = ruleFor(host)

        val parts = query.split('&')
        val kept = parts.filter { part ->
            part.isNotEmpty() && !isTracking(decodeName(part.substringBefore('=')).lowercase(), rule)
        }
        if (kept.size == parts.size) return url
        val base = trimmed.substring(0, queryStart)
        val fragment = trimmed.substring(fragmentStart)
        return buildString {
            append(base)
            if (kept.isNotEmpty()) append('?').append(kept.joinToString("&"))
            append(fragment)
        }
    }

    /**
     * What a long link shows in running text: host and path, with the
     * query and fragment dropped, cut with an ellipsis. Null when the link is
     * short enough to show as it is.
     */
    fun shortLabel(
        url: String,
        maxLength: Int = SHORT_LABEL_MAX,
        plainPathShortenAbove: Int = PLAIN_PATH_SHORTEN_ABOVE,
    ): String? {
        val shown = url.trim()
        if (shown.length <= maxLength) return null
        // A link without query or fragment is only shortened when it is very long:
        // a plain path (Wikipedia) loses information for nothing.
        if ('?' !in shown && '#' !in shown && shown.length <= plainPathShortenAbove) return null
        val withoutScheme = shown.substringAfter("://", shown)
            .substringBefore('#')
            .substringBefore('?')
            .removePrefix("www.")
            .trimEnd('/')
        val label = if (withoutScheme.length > maxLength) {
            withoutScheme.take(maxLength - 1).trimEnd('/', '-', '.', '_') + "…"
        } else {
            withoutScheme + "…"
        }
        return label.takeIf { it.length < shown.length }
    }

    /**
     * CLEANURL05_LINK_CARD_SHOWS_SHORT_ADDRESS_V1: the line a link post's card prints under the
     * domain. A link post's media label is its whole address (a 2 000-character one filled the
     * screen); a web address shows short, anything else (a label) is returned as it is.
     */
    fun cardLabel(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return text
        }
        return shortLabel(trimmed, maxLength = CARD_LABEL_MAX, plainPathShortenAbove = CARD_LABEL_MAX) ?: text
    }

    private fun isTracking(name: String, rule: UrlRule?): Boolean =
        name in trackingExact ||
            trackingPrefixes.any { name.startsWith(it) } ||
            (rule != null && (name in rule.exact || rule.prefixes.any { name.startsWith(it) }))

    private fun ruleFor(host: String): UrlRule? {
        var candidate = host
        while (true) {
            perHost[candidate]?.let { return it }
            val dot = candidate.indexOf('.')
            if (dot < 0 || candidate.indexOf('.', dot + 1) < 0) return null
            candidate = candidate.substring(dot + 1)
        }
    }

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host }.getOrNull()?.lowercase()
            ?: url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
                .substringAfter('@').substringBefore(':').lowercase().takeIf { it.isNotEmpty() }

    private fun decodeName(raw: String): String =
        runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)

    private const val SHORT_LABEL_MAX = 48
    private const val PLAIN_PATH_SHORTEN_ABOVE = 80
    private const val CARD_LABEL_MAX = 60
}

internal class UrlRule(
    val exact: Set<String> = emptySet(),
    val prefixes: List<String> = emptyList(),
)

private val AMAZON = UrlRule(
    exact = setOf(
        "tag", "linkcode", "linkid", "creative", "creativeasin", "camp", "ref", "ref_", "pf_rd_r", "pf_rd_p",
        "pf_rd_m", "pf_rd_s", "pf_rd_t", "pf_rd_i", "pd_rd_r", "pd_rd_w", "pd_rd_wg",
        "qid", "sr", "crid", "sprefix", "dib", "dib_tag", "content-id",
    ),
    prefixes = listOf("pf_rd_", "pd_rd_"),
)
