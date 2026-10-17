package com.lifepowers.core

data class ParsedSymptom(val kind: String, val severity: Int, val phrase: String)

/** Parses spoken or written symptoms into catalog kinds with a severity from intensity words. */
object SymptomParser {
    val catalog: Map<String, List<String>> = mapOf(
        "headache" to listOf("headache", "head is pounding", "head pounding", "head hurts", "my head"),
        "migraine" to listOf("migraine"),
        "cramps" to listOf("cramps", "cramping", "cramp"),
        "bloating" to listOf("bloated", "bloating", "bloat"),
        "breast_tenderness" to listOf("breast tenderness", "breasts hurt", "sore breasts", "tender breasts"),
        "hot_flash" to listOf("hot flash", "hot flashes", "flushing"),
        "night_sweat" to listOf("night sweat", "night sweats", "sweating at night"),
        "mood_swing" to listOf("mood swing", "mood swings", "all over the place"),
        "irritability" to listOf("irritable", "irritated", "snappy", "short fuse"),
        "anxiety" to listOf("anxious", "anxiety", "on edge", "nervous"),
        "low_libido" to listOf("low libido", "no libido", "no drive"),
        "acne" to listOf("acne", "breakout", "breaking out", "pimples"),
        "fatigue" to listOf("fatigue", "exhausted", "wiped", "drained", "so tired", "tired"),
        "brain_fog" to listOf("brain fog", "foggy", "can't think", "cannot think", "fuzzy headed"),
        "insomnia" to listOf("insomnia", "couldn't sleep", "could not sleep", "can't sleep", "cannot sleep"),
        "craving" to listOf("craving", "cravings", "craving sugar", "want to eat everything"),
        "nausea" to listOf("nausea", "nauseous", "queasy", "sick to my stomach"),
        "back_pain" to listOf("back pain", "back hurts", "my back"),
    )

    private val intensity: List<Pair<List<String>, Int>> = listOf(
        listOf("a little", "slight", "slightly", "a bit", "mild", "tiny") to 1,
        listOf("some", "somewhat", "kind of", "sort of", "a touch") to 2,
        listOf("bad", "really", "strong", "quite", "pretty", "very") to 4,
        listOf("awful", "worst", "unbearable", "pounding", "terrible", "horrible", "so much", "extremely", "excruciating") to 5,
    )

    fun parse(text: String): List<ParsedSymptom> {
        val lower = text.lowercase()
        val clauses = lower.split(Regex("[,.;!?]| and | but | plus ")).map { it.trim() }.filter { it.isNotEmpty() }
        val found = LinkedHashMap<String, ParsedSymptom>()
        for (clause in clauses) {
            for ((kind, phrases) in catalog) {
                val hit = phrases.firstOrNull { clause.contains(it) } ?: continue
                val sev = severityOf(clause)
                val existing = found[kind]
                if (existing == null || sev > existing.severity) found[kind] = ParsedSymptom(kind, sev, clause)
            }
        }
        return found.values.toList()
    }

    fun severityOf(clause: String): Int {
        val matched = intensity.filter { (words, _) -> words.any { clause.contains(it) } }.map { it.second }
        if (matched.isEmpty()) {
            return if (Regex("\\bso \\w+").containsMatchIn(clause)) 4 else 3
        }
        val high = matched.filter { it >= 4 }
        return (if (high.isNotEmpty()) high.max() else matched.min()).coerceIn(1, 5)
    }
}
