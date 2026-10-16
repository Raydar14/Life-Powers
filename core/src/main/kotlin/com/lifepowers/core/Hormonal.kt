package com.lifepowers.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class SexAtBirth { FEMALE, MALE, INTERSEX, PREFER_NOT_TO_SAY }

enum class MenstrualStatus { REGULAR, IRREGULAR, PERIMENOPAUSE, POSTMENOPAUSE, AMENORRHEA, PREGNANT, POSTPARTUM, NOT_APPLICABLE }

enum class Contraception { NONE, COMBINED_PILL, PROGESTIN_PILL, HORMONAL_IUD, COPPER_IUD, IMPLANT, INJECTION, RING, PATCH, OTHER }

enum class HormonalModel { NATURAL_CYCLE, NATURAL_CYCLE_LOW_TRUST, PACK_RHYTHM, SUPPRESSED_UNCERTAIN, NO_CYCLE, DOSING_RHYTHM, GESTATIONAL }

enum class Phase { MENSTRUAL, FOLLICULAR, OVULATORY, LUTEAL, UNKNOWN, ACTIVE_1, ACTIVE_2, ACTIVE_3, HORMONE_FREE, DOSE_D0_2, DOSE_D3_6, DOSE_D7_PLUS, GESTATIONAL_WEEK }

/** The hormonal part of the profile. Everything the model selector needs. */
data class HormonalProfile(
    val sexAtBirth: SexAtBirth?,
    val menstrualStatus: MenstrualStatus?,
    val cycleLengthDays: Int? = null,
    val cycleVariabilityDays: Int? = null,
    val lastPeriodStart: LocalDate? = null,
    val contraception: Contraception = Contraception.NONE,
    val onHrt: Boolean = false,
    val onGenderAffirmingHormones: Boolean = false,
    val dueOrDeliveryDate: LocalDate? = null,
)

object HormonalModelSelector {
    /** Variability above this disables the calendar fallback. */
    const val LOW_TRUST_VARIABILITY_DAYS = 5

    fun select(p: HormonalProfile): HormonalModel {
        if (p.onGenderAffirmingHormones || p.onHrt) return HormonalModel.DOSING_RHYTHM
        if (p.sexAtBirth == SexAtBirth.MALE) return HormonalModel.NO_CYCLE
        when (p.menstrualStatus) {
            MenstrualStatus.PREGNANT, MenstrualStatus.POSTPARTUM -> return HormonalModel.GESTATIONAL
            MenstrualStatus.POSTMENOPAUSE, MenstrualStatus.AMENORRHEA, MenstrualStatus.NOT_APPLICABLE -> return HormonalModel.NO_CYCLE
            else -> {}
        }
        return when (p.contraception) {
            Contraception.COMBINED_PILL, Contraception.RING, Contraception.PATCH -> HormonalModel.PACK_RHYTHM
            Contraception.PROGESTIN_PILL, Contraception.HORMONAL_IUD, Contraception.IMPLANT, Contraception.INJECTION -> HormonalModel.SUPPRESSED_UNCERTAIN
            Contraception.NONE, Contraception.COPPER_IUD, Contraception.OTHER -> {
                val irregular = p.menstrualStatus == MenstrualStatus.IRREGULAR ||
                    p.menstrualStatus == MenstrualStatus.PERIMENOPAUSE ||
                    (p.cycleVariabilityDays ?: 0) > LOW_TRUST_VARIABILITY_DAYS
                if (irregular) HormonalModel.NATURAL_CYCLE_LOW_TRUST else HormonalModel.NATURAL_CYCLE
            }
        }
    }

    /** Whether the correlation engine should stratify by phase for this model. */
    fun hasPhaseDimension(model: HormonalModel): Boolean = model != HormonalModel.NO_CYCLE
}

data class PhaseLabel(val phase: Phase, val cycleDay: Int?, val confidence: Double, val basis: String)

/** Evidence the labeler can use for one date. */
data class CycleEvidence(
    /** Day of the LH surge in this cycle, if a strip caught it. */
    val lhSurgeDate: LocalDate? = null,
    /** First day PdG rose above the threshold, confirming the luteal phase. */
    val pdgRiseDate: LocalDate? = null,
    /** Date the current pack started, for pack rhythm. */
    val packStart: LocalDate? = null,
    /** Date of the most recent hormone dose, for dosing rhythm. */
    val lastDose: LocalDate? = null,
)

