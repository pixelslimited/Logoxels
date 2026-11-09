package ng.pixelslimited.logoxels.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Ruler-style slider: drag the scale under a fixed accent marker; a haptic click per step; ticks fade toward the edges. */
@Composable
fun RulerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    steps: Int = 100,
) {
    val accent = Accent
    val ink = Ink
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val step = (valueRange.endInclusive - valueRange.start) / steps
    Box(
        modifier.fillMaxWidth().height(44.dp).pointerInput(valueRange, steps) {
            val tickPx = 10.dp.toPx()
            var lastIdx = ((current - valueRange.start) / step).roundToInt()
            detectHorizontalDragGestures(onDragEnd = { finished?.invoke() }, onDragCancel = { finished?.invoke() }) { pointer, dx ->
                pointer.consume()
                val v = (current - dx / tickPx * step).coerceIn(valueRange.start, valueRange.endInclusive)
                val idx = ((v - valueRange.start) / step).roundToInt()
                if (idx != lastIdx) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); lastIdx = idx }
                change(v)
            }
        },
    ) {
        Canvas(Modifier.fillMaxWidth().height(40.dp).align(Alignment.Center)) {
            val tickPx = 10.dp.toPx()
            val cx = size.width / 2f
            val pos = (value - valueRange.start) / step
            val half = (size.width / 2f / tickPx).toInt() + 2
            val base = floor(pos).toInt()
            for (i in (base - half)..(base + half)) {
                if (i < 0 || i > steps) continue
                val x = cx + (i - pos) * tickPx
                val d = abs(x - cx) / (size.width / 2f)
                val fade = (1f - d * d).coerceIn(0f, 1f)
                val major = i % 10 == 0
                val h = (if (major) 26.dp.toPx() else if (i % 5 == 0) 18.dp.toPx() else 12.dp.toPx()) * (0.55f + 0.45f * fade)
                drawLine(ink.copy(alpha = (if (major) 0.55f else 0.3f) * fade), Offset(x, size.height / 2f - h / 2f), Offset(x, size.height / 2f + h / 2f), strokeWidth = if (major) 2.dp.toPx() else 1.5.dp.toPx(), cap = StrokeCap.Round)
            }
            drawLine(accent, Offset(cx, 2.dp.toPx()), Offset(cx, size.height - 2.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}
