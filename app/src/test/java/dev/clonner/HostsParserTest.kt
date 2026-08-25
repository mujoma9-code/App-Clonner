package dev.clonner

import dev.clonner.data.HostsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostsParserTest {

    @Test
    fun `reads hosts format`() {
        assertEquals("ads.example.com", HostsParser.parse("0.0.0.0 ads.example.com"))
        assertEquals("ads.example.com", HostsParser.parse("127.0.0.1\tads.example.com"))
    }

    @Test
    fun `reads bare domain lists`() {
        assertEquals("tracker.example.org", HostsParser.parse("tracker.example.org"))
    }

    @Test
    fun `strips comments and blank lines`() {
        assertEquals("ads.example.com", HostsParser.parse("0.0.0.0 ads.example.com # inline note"))
        assertNull(HostsParser.parse("# a whole-line comment"))
        assertNull(HostsParser.parse("! adblock-style comment"))
        assertNull(HostsParser.parse("   "))
        assertNull(HostsParser.parse(""))
    }

    @Test
    fun `normalises case, trailing dot and www`() {
        assertEquals("ads.example.com", HostsParser.parse("0.0.0.0 ADS.Example.COM"))
        assertEquals("ads.example.com", HostsParser.parse("0.0.0.0 ads.example.com."))
        assertEquals("example.com", HostsParser.parse("0.0.0.0 www.example.com"))
    }

    @Test
    fun `rejects loopback bookkeeping entries`() {
        assertNull(HostsParser.parse("127.0.0.1 localhost"))
        assertNull(HostsParser.parse("::1 ip6-localhost"))
        assertNull(HostsParser.parse("255.255.255.255 broadcasthost"))
    }

    @Test
    fun `rejects malformed hostnames`() {
        assertNull(HostsParser.parse("0.0.0.0 no-dot-here"))
        assertNull(HostsParser.parse("0.0.0.0 bad..domain.com"))
        assertNull(HostsParser.parse("0.0.0.0 .leading.dot.com"))
        assertNull(HostsParser.parse("0.0.0.0 has spaces.com extra"))
        assertNull(HostsParser.parse("0.0.0.0 sla/sh.com"))
        assertNull(HostsParser.parse("0.0.0.0 " + "a".repeat(250) + ".com"))
    }

    @Test
    fun `rejects bare ip addresses so a list cannot blackhole a resolver`() {
        assertNull(HostsParser.parse("8.8.8.8"))
        assertNull(HostsParser.parse("0.0.0.0 1.1.1.1"))
    }

    @Test
    fun `ignores redirect targets that are not sinkholes`() {
        // A real mapping like "10.0.0.5 intranet.example.com" is a LAN host, not a block rule.
        assertNull(HostsParser.parse("10.0.0.5 intranet.example.com"))
    }
}
