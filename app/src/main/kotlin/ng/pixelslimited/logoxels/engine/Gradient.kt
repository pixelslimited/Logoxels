package ng.pixelslimited.logoxels.engine

import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

enum class GKind { LINEAR, RADIAL }

/** Two-colour gradient laid out relative to the shape's own box, so it follows moves and resizes. */
class Gradient(var kind: GKind, var c1: Int, var c2: Int, var angle: Float = 0f) {
    fun copy() = Gradient(kind, c1, c2, angle)

    fun shader(b: RectF): Shader {
        val cx = b.centerX()
        val cy = b.centerY()
        val w = max(b.width(), 1f)
        val h = max(b.height(), 1f)
        if (kind == GKind.RADIAL) {
            return RadialGradient(cx, cy, hypot(w, h) / 2f, c1, c2, Shader.TileMode.CLAMP)
        }
        val a = Math.toRadians(angle.toDouble())
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        val half = (abs(w * ca) + abs(h * sa)) / 2f
        return LinearGradient(cx - ca * half, cy - sa * half, cx + ca * half, cy + sa * half, c1, c2, Shader.TileMode.CLAMP)
    }

    companion object {
        /** A lighter tint of [c], used as the default second colour. */
        fun lighter(c: Int): Int {
            fun ch(v: Int) = (v + (255 - v) * 0.55f).toInt()
            return (0xFF shl 24) or (ch((c shr 16) and 0xFF) shl 16) or (ch((c shr 8) and 0xFF) shl 8) or ch(c and 0xFF)
        }
    }
}
