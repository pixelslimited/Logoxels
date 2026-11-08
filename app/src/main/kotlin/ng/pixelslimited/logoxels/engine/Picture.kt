package ng.pixelslimited.logoxels.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import java.io.ByteArrayOutputStream

/** An imported raster image: the decoded [bitmap] for drawing and the [bytes] (PNG or JPEG) for saving and SVG export. */
class Picture(val bytes: ByteArray, val bitmap: Bitmap, val mime: String) {
    /** The bytes as base64 text, built once: saving and SVG export reuse it. */
    val base64: String by lazy { Base64.encodeToString(bytes, Base64.NO_WRAP) }

    /** Up to eight distinct main colours of the picture (opaque ARGB), most common first. */
    val palette: List<Int> by lazy {
        val k = 48f / maxOf(bitmap.width, bitmap.height)
        val small = if (k < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * k).toInt().coerceAtLeast(1), (bitmap.height * k).toInt().coerceAtLeast(1), true) else bitmap
        // bucket by the top 4 bits of each channel, then average each bucket
        val buckets = HashMap<Int, IntArray>()
        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                val c = small.getPixel(x, y)
                if (Color.alpha(c) < 128) continue
                val key = (Color.red(c) shr 4 shl 8) or (Color.green(c) shr 4 shl 4) or (Color.blue(c) shr 4)
                val e = buckets.getOrPut(key) { IntArray(4) }
                e[0]++
                e[1] += Color.red(c)
                e[2] += Color.green(c)
                e[3] += Color.blue(c)
            }
        }
        if (small !== bitmap) small.recycle()
        val picked = ArrayList<Int>()
        for (e in buckets.values.sortedByDescending { it[0] }) {
            val col = Color.rgb(e[1] / e[0], e[2] / e[0], e[3] / e[0])
            val apart = picked.none { o ->
                val dr = Color.red(o) - Color.red(col)
                val dg = Color.green(o) - Color.green(col)
                val db = Color.blue(o) - Color.blue(col)
                dr * dr + dg * dg + db * db < 3600
            }
            if (apart) picked.add(col)
            if (picked.size == 8) break
        }
        picked
    }

    companion object {
        private const val MAX_SIDE = 2048

        /** Reads bytes this class wrote earlier, unchanged (no second lossy encode); null when they cannot be decoded. */
        fun restore(raw: ByteArray): Picture? {
            val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
            val png = raw.size > 1 && raw[0] == 0x89.toByte()
            return Picture(raw, bmp, if (png) "image/png" else "image/jpeg")
        }

        /** Decodes [raw] to at most [MAX_SIDE] pixels on its long side; null when it is not an image. */
        fun decode(raw: ByteArray): Picture? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, opts) ?: return null
            val png = bmp.hasAlpha()
            val out = ByteArrayOutputStream()
            bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, out)
            return Picture(out.toByteArray(), bmp, if (png) "image/png" else "image/jpeg")
        }
    }
}
