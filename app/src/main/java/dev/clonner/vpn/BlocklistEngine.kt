package dev.clonner.vpn

import dev.clonner.data.model.BlockEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * The decision point for every DNS question the VPN sees.
 *
 * Rules live in a plain [HashSet] of domains. A question is blocked when the domain
 * itself, or any of its parent domains, is on the list — so a rule for `doubleclick.net`
 * also covers `stats.g.doubleclick.net`. User overrides win over list rules.
 *
 * Reads happen on the packet thread for every lookup, so the hot path is allocation-light
 * and the rule set is swapped by reference rather than mutated in place.
 */
object BlocklistEngine {

    @Volatile
    private var rules: Rules = Rules(emptySet(), emptySet(), emptySet())

    private val blockedCount = AtomicLong()
    private val allowedCount = AtomicLong()

    private val _stats = MutableStateFlow(ShieldStats())
    val stats: StateFlow<ShieldStats> = _stats.asStateFlow()

    private val recent = ArrayDeque<BlockEvent>()
    private val _recentBlocks = MutableStateFlow<List<BlockEvent>>(emptyList())
    val recentBlocks: StateFlow<List<BlockEvent>> = _recentBlocks.asStateFlow()

    private class Rules(
        val blocked: Set<String>,
        val allowOverrides: Set<String>,
        val denyOverrides: Set<String>,
    )

    fun replaceRules(blocked: Set<String>, allowOverrides: Set<String>, denyOverrides: Set<String>) {
        rules = Rules(blocked, allowOverrides, denyOverrides)
        _stats.value = _stats.value.copy(ruleCount = blocked.size)
    }

    val ruleCount: Int get() = rules.blocked.size

    /** True when the DNS question for [domain] should be answered with NXDOMAIN. */
    fun shouldBlock(domain: String): Boolean {
        val snapshot = rules
        val host = domain.lowercase().removeSuffix(".").removePrefix("www.")
        if (host.isEmpty()) return false

        // Walk the label hierarchy: a.b.example.com -> b.example.com -> example.com -> com
        var index = 0
        while (true) {
            val candidate = host.substring(index)
            if (candidate in snapshot.allowOverrides) return false
            if (candidate in snapshot.denyOverrides) return true
            if (candidate in snapshot.blocked) return true

            val dot = host.indexOf('.', index)
            if (dot < 0) return false
            index = dot + 1
            if (index >= host.length) return false
        }
    }

    fun record(domain: String, blocked: Boolean) {
        if (blocked) blockedCount.incrementAndGet() else allowedCount.incrementAndGet()
        _stats.value = _stats.value.copy(
            blocked = blockedCount.get(),
            allowed = allowedCount.get(),
            ruleCount = rules.blocked.size,
        )
        if (!blocked) return

        synchronized(recent) {
            recent.addFirst(BlockEvent(domain, true, System.currentTimeMillis()))
            while (recent.size > RECENT_LIMIT) recent.removeLast()
            _recentBlocks.value = recent.toList()
        }
    }

    fun resetCounters() {
        blockedCount.set(0)
        allowedCount.set(0)
        synchronized(recent) {
            recent.clear()
            _recentBlocks.value = emptyList()
        }
        _stats.value = ShieldStats(ruleCount = rules.blocked.size)
    }

    private const val RECENT_LIMIT = 60
}

data class ShieldStats(
    val blocked: Long = 0,
    val allowed: Long = 0,
    val ruleCount: Int = 0,
) {
    val total: Long get() = blocked + allowed
    val blockRate: Float get() = if (total == 0L) 0f else blocked.toFloat() / total
}
