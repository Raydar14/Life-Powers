package com.lifepowers.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class HormonalTest {
    private val base = HormonalProfile(SexAtBirth.FEMALE, MenstrualStatus.REGULAR, 29, 2, LocalDate.of(2026, 9, 26))

    @Test fun selectorPicksModels() {
        assertEquals(HormonalModel.NATURAL_CYCLE, HormonalModelSelector.select(base))
        assertEquals(HormonalModel.PACK_RHYTHM, HormonalModelSelector.select(base.copy(contraception = Contraception.COMBINED_PILL)))
        assertEquals(HormonalModel.SUPPRESSED_UNCERTAIN, HormonalModelSelector.select(base.copy(contraception = Contraception.HORMONAL_IUD)))
        assertEquals(HormonalModel.NATURAL_CYCLE_LOW_TRUST, HormonalModelSelector.select(base.copy(menstrualStatus = MenstrualStatus.PERIMENOPAUSE)))
        assertEquals(HormonalModel.NO_CYCLE, HormonalModelSelector.select(base.copy(sexAtBirth = SexAtBirth.MALE)))
        assertEquals(HormonalModel.DOSING_RHYTHM, HormonalModelSelector.select(base.copy(onGenderAffirmingHormones = true)))
        assertEquals(HormonalModel.GESTATIONAL, HormonalModelSelector.select(base.copy(menstrualStatus = MenstrualStatus.PREGNANT)))
    }

    @Test fun calendarLabelsDay21AsLuteal() {
        val l = PhaseLabeler.label(LocalDate.of(2026, 10, 16), HormonalModel.NATURAL_CYCLE, base)
        assertEquals(Phase.LUTEAL, l.phase)
        assertEquals(21, l.cycleDay)
        assertEquals("calendar", l.basis)
    }

    @Test fun hormoneEvidenceBeatsCalendar() {
        val e = CycleEvidence(lhSurgeDate = LocalDate.of(2026, 10, 15))
        val l = PhaseLabeler.label(LocalDate.of(2026, 10, 16), HormonalModel.NATURAL_CYCLE, base, e)
        assertEquals(Phase.OVULATORY, l.phase)
        assertEquals("hormone", l.basis)
    }

    @Test fun packRhythmWeeks() {
        val p = base.copy(contraception = Contraception.COMBINED_PILL)
        val e = CycleEvidence(packStart = LocalDate.of(2026, 10, 1))
        assertEquals(Phase.ACTIVE_3, PhaseLabeler.label(LocalDate.of(2026, 10, 16), HormonalModel.PACK_RHYTHM, p, e).phase)
        assertEquals(Phase.HORMONE_FREE, PhaseLabeler.label(LocalDate.of(2026, 10, 25), HormonalModel.PACK_RHYTHM, p, e).phase)
    }

    @Test fun lowTrustHasNoCalendarGuessAfterMenses() {
        val p = base.copy(menstrualStatus = MenstrualStatus.IRREGULAR)
        assertEquals(Phase.UNKNOWN, PhaseLabeler.label(LocalDate.of(2026, 10, 16), HormonalModel.NATURAL_CYCLE_LOW_TRUST, p).phase)
    }
}
