package ng.pixelslimited.logoxels.engine

import android.graphics.BitmapFactory
import java.io.InputStream

/** Greyscale picture ready for tracing; 0 is black, 255 is white. */
class TraceSource(val w: Int, val h: Int, val gray: IntArray)

object ImageLoad {
    /** Decodes an image to at most [maxSide] pixels on its long side. Transparent pixels count as white. */
    fun decode(open: () -> InputStream?, maxSide: Int = 450): TraceSource? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = open()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        try {
            val w = bmp.width
            val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            val gray = IntArray(w * h) {
                val c = px[it]
                val a = c ushr 24
                val g = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
                (g * a + 255 * (255 - a)) / 255
            }
            return TraceSource(w, h, gray)
        } finally {
            bmp.recycle()
        }
    }
}
