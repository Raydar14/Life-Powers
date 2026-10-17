package com.lifepowers.app.ui.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.AutoSources
import com.lifepowers.app.domain.Goal
import com.lifepowers.app.domain.GoalRepository
import com.lifepowers.core.Cadence
import com.lifepowers.core.Comparison
import com.lifepowers.core.Metrics
import com.lifepowers.core.TargetKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/** Where a done or not goal gets its automatic check. */
enum class CheckSource { MEASUREMENT, JOURNAL }

/** The edit form, kept apart from [Goal] so switching kinds does not lose numbers. */
data class GoalForm(
    val name: String = "",
    val cadence: Cadence = Cadence.DAILY,
    val kind: TargetKind = TargetKind.CHECK,
    val count: Int = 3,
    val minutes: Int = 10,
    val days: Int = 1,
    val metric: String? = null,
    val comparison: Comparison = Comparison.AT_LEAST,
    val threshold: Double = 0.0,
    val auto: Boolean = false,
    val checkSource: CheckSource = CheckSource.MEASUREMENT,
    val reminderTimes: List<LocalTime> = emptyList(),
    val reminderDays: Set<DayOfWeek> = emptySet(),
    val closingNotice: Boolean = true,
) {
    /** A measurement is part of the goal: METRIC goals, and done or not goals the data checks. */
    val usesMetric: Boolean
        get() = kind == TargetKind.METRIC || (kind == TargetKind.CHECK && auto && checkSource == CheckSource.MEASUREMENT)
}

data class GoalEditUiState(
    val form: GoalForm = GoalForm(),
    val existing: Goal? = null,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
) {
    val editing: Boolean get() = existing != null
    val canSave: Boolean get() = form.name.isNotBlank() && !saving && (!form.usesMetric || form.metric != null)
}

internal fun maxDays(c: Cadence): Int = when (c) {
    Cadence.DAILY -> 1
    Cadence.WEEKLY -> 7
    Cadence.MONTHLY -> 31
    Cadence.YEARLY -> 366
}

internal fun defaultDays(c: Cadence): Int = when (c) {
    Cadence.DAILY -> 1
    Cadence.WEEKLY -> 5
    Cadence.MONTHLY -> 20
    Cadence.YEARLY -> 200
}

/** Builds the goal to save. [existing] keeps id, habit flag, archive state and creation time. */
internal fun GoalForm.toGoal(existing: Goal?): Goal {
    val linked = if (usesMetric) metric else null
    val autoSource = when {
        !auto -> null
        kind == TargetKind.CHECK && checkSource == CheckSource.JOURNAL -> AutoSources.JOURNAL
        linked == Metrics.SCREEN_TOTAL_MIN -> AutoSources.PHONE_DOWN
        linked != null -> AutoSources.METRIC
        else -> null
    }
    val target = when (kind) {
        TargetKind.CHECK -> 1.0
        TargetKind.COUNT -> count.coerceAtLeast(1).toDouble()
        TargetKind.MINUTES -> minutes.coerceAtLeast(1).toDouble()
        TargetKind.METRIC -> days.coerceIn(1, maxDays(cadence)).toDouble()
    }
    val base = existing ?: Goal(name = name.trim(), cadence = cadence, kind = kind)
    return base.copy(
        name = name.trim(),
        cadence = cadence,
        kind = kind,
        target = target,
        metric = linked,
        comparison = comparison,
        threshold = if (linked != null) threshold else null,
        autoSource = autoSource,
        reminderTimes = reminderTimes.distinct().sorted(),
        reminderDays = if (reminderDays.size == 7) emptySet() else reminderDays,
        closingNotice = closingNotice,
    )
}

@HiltViewModel
class GoalEditViewModel @Inject constructor(private val goalRepository: GoalRepository) : ViewModel() {

    private val _state = MutableStateFlow(GoalEditUiState())
    val state: StateFlow<GoalEditUiState> = _state.asStateFlow()

    private var loadedKey: Pair<Long, String?>? = null

    /** Loads the goal to edit, or a fresh form shaped by [prefillName]. Repeated calls are ignored. */
    fun load(goalId: Long, prefillName: String?) {
        val key = goalId to prefillName
        if (loadedKey == key) return
        loadedKey = key
        viewModelScope.launch {
            val existing = if (goalId > 0) goalRepository.goals.first().firstOrNull { it.id == goalId } else null
            val form = if (existing != null) formOf(existing) else prefill(prefillName)
            _state.value = GoalEditUiState(form = form, existing = existing, loaded = true)
        }
    }

    fun update(transform: (GoalForm) -> GoalForm) {
        _state.update { it.copy(form = transform(it.form)) }
    }

    fun setCadence(cadence: Cadence) = update { f ->
        if (f.cadence == cadence) f else f.copy(cadence = cadence, days = defaultDays(cadence).coerceAtMost(maxDays(cadence)))
    }

    fun setKind(kind: TargetKind) = update { f ->
        if (f.kind == kind) return@update f
        var next = f.copy(kind = kind, auto = kind == TargetKind.METRIC)
        if (kind == TargetKind.METRIC && next.metric == null) next = withMetric(next, GoalWords.metricChoices.first().key)
        if (kind == TargetKind.METRIC) next = next.copy(days = next.days.coerceIn(1, maxDays(next.cadence)))
        next
    }

