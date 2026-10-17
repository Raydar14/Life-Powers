package com.lifepowers.app.patterns

import com.lifepowers.core.Insight
import com.lifepowers.core.InsightKind
import kotlinx.serialization.Serializable

/*
 * Core Insight is not @Serializable, so filesDir/patterns.json stores these mirrors.
 * Every field is defaulted so a file written by an older build still decodes.
 */

@Serializable
data class InsightDto(
    val kind: String = InsightKind.TRENDS_WITH.name,
    val input: String = "",
    val outcome: String = "",
    val lag: Int = 0,
    val n: Int = 0,
    val effect: Double = 0.0,
    val pValue: Double = 1.0,
    val qValue: Double = 1.0,
    val preliminary: Boolean = true,
    val good: Boolean? = null,
    val nWith: Int = 0,
    val nWithout: Int = 0,
) {
    fun toCore(): Insight? {
        val k = InsightKind.entries.firstOrNull { it.name == kind } ?: return null
        return Insight(k, input, outcome, lag, n, effect, pValue, qValue, preliminary, good, nWith, nWithout)
    }

    companion object {
        fun from(i: Insight) = InsightDto(
            i.kind.name, i.input, i.outcome, i.lag, i.n, i.effect, i.pValue, i.qValue, i.preliminary, i.good, i.nWith, i.nWithout,
        )
    }
}

@Serializable
data class PatternsFile(
    val computedUtcMs: Long = 0,
    val daysWithData: Int = 0,
    val behaviorCounts: Map<String, Int> = emptyMap(),
    val insights: List<InsightDto> = emptyList(),
)
