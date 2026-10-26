package app.subshell.prototype.data.network

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Security audit 2026-10-22 (subshell-source #3): debug builds trusted the
 * localhost fixture's test CA for every host, so whoever held its key could
 * stand in for Reddit on a signed-in lane build. The test CA is now trusted
 * only for 127.0.0.1 and localhost; every other host gets the system CAs, as
 * in release. The network security config is read as XML.
 */
class SecAudit03DebugTestCaScopeTest {
    private val repositoryRoot: File by lazy {
        val userDir = System.getProperty("user.dir")
            ?: error("user.dir is unavailable")
        val start = File(userDir).canonicalFile
        generateSequence(start) { current -> current.parentFile }
            .firstOrNull { candidate ->
                File(candidate, "app/src/main/java").isDirectory &&
                    File(candidate, "tools").isDirectory
            }
            ?: error("Could not resolve repository root from ${start.path}")
    }

    private val config: Element by lazy {
        val file = File(repositoryRoot, "app/src/debug/res/xml/subshell_debug_network_security_config.xml")
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    }

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map(childNodes::item).filterIsInstance<Element>().filter { it.tagName == tag }

    private fun Element.anchorSources(): List<String> =
        children("trust-anchors").flatMap { it.children("certificates") }.map { it.getAttribute("src") }

    @Test
    fun everyHostOutsideLocalhostTrustsOnlyTheSystemCas() {
        val base = config.children("base-config").single()
        assertEquals(listOf("system"), base.anchorSources())
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
    }

    @Test
    fun theTestCaIsTrustedOnlyForTheLocalhostFixture() {
        val testCa = "@raw/subshell_media04a1_test_ca"
        val scopes = config.children("domain-config").filter { testCa in it.anchorSources() }
        val scope = scopes.single()
        val domains = scope.children("domain")
        assertEquals(setOf("127.0.0.1", "localhost"), domains.map { it.textContent.trim() }.toSet())
        assertTrue(domains.all { it.getAttribute("includeSubdomains") == "false" })
        assertEquals("false", scope.getAttribute("cleartextTrafficPermitted"))
        assertTrue(scope.children("domain-config").isEmpty())
    }
}