    fun setAuto(on: Boolean) = update { f ->
        var next = f.copy(auto = on)
        if (next.usesMetric && next.metric == null) next = withMetric(next, GoalWords.metricChoices.first().key)
        next
    }

    fun setCheckSource(source: CheckSource) = update { f ->
        var next = f.copy(checkSource = source)
        if (next.usesMetric && next.metric == null) next = withMetric(next, GoalWords.metricChoices.first().key)
        next
    }

    fun pickMetric(key: String) = update { withMetric(it, key) }

    fun stepThreshold(up: Boolean) = update { f ->
        val c = GoalWords.choice(f.metric) ?: return@update f
        val next = if (up) f.threshold + c.step else f.threshold - c.step
        f.copy(threshold = next.coerceIn(c.min, c.max))
    }

    /** [index] below zero adds a time. */
    fun setTime(index: Int, time: LocalTime) = update { f ->
        val list = f.reminderTimes.toMutableList()
        if (index in list.indices) list[index] = time else list.add(time)
        f.copy(reminderTimes = list.distinct().sorted())
    }

    fun removeTime(index: Int) = update { f ->
        f.copy(reminderTimes = f.reminderTimes.filterIndexed { i, _ -> i != index })
    }

    /** An empty set means every day. The last day cannot be switched off. */
    fun toggleDay(day: DayOfWeek) = update { f ->
        val current = if (f.reminderDays.isEmpty()) DayOfWeek.values().toSet() else f.reminderDays
        val next = if (day in current) current - day else current + day
        when {
            next.isEmpty() -> f
            next.size == 7 -> f.copy(reminderDays = emptySet())
            else -> f.copy(reminderDays = next)
        }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            goalRepository.save(s.form.toGoal(s.existing))
            _state.update { it.copy(saving = false, done = true) }
        }
    }

    fun archive() {
        val id = _state.value.existing?.id ?: return
        viewModelScope.launch {
            goalRepository.archive(id)
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = _state.value.existing?.id ?: return
        viewModelScope.launch {
            goalRepository.delete(id)
            _state.update { it.copy(done = true) }
        }
    }

    private fun withMetric(f: GoalForm, key: String): GoalForm {
        val c = GoalWords.choice(key) ?: return f.copy(metric = key)
        return f.copy(metric = key, threshold = c.default, comparison = c.comparison)
    }

    private fun formOf(goal: Goal): GoalForm = GoalForm(
        name = goal.name,
        cadence = goal.cadence,
        kind = goal.kind,
        count = if (goal.kind == TargetKind.COUNT) goal.target.roundToInt().coerceAtLeast(1) else 3,
        minutes = if (goal.kind == TargetKind.MINUTES) goal.target.roundToInt().coerceAtLeast(1) else 10,
        days = if (goal.kind == TargetKind.METRIC) goal.target.roundToInt().coerceIn(1, maxDays(goal.cadence)) else defaultDays(goal.cadence),
        metric = goal.metric,
        comparison = goal.comparison,
        threshold = goal.threshold ?: GoalWords.choice(goal.metric)?.default ?: 0.0,
        auto = goal.autoSource != null,
        checkSource = if (goal.autoSource == AutoSources.JOURNAL) CheckSource.JOURNAL else CheckSource.MEASUREMENT,
        reminderTimes = goal.reminderTimes.sorted(),
        reminderDays = goal.reminderDays,
        closingNotice = goal.closingNotice,
    )

    /**
     * Shapes a new goal from a suggested name: "Walk 3 times a week" becomes a weekly count of 3,
     * "Meditate 10 minutes a day" a daily 10 minutes, "Less phone time" a phone time measurement.
     */
    private fun prefill(name: String?): GoalForm {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) return GoalForm()
        val lower = n.lowercase(Locale.getDefault())
        val match = Regex("""(\d+)\s+(times?|minutes?|mins?)\s+(a|per|each)\s+(day|week|month|year)""").find(lower)
        if (match != null) {
            val num = match.groupValues[1].toIntOrNull() ?: 1
            val cadence = when (match.groupValues[4]) {
                "day" -> Cadence.DAILY
                "week" -> Cadence.WEEKLY
                "month" -> Cadence.MONTHLY
                else -> Cadence.YEARLY
            }
            return if (match.groupValues[2].startsWith("time")) {
                GoalForm(name = n, cadence = cadence, kind = TargetKind.COUNT, count = num.coerceIn(1, 99), days = defaultDays(cadence))
            } else {
                GoalForm(name = n, cadence = cadence, kind = TargetKind.MINUTES, minutes = num.coerceIn(1, 600), days = defaultDays(cadence))
            }
        }
        val directional = lower.startsWith("more ") || lower.startsWith("less ") || lower.startsWith("fewer ")
        val choice = GoalWords.metricChoices.firstOrNull { lower.contains(it.label.lowercase(Locale.getDefault())) }
        if (directional && choice != null) {
            return GoalForm(
                name = n, cadence = Cadence.WEEKLY, kind = TargetKind.METRIC, days = 5,
                metric = choice.key, threshold = choice.default,
                comparison = if (lower.startsWith("more ")) Comparison.AT_LEAST else Comparison.AT_MOST,
                auto = true,
            )
        }
        if (lower.contains("journal")) {
            return GoalForm(name = n, kind = TargetKind.CHECK, auto = true, checkSource = CheckSource.JOURNAL)
        }
        return GoalForm(name = n)
    }
}
