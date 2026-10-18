package com.lifepowers.app.ui.screens.breathe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.BreathGuide
import com.lifepowers.app.domain.BreathRepository
import com.lifepowers.app.domain.BreathSessionController
import com.lifepowers.app.domain.Goal
import com.lifepowers.app.domain.GoalRepository
import com.lifepowers.app.domain.SavedBreathPattern
import com.lifepowers.core.BreathIssue
import com.lifepowers.core.BreathPattern
import com.lifepowers.core.BreathSeverity
import com.lifepowers.core.Breathing
import com.lifepowers.core.TargetKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToLong

enum class BreathField { INHALE, HOLD_IN, EXHALE, HOLD_OUT }

enum class GuideOption { VISUAL, TONES, VOICE, VIBRATION }

/** The edit form, in seconds. */
data class BreathForm(
    val inhaleS: Double = 4.0,
    val holdInS: Double = 0.0,
    val exhaleS: Double = 6.0,
    val holdOutS: Double = 0.0,
    val guide: BreathGuide = BreathGuide(),
    val sessionMin: Int? = 5,
    val goalId: Long? = null,
    val name: String = "",
) {
    val pattern: BreathPattern get() = BreathPattern(inhaleS, holdInS, exhaleS, holdOutS)

    fun value(f: BreathField): Double = when (f) {
        BreathField.INHALE -> inhaleS
        BreathField.HOLD_IN -> holdInS
        BreathField.EXHALE -> exhaleS
        BreathField.HOLD_OUT -> holdOutS
    }

    fun withValue(f: BreathField, v: Double): BreathForm = when (f) {
        BreathField.INHALE -> copy(inhaleS = v)
        BreathField.HOLD_IN -> copy(holdInS = v)
        BreathField.EXHALE -> copy(exhaleS = v)
        BreathField.HOLD_OUT -> copy(holdOutS = v)
    }

    fun isOn(o: GuideOption): Boolean = when (o) {
        GuideOption.VISUAL -> guide.visual
        GuideOption.TONES -> guide.tones
        GuideOption.VOICE -> guide.voice
        GuideOption.VIBRATION -> guide.vibration
    }
}

internal const val STEP_S = 0.5

internal fun lowest(f: BreathField): Double = when (f) {
    BreathField.INHALE, BreathField.EXHALE -> Breathing.MIN_PHASE_S
    BreathField.HOLD_IN, BreathField.HOLD_OUT -> 0.0
}

internal fun highest(f: BreathField): Double = when (f) {
    BreathField.INHALE, BreathField.EXHALE -> Breathing.MAX_PHASE_S
    BreathField.HOLD_IN, BreathField.HOLD_OUT -> Breathing.MAX_HOLD_S
}

private fun half(v: Double): Double = (v * 2).roundToLong() / 2.0

enum class EditEvent { STARTED, SAVED }

data class BreathEditUiState(
    val form: BreathForm = BreathForm(),
    val existing: SavedBreathPattern? = null,
    val loaded: Boolean = false,
    val habits: List<Goal> = emptyList(),
    val sessionRunning: Boolean = false,
    val busy: Boolean = false,
    /** A plain sentence after a refused save or start. */
    val message: String? = null,
) {
    val issues: List<BreathIssue> get() = Breathing.validate(form.pattern)
    val blocked: Boolean get() = issues.any { it.severity == BreathSeverity.BLOCK }
    val canStart: Boolean get() = loaded && !blocked && !busy && !sessionRunning
    val canSave: Boolean get() = loaded && !blocked && !busy && form.name.isNotBlank()
}

