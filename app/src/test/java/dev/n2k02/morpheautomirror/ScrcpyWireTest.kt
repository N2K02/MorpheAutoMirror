package dev.n2k02.morpheautomirror

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrcpyWireTest {
    private fun bytes(write: DataOutputStream.() -> Unit): DataInputStream {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use(write)
        return DataInputStream(ByteArrayInputStream(buffer.toByteArray()))
    }
    @Test fun parsesH264Header() {
        val stream = bytes {
            writeInt(ScrcpyWire.H264)
            writeInt(1280)
            writeInt(720)
        }
        assertEquals(ScrcpyVideoHeader(ScrcpyWire.H264, 1280, 720), ScrcpyWire.readHeader(stream))
    }
    @Test fun rejectsUnexpectedCodec() {
        val stream = bytes { writeInt(0); writeInt(1280); writeInt(720) }
        assertThrows(IllegalArgumentException::class.java) { ScrcpyWire.readHeader(stream) }
    }
    @Test fun rejectsBogusDimensions() {
        val stream = bytes { writeInt(ScrcpyWire.H264); writeInt(0); writeInt(720) }
        assertThrows(IllegalArgumentException::class.java) { ScrcpyWire.readHeader(stream) }
    }
    @Test fun acceptsConfigPacket() {
        val stream = bytes {
            writeLong(Long.MIN_VALUE)
            writeInt(3)
            write(byteArrayOf(0, 1, 2))
        }
        val packet = ScrcpyWire.readPacket(stream)
        assertTrue(packet.isConfig)
        assertFalse(packet.isKeyFrame)
        assertEquals(0L, packet.ptsUs)
        assertArrayEquals(byteArrayOf(0, 1, 2), packet.payload)
    }
    @Test fun acceptsKeyFrameWithPts() {
        val stream = bytes {
            writeLong((1L shl 62) or 234567L)
            writeInt(2)
            write(byteArrayOf(0, 4))
        }
        val packet = ScrcpyWire.readPacket(stream)
        assertFalse(packet.isConfig)
        assertTrue(packet.isKeyFrame)
        assertEquals(234567L, packet.ptsUs)
    }
    @Test fun rejectsOversizedPacketsWithoutAllocation() {
        val stream = bytes { writeLong(1L); writeInt(ScrcpyWire.MAX_VIDEO_PACKET + 1) }
        assertThrows(IllegalArgumentException::class.java) { ScrcpyWire.readPacket(stream) }
    }
    @Test fun rejectsZeroSizePacket() {
        val stream = bytes { writeLong(1L); writeInt(0) }
        assertThrows(IllegalArgumentException::class.java) { ScrcpyWire.readPacket(stream) }
    }
}
