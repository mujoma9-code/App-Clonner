package dev.clonner

import dev.clonner.vpn.Packets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PacketsTest {

    private val client = byteArrayOf(10, (215).toByte(), (173).toByte(), 1)
    private val server = byteArrayOf(10, (215).toByte(), (173).toByte(), 2)

    @Test
    fun `round-trips a udp datagram`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5, 6, 7)
        val packet = Packets.buildUdp(client, server, 40000, 53, payload)

        val parsed = Packets.parseUdp(packet, packet.size)
        assertNotNull(parsed)
        parsed!!
        assertArrayEquals(client, parsed.sourceIp)
        assertArrayEquals(server, parsed.destIp)
        assertEquals(40000, parsed.sourcePort)
        assertEquals(53, parsed.destPort)
        assertArrayEquals(payload, parsed.payload)
    }

    @Test
    fun `handles an odd-length payload`() {
        val payload = ByteArray(33) { it.toByte() }
        val packet = Packets.buildUdp(server, client, 53, 51234, payload)
        val parsed = Packets.parseUdp(packet, packet.size)!!
        assertArrayEquals(payload, parsed.payload)
    }

    @Test
    fun `header fields are well formed`() {
        val packet = Packets.buildUdp(client, server, 1234, 53, ByteArray(10))

        assertEquals(0x45, packet[0].toInt() and 0xFF)               // IPv4, IHL 5
        assertEquals(Packets.PROTO_UDP, packet[9].toInt() and 0xFF)  // protocol
        assertEquals(20 + 8 + 10, packet.size)

        val totalLength = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        assertEquals(packet.size, totalLength)

        val udpLength = ((packet[24].toInt() and 0xFF) shl 8) or (packet[25].toInt() and 0xFF)
        assertEquals(8 + 10, udpLength)
    }

    @Test
    fun `ip header checksum verifies to zero`() {
        val packet = Packets.buildUdp(client, server, 4321, 53, ByteArray(24) { 0x5A })
        assertEquals(0, onesComplementSum(packet, 0, 20))
    }

    @Test
    fun `udp checksum verifies over the pseudo-header`() {
        val payload = ByteArray(19) { (it * 7).toByte() }
        val packet = Packets.buildUdp(client, server, 5555, 53, payload)

        // Pseudo-header: src, dst, zero, protocol, udp length.
        var sum = 0L
        for (i in 12 until 20 step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        sum += Packets.PROTO_UDP.toLong()
        val udpLength = 8 + payload.size
        sum += udpLength.toLong()

        var offset = 20
        var remaining = udpLength
        while (remaining > 1) {
            sum += ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
            offset += 2
            remaining -= 2
        }
        if (remaining == 1) sum += (packet[offset].toInt() and 0xFF) shl 8
        while ((sum shr 16) != 0L) sum = (sum and 0xFFFF) + (sum shr 16)

        assertEquals(0xFFFF, sum.toInt())
    }

    @Test
    fun `rejects non-ipv4 and non-udp traffic`() {
        val ipv6 = ByteArray(40).also { it[0] = 0x60 }
        assertNull(Packets.parseUdp(ipv6, ipv6.size))

        val tcp = Packets.buildUdp(client, server, 80, 80, ByteArray(4))
            .also { it[9] = 6 } // rewrite protocol to TCP
        assertNull(Packets.parseUdp(tcp, tcp.size))
    }

    @Test
    fun `rejects runts and fragments`() {
        assertNull(Packets.parseUdp(ByteArray(8), 8))

        val fragment = Packets.buildUdp(client, server, 1000, 53, ByteArray(8))
            .also { it[6] = 0x20 } // more-fragments flag
        assertNull(Packets.parseUdp(fragment, fragment.size))
    }

    @Test
    fun `ignores trailing bytes beyond the declared udp length`() {
        val payload = byteArrayOf(9, 8, 7)
        val packet = Packets.buildUdp(client, server, 2000, 53, payload)
        val padded = packet.copyOf(packet.size + 16)

        val parsed = Packets.parseUdp(padded, padded.size)!!
        assertArrayEquals(payload, parsed.payload)
    }

    private fun onesComplementSum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        while ((sum shr 16) != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }
}
