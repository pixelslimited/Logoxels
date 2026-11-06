package ng.pixelslimited.logoxels.engine

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream

/** One PSD layer: pixels are straight (non-premultiplied) RGBA, row by row, inside the given rectangle. */
class PsdLayer(val name: String, val left: Int, val top: Int, val width: Int, val height: Int, val rgba: ByteArray)

/** Minimal Photoshop (PSD) writer: 8-bit RGB with alpha, uncompressed, layers bottom to top. */
object Psd {
    fun write(width: Int, height: Int, layers: List<PsdLayer>, composite: ByteArray, out: OutputStream) {
        require(composite.size == width * height * 4) { "composite size" }
        val o = DataOutputStream(out)
        o.writeBytes("8BPS")
        o.writeShort(1)
        o.write(ByteArray(6))
        o.writeShort(4)
        o.writeInt(height)
        o.writeInt(width)
        o.writeShort(8)
        o.writeShort(3)
        o.writeInt(0) // colour mode data
        o.writeInt(0) // image resources

        val info = ByteArrayOutputStream()
        val d = DataOutputStream(info)
        d.writeShort(-layers.size) // negative: first alpha channel holds the merged image's transparency
        for (l in layers) {
            val chan = l.width * l.height + 2
            d.writeInt(l.top); d.writeInt(l.left); d.writeInt(l.top + l.height); d.writeInt(l.left + l.width)
            d.writeShort(4)
            for (id in intArrayOf(0, 1, 2, -1)) { d.writeShort(id); d.writeInt(chan) }
            d.writeBytes("8BIM"); d.writeBytes("norm")
            d.writeByte(255); d.writeByte(0); d.writeByte(0); d.writeByte(0)
            val name = l.name.filter { it.code in 32..126 }.take(31)
            val pascal = (1 + name.length).let { it + (4 - it % 4) % 4 }
            d.writeInt(4 + 4 + pascal)
            d.writeInt(0) // layer mask
            d.writeInt(0) // blending ranges
            d.writeByte(name.length); d.writeBytes(name); d.write(ByteArray(pascal - 1 - name.length))
        }
        for (l in layers) {
            val n = l.width * l.height
            for (c in intArrayOf(0, 1, 2, 3)) {
                d.writeShort(0) // raw
                val plane = ByteArray(n)
                for (i in 0 until n) plane[i] = l.rgba[i * 4 + c]
                d.write(plane)
            }
        }
        if (info.size() % 2 != 0) d.writeByte(0)
        d.flush()

        o.writeInt(info.size() + 8) // layer-info length field + layer info + global mask length field
        o.writeInt(info.size())
        info.writeTo(o)
        o.writeInt(0) // global layer mask info

        o.writeShort(0) // merged image, raw, planar
        val n = width * height
        for (c in intArrayOf(0, 1, 2, 3)) {
            val plane = ByteArray(n)
            for (i in 0 until n) plane[i] = composite[i * 4 + c]
            o.write(plane)
        }
        o.flush()
    }
}
