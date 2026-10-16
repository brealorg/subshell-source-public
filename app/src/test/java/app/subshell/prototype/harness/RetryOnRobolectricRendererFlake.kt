package app.subshell.prototype.harness

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric (4.16, SDK 36) intermittently fails a window-focus event with
 * `HardwareRenderer.setLightSourceGeometry`: "lightZ must be a finite
 * positive, given=Infinity" (ThreadedRenderer.setup on
 * ViewRootImpl.dispatchFocusEvent). It is a harness fault, not an app
 * fault: the same test passes on the next run, and only the search-route
 * tests hit it. This rule re-runs a test when — and only when — the
 * failure is exactly that exception; everything else propagates unchanged.
 * Wrap it *outside* the compose rule (RuleChain.outerRule) so the retry
 * gets a fresh activity.
 *
 * Cause (established on the bundle-post-mutations land, 2026-09-22):
 * `ThreadedRenderer.setLightCenter` computes
 * `lightZ = ((min(w, h) / (450 * density)) + 2) / 3 * mLightZ` from
 * `attachInfo.mDisplay.getRealMetrics()`, and Robolectric's per-test
 * display setup sometimes leaves the default display with its size
 * (1280x800 for `w1280dp-h800dp`) but `DisplayInfo.logicalDensityDpi = 0`,
 * while the application resources still carry `densityDpi = 160` and
 * `RuntimeEnvironment.getQualifiers()` has degraded to `en-rUS-ldltr`. The
 * state is present before the test body starts and persists for the test
 * method. Reconfiguring the display from those degraded qualifiers fixes
 * the density but shrinks the display to 320x470, which breaks the
 * two-pane tests; restoring only the density from the application's own
 * configuration keeps the size.
 *
 * A second land (2026-09-22) showed the display is only half-configured in
 * that state: it has the size from the test's qualifiers but no density, and
 * `RuntimeEnvironment.getQualifiers()` reports the bare defaults, so the
 * composition also lays out for the wrong window. Restoring the density alone
 * avoided the crash and then failed the assertion. So the rule re-applies the
 * test's own `@Config(qualifiers = …)` with `RuntimeEnvironment.setQualifiers`,
 * which reconfigures the application resources and the display together, and
 * falls back to restoring the density from the application resources when the
 * test declares no qualifiers. The retry stays as a fallback for the same
 * exception, repairing the same way first.
 */
class RetryOnRobolectricRendererFlake(private val attempts: Int = 3) : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                var before = displayState()
                if (before.abnormal) {
                    repairDefaultDisplay(description)
                    val repaired = displayState()
                    System.err.println(
                        "RetryOnRobolectricRendererFlake: ${description.displayName} started with an " +
                            "abnormal default display: $before; reconfigured: $repaired",
                    )
                    before = repaired
                }
                repeat(attempts) { attempt ->
                    try {
                        base.evaluate()
                        return
                    } catch (failure: Throwable) {
                        if (!isRendererFlake(failure)) throw failure
                        System.err.println(
                            "RetryOnRobolectricRendererFlake: ${description.displayName} hit the " +
                                "HardwareRenderer lightZ flake on attempt ${attempt + 1}/$attempts; " +
                                "display before test: $before; at failure: ${displayState()}",
                        )
                        if (attempt == attempts - 1) throw failure
                        repairDefaultDisplay(description)
                        System.err.println(
                            "RetryOnRobolectricRendererFlake: default display reconfigured: " +
                                "${displayState()}; retrying (${attempt + 2}/$attempts)",
                        )
                    }
                }
            }
        }

    private fun isRendererFlake(failure: Throwable): Boolean =
        generateSequence(failure) { it.cause }.any { cause ->
            cause is IllegalArgumentException &&
                cause.message?.startsWith("lightZ must be a finite positive") == true &&
                cause.stackTrace.any { it.className == "android.graphics.HardwareRenderer" }
        }

    /**
     * Re-apply the test's declared qualifiers, which reconfigures the
     * application resources and the default display together. Without
     * qualifiers, put the missing density back from the application resources
     * through the shadow's per-field setters so the display size survives.
     * `ShadowDisplayManager.configureDefaultDisplay` cannot be used here: it
     * may only be called once per test.
     */
    private fun repairDefaultDisplay(description: Description) {
        runCatching {
            val qualifiers = declaredQualifiers(description)
            if (qualifiers != null) {
                RuntimeEnvironment.setQualifiers(qualifiers)
                return
            }
            val context = ApplicationProvider.getApplicationContext<Context>()
            val metrics = context.resources.displayMetrics
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            val shadow = shadowOf(display)
            shadow.setDensityDpi(context.resources.configuration.densityDpi)
            if (metrics.xdpi > 0f) shadow.setXdpi(metrics.xdpi)
            if (metrics.ydpi > 0f) shadow.setYdpi(metrics.ydpi)
        }.onFailure { System.err.println("RetryOnRobolectricRendererFlake: display repair failed: $it") }
    }

    /** The `@Config(qualifiers = …)` of the test method, else of its class. */
    private fun declaredQualifiers(description: Description): String? =
        listOfNotNull(
            description.getAnnotation(Config::class.java),
            description.testClass?.getAnnotation(Config::class.java),
        ).firstNotNullOfOrNull { config ->
            config.qualifiers.takeIf { it.isNotBlank() && it != Config.DEFAULT_QUALIFIERS }
        }

    /** What ThreadedRenderer will see: the default display's real metrics, plus where they come from. */
    private fun displayState(): DisplayState = runCatching {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        // Deprecated on API 31+, but it is the call the renderer itself makes
        // (see the class comment), so it is the value to record.
        @Suppress("DEPRECATION")
        val real = DisplayMetrics().also { display.getRealMetrics(it) }
        val info = displayInfo(Display.DEFAULT_DISPLAY)
        DisplayState(
            realDensity = real.density,
            realSize = "${real.widthPixels}x${real.heightPixels}",
            infoDensityDpi = info?.first,
            infoIdentity = info?.second,
            configDensityDpi = context.resources.configuration.densityDpi,
            qualifiers = RuntimeEnvironment.getQualifiers(),
            freeMb = Runtime.getRuntime().let { (it.freeMemory() / 1_048_576).toInt() },
        )
    }.getOrElse { DisplayState(error = it.toString()) }

    /** `DisplayManagerGlobal.getInstance().getDisplayInfo(id)`: logicalDensityDpi and the object identity. */
    private fun displayInfo(displayId: Int): Pair<Int, String>? = runCatching {
        val globalClass = Class.forName("android.hardware.display.DisplayManagerGlobal")
        val global = globalClass.getMethod("getInstance").invoke(null)
        val info = globalClass.getMethod("getDisplayInfo", Int::class.javaPrimitiveType).invoke(global, displayId)
            ?: return null
        val dpi = info.javaClass.getField("logicalDensityDpi").getInt(info)
        dpi to "${System.identityHashCode(global)}/${System.identityHashCode(info)}"
    }.getOrNull()

    private data class DisplayState(
        val realDensity: Float = Float.NaN,
        val realSize: String = "?",
        val infoDensityDpi: Int? = null,
        val infoIdentity: String? = null,
        val configDensityDpi: Int = -1,
        val qualifiers: String = "?",
        val freeMb: Int = -1,
        val error: String? = null,
    ) {
        val abnormal: Boolean get() = error != null || !(realDensity > 0f) || (infoDensityDpi ?: 1) <= 0
    }
}
