package ng.pixelslimited.logoxels.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Shared timing: entrances ease out, exits are 20% shorter, no bounce anywhere. */
internal object Motion {
    const val ENTER = 200
    const val EXIT = 160
    const val STAGGER = 30
    const val PRESS_SCALE = 0.97f
    const val POPOVER_START = 0.95f
}

/** Shrinks to 0.97 while pressed. Watches touches without consuming them, so the control's own tap still works. */
internal fun Modifier.pressScale(): Modifier = composed {
    var down by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (down) Motion.PRESS_SCALE else 1f, tween(90, easing = FastOutSlowInEasing), label = "press")
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                down = true
                waitForUpOrCancellation(PointerEventPass.Initial)
                down = false
            }
        }
        .graphicsLayer { scaleX = scale; scaleY = scale }
}
