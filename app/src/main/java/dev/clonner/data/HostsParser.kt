package dev.clonner.data

/**
 * Reads one line of a blocklist into a bare domain.
 *
 * Handles both hosts format (`0.0.0.0 ads.example.com`) and plain domain lists, and
 * rejects comments, loopback bookkeeping entries and anything that is not a plausible
 * hostname — a malformed line in a 150k-line list must not become a rule that blocks
 * something real.
 */
object HostsParser {

    fun parse(raw: String): String? {
        val line = raw.substringBefore('#').substringBefore('!').trim()
        if (line.isEmpty()) return null

        val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
        val candidate = when {
            parts.size >= 2 && parts[0] in REDIRECT_TARGETS -> parts[1]
            parts.size == 1 -> parts[0]
            else -> return null
        }.lowercase().removeSuffix(".")

        if (candidate.isEmpty() || candidate.length > 253) return null
        if (candidate in NON_DOMAINS) return null
        if (!candidate.contains('.')) return null
        if (candidate.any { it !in ALLOWED_CHARS }) return null
        if (candidate.startsWith('.') || candidate.contains("..")) return null
        if (candidate.isIpv4()) return null

        return candidate.removePrefix("www.").ifEmpty { null }
    }

    private fun String.isIpv4(): Boolean {
        val octets = split('.')
        if (octets.size != 4) return false
        return octets.all { part ->
            part.isNotEmpty() && part.all(Char::isDigit) && part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    private const val ALLOWED_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789.-_"

    private val REDIRECT_TARGETS = setOf("0.0.0.0", "127.0.0.1", "::1", "::")

    private val NON_DOMAINS = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost",
        "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
        "ip6-allnodes", "ip6-allrouters",
    )
}
