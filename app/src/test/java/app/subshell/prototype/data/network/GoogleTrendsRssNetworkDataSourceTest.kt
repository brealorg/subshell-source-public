package app.subshell.prototype.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleTrendsRssNetworkDataSourceTest {
    @Test
    fun parsesTitlesAndApproximateTrafficFromDocumentedRssShape() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss xmlns:ht="https://trends.google.com/trending/rss" version="2.0">
              <channel>
                <item>
                  <title>Fever vs Wings</title>
                  <ht:approx_traffic>200K+</ht:approx_traffic>
                </item>
                <item>
                  <title>Sundance</title>
                  <ht:approx_traffic>20K+</ht:approx_traffic>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val items = parseGoogleTrendsRss(xml)

        assertEquals(listOf("Fever vs Wings", "Sundance"), items.map { it.title })
        assertEquals(listOf("200K+", "20K+"), items.map { it.approximateTraffic })
    }

    @Test
    fun rejectsDocumentTypeDeclarations() {
        val xml = "<!DOCTYPE rss><rss><channel><item><title>x</title></item></channel></rss>"
        assertTrue(parseGoogleTrendsRss(xml).isEmpty())
    }
}
