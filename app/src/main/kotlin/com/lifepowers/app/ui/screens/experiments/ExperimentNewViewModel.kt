package com.lifepowers.app.ui.screens.experiments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.ExperimentRecord
import com.lifepowers.app.domain.ExperimentRepository
import com.lifepowers.app.experiments.ExperimentMath
import com.lifepowers.core.Condition
import com.lifepowers.core.ExperimentSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ExperimentForm(
    val behavior: String = "",
    val outcome: String? = null,
    val nextDay: Boolean = false,
    val blockDays: Int = 3,
    val targetDays: Int = 28,
    val reminderHour: Int? = 8,
)

data class ExperimentNewUiState(
    val form: ExperimentForm = ExperimentForm(),
    /** Outcome keys offered, the friendly list plus a prefilled key that is not on it. */
    val outcomes: List<String> = ExperimentChoices.outcomes,
    val today: LocalDate = LocalDate.now(),
    /**
     * The id the experiment will most likely get. The schedule's coin is seeded with the id, so the
     * preview uses it and the repository keeps it when it is free.
     */
    val seedId: Long = 1L,
    val saving: Boolean = false,
    val failed: Boolean = false,
    val doneId: Long? = null,
) {
    val canStart: Boolean get() = form.behavior.isNotBlank() && form.outcome != null && !saving && doneId == null

    fun spec(): ExperimentSpec = ExperimentSpec(
        id = seedId,
        behavior = form.behavior.trim().trimEnd('.'),
        outcomeMetric = form.outcome ?: "",
        startedOn = today,
        blockDays = form.blockDays,
        targetDays = form.targetDays,
        outcomeLag = if (form.nextDay) 1 else 0,
    )

    /** The first two weeks, on and off, as the schedule will run. */
    val preview: List<Condition> get() = ExperimentMath.preview(spec(), 14)
}

@HiltViewModel
class ExperimentNewViewModel @Inject constructor(private val repository: ExperimentRepository) : ViewModel() {

    private val _state = MutableStateFlow(ExperimentNewUiState())
    val state: StateFlow<ExperimentNewUiState> = _state.asStateFlow()

    private var prefilled = false

    init {
        viewModelScope.launch {
            val maxId = try {
                repository.views(LocalDate.now()).first().maxOfOrNull { it.record.spec.id } ?: 0L
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                0L
            }
            _state.update { it.copy(seedId = maxId + 1) }
        }
    }

    /** Applies the route's prefill once, so returning to the screen keeps what was typed. */
    fun prefill(behavior: String?, outcome: String?) {
        if (prefilled) return
        prefilled = true
        _state.update { s ->
            val outcomes = if (outcome != null && outcome !in s.outcomes) listOf(outcome) + s.outcomes else s.outcomes
            s.copy(
                outcomes = outcomes,
                form = s.form.copy(
                    behavior = behavior?.trim()?.let { ExperimentMath.upperFirst(it) } ?: s.form.behavior,
                    outcome = outcome ?: s.form.outcome,
                    nextDay = outcome?.let { ExperimentMath.defaultLag(it) == 1 } ?: s.form.nextDay,
                ),
            )
        }
    }

    fun setBehavior(text: String) = _state.update { it.copy(form = it.form.copy(behavior = text)) }

    /** Picking an outcome sets whether it shows up the next day to that outcome's usual. */
    fun setOutcome(key: String) = _state.update {
        it.copy(form = it.form.copy(outcome = key, nextDay = ExperimentMath.defaultLag(key) == 1))
    }

    fun setNextDay(on: Boolean) = _state.update { it.copy(form = it.form.copy(nextDay = on)) }
    fun setBlockDays(days: Int) = _state.update { it.copy(form = it.form.copy(blockDays = days)) }
    fun setTargetDays(days: Int) = _state.update { it.copy(form = it.form.copy(targetDays = days)) }
    fun setReminderHour(hour: Int?) = _state.update { it.copy(form = it.form.copy(reminderHour = hour)) }

    fun start() {
        val s = _state.value
        if (!s.canStart) return
        _state.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            val today = LocalDate.now()
            val spec = s.copy(today = today).spec()
            val record = ExperimentRecord(
                spec = spec,
                name = ExperimentMath.upperFirst(spec.behavior),
                reminderHour = s.form.reminderHour,
            )
            try {
                val id = repository.start(record)
                _state.update { it.copy(saving = false, doneId = id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, failed = true) }
            }
        }
    }
}
