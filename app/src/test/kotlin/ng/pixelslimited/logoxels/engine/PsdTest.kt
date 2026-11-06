package ng.pixelslimited.logoxels.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Test

class PsdTest {
    @Test fun headerLayersAndLength() {
        val layer = PsdLayer("Rect 1", 1, 2, 3, 2, ByteArray(3 * 2 * 4) { it.toByte() })
        val out = ByteArrayOutputStream()
        Psd.write(8, 6, listOf(layer), ByteArray(8 * 6 * 4), out)
        val b = ByteBuffer.wrap(out.toByteArray())
        assertEquals(0x38425053, b.getInt(0)) // "8BPS"
        assertEquals(1, b.getShort(4).toInt())
        assertEquals(4, b.getShort(12).toInt()) // channels
        assertEquals(6, b.getInt(14)) // height
        assertEquals(8, b.getInt(18)) // width
        assertEquals(8, b.getShort(22).toInt())
        assertEquals(3, b.getShort(24).toInt()) // RGB
        // 26 colour mode len, 30 resources len, 34 layer+mask len, 38 layer info len, 42 layer count
        val layerInfoLen = b.getInt(38)
        assertEquals(-1, b.getShort(42).toInt())
        assertEquals(2, b.getInt(44)) // top
        assertEquals(1, b.getInt(48)) // left
        assertEquals(4, b.getInt(52)) // bottom
        assertEquals(4, b.getInt(56)) // right
        assertEquals(b.getInt(34), layerInfoLen + 8)
        val merged = 42 + layerInfoLen + 4
        assertEquals(merged + 2 + 8 * 6 * 4, out.size())
    }
}
