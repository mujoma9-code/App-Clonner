package dev.clonner

import dev.clonner.vpn.BlocklistEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BlocklistEngineTest {

    @Before
    fun reset() {
        BlocklistEngine.replaceRules(emptySet(), emptySet(), emptySet())
        BlocklistEngine.resetCounters()
    }

    @Test
    fun `blocks an exact match`() {
        BlocklistEngine.replaceRules(setOf("doubleclick.net"), emptySet(), emptySet())
        assertTrue(BlocklistEngine.shouldBlock("doubleclick.net"))
    }

    @Test
    fun `a rule covers subdomains`() {
        BlocklistEngine.replaceRules(setOf("doubleclick.net"), emptySet(), emptySet())
        assertTrue(BlocklistEngine.shouldBlock("stats.g.doubleclick.net"))
        assertTrue(BlocklistEngine.shouldBlock("a.b.c.doubleclick.net"))
    }

    @Test
    fun `a rule does not leak to a lookalike parent`() {
        BlocklistEngine.replaceRules(setOf("ads.example.com"), emptySet(), emptySet())
        assertFalse(BlocklistEngine.shouldBlock("example.com"))
        assertFalse(BlocklistEngine.shouldBlock("notads.example.com"))
        assertFalse(BlocklistEngine.shouldBlock("myads.example.com"))
    }

    @Test
    fun `normalises case, trailing dot and www`() {
        BlocklistEngine.replaceRules(setOf("ads.example.com"), emptySet(), emptySet())
        assertTrue(BlocklistEngine.shouldBlock("ADS.Example.com"))
        assertTrue(BlocklistEngine.shouldBlock("ads.example.com."))
        assertTrue(BlocklistEngine.shouldBlock("www.ads.example.com"))
    }

    @Test
    fun `an allow override beats the list`() {
        BlocklistEngine.replaceRules(
            blocked = setOf("example.com"),
            allowOverrides = setOf("shop.example.com"),
            denyOverrides = emptySet(),
        )
        assertFalse(BlocklistEngine.shouldBlock("shop.example.com"))
        assertTrue(BlocklistEngine.shouldBlock("ads.example.com"))
    }

    @Test
    fun `the most specific override wins`() {
        BlocklistEngine.replaceRules(
            blocked = emptySet(),
            allowOverrides = setOf("cdn.example.com"),
            denyOverrides = setOf("example.com"),
        )
        assertFalse("child allow beats parent deny", BlocklistEngine.shouldBlock("cdn.example.com"))
        assertTrue(BlocklistEngine.shouldBlock("other.example.com"))
    }

    @Test
    fun `a deny override blocks without any list`() {
        BlocklistEngine.replaceRules(emptySet(), emptySet(), setOf("tracker.io"))
        assertTrue(BlocklistEngine.shouldBlock("beacon.tracker.io"))
    }

    @Test
    fun `an empty rule set blocks nothing`() {
        assertFalse(BlocklistEngine.shouldBlock("example.com"))
        assertFalse(BlocklistEngine.shouldBlock(""))
    }

    @Test
    fun `a bare tld rule does not block everything under it`() {
        BlocklistEngine.replaceRules(setOf("ads.net"), emptySet(), emptySet())
        assertFalse(BlocklistEngine.shouldBlock("example.net"))
    }

    @Test
    fun `counters and the block rate track decisions`() {
        BlocklistEngine.replaceRules(setOf("ads.example.com"), emptySet(), emptySet())
        BlocklistEngine.record("ads.example.com", blocked = true)
        BlocklistEngine.record("ads.example.com", blocked = true)
        BlocklistEngine.record("example.com", blocked = false)
        BlocklistEngine.record("example.org", blocked = false)

        val stats = BlocklistEngine.stats.value
        assertEquals(2L, stats.blocked)
        assertEquals(2L, stats.allowed)
        assertEquals(4L, stats.total)
        assertEquals(0.5f, stats.blockRate, 0.0001f)
        assertEquals(1, stats.ruleCount)

        assertEquals(2, BlocklistEngine.recentBlocks.value.size)
        assertTrue(BlocklistEngine.recentBlocks.value.all { it.blocked })
    }

    @Test
    fun `resetting clears counters and history`() {
        BlocklistEngine.record("ads.example.com", blocked = true)
        BlocklistEngine.resetCounters()

        assertEquals(0L, BlocklistEngine.stats.value.blocked)
        assertEquals(0f, BlocklistEngine.stats.value.blockRate, 0.0001f)
        assertTrue(BlocklistEngine.recentBlocks.value.isEmpty())
    }
}
