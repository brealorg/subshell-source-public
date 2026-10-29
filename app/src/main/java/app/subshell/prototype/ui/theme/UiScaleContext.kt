package app.subshell.prototype.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import kotlin.math.roundToInt

/**
 * ISSUE278_EVERY_WINDOW_FOLLOWS_THE_UI_SIZE_V1 (owner 2026-10-28, "ja" to
 * scaling through Android's context): a dialog, sheet, menu or popup is its own
 * window, and Compose gives each window the density of its context, not the
 * LocalDensity of the screen that opened it. SubshellTheme's override therefore
 * stopped at the window's edge: UI size and Text size did nothing in ~90
 * dialogs, sheets and menus, and their touch targets fell under 48 dp at 110 %.
 *
 * The app's root view is created with this context instead. Its resources carry
 * the UI size in densityDpi (and the window's dp sizes) and Text size in
 * fontScale, so every window opened from it is scaled by Android itself.
 * SubshellTheme applies only what the window has not (see [windowScales]).
 */
class UiScaleContext(base: Context) : ContextWrapper(base) {
    var uiScale = 1f
        private set
    var textScale = 1f
        private set

    private var scaledFor: Pair<Configuration, Pair<Float, Float>>? = null
    private var scaled: Resources? = null

    private var root: View? = null

    /**
     * Sets the scales and tells the root view, which then recomposes with the
     * new density and font scale (a configuration change, not a new UI, so what
     * the screens remember stays). Before the root exists they wait for it.
     */
    fun update(uiScale: Float, textScale: Float) {
        if (uiScale == this.uiScale && textScale == this.textScale) return
        this.uiScale = uiScale
        this.textScale = textScale
        dispatch()
    }

    internal fun attach(view: View) {
        root = view
        dispatch()
    }

    /** Tells the root view again (Android hands it the unscaled configuration after a rotation). */
    fun dispatch() {
        root?.dispatchConfigurationChanged(resources.configuration)
    }

    // Rebuilt whenever the base configuration (rotation, window size) or a scale changes.
    override fun getResources(): Resources {
        val base = baseContext.resources
        val key = Configuration(base.configuration) to (uiScale to textScale)
        scaled?.takeIf { scaledFor == key }?.let { return it }
        val resources = if (uiScale == 1f && textScale == 1f) {
            base
        } else {
            createConfigurationContext(scaledConfiguration(base.configuration, uiScale, textScale)).resources
        }
        scaledFor = key
        scaled = resources
        return resources
    }

}

internal fun scaledConfiguration(base: Configuration, uiScale: Float, textScale: Float): Configuration =
    Configuration(base).apply {
        densityDpi = (base.densityDpi * uiScale).roundToInt()
        fontScale = base.fontScale * textScale
        // The window has the same pixels; at a smaller density they are more dp.
        if (base.screenWidthDp != Configuration.SCREEN_WIDTH_DP_UNDEFINED) {
            screenWidthDp = (base.screenWidthDp / uiScale).roundToInt()
        }
        if (base.screenHeightDp != Configuration.SCREEN_HEIGHT_DP_UNDEFINED) {
            screenHeightDp = (base.screenHeightDp / uiScale).roundToInt()
        }
        if (base.smallestScreenWidthDp != Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED) {
            smallestScreenWidthDp = (base.smallestScreenWidthDp / uiScale).roundToInt()
        }
    }

/** The UI size and Text size [context]'s window already carries (1, 1 outside the app's root). */
internal fun windowScales(context: Context): Pair<Float, Float> {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is UiScaleContext) return current.uiScale to current.textScale
        current = current.baseContext
    }
    return 1f to 1f
}

/**
 * The activity's content as a ComposeView created with [scaleContext], so every
 * window it opens inherits the scales. ComponentActivity.setContentView sets the
 * view tree owners (lifecycle, saved state, back) as setContent would.
 */
fun ComponentActivity.setScaledContent(scaleContext: UiScaleContext, content: @Composable () -> Unit): ComposeView {
    val view = ComposeView(scaleContext).apply { setContent(content) }
    setContentView(view)
    scaleContext.attach(view)
    return view
}
