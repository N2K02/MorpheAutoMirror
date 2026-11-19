package dev.n2k02.morpheautomirror

import java.io.DataInputStream

/** scrcpy v3.3.4 wire contract; no controls or audio. Version is pinned to the server asset. */
internal data class ScrcpyVideoHeader(val codec: Int, val width: Int, val height: Int)
internal data class ScrcpyVideoPacket(val payload: ByteArray, val ptsUs: Long, val isConfig: Boolean, val isKeyFrame: Boolean)

internal object ScrcpyWire {
    const val H264 = 0x68323634
    const val MAX_VIDEO_PACKET = 4 * 1024 * 1024
    private const val PTS_MASK = 0x3FFFFFFFFFFFFFFFL

    fun readHeader(input: DataInputStream): ScrcpyVideoHeader {
        val result = ScrcpyVideoHeader(input.readInt(), input.readInt(), input.readInt())
        require(result.codec == H264) { "Expected H.264 scrcpy codec" }
        require(result.width in 1..8192 && result.height in 1..8192) { "Invalid scrcpy video size" }
        return result
    }

    fun readPacket(input: DataInputStream): ScrcpyVideoPacket {
        val header = input.readLong()
        val size = input.readInt()
        require(size in 1..MAX_VIDEO_PACKET) { "Invalid scrcpy packet size: $size" }
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return ScrcpyVideoPacket(
            payload = bytes,
            ptsUs = header and PTS_MASK,
            isConfig = header < 0L,
            isKeyFrame = header and (1L shl 62) != 0L
        )
    }
}