object PhaseLabeler {
    fun label(date: LocalDate, model: HormonalModel, profile: HormonalProfile, evidence: CycleEvidence = CycleEvidence()): PhaseLabel {
        return when (model) {
            HormonalModel.NATURAL_CYCLE, HormonalModel.NATURAL_CYCLE_LOW_TRUST -> natural(date, model, profile, evidence)
            HormonalModel.PACK_RHYTHM -> pack(date, profile, evidence)
            HormonalModel.SUPPRESSED_UNCERTAIN -> {
                if (evidence.lhSurgeDate != null) natural(date, HormonalModel.NATURAL_CYCLE_LOW_TRUST, profile, evidence)
                else PhaseLabel(Phase.UNKNOWN, null, 0.2, "hormone")
            }
            HormonalModel.NO_CYCLE -> PhaseLabel(Phase.UNKNOWN, null, 1.0, "none")
            HormonalModel.DOSING_RHYTHM -> dosing(date, evidence)
            HormonalModel.GESTATIONAL -> gestational(date, profile)
        }
    }

    private fun natural(date: LocalDate, model: HormonalModel, p: HormonalProfile, e: CycleEvidence): PhaseLabel {
        val start = p.lastPeriodStart ?: return PhaseLabel(Phase.UNKNOWN, null, 0.0, "calendar")
        val day = ChronoUnit.DAYS.between(start, date).toInt() + 1
        if (day < 1) return PhaseLabel(Phase.UNKNOWN, null, 0.0, "calendar")
        val length = p.cycleLengthDays ?: 28

        // Hormone evidence first.
        e.lhSurgeDate?.let { surge ->
            val sinceSurge = ChronoUnit.DAYS.between(surge, date).toInt()
            val phase = when {
                sinceSurge < -1 -> if (day <= 5) Phase.MENSTRUAL else Phase.FOLLICULAR
                sinceSurge in -1..1 -> Phase.OVULATORY
                else -> Phase.LUTEAL
            }
            val conf = if (e.pdgRiseDate != null && phase == Phase.LUTEAL) 0.95 else 0.85
            return PhaseLabel(phase, day, conf, "hormone")
        }

        // Calendar fallback, disabled for low trust.
        if (model == HormonalModel.NATURAL_CYCLE_LOW_TRUST) {
            return if (day <= 5) PhaseLabel(Phase.MENSTRUAL, day, 0.6, "calendar")
            else PhaseLabel(Phase.UNKNOWN, day, 0.2, "calendar")
        }
        val ovulationDay = length - 14
        val phase = when {
            day <= 5 -> Phase.MENSTRUAL
            day < ovulationDay - 1 -> Phase.FOLLICULAR
            day <= ovulationDay + 1 -> Phase.OVULATORY
            day <= length + 3 -> Phase.LUTEAL
            else -> Phase.UNKNOWN
        }
        return PhaseLabel(phase, day, 0.5, "calendar")
    }

    private fun pack(date: LocalDate, p: HormonalProfile, e: CycleEvidence): PhaseLabel {
        val start = e.packStart ?: p.lastPeriodStart ?: return PhaseLabel(Phase.UNKNOWN, null, 0.0, "pack_calendar")
        val day = (ChronoUnit.DAYS.between(start, date).toInt() % 28 + 28) % 28 + 1
        val phase = when {
            day <= 7 -> Phase.ACTIVE_1
            day <= 14 -> Phase.ACTIVE_2
            day <= 21 -> Phase.ACTIVE_3
            else -> Phase.HORMONE_FREE
        }
        return PhaseLabel(phase, day, 0.9, "pack_calendar")
    }

    private fun dosing(date: LocalDate, e: CycleEvidence): PhaseLabel {
        val last = e.lastDose ?: return PhaseLabel(Phase.UNKNOWN, null, 0.0, "dose_log")
        val since = ChronoUnit.DAYS.between(last, date).toInt()
        val phase = when {
            since < 0 -> Phase.UNKNOWN
            since <= 2 -> Phase.DOSE_D0_2
            since <= 6 -> Phase.DOSE_D3_6
            else -> Phase.DOSE_D7_PLUS
        }
        return PhaseLabel(phase, since, 0.9, "dose_log")
    }

    private fun gestational(date: LocalDate, p: HormonalProfile): PhaseLabel {
        val anchor = p.dueOrDeliveryDate ?: return PhaseLabel(Phase.UNKNOWN, null, 0.0, "calendar")
        val weeks = ChronoUnit.WEEKS.between(anchor.minusWeeks(40), date).toInt()
        return PhaseLabel(Phase.GESTATIONAL_WEEK, weeks, 0.9, "calendar")
    }
}
