package com.lifepowers.core

/**
 * Per stream sync cursors packed into the single cursor string a connector stores.
 * Format: `stream=value;stream=value`. A legacy cursor with no `=` applies to every stream.
 * A stream that failed keeps its previous cursor, so its history is retried next time.
 */
object StreamCursors {
    fun decode(cursor: String?): Map<String, String> {
        if (cursor.isNullOrBlank()) return emptyMap()
        if (!cursor.contains('=')) return mapOf(LEGACY to cursor)
        return cursor.split(';').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0 || i == part.length - 1) null else part.substring(0, i) to part.substring(i + 1)
        }.toMap()
    }

    /** The stored cursor for [stream], falling back to a legacy whole-connector cursor. */
    fun forStream(decoded: Map<String, String>, stream: String): String? = decoded[stream] ?: decoded[LEGACY]

    /** Advances only [succeeded] streams to [newValue]; every other stream keeps what it had. */
    fun advance(previous: Map<String, String>, streams: Collection<String>, succeeded: Set<String>, newValue: String): String? {
        val next = LinkedHashMap<String, String>()
        for (s in streams) {
            val value = if (s in succeeded) newValue else forStream(previous, s)
            if (value != null) next[s] = value
        }
        return if (next.isEmpty()) null else next.entries.joinToString(";") { "${it.key}=${it.value}" }
    }

    private const val LEGACY = "*"
}
