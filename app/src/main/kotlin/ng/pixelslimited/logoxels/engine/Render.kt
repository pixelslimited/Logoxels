package ng.pixelslimited.logoxels.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.roundToInt

/** Draws items in artboard units; the screen canvas and every raster/PDF export share this. */
object Render {
    fun draw(canvas: Canvas, items: List<Item>) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        for (item in items) {
            if (!item.visible) continue
            val faded = item.opacity < 0.999f
            if (faded) {
                val b = RectF(item.box())
                val pad = item.strokeWidth * 2f + 2f
                b.inset(-pad, -pad)
                canvas.saveLayerAlpha(b, (item.opacity * 255f).roundToInt())
            }
            val rp = item.renderPath()
            item.fill?.let {
                fill.color = it
                fill.shader = item.gradient?.shader(item.box())
                canvas.drawPath(rp, fill)
                fill.shader = null
            }
            item.stroke?.let {
                if (item.strokeWidth > 0f) {
                    stroke.color = it
                    item.configureStroke(stroke)
                    canvas.drawPath(rp, stroke)
                }
            }
            if (faded) canvas.restore()
        }
    }
}