/** The private part of the state; habits and the running flag join it from their flows. */
private data class EditCore(
    val form: BreathForm = BreathForm(),
    val existing: SavedBreathPattern? = null,
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class BreathEditViewModel @Inject constructor(
    private val breathRepository: BreathRepository,
    goalRepository: GoalRepository,
    private val controller: BreathSessionController,
) : ViewModel() {

    private val core = MutableStateFlow(EditCore())

    private val habits = goalRepository.goals.map { goals ->
        goals.filter { !it.archived && it.isHabit && (it.kind == TargetKind.MINUTES || it.kind == TargetKind.CHECK) }
    }

    private val running = controller.live.map { it != null }.distinctUntilChanged()

    val state: StateFlow<BreathEditUiState> = combine(core, habits, running) { c, h, r ->
        BreathEditUiState(
            form = c.form,
            existing = c.existing,
            loaded = c.loaded,
            habits = h,
            sessionRunning = r,
            busy = c.busy,
            message = c.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BreathEditUiState(sessionRunning = controller.live.value != null))

    private val _event = MutableStateFlow<EditEvent?>(null)
    val event: StateFlow<EditEvent?> = _event.asStateFlow()

    private var loadedKey: Pair<Long, String?>? = null

    /** Loads a saved pattern by id, or a fresh form prefilled from a preset by name. Repeated calls are ignored. */
    fun load(patternId: Long, presetName: String?) {
        val key = patternId to presetName
        if (loadedKey == key) return
        loadedKey = key
        viewModelScope.launch {
            val existing = if (patternId > 0) breathRepository.patterns.first().firstOrNull { it.id == patternId } else null
            val preset = if (existing == null && presetName != null) Breathing.PRESETS.firstOrNull { it.name == presetName } else null
            val form = when {
                existing != null -> BreathForm(
                    inhaleS = existing.pattern.inhaleS,
                    holdInS = existing.pattern.holdInS,
                    exhaleS = existing.pattern.exhaleS,
                    holdOutS = existing.pattern.holdOutS,
                    guide = existing.guide,
                    sessionMin = existing.sessionMin,
                    name = existing.name,
                )
                preset != null -> BreathForm(
                    inhaleS = preset.pattern.inhaleS,
                    holdInS = preset.pattern.holdInS,
                    exhaleS = preset.pattern.exhaleS,
                    holdOutS = preset.pattern.holdOutS,
                    name = preset.name,
                )
                else -> BreathForm()
            }
            val missing = if (patternId > 0 && existing == null) "That pattern is no longer saved, so this starts fresh." else null
            core.value = EditCore(form = form, existing = existing, loaded = true, message = missing)
        }
    }

    private fun edit(transform: (BreathForm) -> BreathForm) {
        core.update { it.copy(form = transform(it.form), message = null) }
    }

    fun step(field: BreathField, up: Boolean) = edit { f ->
        val next = half(f.value(field) + if (up) STEP_S else -STEP_S).coerceIn(lowest(field), highest(field))
        f.withValue(field, next)
    }

    /** One tap fix for a longer in breath: the out breath grows to match it. */
    fun matchOut() = edit { f -> f.copy(exhaleS = f.inhaleS.coerceAtMost(Breathing.MAX_PHASE_S)) }

    /** At least one guide stays on; turning off the last one is ignored. */
    fun toggleGuide(option: GuideOption) = edit { f ->
        val g = f.guide
        val next = when (option) {
            GuideOption.VISUAL -> g.copy(visual = !g.visual)
            GuideOption.TONES -> g.copy(tones = !g.tones)
            GuideOption.VOICE -> g.copy(voice = !g.voice)
            GuideOption.VIBRATION -> g.copy(vibration = !g.vibration)
        }
        if (!next.visual && !next.tones && !next.voice && !next.vibration) f else f.copy(guide = next)
    }

    fun setLength(min: Int?) = edit { it.copy(sessionMin = min) }

    fun setGoal(goalId: Long?) = edit { it.copy(goalId = goalId) }

    fun setName(name: String) = edit { it.copy(name = name.take(60)) }

    fun consumeEvent() {
        _event.value = null
    }

    private fun BreathForm.toSaved(existing: SavedBreathPattern?): SavedBreathPattern {
        val cleanName = name.trim()
        val base = existing ?: SavedBreathPattern(name = cleanName, pattern = pattern)
        return base.copy(name = cleanName.ifBlank { base.name }, pattern = pattern, guide = guide, sessionMin = sessionMin)
    }

    private fun refusal(form: BreathForm): String =
        Breathing.validate(form.pattern).firstOrNull { it.severity == BreathSeverity.BLOCK }?.message
            ?: "This pattern could not be saved. Check the timings and try again."

    /**
     * Starts the pattern. A new or preset pattern starts without being saved; an existing saved
     * pattern keeps its edits and starts under its own id.
     */
    fun startNow() {
        val s = state.value
        if (!s.canStart) return
        core.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val existing = s.existing
            val toStart = if (existing != null) {
                val updated = s.form.toSaved(existing)
                val id = breathRepository.savePattern(updated)
                if (id == null) {
                    core.update { it.copy(busy = false, message = refusal(s.form)) }
                    return@launch
                }
                updated.copy(id = id)
            } else {
                val p = s.form.pattern
                SavedBreathPattern(
                    id = 0,
                    name = s.form.name.trim().ifBlank { p.describe().replaceFirstChar { it.uppercase() } },
                    pattern = p,
                    guide = s.form.guide,
                    sessionMin = s.form.sessionMin,
                )
            }
            if (controller.live.value != null) {
                core.update { it.copy(busy = false, message = "A session is already running. Finish it before starting another.") }
                return@launch
            }
            controller.start(toStart, s.form.goalId)
            core.update { it.copy(busy = false, existing = if (existing != null) toStart else null) }
            _event.value = EditEvent.STARTED
        }
    }

    fun save() {
        val s = state.value
        if (!s.canSave) return
        core.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val toSave = s.form.toSaved(s.existing)
            val id = breathRepository.savePattern(toSave)
            if (id == null) {
                core.update { it.copy(busy = false, message = refusal(s.form)) }
            } else {
                core.update { it.copy(busy = false, existing = toSave.copy(id = id)) }
                _event.value = EditEvent.SAVED
            }
        }
    }
}
