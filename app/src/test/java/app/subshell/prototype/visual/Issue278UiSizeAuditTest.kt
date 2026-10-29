package app.subshell.prototype.visual

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.ui.theme.AppTextScale
import app.subshell.prototype.ui.theme.AppUiScale
import app.subshell.prototype.ui.theme.ThemeMode
import app.subshell.prototype.ui.theme.UiScaleContext
import app.subshell.prototype.ui.theme.setScaledContent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ISSUE278 verification (owner 2026-10-28: "den må sitte 110 % korrekt"):
 * renders every VISUAL01 surface at each UI size and checks the layout, so a
 * reader's 85 % or 110 % is looked at without a phone.
 *
 * For each surface, window and UI size it lists
 *   touch     a clickable node whose touch area is under 48 dp physical
 *   clip      text more than a pixel larger than its box
 *   ellipsis  text cut with "…"
 *   offscreen a node past the window's right edge outside a horizontal scroller
 * Ellipsis and offscreen also happen by design at 100 %; what counts is what
 * a UI size adds, so read the findings against the 100 % row.
 *
 * Without SUBSHELL_UI_SIZE_AUDIT_OUT it runs Home on the owner's phone setup
 * and asserts that 85 % and 110 % add no touch finding. With it set to a
 * directory outside the repo, every surface runs and the PNGs plus
 * findings.tsv (surface, window, scale, kind, what) land there:
 *
 *   SUBSHELL_UI_SIZE_AUDIT_OUT=/tmp/ui-size ./gradlew --offline --no-daemon \
 *     --no-configuration-cache :app:testDebugUnitTest \
 *     --tests 'app.subshell.prototype.visual.Issue278UiSizeAuditTest'
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Issue278UiSizeAuditTest(private val surface: VisualSurface) {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phone() = audit("phone")

    // The owner's phone: Android font size 1.15 and Text size 115 % (1.32, large text).
    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi", fontScale = 1.15f)
    fun phoneLargeText() = audit("phone-text132", AppTextScale.LARGE, smoke = true)

    // A 720p phone, the size the reader on #278 asked about.
    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun smallPhone() = audit("small")

    private fun audit(window: String, textScale: AppTextScale = AppTextScale.DEFAULT, smoke: Boolean = false) {
        val out = System.getenv(OUT_ENV)?.takeIf { it.isNotBlank() }?.let(::File)
        assumeTrue(out != null || (smoke && surface == VisualSurface.HOME))

        // Hosted as MainActivity hosts the app, so dialogs and sheets get the scales too.
        var scaleContext: UiScaleContext? = null
        val state = composeRule.renderVisualSurface(
            surface,
            ThemeMode.LIGHT,
            configure = { it.updateAppTextScale(textScale) },
            setAppContent = { content ->
                composeRule.runOnUiThread {
                    scaleContext = UiScaleContext(composeRule.activity)
                    composeRule.activity.setScaledContent(requireNotNull(scaleContext), content)
                }
            },
            onThemeSettingsChanged = { settings ->
                scaleContext?.update(settings.uiScale, settings.textScale)
            },
        )
        val physicalDensity = ApplicationProvider.getApplicationContext<Application>().resources.displayMetrics.density
        val seen = mutableMapOf<AppUiScale, Set<Finding>>()
        for (scale in AppUiScale.entries) {
            state.updateAppUiScale(scale)
            composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
            composeRule.waitForIdle()
            seen[scale] = findings(physicalDensity)
            if (out != null) {
                out.mkdirs()
                val image = captureScreen()
                File(out, "${surface.id}_${window}_${scale.percent}.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                File(out, "findings.tsv").appendText(
                    seen.getValue(scale).joinToString("") { "${surface.id}\t$window\t${scale.percent}\t${it.kind}\t${it.what}\n" },
                )
            }
        }
        if (out == null) {
            val atDefault = seen.getValue(AppUiScale.DEFAULT).touch()
            assertEquals("85 % adds no small touch target", emptySet<String>(), seen.getValue(AppUiScale.SMALLEST).touch() - atDefault)
            assertEquals("110 % adds no small touch target", emptySet<String>(), seen.getValue(AppUiScale.LARGER).touch() - atDefault)
        }
    }

    private data class Finding(val kind: String, val what: String)

    // Compared by name: the same button is a different size at another UI size.
    private fun Set<Finding>.touch(): Set<String> =
        filter { it.kind == "touch" }.mapTo(mutableSetOf()) { it.what.substringBeforeLast(" (") }

    private fun findings(physicalDensity: Float): Set<Finding> {
        val found = mutableSetOf<Finding>()
        val minimumTouchPx = 48f * physicalDensity - 1f
        composeRule.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach { root ->
            val windowWidth = root.size.width
            root.walk { node ->
                val label = node.label()
                if (SemanticsActions.OnClick in node.config && SemanticsProperties.Disabled !in node.config) {
                    val touch = node.touchBoundsInRoot
                    if (touch.width < minimumTouchPx || touch.height < minimumTouchPx) {
                        val dp = { px: Float -> Math.round(px / physicalDensity) }
                        found += Finding("touch", "$label (${dp(touch.width)}×${dp(touch.height)} dp)")
                    }
                }
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { getLayout ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    if (getLayout(layouts)) {
                        layouts.forEach { layout ->
                            val text = layout.layoutInput.text.text.oneLine()
                            // A line reaching past its box, or lines taller than it, by
                            // more than a pixel (rounding at 1.7 px per dp gives fractions).
                            val widest = (0 until layout.lineCount).maxOfOrNull { layout.getLineRight(it) } ?: 0f
                            val w = widest - layout.size.width
                            val h = layout.multiParagraph.height - layout.size.height
                            if (w > 1f || h > 1f) {
                                found += Finding("clip", "$text (+${Math.round(w.coerceAtLeast(0f))}×${Math.round(h.coerceAtLeast(0f))} px)")
                            }
                            if (layout.lineCount > 0 && layout.isLineEllipsized(layout.lineCount - 1)) {
                                found += Finding("ellipsis", text)
                            }
                        }
                    }
                }
                if (node.boundsInWindow.right > windowWidth + 1f && !node.insideHorizontalScroller()) {
                    found += Finding("offscreen", label)
                }
            }
        }
        return found
    }

    private fun SemanticsNode.walk(visit: (SemanticsNode) -> Unit) {
        visit(this)
        children.forEach { it.walk(visit) }
    }

    private fun SemanticsNode.insideHorizontalScroller(): Boolean {
        var node: SemanticsNode? = parent
        while (node != null) {
            if (SemanticsProperties.HorizontalScrollAxisRange in node.config) return true
            node = node.parent
        }
        return false
    }

    private fun SemanticsNode.label(): String {
        val text = config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            ?: config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
            ?: config.getOrNull(SemanticsProperties.TestTag)
            ?: children.firstNotNullOfOrNull { child ->
                child.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                    ?: child.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
            }
        return text?.oneLine() ?: "(unlabelled)"
    }

    private fun String.oneLine() = replace('\n', ' ').take(60)

    private val AppUiScale.percent get() = Math.round(factor * 100)

    companion object {
        private const val OUT_ENV = "SUBSHELL_UI_SIZE_AUDIT_OUT"
        private const val SETTLE_MILLIS = 1_500L

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters(): List<Array<Any>> = VisualSurface.entries.map { arrayOf<Any>(it) }
    }
}
