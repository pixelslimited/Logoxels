package ng.pixelslimited.logoxels.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.InputStream

/** Greyscale picture ready for tracing; 0 is black, 255 is white. [place] maps its pixels onto the artboard, or null to centre the result. */
class TraceSource(val w: Int, val h: Int, val gray: IntArray, val place: Matrix? = null)

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
            return grey(bmp, null)
        } finally {
            bmp.recycle()
        }
    }

    /** Greyscale copy of [bmp] (left untouched), reduced to at most [maxSide] pixels; [place] maps bitmap pixels onto the artboard. */
    fun fromBitmap(bmp: Bitmap, place: Matrix, maxSide: Int = 450): TraceSource {
        val k = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
        if (k >= 1f) return grey(bmp, place)
        val small = Bitmap.createScaledBitmap(bmp, maxOf(1, (bmp.width * k).toInt()), maxOf(1, (bmp.height * k).toInt()), true)
        try {
            // pixels of the reduced copy to pixels of the original, then onto the artboard
            val m = Matrix().apply { setScale(bmp.width.toFloat() / small.width, bmp.height.toFloat() / small.height); postConcat(place) }
            return grey(small, m)
        } finally {
            small.recycle()
        }
    }

    private fun grey(bmp: Bitmap, place: Matrix?): TraceSource {
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
        return TraceSource(w, h, gray, place)
    }
}
