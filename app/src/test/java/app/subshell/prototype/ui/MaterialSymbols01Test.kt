package app.subshell.prototype.ui

import app.subshell.prototype.ui.symbols.Symbols
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MATERIALSYMBOLS01_ICONS_ARE_MATERIAL_SYMBOLS_V1 (owner 2026-10-18): the app's
 * icons are Google's Material Symbols (Rounded), outlined by default and
 * filled for an "on" state, generated from tools/symbols/symbols.tsv.
 */
class MaterialSymbols01Test {
    private val root: File = requireNotNull(File(requireNotNull(System.getProperty("user.dir"))).parentFile)

    @Test
    fun noAppSourceUsesTheOldMaterialIcons() {
        val offenders = File(root, "app/src/main/java").walkTopDown()
            .filter { it.extension == "kt" && it.readText().contains("androidx.compose.material.icons") }
            .map { it.relativeTo(root).path }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun theSelectedTabShowsTheFilledSymbol() {
        navItems.forEach { item ->
            assertEquals(item.icon.name + "Filled", item.selectedIcon.name)
        }
    }

    @Test
    fun everySymbolInTheTableIsGenerated() {
        val generated = Symbols::class.java.declaredMethods.map { it.name.removePrefix("get") }.toSet()
        val rows = File(root, "tools/symbols/symbols.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split("\t") }
        assertTrue(rows.isNotEmpty())
        rows.forEach { (name, _, mirror, filled) ->
            assertTrue("$name is not generated; run tools/symbols/generate-symbols.py", name in generated)
            if (filled == "filled") assertTrue("${name}Filled is not generated", "${name}Filled" in generated)
            val symbol = Symbols::class.java.getMethod("get$name").invoke(Symbols) as androidx.compose.ui.graphics.vector.ImageVector
            assertEquals("$name mirroring", mirror == "mirror", symbol.autoMirror)
        }
    }
}
