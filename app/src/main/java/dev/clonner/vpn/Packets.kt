package dev.clonner.vpn

/**
 * Just enough IPv4 + UDP to read a DNS query off the TUN interface and write a reply
 * back into it. Clonner only routes DNS to itself, so these are the only packets
 * that ever reach [ClonnerVpnService].
 */
object Packets {

    const val PROTO_UDP = 17
    private const val IPV4_MIN_HEADER = 20
    private const val UDP_HEADER = 8

    class UdpDatagram(
        val sourceIp: ByteArray,
        val destIp: ByteArray,
        val sourcePort: Int,
        val destPort: Int,
        val payload: ByteArray,
    )

    /** Parses an IPv4/UDP datagram, or returns null for anything else (IPv6, TCP, runts). */
    fun parseUdp(buffer: ByteArray, length: Int): UdpDatagram? {
        if (length < IPV4_MIN_HEADER) return null
        val version = (buffer[0].toInt() and 0xF0) ushr 4
        if (version != 4) return null

        val ihl = (buffer[0].toInt() and 0x0F) * 4
        if (ihl < IPV4_MIN_HEADER || length < ihl + UDP_HEADER) return null
        if ((buffer[9].toInt() and 0xFF) != PROTO_UDP) return null

        // Fragmented datagrams are dropped rather than reassembled; DNS over UDP fits in one.
        val fragmentInfo = ((buffer[6].toInt() and 0x1F) shl 8) or (buffer[7].toInt() and 0xFF)
        val moreFragments = (buffer[6].toInt() and 0x20) != 0
        if (fragmentInfo != 0 || moreFragments) return null

        val sourceIp = buffer.copyOfRange(12, 16)
        val destIp = buffer.copyOfRange(16, 20)

        val sourcePort = readShort(buffer, ihl)
        val destPort = readShort(buffer, ihl + 2)
        val udpLength = readShort(buffer, ihl + 4)

        val payloadLength = (udpLength - UDP_HEADER).coerceAtLeast(0)
        val available = length - ihl - UDP_HEADER
        val take = minOf(payloadLength, available)
        if (take <= 0) return null

        val payload = buffer.copyOfRange(ihl + UDP_HEADER, ihl + UDP_HEADER + take)
        return UdpDatagram(sourceIp, destIp, sourcePort, destPort, payload)
    }

    /**
     * Builds a complete IPv4/UDP packet. Used to send a reply back to the app, so the
     * source is the datagram's original destination and vice versa.
     */
    fun buildUdp(
        sourceIp: ByteArray,
        destIp: ByteArray,
        sourcePort: Int,
        destPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val totalLength = IPV4_MIN_HEADER + UDP_HEADER + payload.size
        val packet = ByteArray(totalLength)

        packet[0] = 0x45                       // IPv4, 5 x 32-bit words of header
        packet[1] = 0                          // DSCP / ECN
        writeShort(packet, 2, totalLength)
        writeShort(packet, 4, 0)               // identification
        writeShort(packet, 6, 0x4000)          // don't fragment
        packet[8] = 64                         // TTL
        packet[9] = PROTO_UDP.toByte()
        // 10..11 checksum, filled in below
        System.arraycopy(sourceIp, 0, packet, 12, 4)
        System.arraycopy(destIp, 0, packet, 16, 4)
        writeShort(packet, 10, checksum(packet, 0, IPV4_MIN_HEADER))

        val udpOffset = IPV4_MIN_HEADER
        writeShort(packet, udpOffset, sourcePort)
        writeShort(packet, udpOffset + 2, destPort)
        writeShort(packet, udpOffset + 4, UDP_HEADER + payload.size)
        System.arraycopy(payload, 0, packet, udpOffset + UDP_HEADER, payload.size)
        writeShort(packet, udpOffset + 6, udpChecksum(packet, sourceIp, destIp, payload.size))

        return packet
    }

    /** UDP checksum over the IPv4 pseudo-header plus the UDP header and payload. */
    private fun udpChecksum(packet: ByteArray, sourceIp: ByteArray, destIp: ByteArray, payloadSize: Int): Int {
        val udpLength = UDP_HEADER + payloadSize
        var sum = 0L

        for (i in 0 until 4 step 2) {
            sum += ((sourceIp[i].toInt() and 0xFF) shl 8) or (sourceIp[i + 1].toInt() and 0xFF)
            sum += ((destIp[i].toInt() and 0xFF) shl 8) or (destIp[i + 1].toInt() and 0xFF)
        }
        sum += PROTO_UDP.toLong()
        sum += udpLength.toLong()

        var offset = IPV4_MIN_HEADER
        var remaining = udpLength
        while (remaining > 1) {
            sum += ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
            offset += 2
            remaining -= 2
        }
        if (remaining == 1) sum += (packet[offset].toInt() and 0xFF) shl 8

        while ((sum shr 16) != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val result = (sum.inv() and 0xFFFF).toInt()
        // A transmitted checksum of zero means "not computed", so send the equivalent 0xFFFF.
        return if (result == 0) 0xFFFF else result
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i < end - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (data[i].toInt() and 0xFF) shl 8
        while ((sum shr 16) != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun readShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun writeShort(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value ushr 8) and 0xFF).toByte()
        data[offset + 1] = (value and 0xFF).toByte()
    }
}
