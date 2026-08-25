package dev.clonner.vpn

/**
 * Minimal DNS wire-format handling: read the question name out of a query, and turn a
 * query into an NXDOMAIN answer. Clonner never needs to build resource records — allowed
 * queries are forwarded verbatim to the upstream resolver and its reply is relayed back
 * untouched.
 */
object DnsMessage {

    private const val HEADER_SIZE = 12
    private const val MAX_NAME_LENGTH = 253

    /**
     * Returns the first question's domain name, or null when the message is not a
     * standard query we can read (truncated, a response, opcode != QUERY, no questions).
     */
    fun readQuestionName(message: ByteArray): String? {
        if (message.size < HEADER_SIZE) return null

        val flags = ((message[2].toInt() and 0xFF) shl 8) or (message[3].toInt() and 0xFF)
        val isResponse = (flags and 0x8000) != 0
        val opcode = (flags ushr 11) and 0x0F
        if (isResponse || opcode != 0) return null

        val questionCount = ((message[4].toInt() and 0xFF) shl 8) or (message[5].toInt() and 0xFF)
        if (questionCount < 1) return null

        val builder = StringBuilder(64)
        var offset = HEADER_SIZE

        while (offset < message.size) {
            val length = message[offset].toInt() and 0xFF
            if (length == 0) {
                return if (builder.isEmpty()) null else builder.toString()
            }
            // A compression pointer cannot legally start the first question name.
            if ((length and 0xC0) != 0) return null

            offset++
            if (offset + length > message.size) return null
            if (builder.length + length + 1 > MAX_NAME_LENGTH) return null

            if (builder.isNotEmpty()) builder.append('.')
            for (i in 0 until length) {
                builder.append((message[offset + i].toInt() and 0xFF).toChar())
            }
            offset += length
        }
        return null
    }

    /**
     * Builds an NXDOMAIN response for [query]: the header and question section are kept
     * so the resolver in the calling app matches it to its outstanding request, the QR
     * bit is set and RCODE becomes 3.
     */
    fun buildNxDomain(query: ByteArray): ByteArray {
        val questionEnd = questionSectionEnd(query) ?: return minimalNxDomain(query)
        val response = query.copyOf(questionEnd)

        val recursionDesired = (query[2].toInt() and 0x01)
        response[2] = (0x80 or recursionDesired).toByte()  // QR=1, opcode 0, RD carried over
        response[3] = 0x83.toByte()                        // RA=1, RCODE=3 (NXDOMAIN)

        // One question, no answer/authority/additional records.
        response[6] = 0; response[7] = 0
        response[8] = 0; response[9] = 0
        response[10] = 0; response[11] = 0
        return response
    }

    /** Offset just past the first question, or null if it does not parse. */
    private fun questionSectionEnd(message: ByteArray): Int? {
        if (message.size < HEADER_SIZE) return null
        var offset = HEADER_SIZE
        while (offset < message.size) {
            val length = message[offset].toInt() and 0xFF
            if (length == 0) {
                val end = offset + 1 + 4 // null label + QTYPE + QCLASS
                return if (end <= message.size) end else null
            }
            if ((length and 0xC0) != 0) return null
            offset += length + 1
        }
        return null
    }

    /** Fallback for a query whose question section will not parse: header-only refusal. */
    private fun minimalNxDomain(query: ByteArray): ByteArray {
        val response = ByteArray(HEADER_SIZE)
        if (query.size >= 2) {
            response[0] = query[0]
            response[1] = query[1]
        }
        response[2] = 0x80.toByte()
        response[3] = 0x83.toByte()
        return response
    }
}
