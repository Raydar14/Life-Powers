package com.lifepowers.app.data

import com.lifepowers.app.domain.EventKinds
import com.lifepowers.app.domain.PhaseForDay
import com.lifepowers.app.domain.ProfileRepository
import com.lifepowers.app.domain.UserProfileState
import com.lifepowers.app.domain.nowZoneOffsetMin
import com.lifepowers.core.Baseline
import com.lifepowers.core.Contraception
import com.lifepowers.core.CycleEvidence
import com.lifepowers.core.HormonalModel
import com.lifepowers.core.HormonalModelSelector
import com.lifepowers.core.HormonalProfile
import com.lifepowers.core.MenstrualStatus
import com.lifepowers.core.Metrics
import com.lifepowers.core.PhaseLabeler
import com.lifepowers.core.SexAtBirth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val dao: UserProfileDao,
    private val events: BehaviorEventDao,
    private val samples: MetricSampleDao,
) : ProfileRepository {

    override val profile: Flow<UserProfileState> = dao.observe().map { it?.toState() ?: UserProfileState() }

    override suspend fun save(profile: UserProfileState) {
        val model = HormonalModelSelector.select(profile.toHormonalProfile())
        dao.upsert(profile.copy(hormonalModel = model).toEntity(System.currentTimeMillis()))
    }

    /**
     * Labels one date. Hormone evidence comes from stored hormone.lh and hormone.pdg samples in
     * the 25 days up to the date; the period anchor is the newer of the profile's date and the
     * latest period_start event.
     */
    override suspend fun phaseFor(date: LocalDate): PhaseForDay {
        val state = dao.get()?.toState() ?: UserProfileState()
        val zone = nowZoneOffsetMin()
        val endOfDay = Dates.startOfDayMs(date.plusDays(1), zone) - 1

        val eventStart = events.latestOfKind(EventKinds.PERIOD_START, endOfDay)
            ?.let { Dates.localDate(it.startUtc, it.zoneOffsetMin) }
        val anchor = listOfNotNull(state.lastPeriodStart, eventStart).maxOrNull()
        val hp = state.toHormonalProfile().copy(lastPeriodStart = anchor)

        val windowStart = Dates.startOfDayMs(date.minusDays(25), zone)
        val lh = samples.forMetricBetween(Metrics.HORMONE_LH, windowStart, endOfDay)
        val surge = lh.maxByOrNull { it.value }?.let { peak ->
            val others = lh.filter { it !== peak }.map { it.value }
            if (others.isNotEmpty() && peak.value >= 1.5 * Baseline.median(others)) {
                Dates.localDate(peak.startUtc, peak.zoneOffsetMin)
            } else null
        }
        val pdgRise = surge?.let { s ->
            samples.forMetricBetween(Metrics.HORMONE_PDG, Dates.startOfDayMs(s.plusDays(1), zone), endOfDay)
                .filter { it.value > PDG_LUTEAL_THRESHOLD }
                .minByOrNull { it.startUtc }
                ?.let { Dates.localDate(it.startUtc, it.zoneOffsetMin) }
        }
        val lastDose = events.latestOfKind(HORMONE_DOSE_KIND, endOfDay)
            ?.let { Dates.localDate(it.startUtc, it.zoneOffsetMin) }

        val label = PhaseLabeler.label(
            date,
            state.hormonalModel,
            hp,
            CycleEvidence(lhSurgeDate = surge, pdgRiseDate = pdgRise, packStart = anchor, lastDose = lastDose),
        )
        return PhaseForDay(date, label.phase, label.cycleDay, label.confidence, label.basis)
    }

    companion object {
        const val PDG_LUTEAL_THRESHOLD = 5.0
        const val HORMONE_DOSE_KIND = "hormone_dose"
    }
}

fun UserProfileState.toHormonalProfile() = HormonalProfile(
    sexAtBirth = sexAtBirth,
    menstrualStatus = menstrualStatus,
    cycleLengthDays = cycleLengthDays,
    cycleVariabilityDays = cycleVariabilityDays,
    lastPeriodStart = lastPeriodStart,
    contraception = contraception,
    onHrt = !hrt.isNullOrBlank(),
    onGenderAffirmingHormones = !genderAffirmingHormones.isNullOrBlank(),
    dueOrDeliveryDate = dueOrDeliveryDate,
)

fun UserProfileState.toEntity(updatedAt: Long) = UserProfileEntity(
    id = 1,
    displayName = displayName,
    dateOfBirth = dateOfBirth?.toString(),
    sexAtBirth = sexAtBirth?.name,
    genderIdentity = genderIdentity,
    menstrualStatus = menstrualStatus?.name,
    cycleLengthDays = cycleLengthDays,
    cycleVariabilityDays = cycleVariabilityDays,
    lastPeriodStart = lastPeriodStart?.toString(),
    contraception = contraception.name,
    hrt = hrt,
    genderAffirmingHormones = genderAffirmingHormones,
    dueOrDeliveryDate = dueOrDeliveryDate?.toString(),
    medications = medications,
    hormonalModel = hormonalModel.name,
    onboardingDone = onboardingDone,
    playFirst = playFirst,
    updatedAt = updatedAt,
)

fun UserProfileEntity.toState() = UserProfileState(
    displayName = displayName,
    dateOfBirth = Dates.parseIso(dateOfBirth),
    sexAtBirth = sexAtBirth?.let { enumOrNull<SexAtBirth>(it) },
    genderIdentity = genderIdentity,
    menstrualStatus = menstrualStatus?.let { enumOrNull<MenstrualStatus>(it) },
    cycleLengthDays = cycleLengthDays,
    cycleVariabilityDays = cycleVariabilityDays,
    lastPeriodStart = Dates.parseIso(lastPeriodStart),
    contraception = enumOrNull<Contraception>(contraception) ?: Contraception.NONE,
    hrt = hrt,
    genderAffirmingHormones = genderAffirmingHormones,
    dueOrDeliveryDate = Dates.parseIso(dueOrDeliveryDate),
    medications = medications,
    hormonalModel = enumOrNull<HormonalModel>(hormonalModel) ?: HormonalModel.NO_CYCLE,
    onboardingDone = onboardingDone,
    playFirst = playFirst,
)

inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
    enumValues<E>().firstOrNull { it.name == name }
