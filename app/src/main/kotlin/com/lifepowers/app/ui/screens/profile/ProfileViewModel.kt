package com.lifepowers.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepowers.app.domain.EventRepository
import com.lifepowers.app.domain.ProfileRepository
import com.lifepowers.app.domain.UserProfileState
import com.lifepowers.app.ui.components.Fmt
import com.lifepowers.core.Contraception
import com.lifepowers.core.HormonalModel
import com.lifepowers.core.HormonalModelSelector
import com.lifepowers.core.HormonalProfile
import com.lifepowers.core.MenstrualStatus
import com.lifepowers.core.SexAtBirth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The editable copy of the profile. Dates and numbers stay as text until save. */
data class ProfileForm(
    val displayName: String = "",
    val dateOfBirth: String = "",
    val sexAtBirth: SexAtBirth? = null,
    val genderIdentity: String = "",
    val menstrualStatus: MenstrualStatus? = null,
    val cycleLengthDays: String = "",
    val cycleVariabilityDays: String = "",
    val lastPeriodStart: String = "",
    val contraception: Contraception = Contraception.NONE,
    val hrt: String = "",
    val medications: String = "",
) {
    val model: HormonalModel
        get() = HormonalModelSelector.select(
            HormonalProfile(
                sexAtBirth = sexAtBirth,
                menstrualStatus = menstrualStatus,
                cycleLengthDays = cycleLengthDays.trim().toIntOrNull(),
                cycleVariabilityDays = cycleVariabilityDays.trim().toIntOrNull(),
                lastPeriodStart = parseDate(lastPeriodStart),
                contraception = contraception,
                onHrt = hrt.isNotBlank() && !hrt.trim().equals("none", ignoreCase = true),
            ),
        )

    companion object {
        fun from(p: UserProfileState) = ProfileForm(
            displayName = p.displayName ?: "",
            dateOfBirth = p.dateOfBirth?.toString() ?: "",
            sexAtBirth = p.sexAtBirth,
            genderIdentity = p.genderIdentity ?: "",
            menstrualStatus = p.menstrualStatus,
            cycleLengthDays = p.cycleLengthDays?.toString() ?: "",
            cycleVariabilityDays = p.cycleVariabilityDays?.toString() ?: "",
            lastPeriodStart = p.lastPeriodStart?.toString() ?: "",
            contraception = p.contraception,
            hrt = p.hrt ?: "",
            medications = p.medications ?: "",
        )

        fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim()) }.getOrNull()
    }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    eventRepository: EventRepository,
) : ViewModel() {

    private val _form = MutableStateFlow<ProfileForm?>(null)
    val form: StateFlow<ProfileForm?> = _form.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val today: LocalDate = LocalDate.now()

    /** Distinct symptom kinds logged in the last 30 days, most recent first. */
    val recentSymptoms: StateFlow<List<String>> = eventRepository
        .between(Fmt.startOfDayMs(today.minusDays(30)), Fmt.endOfDayMs(today))
        .map { events -> events.filter { Fmt.isSymptom(it.kind) }.sortedByDescending { it.startUtcMs }.map { it.kind }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val p = profileRepository.profile.first()
            if (_form.value == null) _form.value = ProfileForm.from(p)
        }
    }

    fun update(change: (ProfileForm) -> ProfileForm) {
        val current = _form.value ?: return
        _form.value = change(current)
    }

    fun save(onDone: () -> Unit) {
        val f = _form.value ?: return
        _saving.value = true
        viewModelScope.launch {
            val current = profileRepository.profile.first()
            val next = current.copy(
                displayName = f.displayName.trim().takeIf { it.isNotEmpty() },
                dateOfBirth = ProfileForm.parseDate(f.dateOfBirth),
                sexAtBirth = f.sexAtBirth,
                genderIdentity = f.genderIdentity.trim().takeIf { it.isNotEmpty() },
                menstrualStatus = f.menstrualStatus,
                cycleLengthDays = f.cycleLengthDays.trim().toIntOrNull(),
                cycleVariabilityDays = f.cycleVariabilityDays.trim().toIntOrNull(),
                lastPeriodStart = ProfileForm.parseDate(f.lastPeriodStart),
                contraception = f.contraception,
                hrt = f.hrt.trim().takeIf { it.isNotEmpty() && !it.equals("none", ignoreCase = true) },
                medications = f.medications.trim().takeIf { it.isNotEmpty() },
                hormonalModel = f.model,
                onboardingDone = true,
            )
            runCatching { profileRepository.save(next) }
            _saving.value = false
            onDone()
        }
    }
}

fun HormonalModel.title(): String = when (this) {
    HormonalModel.NATURAL_CYCLE -> "Natural cycle, regular"
    HormonalModel.NATURAL_CYCLE_LOW_TRUST -> "Natural cycle, calendar not trusted"
    HormonalModel.PACK_RHYTHM -> "Pack rhythm"
    HormonalModel.SUPPRESSED_UNCERTAIN -> "Suppressed, uncertain"
    HormonalModel.NO_CYCLE -> "No cycle"
    HormonalModel.DOSING_RHYTHM -> "Dosing rhythm"
    HormonalModel.GESTATIONAL -> "Gestational"
}

fun HormonalModel.explain(): String = when (this) {
    HormonalModel.NATURAL_CYCLE -> "Hormone strips first, temperature second, calendar last. Four phases."
    HormonalModel.NATURAL_CYCLE_LOW_TRUST -> "Strips and temperature only. The calendar names the period and nothing else."
    HormonalModel.PACK_RHYTHM -> "Three active weeks and a hormone free week, counted from the pack start."
    HormonalModel.SUPPRESSED_UNCERTAIN -> "No phase unless a strip catches a surge. Symptoms still count."
    HormonalModel.NO_CYCLE -> "No phase dimension. Everything else works the same."
    HormonalModel.DOSING_RHYTHM -> "Days since the last dose, in three bands."
    HormonalModel.GESTATIONAL -> "Weeks counted from the due or delivery date."
}

fun SexAtBirth.label(): String = when (this) {
    SexAtBirth.FEMALE -> "Female"
    SexAtBirth.MALE -> "Male"
    SexAtBirth.INTERSEX -> "Intersex"
    SexAtBirth.PREFER_NOT_TO_SAY -> "Prefer not to say"
}

fun MenstrualStatus.label(): String = when (this) {
    MenstrualStatus.REGULAR -> "Regular cycles"
    MenstrualStatus.IRREGULAR -> "Irregular cycles"
    MenstrualStatus.PERIMENOPAUSE -> "Perimenopause"
    MenstrualStatus.POSTMENOPAUSE -> "Postmenopause"
    MenstrualStatus.AMENORRHEA -> "No periods"
    MenstrualStatus.PREGNANT -> "Pregnant"
    MenstrualStatus.POSTPARTUM -> "Postpartum"
    MenstrualStatus.NOT_APPLICABLE -> "Not applicable"
}

fun Contraception.label(): String = when (this) {
    Contraception.NONE -> "None"
    Contraception.COMBINED_PILL -> "Combined pill"
    Contraception.PROGESTIN_PILL -> "Progestin only pill"
    Contraception.HORMONAL_IUD -> "Hormonal IUD"
    Contraception.COPPER_IUD -> "Copper IUD"
    Contraception.IMPLANT -> "Implant"
    Contraception.INJECTION -> "Injection"
    Contraception.RING -> "Ring"
    Contraception.PATCH -> "Patch"
    Contraception.OTHER -> "Other"
}
