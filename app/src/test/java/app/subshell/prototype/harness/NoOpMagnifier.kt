package app.subshell.prototype.harness

import android.widget.Magnifier
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Android's text magnifier draws into a Surface that Robolectric cannot make,
 * so dismissing it throws. Tests that select text by touch (#235) use this
 * shadow: the magnifier shows nothing and dismisses quietly.
 */
@Implements(Magnifier::class)
class NoOpMagnifier {
    @Implementation
    fun show(sourceCenterX: Float, sourceCenterY: Float) = Unit

    @Implementation
    fun show(sourceCenterX: Float, sourceCenterY: Float, magnifierCenterX: Float, magnifierCenterY: Float) = Unit

    @Implementation
    fun update() = Unit

    @Implementation
    fun dismiss() = Unit
}
