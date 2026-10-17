package com.lifepowers.app.ui.screens.patterns

import com.lifepowers.core.Insight
import java.util.Locale

/** Turns a finding into something to test: a behavior phrase that reads after "on days with". */
object PatternExperiments {

    private const val HABIT_PREFIX = "habit:"

    /**
     * The habit's own name, "no alcohol" for an event that went with a worse outcome, the event
     * itself for one that went with a better one, and "less phone time after 9 pm" or "more
     * daylight" for measurements, following the good direction.
     */
    fun behavior(insight: Insight): String {
        val key = insight.input
        if (key.startsWith(HABIT_PREFIX)) return key.removePrefix(HABIT_PREFIX).trim()
        val label = PatternLabels.label(key)
        if (PatternLabels.isEventKind(key)) return if (insight.good == false) "no $label" else label
        return lowerFirst(PatternLabels.goalSuggestion(insight))
    }

    private fun lowerFirst(s: String): String {
        if (s.isEmpty() || (s.length > 1 && s[1].isUpperCase())) return s
        return s.substring(0, 1).lowercase(Locale.ROOT) + s.substring(1)
    }
}
