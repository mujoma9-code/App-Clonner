package dev.clonner

import dev.clonner.vpn.DnsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsMessageTest {

    /** Builds a standard A-record query for [domain]. */
    private fun query(domain: String, id: Int = 0x1234): ByteArray {
        val labels = domain.split('.')
        val size = 12 + labels.sumOf { it.length + 1 } + 1 + 4
        val out = ByteArray(size)

        out[0] = ((id shr 8) and 0xFF).toByte()
        out[1] = (id and 0xFF).toByte()
        out[2] = 0x01           // RD set
        out[3] = 0x00
        out[5] = 1              // QDCOUNT = 1

        var offset = 12
        labels.forEach { label ->
            out[offset++] = label.length.toByte()
            label.forEach { out[offset++] = it.code.toByte() }
        }
        out[offset++] = 0
        out[offset++] = 0; out[offset++] = 1   // QTYPE = A
        out[offset++] = 0; out[offset] = 1     // QCLASS = IN
        return out
    }

    @Test
    fun `reads the question name`() {
        assertEquals("ads.example.com", DnsMessage.readQuestionName(query("ads.example.com")))
        assertEquals("a.b.c.d.example.org", DnsMessage.readQuestionName(query("a.b.c.d.example.org")))
    }

    @Test
    fun `ignores responses and non-query opcodes`() {
        val response = query("example.com").also { it[2] = 0x81.toByte() } // QR set
        assertNull(DnsMessage.readQuestionName(response))

        val notify = query("example.com").also { it[2] = 0x20 } // opcode 4
        assertNull(DnsMessage.readQuestionName(notify))
    }

    @Test
    fun `ignores malformed messages`() {
        assertNull(DnsMessage.readQuestionName(ByteArray(4)))
        assertNull(DnsMessage.readQuestionName(ByteArray(12)))       // no question
        assertNull(DnsMessage.readQuestionName(query("example.com").copyOf(15))) // truncated
    }

    @Test
    fun `refuses a compression pointer in the first question`() {
        val pointer = query("example.com").also { it[12] = 0xC0.toByte() }
        assertNull(DnsMessage.readQuestionName(pointer))
    }

    @Test
    fun `nxdomain reply matches the query and sets rcode 3`() {
        val q = query("ads.example.com", id = 0xBEEF)
        val reply = DnsMessage.buildNxDomain(q)

        // Transaction id is echoed so the caller's resolver matches it up.
        assertEquals(q[0], reply[0])
        assertEquals(q[1], reply[1])

        val flags = ((reply[2].toInt() and 0xFF) shl 8) or (reply[3].toInt() and 0xFF)
        assertTrue("QR must be set", (flags and 0x8000) != 0)
        assertEquals("RCODE must be NXDOMAIN", 3, flags and 0x000F)
        assertTrue("RD should be carried over", (flags and 0x0100) != 0)
        assertTrue("RA should be set", (flags and 0x0080) != 0)

        assertEquals("QDCOUNT stays 1", 1, readShort(reply, 4))
        assertEquals("ANCOUNT is 0", 0, readShort(reply, 6))
        assertEquals("NSCOUNT is 0", 0, readShort(reply, 8))
        assertEquals("ARCOUNT is 0", 0, readShort(reply, 10))

        // The question section is preserved verbatim.
        assertEquals(q.size, reply.size)
        for (i in 12 until q.size) {
            assertEquals("question byte $i", q[i], reply[i])
        }
    }

    @Test
    fun `nxdomain falls back to a header-only refusal for an unparseable question`() {
        val broken = ByteArray(14).also {
            it[0] = 0x0A; it[1] = 0x0B
            it[5] = 1
            it[12] = 0x40 // a length byte that runs off the end
        }
        val reply = DnsMessage.buildNxDomain(broken)
        assertEquals(12, reply.size)
        assertEquals(0x0A.toByte(), reply[0])
        assertEquals(3, reply[3].toInt() and 0x0F)
    }

    private fun readShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
}
