package ng.pixelslimited.logoxels.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

/** An imported raster image: the decoded [bitmap] for drawing and the [bytes] (PNG or JPEG) for saving and SVG export. */
class Picture(val bytes: ByteArray, val bitmap: Bitmap, val mime: String) {
    /** The bytes as base64 text, built once: saving and SVG export reuse it. */
    val base64: String by lazy { Base64.encodeToString(bytes, Base64.NO_WRAP) }

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
